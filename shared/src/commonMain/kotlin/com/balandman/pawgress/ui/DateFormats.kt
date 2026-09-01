package com.balandman.pawgress.ui

import com.balandman.pawgress.data.GymDay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Small hand-rolled replacement for `java.time.format.DateTimeFormatter`, which has no
 * multiplatform equivalent. `kotlinx-datetime`'s `LocalDate`/`DayOfWeek`/`Month` are plain
 * enums with no locale-aware display-name API in common code, so this hardcodes the English
 * abbreviations/names the original app's patterns actually produced -- there was never any
 * other locale in play (see [GymDay] for the same "4am gym day" convention these build on).
 *
 * Covers exactly the six `DateTimeFormatter.ofPattern(...)` calls found across the original
 * screens: `"h:mm a"`, `"MMM d"`, `"EEEE, MMM d"`, `"MMM d 'at' h:mm a"`, `"EEE"`, `"MMM"`.
 */
object DateFormats {

    private val MONTH_ABBR = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    private val WEEKDAY_ABBR = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val WEEKDAY_FULL = arrayOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    )

    private fun monthAbbr(month: Month): String = MONTH_ABBR[month.number - 1]
    private fun weekdayAbbr(dayOfWeek: DayOfWeek): String = WEEKDAY_ABBR[dayOfWeek.isoDayNumber - 1]
    private fun weekdayFull(dayOfWeek: DayOfWeek): String = WEEKDAY_FULL[dayOfWeek.isoDayNumber - 1]

    /** `"MMM"` -- e.g. "Aug". */
    fun monthAbbr(date: LocalDate): String = monthAbbr(date.month)

    /** `"EEE"` -- e.g. "Sat". */
    fun weekdayAbbr(date: LocalDate): String = weekdayAbbr(date.dayOfWeek)

    /** `"MMM d"` -- e.g. "Aug 29". */
    fun monthDay(date: LocalDate): String = "${monthAbbr(date.month)} ${date.dayOfMonth}"

    /** `"EEEE, MMM d"` -- e.g. "Saturday, Aug 29". */
    fun weekdayMonthDay(date: LocalDate): String =
        "${weekdayFull(date.dayOfWeek)}, ${monthAbbr(date.month)} ${date.dayOfMonth}"

    /** `"h:mm a"` -- e.g. "3:45 PM". Hour is un-padded, matching the original pattern. */
    fun time(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val moment = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
        val hour12 = when (val h = moment.hour % 12) {
            0 -> 12
            else -> h
        }
        val amPm = if (moment.hour < 12) "AM" else "PM"
        val minute = moment.minute.toString().padStart(2, '0')
        return "$hour12:$minute $amPm"
    }

    /** `"MMM d"` applied to the local date of an epoch-millis instant -- e.g. "Aug 29". */
    fun monthDay(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
        monthDay(Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date)

    /** `"MMM d 'at' h:mm a"` -- e.g. "Aug 29 at 3:45 PM". */
    fun monthDayAtTime(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
        "${monthDay(epochMillis, zone)} at ${time(epochMillis, zone)}"
}
