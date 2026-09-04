package com.balandman.pawgress.sync

import com.balandman.pawgress.data.Equipment
import com.balandman.pawgress.data.MachineGroup
import com.balandman.pawgress.data.MachineSetting
import com.balandman.pawgress.data.SettingsSnapshot
import com.balandman.pawgress.data.WeightRange

/**
 * Converts a [SettingsSnapshot] to and from the rows of the spreadsheet's
 * "Settings" tab.
 *
 * The tab is one fixed grid with a Type column rather than two tables stacked
 * on top of each other, because a single rectangular block is what a
 * spreadsheet is actually good at — sortable, filterable, and safe to hand-edit.
 * Scalar settings fill only Type/Id/Value; machine rows fill the rest and
 * leave Value blank.
 *
 * Parsing is deliberately forgiving. Someone will edit this sheet by hand, and
 * a row with a typo in it should be skipped rather than abort the whole
 * restore — the machines either side of it are still perfectly good.
 */
internal object SettingsRows {

    private const val TYPE_SETTING = "setting"
    private const val TYPE_MACHINE = "machine"

    private const val KEY_MACHINE_MIN = "machineWeightMin"
    private const val KEY_MACHINE_MAX = "machineWeightMax"
    private const val KEY_MACHINE_STEP = "machineWeightStep"
    private const val KEY_FREE_MIN = "freeWeightMin"
    private const val KEY_FREE_MAX = "freeWeightMax"
    private const val KEY_FREE_STEP = "freeWeightStep"

    fun toRows(snapshot: SettingsSnapshot): List<List<String>> {
        val scalars = listOf(
            KEY_MACHINE_MIN to snapshot.machineWeights.min,
            KEY_MACHINE_MAX to snapshot.machineWeights.max,
            KEY_MACHINE_STEP to snapshot.machineWeights.step,
            KEY_FREE_MIN to snapshot.freeWeightWeights.min,
            KEY_FREE_MAX to snapshot.freeWeightWeights.max,
            KEY_FREE_STEP to snapshot.freeWeightWeights.step,
        ).map { (key, value) ->
            listOf(TYPE_SETTING, key, "", "", "", "", "", "", "", value.toString())
        }

        val machines = snapshot.machines.map { m ->
            listOf(
                TYPE_MACHINE,
                m.id,
                m.name,
                m.group.label,
                m.equipment.label,
                m.visible.yesNo(),
                m.iconKey,
                m.illustrated.yesNo(),
                m.sortOrder.toString(),
                "",
            )
        }
        return scalars + machines
    }

    /**
     * Null when the tab holds nothing usable — an empty tab, or one whose rows
     * are all unparseable. The caller treats that as "nothing to restore"
     * rather than as an error.
     */
    fun fromRows(rows: List<List<String>>, current: SettingsSnapshot): SettingsSnapshot? {
        if (rows.isEmpty()) return null

        val scalars = mutableMapOf<String, Int>()
        val machines = mutableListOf<MachineSetting>()

        for (row in rows) {
            fun cell(index: Int): String = row.getOrNull(index)?.trim().orEmpty()
            when (cell(0).lowercase()) {
                TYPE_SETTING -> {
                    val key = cell(1)
                    val value = cell(9).toIntOrNull()
                    if (key.isNotBlank() && value != null) scalars[key] = value
                }
                TYPE_MACHINE -> {
                    val id = cell(1)
                    val name = cell(2)
                    // An id and a name are the minimum that makes a row mean
                    // anything; everything else has a sane fallback.
                    if (id.isBlank() || name.isBlank()) continue
                    machines += MachineSetting(
                        id = id,
                        name = name,
                        group = MachineGroup.fromLabel(cell(3)) ?: MachineGroup.OTHER,
                        equipment = Equipment.fromLabel(cell(4)) ?: Equipment.MACHINE,
                        visible = cell(5).toBoolOr(true),
                        iconKey = cell(6).ifBlank { "machine" },
                        illustrated = cell(7).toBoolOr(true),
                        sortOrder = cell(8).toIntOrNull() ?: 0,
                    )
                }
            }
        }

        if (scalars.isEmpty() && machines.isEmpty()) return null

        // Missing scalars keep whatever the device already has, so a partially
        // hand-edited sheet can't blank out a range.
        fun range(fallback: WeightRange, minKey: String, maxKey: String, stepKey: String) =
            WeightRange.of(
                scalars[minKey] ?: fallback.min,
                scalars[maxKey] ?: fallback.max,
                scalars[stepKey] ?: fallback.step,
            )

        return SettingsSnapshot(
            machineWeights = range(
                current.machineWeights, KEY_MACHINE_MIN, KEY_MACHINE_MAX, KEY_MACHINE_STEP
            ),
            freeWeightWeights = range(
                current.freeWeightWeights, KEY_FREE_MIN, KEY_FREE_MAX, KEY_FREE_STEP
            ),
            machines = machines,
        )
    }

    private fun Boolean.yesNo(): String = if (this) "yes" else "no"

    /** Accepts what a person might plausibly type, not just what we write. */
    private fun String.toBoolOr(fallback: Boolean): Boolean = when (lowercase()) {
        "yes", "y", "true", "1", "shown", "visible" -> true
        "no", "n", "false", "0", "hidden" -> false
        else -> fallback
    }
}
