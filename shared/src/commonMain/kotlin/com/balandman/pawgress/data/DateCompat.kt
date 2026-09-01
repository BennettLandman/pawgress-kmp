package com.balandman.pawgress.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * `java.time.LocalDate` (Android/JVM-only) has `.plusDays`/`.minusDays` built
 * in; `kotlinx.datetime.LocalDate` (the multiplatform equivalent) does not.
 * These extensions keep call sites ported from the original Android-only code
 * working unchanged as more files move into this shared module.
 */
fun LocalDate.plusDays(days: Int): LocalDate = this.plus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Int): LocalDate = this.minus(days, DateTimeUnit.DAY)
fun LocalDate.plusDays(days: Long): LocalDate = this.plus(days.toInt(), DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Long): LocalDate = this.minus(days.toInt(), DateTimeUnit.DAY)

/**
 * The rest of `java.time.LocalDate`'s calendar-navigation API TrendsScreen.kt
 * needs, none of which `kotlinx.datetime.LocalDate` has built in. Every
 * primitive used below (the `LocalDate(year, month, dayOfMonth)` constructor,
 * `.dayOfYear`, `.toEpochDays()`, `DateTimeUnit.MONTH`, `.monthsUntil()`) was
 * checked against the actual kotlinx-datetime 0.6.1 source before use, after
 * DateFormats.kt's `.number`/`.isoDayNumber` guess turned out to be from a
 * later release than this project pins.
 */
fun LocalDate.plusMonths(months: Int): LocalDate = this.plus(months, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(months: Long): LocalDate = this.plus(months.toInt(), DateTimeUnit.MONTH)

fun LocalDate.withDayOfMonth(dayOfMonth: Int): LocalDate = LocalDate(this.year, this.month, dayOfMonth)

/** Only ever called with `dayOfYear = 1` today, but implemented generally. */
fun LocalDate.withDayOfYear(dayOfYear: Int): LocalDate = this.minusDays(this.dayOfYear - dayOfYear)

fun LocalDate.lengthOfMonth(): Int {
    val firstOfThisMonth = this.withDayOfMonth(1)
    val firstOfNextMonth = firstOfThisMonth.plusMonths(1)
    return firstOfNextMonth.toEpochDays() - firstOfThisMonth.toEpochDays()
}
