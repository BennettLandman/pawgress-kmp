package com.balandman.pawgress.data

import kotlinx.datetime.LocalDate

/** Muscle-group buckets, used only to group the settings list. */
/**
 * What you actually lift with.
 *
 * Exists so Settings can separate the machine catalogue from the free-weight
 * one -- two long lists that are much easier to pick from apart than together.
 * It is deliberately *not* a body area: [MachineGroup] still answers "what does
 * this work", and the two are independent (a barbell squat and a leg press are
 * both LOWER, but different equipment).
 *
 * MACHINE is the default everywhere, including when reading a saved profile
 * written before this field existed -- every machine that predates free weights
 * genuinely is one, so the old data needs no migration.
 */
enum class Equipment(val label: String) {
    MACHINE("Machine"),
    FREE_WEIGHT("Free weight");

    companion object {
        fun fromName(value: String?): Equipment =
            entries.firstOrNull { it.name == value } ?: MACHINE
    }
}

enum class MachineGroup(val label: String) {
    UPPER("Upper body"),
    CORE("Core"),
    LOWER("Lower body"),
    OTHER("Other");

    companion object {
        fun fromName(value: String?): MachineGroup =
            entries.firstOrNull { it.name == value } ?: OTHER

        /** Matches the label as written in the Google Sheet's Area column. */
        fun fromLabel(value: String?): MachineGroup? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }
        }
    }
}

/**
 * How a set felt — optional, self-reported, and shown as a color code rather
 * than a number so it reads at a glance next to the weight.
 */
enum class Difficulty(val label: String) {
    VERY_EASY("Very Easy"),
    EASY("Easy"),
    ABOUT_RIGHT("About Right"),
    HARD("Hard"),
    VERY_HARD("Very Hard");

    companion object {
        fun fromName(value: String?): Difficulty? = entries.firstOrNull { it.name == value }

        /** Matches the label as written in the Google Sheet, case-insensitively. */
        fun fromLabel(value: String?): Difficulty? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }
        }
    }
}

/**
 * One piece of equipment. [lastWeight] is what shows on the tile; [lastLoggedAt]
 * is what decides whether the tile renders as done today.
 */
data class Machine(
    val id: String,
    val name: String,
    val iconKey: String,
    val group: MachineGroup,
    /** Machine or free weight. Defaults to MACHINE — see [Equipment]. */
    val equipment: Equipment = Equipment.MACHINE,
    val visible: Boolean = true,
    val custom: Boolean = false,
    val sortOrder: Int = 0,
    val lastWeight: Int? = null,
    val lastLoggedAt: Long? = null,
    /**
     * Prefer the full-color illustration for [iconKey] when one exists. Off
     * switches back to the hand-drawn line icon, which stays available for
     * every key — real artwork doesn't retire it.
     */
    val illustrated: Boolean = true,
    /** How the most recent logged session for this machine felt, if recorded. */
    val lastDifficulty: Difficulty? = null,
)

/** One completed exercise. Append-only; this is what gets mirrored to Sheets. */
data class LogEntry(
    val id: String,
    val machineId: String,
    val machineName: String,
    val weight: Int,
    val loggedAt: Long,
    val synced: Boolean = false,
    /**
     * The machine's body area *at the time of this lift* — snapshotted like
     * [machineName], so re-grouping a machine later never rewrites history.
     */
    val machineGroup: MachineGroup = MachineGroup.OTHER,
    val difficulty: Difficulty? = null,
)

/**
 * One person's everything: their machine grid, their history, and their own
 * spreadsheet.
 *
 * The app keeps a profile per Google account on the device, so two people
 * sharing a phone never see each other's weights and never write into each
 * other's Drive. Signing in switches the whole app over.
 */
data class Profile(
    val key: String,
    val accountEmail: String?,
    val machines: List<Machine>,
    val log: List<LogEntry> = emptyList(),
    /** False after "stop syncing" — the profile and its data stay put. */
    val connected: Boolean = false,
    val spreadsheetId: String? = null,
    val spreadsheetUrl: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val pendingDeletions: List<String> = emptyList(),
    /** Spendable currency: one pawprint per machine, per gym-day, first log only. */
    val pawprintsBalance: Int = 0,
    /** Lifetime earned, never decremented — a fun-facts style counter, not a wallet. */
    val pawprintsEarnedTotal: Int = 0,
    /** Coach id 1 (Coach Moose) is free and always unlocked. */
    val unlockedCoachIds: Set<Int> = setOf(1),
    /** Which coach currently fronts the Fun Facts screen. */
    val selectedCoachId: Int = 1,
    /** Owned outfits, keyed by "coachId:themeSlug" — see [outfitKey]. */
    val unlockedOutfits: Set<String> = emptySet(),
    /** Which outfit (theme slug) is currently equipped per coach id, if any. */
    val equippedOutfits: Map<Int, String> = emptyMap(),
    /** Weight dial limits for machines. */
    val machineWeights: WeightRange = WeightRange(),
    /** Weight dial limits for free weights, independent of [machineWeights]. */
    val freeWeightWeights: WeightRange = WeightRange(),
) {

    /** The dial limits that apply to [equipment]. */
    fun weightsFor(equipment: Equipment): WeightRange =
        if (equipment == Equipment.FREE_WEIGHT) freeWeightWeights else machineWeights

    companion object {
        /** The profile used before anyone has ever signed in. */
        const val LOCAL_KEY = "local"
    }
}

/**
 * A calendar month+day, independent of year. `java.time.MonthDay` has no
 * Kotlin Multiplatform equivalent (kotlinx-datetime doesn't ship one), so
 * this is a minimal stand-in used only to express [CoachTheme]'s recurring
 * yearly windows.
 */
data class MonthDay(val month: Int, val day: Int) : Comparable<MonthDay> {
    override fun compareTo(other: MonthDay): Int {
        val byMonth = month.compareTo(other.month)
        return if (byMonth != 0) byMonth else day.compareTo(other.day)
    }

    companion object {
        fun of(month: Int, day: Int): MonthDay = MonthDay(month, day)
    }
}

/**
 * Seasonal outfit theme. Every coach shares the same wardrobe. A theme is only
 * purchasable/wearable while [isActiveOn] its real-world date window — the
 * chosen outfit is still remembered outside the window (see
 * [Profile.equippedOutfits]), it just silently stops rendering until the
 * window comes back around next year.
 */
enum class CoachTheme(
    val slug: String,
    val displayName: String,
    private val start: MonthDay,
    private val end: MonthDay,
) {
    NEW_YEAR("newyear", "New Year Sparkle", MonthDay.of(1, 1), MonthDay.of(1, 7)),
    VALENTINE("valentine", "Valentine's", MonthDay.of(2, 1), MonthDay.of(2, 14)),
    SPRING("spring", "Spring Bloom", MonthDay.of(3, 15), MonthDay.of(4, 15)),
    SUMMER("summer", "Summer Shades", MonthDay.of(6, 1), MonthDay.of(8, 31)),
    BACK_TO_SCHOOL("backtoschool", "Back-to-School", MonthDay.of(8, 15), MonthDay.of(9, 15)),
    HALLOWEEN("halloween", "Halloween", MonthDay.of(10, 1), MonthDay.of(10, 31)),
    THANKSGIVING("thanksgiving", "Thanksgiving Harvest", MonthDay.of(11, 1), MonthDay.of(11, 30)),
    WINTER_HOLIDAY("winterholiday", "Winter Holiday", MonthDay.of(12, 1), MonthDay.of(12, 25));

    /** True when [date] falls within this theme's real-world window, every year. */
    fun isActiveOn(date: LocalDate): Boolean {
        val md = MonthDay.of(date.monthNumber, date.dayOfMonth)
        return if (start <= end) {
            md >= start && md <= end
        } else {
            // Not used by any window above, but keeps a future New Year's-spanning
            // theme (e.g. Dec 26-Jan 2) correct without touching this method.
            md >= start || md <= end
        }
    }

    companion object {
        fun fromSlug(value: String?): CoachTheme? = entries.firstOrNull { it.slug == value }
    }
}

/** Key used in [Profile.unlockedOutfits] for one coach's ownership of one theme. */
fun outfitKey(coachId: Int, theme: CoachTheme): String = "$coachId:${theme.slug}"

/** The Google connection, flattened for the UI. */
data class SyncState(
    /** Non-null only while actually connected. */
    val accountEmail: String? = null,
    /** Whose grid is on screen, connected or not. */
    val profileEmail: String? = null,
    val profileCount: Int = 1,
    val spreadsheetId: String? = null,
    val spreadsheetUrl: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

/** One data row read back out of the Google Sheet, for the "restore" flow. */
data class SheetRow(
    val loggedAt: Long,
    val exercise: String,
    val area: String?,
    val weight: Int,
    val difficultyLabel: String?,
    val entryId: String,
)

/** What a restore actually did, so the user sees more than just "done". */
data class RestoreSummary(
    val entriesAdded: Int,
    val entriesTotal: Int,
    val machinesCreated: Int,
)

/**
 * The dial's limits for one kind of equipment: how low, how high, and how big
 * a nudge.
 *
 * Machines and free weights get their own, because they are not the same
 * problem. A pin stack starts at 10 lb and climbs in fives; a barbell starts
 * at the bar and a dumbbell rack might step in twos. One shared range forces a
 * compromise that is wrong for both.
 *
 * [Weights] still holds the defaults, so an install that has never touched
 * this setting behaves exactly as it always did.
 */
data class WeightRange(
    val min: Int = Weights.MIN,
    val max: Int = Weights.MAX,
    val step: Int = Weights.STEP,
) {
    /**
     * Snap to this range's grid and clamp to its ends.
     *
     * The grid is measured from [min], not from zero — a range of 45..500 in
     * steps of 5 should offer 45, 50, 55, not 45 then 50 with the first stop
     * off-grid. Snapping from zero only looked right while min happened to be
     * a multiple of step.
     */
    fun clamp(value: Int): Int {
        val steps = kotlin.math.round((value - min).toFloat() / step).toInt()
        return (min + steps * step).coerceIn(min, max)
    }

    /**
     * What Compose's Slider wants: the number of discrete stops *between* the
     * two ends. Never negative — a range narrower than one step would
     * otherwise hand the slider a negative count and crash it.
     */
    val sliderSteps: Int get() = (((max - min) / step) - 1).coerceAtLeast(0)

    /** Where the dial opens for an exercise with no history yet. */
    val startingWeight: Int get() = clamp(Weights.DEFAULT)

    companion object {
        /**
         * Builds a range that cannot break the UI, whatever gets typed into
         * the settings fields. A zero step, or a max below the min, would make
         * both [clamp] and the slider misbehave, so the bounds are enforced
         * here rather than trusted from input.
         */
        fun of(min: Int, max: Int, step: Int): WeightRange {
            val safeStep = step.coerceIn(1, 100)
            val safeMin = min.coerceIn(0, 9_000)
            val safeMax = max.coerceIn(safeMin + safeStep, 10_000)
            return WeightRange(safeMin, safeMax, safeStep)
        }
    }
}

/** Defaults, and the range every profile starts with. */
object Weights {
    const val MIN = 10
    const val MAX = 300
    const val STEP = 5

    /** Snap to the 5 lb grid and clamp to the machine's range. */
    fun clamp(value: Int): Int {
        val snapped = kotlin.math.round(value / STEP.toFloat()).toInt() * STEP
        return snapped.coerceIn(MIN, MAX)
    }

    val DEFAULT = 50
}
