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
