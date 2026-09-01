package com.balandman.pawgress.sync

import com.balandman.pawgress.data.LogEntry
import com.balandman.pawgress.data.SheetRow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put

class SheetsException(val code: Int, message: String) : Exception(message) {
    val isAuthExpired: Boolean get() = code == 401
    val isMissing: Boolean get() = code == 404
}

data class SpreadsheetRef(val id: String, val url: String)

/**
 * Thin wrapper over the Sheets REST API. Every call suspends, so callers must
 * be in a coroutine (the original Android version used blocking OkHttp calls
 * from a background dispatcher — Ktor's client is suspend-based instead, so
 * the same "don't call this from the main thread" rule now falls out of it
 * being a suspend function rather than needing an explicit dispatcher).
 *
 * [client] defaults to a bare `HttpClient()` with no engine specified — each
 * platform supplies exactly one Ktor engine dependency (`ktor-client-okhttp`
 * in androidMain, `ktor-client-darwin` in iosMain), which Ktor discovers
 * automatically, so this file never names a platform-specific engine type.
 */
class SheetsApi(
    private val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 30_000
        }
    }
) {

    // ------------------------------------------------------------------ account

    suspend fun userEmail(token: String): String? {
        val body = get("https://www.googleapis.com/oauth2/v3/userinfo", token)
        return parseObject(body).optString("email")?.takeIf { it.isNotEmpty() }
    }

    // -------------------------------------------------------------- spreadsheet

    suspend fun createLogSpreadsheet(token: String, title: String = "Pawgress"): SpreadsheetRef {
        val payload = buildJsonObject {
            put("properties", buildJsonObject { put("title", title) })
            putJsonArray("sheets") {
                add(
                    buildJsonObject {
                        put(
                            "properties",
                            buildJsonObject {
                                put("title", SHEET_TITLE)
                                put("index", 0)
                            }
                        )
                    }
                )
            }
        }
        val body = post("https://sheets.googleapis.com/v4/spreadsheets", token, payload)
        val json = parseObject(body)
        val id = json.optString("spreadsheetId").orEmpty()
        if (id.isEmpty()) throw SheetsException(-1, "Sheets did not return a spreadsheet id.")
        val url = json.optString("spreadsheetUrl")
            ?.takeIf { it.isNotEmpty() }
            ?: "https://docs.google.com/spreadsheets/d/$id/edit"

        appendValues(token, id, listOf(HEADER))
        runCatching { formatHeader(token, id) }   // cosmetic only
        return SpreadsheetRef(id, url)
    }

    /**
     * Rewrites just the header row to the current column set. Safe to call any
     * time: it only ever touches row 1, so a sheet created before Area/Difficulty
     * existed picks up the new headers without disturbing a single data row —
     * older rows simply have blank cells under the new columns.
     */
    suspend fun ensureHeaderUpToDate(token: String, spreadsheetId: String) {
        val range = "$SHEET_TITLE!A1".encodeURLParameter()
        val values = buildJsonArray { add(buildJsonArray { HEADER.forEach { add(it) } }) }
        put(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range" +
                "?valueInputOption=USER_ENTERED",
            token,
            buildJsonObject { put("values", values) }
        )
    }

    /** Every data row in the log sheet, oldest first — the raw material for a restore. */
    suspend fun readAllRows(token: String, spreadsheetId: String): List<SheetRow> {
        val range = "$SHEET_TITLE!A2:G".encodeURLParameter()
        val body = get(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range",
            token
        )
        val rows = parseObject(body).optJsonArray("values") ?: return emptyList()
        val result = mutableListOf<SheetRow>()
        for (rowElement in rows) {
            val cells = rowElement as? JsonArray ?: continue
            fun cell(index: Int): String = cells.getOrNull(index)?.asStringOrNull().orEmpty()

            val date = cell(0)
            val time = cell(1)
            val exercise = cell(2)
            val weightText = cell(3)
            val entryId = cell(4)
            val area = cell(5).takeIf { it.isNotBlank() }
            val difficulty = cell(6).takeIf { it.isNotBlank() }

            if (exercise.isBlank() || entryId.isBlank()) continue
            val weight = weightText.toIntOrNull() ?: continue
            val loggedAt = parseLoggedAt(date, time) ?: continue

            result += SheetRow(
                loggedAt = loggedAt,
                exercise = exercise,
                area = area,
                weight = weight,
                difficultyLabel = difficulty,
                entryId = entryId,
            )
        }
        return result
    }

    suspend fun spreadsheetExists(token: String, spreadsheetId: String): Boolean = try {
        get(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=spreadsheetId",
            token
        )
        true
    } catch (e: SheetsException) {
        if (e.isMissing) false else throw e
    }

    // --------------------------------------------------------------------- rows

    suspend fun appendEntries(token: String, spreadsheetId: String, entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        appendValues(token, spreadsheetId, entries.map { it.toRow() })
    }

    /**
     * Removes rows for entries that were undone or corrected after they had
     * already been pushed. Rows are matched on the Entry ID column, and deleted
     * one at a time because each deletion shifts everything below it.
     */
    suspend fun deleteEntries(
        token: String,
        spreadsheetId: String,
        entryIds: List<String>,
    ): List<String> {
        if (entryIds.isEmpty()) return emptyList()
        val gid = sheetGid(token, spreadsheetId) ?: return emptyList()
        val removed = mutableListOf<String>()

        for (entryId in entryIds) {
            val rowIndex = findRowIndex(token, spreadsheetId, entryId)
            if (rowIndex == null) {
                // Already gone, or never made it up — either way, stop tracking it.
                removed += entryId
                continue
            }
            val payload = buildJsonObject {
                putJsonArray("requests") {
                    add(
                        buildJsonObject {
                            put(
                                "deleteDimension",
                                buildJsonObject {
                                    put(
                                        "range",
                                        buildJsonObject {
                                            put("sheetId", gid)
                                            put("dimension", "ROWS")
                                            put("startIndex", rowIndex)
                                            put("endIndex", rowIndex + 1)
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            }
            post(
                "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate",
                token,
                payload
            )
            removed += entryId
        }
        return removed
    }

    // ------------------------------------------------------------------ internals

    private suspend fun appendValues(token: String, spreadsheetId: String, rows: List<List<String>>) {
        val values = buildJsonArray {
            rows.forEach { row -> add(buildJsonArray { row.forEach { add(it) } }) }
        }
        val range = "$SHEET_TITLE!A1".encodeURLParameter()
        post(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range:append" +
                "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS",
            token,
            buildJsonObject { put("values", values) }
        )
    }

    private suspend fun findRowIndex(token: String, spreadsheetId: String, entryId: String): Int? {
        val range = "$SHEET_TITLE!E:E".encodeURLParameter()
        val body = get(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range",
            token
        )
        val rows = parseObject(body).optJsonArray("values") ?: return null
        for (i in rows.indices) {
            val cell = (rows[i] as? JsonArray)?.getOrNull(0)?.asStringOrNull().orEmpty()
            if (cell == entryId) return i
        }
        return null
    }

    private suspend fun sheetGid(token: String, spreadsheetId: String): Int? {
        val body = get(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId" +
                "?fields=sheets(properties(sheetId,title))",
            token
        )
        val sheets = parseObject(body).optJsonArray("sheets") ?: return null
        for (sheetElement in sheets) {
            val props = (sheetElement as? JsonObject)?.optJsonObject("properties") ?: continue
            if (props.optString("title") == SHEET_TITLE) return props.optInt("sheetId")
        }
        return null
    }

    private suspend fun formatHeader(token: String, spreadsheetId: String) {
        val gid = sheetGid(token, spreadsheetId) ?: return
        val requests = buildJsonArray {
            add(
                buildJsonObject {
                    put(
                        "repeatCell",
                        buildJsonObject {
                            put(
                                "range",
                                buildJsonObject {
                                    put("sheetId", gid)
                                    put("startRowIndex", 0)
                                    put("endRowIndex", 1)
                                }
                            )
                            put(
                                "cell",
                                buildJsonObject {
                                    put(
                                        "userEnteredFormat",
                                        buildJsonObject {
                                            put("textFormat", buildJsonObject { put("bold", true) })
                                        }
                                    )
                                }
                            )
                            put("fields", "userEnteredFormat.textFormat.bold")
                        }
                    )
                }
            )
            add(
                buildJsonObject {
                    put(
                        "updateSheetProperties",
                        buildJsonObject {
                            put(
                                "properties",
                                buildJsonObject {
                                    put("sheetId", gid)
                                    put(
                                        "gridProperties",
                                        buildJsonObject { put("frozenRowCount", 1) }
                                    )
                                }
                            )
                            put("fields", "gridProperties.frozenRowCount")
                        }
                    )
                }
            )
        }
        post(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate",
            token,
            buildJsonObject { put("requests", requests) }
        )
    }

    private suspend fun get(url: String, token: String): String = execute {
        client.get(url) { header("Authorization", "Bearer $token") }
    }

    private suspend fun post(url: String, token: String, payload: JsonObject): String = execute {
        client.post(url) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
    }

    private suspend fun put(url: String, token: String, payload: JsonObject): String = execute {
        client.put(url) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
    }

    private suspend fun execute(request: suspend () -> HttpResponse): String {
        val response = request()
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw SheetsException(response.status.value, describe(response.status.value, body))
        }
        return body
    }

    private fun describe(code: Int, body: String): String {
        val apiMessage = runCatching {
            parseObject(body).optJsonObject("error")?.optString("message")
        }.getOrNull().orEmpty()

        return when (code) {
            401 -> "Google sign-in expired."
            403 -> if (apiMessage.contains("has not been used", ignoreCase = true) ||
                apiMessage.contains("disabled", ignoreCase = true)
            ) {
                "The Google Sheets API is not enabled for this project yet. See SETUP.md step 3."
            } else {
                "Google refused the request: $apiMessage"
            }

            404 -> "The Pawgress spreadsheet no longer exists."
            else -> if (apiMessage.isNotEmpty()) apiMessage else "Sheets request failed ($code)."
        }
    }

    companion object {
        private const val SHEET_TITLE = "Log"

        // Area and Difficulty were added after Entry ID was already column E on
        // real sheets — they go on the end rather than between Exercise and
        // Weight, so every existing row (and the hardcoded "E:E"/"A:G" ranges
        // above) keeps meaning exactly what it always did.
        private val HEADER =
            listOf("Date", "Time", "Exercise", "Weight (lb)", "Entry ID", "Area", "Difficulty")

        private fun Int.pad2(): String = if (this < 10) "0$this" else this.toString()

        private fun LogEntry.toRow(): List<String> {
            val moment = Instant.fromEpochMilliseconds(loggedAt)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            return listOf(
                "${moment.year}-${moment.monthNumber.pad2()}-${moment.dayOfMonth.pad2()}",
                "${moment.hour.pad2()}:${moment.minute.pad2()}",
                machineName,
                weight.toString(),
                id,
                machineGroup.label,
                difficulty?.label.orEmpty(),
            )
        }

        /**
         * Hand-rolled instead of `java.time.format.DateTimeFormatter` (JVM-only)
         * — the sheet only ever holds these two fixed, simple patterns
         * ("yyyy-MM-dd" and "HH:mm"), so a small manual parser is safer here
         * than pulling in a bigger multiplatform formatting API this session
         * can't verify by compiling.
         */
        private fun parseLoggedAt(date: String, time: String): Long? {
            val dateParts = date.split("-")
            val timeParts = time.split(":")
            if (dateParts.size != 3 || timeParts.size != 2) return null
            return try {
                val year = dateParts[0].toInt()
                val month = dateParts[1].toInt()
                val day = dateParts[2].toInt()
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                LocalDateTime(year, month, day, hour, minute)
                    .toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()
            } catch (e: Exception) {
                null
            }
        }

        // ---------------------------------------------------- small JSON helpers
        // kotlinx.serialization.json's JsonObject/JsonArray are read-only views
        // with no org.json-style optString/optInt — these mirror that forgiving,
        // never-throws-on-a-missing-or-wrong-typed-field behavior, since every
        // response here is parsed dynamically rather than into a typed class.

        private fun parseObject(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

        private fun JsonElement.asStringOrNull(): String? =
            (this as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.optString(key: String): String? = this[key]?.asStringOrNull()

        private fun JsonObject.optJsonObject(key: String): JsonObject? = this[key] as? JsonObject

        private fun JsonObject.optJsonArray(key: String): JsonArray? = this[key] as? JsonArray

        private fun JsonObject.optInt(key: String, default: Int = 0): Int =
            (this[key] as? JsonPrimitive)?.intOrNull ?: default
    }
}
