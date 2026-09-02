package com.balandman.pawgress.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A "gym day" runs 4am to 4am rather than midnight to midnight, so a late-night
 * session still counts as that evening's workout and the tiles do not reset out
 * from under you partway through.
 */
object GymDay {

    const val RESET_HOUR = 4

    fun dayOf(epochMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate {
        val moment = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
        return if (moment.hour < RESET_HOUR) {
            moment.date.minusDays(1)
        } else {
            moment.date
        }
    }

    fun today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        dayOf(currentEpochMillis(), zone)

    /** True when [epochMillis] falls inside the current gym day. */
    fun isToday(epochMillis: Long?, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        if (epochMillis == null) return false
        return dayOf(epochMillis, zone) == today(zone)
    }
}
