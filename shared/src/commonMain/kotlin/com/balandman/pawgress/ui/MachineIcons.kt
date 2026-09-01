package com.balandman.pawgress.ui

import com.balandman.pawgress.resources.Res
import com.balandman.pawgress.resources.art_ab_crunch
import com.balandman.pawgress.resources.art_assist_dip_chin
import com.balandman.pawgress.resources.art_back_extension
import com.balandman.pawgress.resources.art_barbell
import com.balandman.pawgress.resources.art_bench_press
import com.balandman.pawgress.resources.art_biceps_curl
import com.balandman.pawgress.resources.art_cable
import com.balandman.pawgress.resources.art_chest_press
import com.balandman.pawgress.resources.art_dip
import com.balandman.pawgress.resources.art_dumbbell
import com.balandman.pawgress.resources.art_farmers_carry
import com.balandman.pawgress.resources.art_fixed_pulldown
import com.balandman.pawgress.resources.art_hack_squat
import com.balandman.pawgress.resources.art_hip_abduction
import com.balandman.pawgress.resources.art_hip_adduction
import com.balandman.pawgress.resources.art_hip_and_glute
import com.balandman.pawgress.resources.art_horizontal_calf
import com.balandman.pawgress.resources.art_incline_press
import com.balandman.pawgress.resources.art_kettlebell
import com.balandman.pawgress.resources.art_lat_pulldown
import com.balandman.pawgress.resources.art_lateral_raise
import com.balandman.pawgress.resources.art_leg_curl
import com.balandman.pawgress.resources.art_leg_extension
import com.balandman.pawgress.resources.art_leg_lift
import com.balandman.pawgress.resources.art_machine
import com.balandman.pawgress.resources.art_pec_fly
import com.balandman.pawgress.resources.art_pec_fly_rear_delt
import com.balandman.pawgress.resources.art_plate
import com.balandman.pawgress.resources.art_preacher_curl
import com.balandman.pawgress.resources.art_pullup
import com.balandman.pawgress.resources.art_rear_delt
import com.balandman.pawgress.resources.art_run
import com.balandman.pawgress.resources.art_seated_leg_curl
import com.balandman.pawgress.resources.art_seated_leg_press
import com.balandman.pawgress.resources.art_seated_row
import com.balandman.pawgress.resources.art_shoulder_press
import com.balandman.pawgress.resources.art_shrug
import com.balandman.pawgress.resources.art_shuttle_run
import com.balandman.pawgress.resources.art_squat
import com.balandman.pawgress.resources.art_standing_calf
import com.balandman.pawgress.resources.art_tbar_row
import com.balandman.pawgress.resources.art_torso_rotation
import com.balandman.pawgress.resources.art_triceps_extension
import com.balandman.pawgress.resources.art_triceps_pushdown
import com.balandman.pawgress.resources.art_woodchop
import com.balandman.pawgress.resources.ic_m_ab_crunch
import com.balandman.pawgress.resources.ic_m_assist_dip_chin
import com.balandman.pawgress.resources.ic_m_back_extension
import com.balandman.pawgress.resources.ic_m_barbell
import com.balandman.pawgress.resources.ic_m_bench_press
import com.balandman.pawgress.resources.ic_m_biceps_curl
import com.balandman.pawgress.resources.ic_m_cable
import com.balandman.pawgress.resources.ic_m_chest_press
import com.balandman.pawgress.resources.ic_m_dip
import com.balandman.pawgress.resources.ic_m_dumbbell
import com.balandman.pawgress.resources.ic_m_farmers_carry
import com.balandman.pawgress.resources.ic_m_fixed_pulldown
import com.balandman.pawgress.resources.ic_m_hack_squat
import com.balandman.pawgress.resources.ic_m_hip_abduction
import com.balandman.pawgress.resources.ic_m_hip_adduction
import com.balandman.pawgress.resources.ic_m_hip_and_glute
import com.balandman.pawgress.resources.ic_m_horizontal_calf
import com.balandman.pawgress.resources.ic_m_incline_press
import com.balandman.pawgress.resources.ic_m_kettlebell
import com.balandman.pawgress.resources.ic_m_lat_pulldown
import com.balandman.pawgress.resources.ic_m_lateral_raise
import com.balandman.pawgress.resources.ic_m_leg_curl
import com.balandman.pawgress.resources.ic_m_leg_extension
import com.balandman.pawgress.resources.ic_m_leg_lift
import com.balandman.pawgress.resources.ic_m_machine
import com.balandman.pawgress.resources.ic_m_pec_fly
import com.balandman.pawgress.resources.ic_m_pec_fly_rear_delt
import com.balandman.pawgress.resources.ic_m_plate
import com.balandman.pawgress.resources.ic_m_preacher_curl
import com.balandman.pawgress.resources.ic_m_pullup
import com.balandman.pawgress.resources.ic_m_rear_delt
import com.balandman.pawgress.resources.ic_m_run
import com.balandman.pawgress.resources.ic_m_seated_leg_curl
import com.balandman.pawgress.resources.ic_m_seated_leg_press
import com.balandman.pawgress.resources.ic_m_seated_row
import com.balandman.pawgress.resources.ic_m_shoulder_press
import com.balandman.pawgress.resources.ic_m_shrug
import com.balandman.pawgress.resources.ic_m_squat
import com.balandman.pawgress.resources.ic_m_standing_calf
import com.balandman.pawgress.resources.ic_m_tbar_row
import com.balandman.pawgress.resources.ic_m_torso_rotation
import com.balandman.pawgress.resources.ic_m_triceps_extension
import com.balandman.pawgress.resources.ic_m_triceps_pushdown
import com.balandman.pawgress.resources.ic_m_woodchop
import org.jetbrains.compose.resources.DrawableResource

/**
 * Maps a machine's stored icon key to its vector drawable.
 *
 * Ported from the Android-only original, which used typed `R.drawable.<name>`
 * fields; Compose Multiplatform's generated `Res.drawable.<name>` accessors
 * are the direct equivalent (both compile-time-checked, so a typo here would
 * fail the build either way) -- this map's shape and every key are otherwise
 * unchanged. The individual per-resource imports above (rather than just
 * `import com.balandman.pawgress.resources.Res` and qualifying every use as
 * `Res.drawable.ic_m_ab_crunch`) are Kotlin's usual style for a generated
 * accessor object with this many members; either compiles the same way.
 */
object MachineIcons {

    private val BY_KEY: Map<String, DrawableResource> = mapOf(
        "ab_crunch" to Res.drawable.ic_m_ab_crunch,
        "assist_dip_chin" to Res.drawable.ic_m_assist_dip_chin,
        "back_extension" to Res.drawable.ic_m_back_extension,
        "barbell" to Res.drawable.ic_m_barbell,
        "bench_press" to Res.drawable.ic_m_bench_press,
        "biceps_curl" to Res.drawable.ic_m_biceps_curl,
        "cable" to Res.drawable.ic_m_cable,
        "standing_calf" to Res.drawable.ic_m_standing_calf,
        "chest_press" to Res.drawable.ic_m_chest_press,
        "dip" to Res.drawable.ic_m_dip,
        "dumbbell" to Res.drawable.ic_m_dumbbell,
        "farmers_carry" to Res.drawable.ic_m_farmers_carry,
        "fixed_pulldown" to Res.drawable.ic_m_fixed_pulldown,
        "hip_and_glute" to Res.drawable.ic_m_hip_and_glute,
        "hack_squat" to Res.drawable.ic_m_hack_squat,
        "hip_abduction" to Res.drawable.ic_m_hip_abduction,
        "hip_adduction" to Res.drawable.ic_m_hip_adduction,
        "horizontal_calf" to Res.drawable.ic_m_horizontal_calf,
        "incline_press" to Res.drawable.ic_m_incline_press,
        "kettlebell" to Res.drawable.ic_m_kettlebell,
        "lat_pulldown" to Res.drawable.ic_m_lat_pulldown,
        "lateral_raise" to Res.drawable.ic_m_lateral_raise,
        "leg_curl" to Res.drawable.ic_m_leg_curl,
        "leg_extension" to Res.drawable.ic_m_leg_extension,
        "leg_lift" to Res.drawable.ic_m_leg_lift,
        "seated_leg_press" to Res.drawable.ic_m_seated_leg_press,
        "machine" to Res.drawable.ic_m_machine,
        "pec_fly" to Res.drawable.ic_m_pec_fly,
        "pec_fly_rear_delt" to Res.drawable.ic_m_pec_fly_rear_delt,
        "plate" to Res.drawable.ic_m_plate,
        "preacher_curl" to Res.drawable.ic_m_preacher_curl,
        "pullup" to Res.drawable.ic_m_pullup,
        "rear_delt" to Res.drawable.ic_m_rear_delt,
        "run" to Res.drawable.ic_m_run,
        "seated_leg_curl" to Res.drawable.ic_m_seated_leg_curl,
        "seated_row" to Res.drawable.ic_m_seated_row,
        "shoulder_press" to Res.drawable.ic_m_shoulder_press,
        "shrug" to Res.drawable.ic_m_shrug,
        "squat" to Res.drawable.ic_m_squat,
        "tbar_row" to Res.drawable.ic_m_tbar_row,
        "torso_rotation" to Res.drawable.ic_m_torso_rotation,
        "triceps_extension" to Res.drawable.ic_m_triceps_extension,
        "triceps_pushdown" to Res.drawable.ic_m_triceps_pushdown,
        "woodchop" to Res.drawable.ic_m_woodchop,
    )

    fun resFor(key: String): DrawableResource = BY_KEY[key] ?: Res.drawable.ic_m_dumbbell

    /**
     * Full-color illustrations, keyed the same way as [BY_KEY]. Not every key
     * has one — a key without an entry here just falls back to its line icon,
     * which is how a brand-new custom icon key behaves until art exists for it.
     */
    private val ART_BY_KEY: Map<String, DrawableResource> = mapOf(
        "ab_crunch" to Res.drawable.art_ab_crunch,
        "assist_dip_chin" to Res.drawable.art_assist_dip_chin,
        "back_extension" to Res.drawable.art_back_extension,
        "barbell" to Res.drawable.art_barbell,
        "bench_press" to Res.drawable.art_bench_press,
        "biceps_curl" to Res.drawable.art_biceps_curl,
        "cable" to Res.drawable.art_cable,
        "standing_calf" to Res.drawable.art_standing_calf,
        "chest_press" to Res.drawable.art_chest_press,
        "dip" to Res.drawable.art_dip,
        "dumbbell" to Res.drawable.art_dumbbell,
        "farmers_carry" to Res.drawable.art_farmers_carry,
        "fixed_pulldown" to Res.drawable.art_fixed_pulldown,
        "hip_and_glute" to Res.drawable.art_hip_and_glute,
        "hack_squat" to Res.drawable.art_hack_squat,
        "hip_abduction" to Res.drawable.art_hip_abduction,
        "hip_adduction" to Res.drawable.art_hip_adduction,
        "horizontal_calf" to Res.drawable.art_horizontal_calf,
        "incline_press" to Res.drawable.art_incline_press,
        "kettlebell" to Res.drawable.art_kettlebell,
        "lat_pulldown" to Res.drawable.art_lat_pulldown,
        "lateral_raise" to Res.drawable.art_lateral_raise,
        "leg_curl" to Res.drawable.art_leg_curl,
        "leg_extension" to Res.drawable.art_leg_extension,
        "leg_lift" to Res.drawable.art_leg_lift,
        "seated_leg_press" to Res.drawable.art_seated_leg_press,
        "machine" to Res.drawable.art_machine,
        "pec_fly" to Res.drawable.art_pec_fly,
        "pec_fly_rear_delt" to Res.drawable.art_pec_fly_rear_delt,
        "plate" to Res.drawable.art_plate,
        "preacher_curl" to Res.drawable.art_preacher_curl,
        "pullup" to Res.drawable.art_pullup,
        "rear_delt" to Res.drawable.art_rear_delt,
        "run" to Res.drawable.art_run,
        "seated_leg_curl" to Res.drawable.art_seated_leg_curl,
        "seated_row" to Res.drawable.art_seated_row,
        "shoulder_press" to Res.drawable.art_shoulder_press,
        "shrug" to Res.drawable.art_shrug,
        "shuttle_run" to Res.drawable.art_shuttle_run,
        "squat" to Res.drawable.art_squat,
        "tbar_row" to Res.drawable.art_tbar_row,
        "torso_rotation" to Res.drawable.art_torso_rotation,
        "triceps_extension" to Res.drawable.art_triceps_extension,
        "triceps_pushdown" to Res.drawable.art_triceps_pushdown,
        "woodchop" to Res.drawable.art_woodchop,
    )

    /** The illustrated artwork for a key, or null when only the line icon exists. */
    fun artFor(key: String): DrawableResource? = ART_BY_KEY[key]
}
