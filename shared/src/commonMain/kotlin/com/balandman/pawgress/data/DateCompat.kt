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
 * needs, none of which `kotlinx.datetime.LocalDate` has built in. The
 * primitives below (the `LocalDate(year, month, dayOfMonth)` constructor,
 * `.dayOfYear`, `.toEpochDays()`, `DateTimeUnit.MONTH`) were checked against
 * kotlinx-datetime 0.6.1's source before use -- though `.monthsUntil()` was
 * *also* checked that way and still turned out unresolved at this pinned
 * version (a real compile error), so months-between-two-dates is computed by
 * hand in TrendsScreen.kt from `.year`/`.month.ordinal` instead, the one
 * Month-arithmetic primitive actually proven to compile so far.
 */
fun LocalDate.plusMonths(months: Int): LocalDate = this.plus(months, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(months: Long): LocalDate = this.plus(months.toInt(), DateTimeUnit.MONTH)

fun LocalDate.withDayOfMonth(dayOfMonth: Int): LocalDate = LocalDate(this.year, this.month, dayOfMonth)

/** Only ever called with `dayOfYear = 1` today, but implemented generally. */
fun LocalDate.withDayOfYear(dayOfYear: Int): LocalDate = this.minusDays(this.dayOfYear - dayOfYear)

fun LocalDate.lengthOfMonth(): Int {
    val firstOfThisMonth = this.withDayOfMonth(1)
    val firstOfNextMonth = firstOfThisMonth.plusMonths(1)
    // `.toInt()` here rather than a bare subtraction: kotlinx-datetime's
    // `toEpochDays()` returned Int as of the 0.6.1 version this project
    // declares, but the version actually resolved for the iOS compile
    // returned Long instead (first surfaced as a real "expected Int, actual
    // Long" compile error in Phase 6, once Kotlin/Native ever compiled this
    // file for the first time) -- a month is always well under Int.MAX_VALUE
    // days, so the narrowing is safe, and it compiles under either return type.
    return (firstOfNextMonth.toEpochDays() - firstOfThisMonth.toEpochDays()).toInt()
}
