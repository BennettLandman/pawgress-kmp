package com.balandman.pawgress.data

/**
 * Current wall-clock time in epoch milliseconds.
 *
 * This used to be `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()`
 * at each call site, but that resolved to "Unresolved reference 'System'" the
 * first time this project's shared/iOS source was ever actually compiled by
 * Kotlin/Native (Phase 6) -- and kotlinx-datetime's own Instant/Clock types
 * have been in genuine flux across versions since (0.7.0 removed them in
 * favor of stdlib's kotlin.time.Instant/Clock, 0.7.1 added them back as
 * aliases), so whatever the exact cause on this project's resolved version,
 * chasing it is a moving target. A plain two-line expect/actual sidesteps the
 * whole question instead.
 */
expect fun currentEpochMillis(): Long
