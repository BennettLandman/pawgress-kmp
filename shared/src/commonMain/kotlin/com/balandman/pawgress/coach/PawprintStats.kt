package com.balandman.pawgress.coach

import com.balandman.pawgress.data.GymDay
import com.balandman.pawgress.data.LogEntry
import com.balandman.pawgress.data.MachineGroup

/**
 * Where a profile's pawprints actually came from.
 *
 * **Nothing new is stored to build this.** A pawprint mints exactly once per
 * machine per gym day (`LiftRepository.logLift`), and there is only ever one
 * log entry per machine per day — undoing today's entry removes both the row
 * and the pawprint. So the number of pawprints an exercise has earned is
 * precisely the number of distinct (machineId, gym day) pairs in the log, and
 * every breakdown below is a `groupBy` over history the app already keeps.
 *
 * That matters for two reasons beyond saving a field: it works retroactively
 * for people who have been logging for months, and it cannot drift out of
 * sync with the log the way a separately-maintained tally would.
 *
 * Only exercise-earned pawprints are counted. Pawgress has no login,
 * promotional or purchased pawprints at all; the one other source is the debug
 * grant in Settings, which deliberately touches only the spendable balance and
 * never [com.balandman.pawgress.data.Profile.pawprintsEarnedTotal] — so both
 * the lifetime figure and these breakdowns are workout activity by
 * construction.
 */
data class PawprintStats(
    /**
     * The profile's lifetime counter — never decremented when pawprints are
     * spent. Passed in rather than derived, because it is the authority: it
     * survives a full reset, which clears the log.
     */
    val lifetimeEarned: Int,
    /** Pawprints visible in the current log, newest history only. */
    val fromCurrentLog: Int,
    /** Exercise name to pawprints earned, biggest first. */
    val byExercise: List<Tally>,
    /** Body area to pawprints earned, biggest first. */
    val byArea: List<Tally>,
    /** Distinct gym days that earned at least one pawprint. */
    val activeDays: Int,
) {
    data class Tally(val name: String, val pawprints: Int)

    /**
     * True when the lifetime counter has outrun the log — the profile was
     * fully reset at some point, so the breakdowns describe a shorter window
     * than the headline number. Worth saying out loud rather than letting the
     * two numbers quietly disagree on screen.
     */
    val logIsPartial: Boolean get() = lifetimeEarned > fromCurrentLog

    val topExercise: Tally? get() = byExercise.firstOrNull()

    companion object {
        fun from(log: List<LogEntry>, lifetimeEarned: Int): PawprintStats {
            // One pawprint per (machine, gym day). Group by the machine's *id*
            // so renaming an exercise doesn't split its history in two, but
            // report the most recent name it was logged under.
            val earning = log
                .map { entry -> entry to GymDay.dayOf(entry.loggedAt) }
                .distinctBy { (entry, day) -> entry.machineId to day }

            val nameFor = log
                .sortedBy { it.loggedAt }
                .associate { it.machineId to it.machineName }

            val byExercise = earning
                .groupingBy { (entry, _) -> entry.machineId }
                .eachCount()
                .map { (machineId, count) -> Tally(nameFor[machineId] ?: machineId, count) }
                .sortedWith(compareByDescending<Tally> { it.pawprints }.thenBy { it.name })

            val byArea = earning
                .groupingBy { (entry, _) -> entry.machineGroup }
                .eachCount()
                .map { (group, count) -> Tally(group.label, count) }
                .sortedWith(compareByDescending<Tally> { it.pawprints }.thenBy { it.name })

            return PawprintStats(
                lifetimeEarned = lifetimeEarned,
                fromCurrentLog = earning.size,
                byExercise = byExercise,
                byArea = byArea,
                activeDays = earning.map { (_, day) -> day }.distinct().size,
            )
        }

        /** Kept beside [from] so the "Other" area can be filtered consistently. */
        internal val UNINTERESTING_AREAS = setOf(MachineGroup.OTHER.label)
    }

    /**
     * Playful, coach-flavoured lines about where the pawprints came from.
     *
     * Each is headline + detail, matching the existing fact cards. Only facts
     * the data actually supports are returned — a profile with three lifts on
     * one machine gets one line, not five padded ones.
     */
    fun funFacts(): List<Pair<String, String>> {
        if (fromCurrentLog == 0) return emptyList()
        val facts = mutableListOf<Pair<String, String>>()

        topExercise?.let { top ->
            val share = (top.pawprints * 100) / fromCurrentLog.coerceAtLeast(1)
            facts += "${top.name} is your pawprint machine" to
                if (byExercise.size > 1) {
                    "${paws(top.pawprints)} earned there — $share% of everything " +
                        "you've clawed together."
                } else {
                    "${paws(top.pawprints)} earned there, and nowhere else so far."
                }
        }

        if (byExercise.size >= 3) {
            val three = byExercise.take(3)
            facts += "Your top three haunts" to
                three.joinToString(", ") { "${it.name} (${it.pawprints})" } + "."
        }

        byArea.firstOrNull { it.name !in UNINTERESTING_AREAS }?.let { area ->
            facts += "${area.name} pays best" to
                "${paws(area.pawprints)} of yours came from ${area.name.lowercase()} work."
        }

        if (activeDays > 0) {
            facts += "${rate(fromCurrentLog, activeDays)} per gym day" to
                "${paws(fromCurrentLog)} across $activeDays " +
                (if (activeDays == 1) "day" else "days") + " of showing up."
        }

        // Only worth calling an exercise neglected if it is actually behind.
        // With two exercises tied at one pawprint each, naming one of them
        // "neglected" is just wrong.
        val quietest = byExercise.lastOrNull()
        val busiest = byExercise.firstOrNull()
        if (quietest != null && busiest != null && quietest.pawprints * 2 <= busiest.pawprints) {
            facts += "${quietest.name} is feeling neglected" to
                "Only ${paws(quietest.pawprints)} from that one. It's waiting."
        }

        return facts
    }

    /** "1 pawprint" / "4 pawprints" — this text is user-facing often enough to get it right. */
    private fun paws(n: Int): String = if (n == 1) "1 pawprint" else "$n pawprints"

    /**
     * Pawprints per day to one decimal, without a pointless ".0" — "2 pawprints
     * per gym day" reads better than "2.0", and Kotlin has no printf here that
     * works the same way on both platforms.
     */
    private fun rate(total: Int, days: Int): String {
        val tenths = (total * 10 + days / 2) / days
        return if (tenths % 10 == 0) paws(tenths / 10) else "${tenths / 10}.${tenths % 10} pawprints"
    }
}
