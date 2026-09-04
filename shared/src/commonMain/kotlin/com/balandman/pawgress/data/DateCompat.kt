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
    // DO NOT "clean up" the `.toInt()` below, however redundant it looks.
    //
    // kotlinx-datetime's `toEpochDays()` returns Int in the version resolved
    // for the Android target but Long in the one resolved for iOS -- the same
    // common source file, two different resolutions. Without the conversion,
    // iOS fails outright with "expected Int, actual Long" (which is exactly
    // how this was discovered, the first time Kotlin/Native ever compiled this
    // file, in Phase 6). With it, both targets compile.
    //
    // The cost is that the Android build now emits
    //   w: DateCompat.kt:NN Redundant call of conversion method
    // on every build. That warning is correct *for Android alone* and wrong
    // for the project: deleting the call to silence it breaks the iOS build.
    // A month is always far under Int.MAX_VALUE days, so the narrowing is
    // safe either way.
    return (firstOfNextMonth.toEpochDays() - firstOfThisMonth.toEpochDays()).toInt()
}
