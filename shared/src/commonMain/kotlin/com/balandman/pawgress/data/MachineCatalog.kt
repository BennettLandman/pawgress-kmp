package com.balandman.pawgress.data

/**
 * The machines the app ships with.
 *
 * The first 22 are the circuit Bennett actually uses, named the way the machines
 * themselves are labelled, and they are visible on first launch. The rest are
 * common equipment that starts hidden — switch any of them on from Settings.
 * Hiding never deletes a machine or its history.
 */
object MachineCatalog {

    private data class Seed(
        val id: String,
        val name: String,
        val icon: String,
        val group: MachineGroup,
        val visible: Boolean = true,
        val equipment: Equipment = Equipment.MACHINE,
    )

    private val SEEDS = listOf(
        // ------------------------------------------------ the circuit: upper body
        Seed("assist_dip_chin", "Assist Dip/Chin", "assist_dip_chin", MachineGroup.UPPER),
        Seed("biceps_curl", "Biceps Curl", "biceps_curl", MachineGroup.UPPER),
        Seed("chest_press", "Chest Press", "chest_press", MachineGroup.UPPER),
        Seed("fixed_pulldown", "Fixed Pulldown", "fixed_pulldown", MachineGroup.UPPER),
        Seed("lat_pulldown", "Lat Pulldown", "lat_pulldown", MachineGroup.UPPER),
        Seed("lateral_raise", "Lateral Raise", "lateral_raise", MachineGroup.UPPER),
        Seed("pec_fly", "Pectoral Fly", "pec_fly", MachineGroup.UPPER),
        Seed(
            "pec_fly_rear_delt", "Pectoral Fly/Rear Deltoid",
            "pec_fly_rear_delt", MachineGroup.UPPER
        ),
        Seed("seated_row", "Seated Row", "seated_row", MachineGroup.UPPER),
        Seed("shoulder_press", "Shoulder Press", "shoulder_press", MachineGroup.UPPER),
        Seed("triceps_extension", "Triceps Extension", "triceps_extension", MachineGroup.UPPER),

        // ------------------------------------------------------ the circuit: core
        Seed("ab_crunch", "Abdominal Crunch", "ab_crunch", MachineGroup.CORE),
        Seed("back_extension", "Back Extension", "back_extension", MachineGroup.CORE),

        // ------------------------------------------------ the circuit: lower body
        Seed("hip_abduction", "Hip Abduction", "hip_abduction", MachineGroup.LOWER),
        Seed("hip_adduction", "Hip Adduction", "hip_adduction", MachineGroup.LOWER),
        Seed("hip_and_glute", "Hip and Glute", "hip_and_glute", MachineGroup.LOWER),
        Seed("horizontal_calf", "Horizontal Calf", "horizontal_calf", MachineGroup.LOWER),
        Seed("leg_curl", "Leg Curl", "leg_curl", MachineGroup.LOWER),
        Seed("leg_extension", "Leg Extension", "leg_extension", MachineGroup.LOWER),
        Seed("seated_leg_curl", "Seated Leg Curl", "seated_leg_curl", MachineGroup.LOWER),
        Seed("seated_leg_press", "Seated Leg Press", "seated_leg_press", MachineGroup.LOWER),
        Seed("standing_calf", "Standing Calf", "standing_calf", MachineGroup.LOWER),

        // ----------------------------------------- available but hidden by default
        Seed("bench_press", "Bench Press", "bench_press", MachineGroup.UPPER, visible = false),
        Seed(
            "incline_press", "Incline Chest Press",
            "incline_press", MachineGroup.UPPER, visible = false
        ),
        Seed(
            "triceps_pushdown", "Triceps Pushdown",
            "triceps_pushdown", MachineGroup.UPPER, visible = false
        ),
        Seed("preacher_curl", "Preacher Curl", "preacher_curl", MachineGroup.UPPER, visible = false),
        Seed("tbar_row", "T-Bar Row", "tbar_row", MachineGroup.UPPER, visible = false),
        Seed("rear_delt", "Rear Deltoid Fly", "rear_delt", MachineGroup.UPPER, visible = false),
        Seed("cable_face_pull", "Cable Face Pull", "cable_face_pull", MachineGroup.UPPER, visible = false),
        Seed("shrug", "Shrug", "shrug", MachineGroup.UPPER, visible = false),
        Seed("pullup", "Assisted Pull-Up", "pullup", MachineGroup.UPPER, visible = false),
        Seed("dip", "Dip", "dip", MachineGroup.UPPER, visible = false),

        Seed("leg_lift", "Leg Lift", "leg_lift", MachineGroup.CORE, visible = false),
        Seed("torso_rotation", "Torso Rotation", "torso_rotation", MachineGroup.CORE, visible = false),
        Seed("woodchop", "Cable Woodchop", "woodchop", MachineGroup.CORE, visible = false),

        Seed("hack_squat", "Hack Squat", "hack_squat", MachineGroup.LOWER, visible = false),
        Seed("smith_squat", "Smith Machine Squat", "squat", MachineGroup.LOWER, visible = false),

        Seed("farmers_carry", "Farmer's Carry", "farmers_carry", MachineGroup.OTHER, visible = false),
        Seed("shuttle_run", "Shuttle Run", "shuttle_run", MachineGroup.OTHER, visible = false),

        // ------------------------------------- free weights: legs and hips
        Seed("back_squat", "Back Squat", "back_squat",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("front_squat", "Front Squat", "front_squat",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("goblet_squat", "Goblet Squat", "goblet_squat",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("deadlift", "Deadlift", "deadlift",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("romanian_deadlift", "Romanian Deadlift", "romanian_deadlift",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("kettlebell_deadlift", "Kettlebell Deadlift", "kettlebell_deadlift",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("forward_lunge", "Forward Lunge", "forward_lunge",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("reverse_lunge", "Reverse Lunge", "reverse_lunge",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("split_squat", "Split Squat", "split_squat",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("step_up", "Step-Up", "step_up",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("hip_thrust", "Hip Thrust", "hip_thrust",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("weighted_glute_bridge", "Weighted Glute Bridge", "weighted_glute_bridge",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("standing_calf_raise", "Standing Calf Raise", "standing_calf_raise",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),

        // ------------------------------ free weights: chest, back and arms
        Seed("barbell_bench_press", "Barbell Bench Press", "barbell_bench_press",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("dumbbell_bench_press", "Dumbbell Bench Press", "dumbbell_bench_press",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("floor_press", "Floor Press", "floor_press",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("standing_overhead_press", "Standing Overhead Press", "standing_overhead_press",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("dumbbell_shoulder_press", "Dumbbell Shoulder Press", "dumbbell_shoulder_press",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("barbell_bent_over_row", "Barbell Bent-Over Row", "barbell_bent_over_row",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("one_arm_dumbbell_row", "One-Arm Dumbbell Row", "one_arm_dumbbell_row",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("chest_supported_row", "Chest-Supported Row", "chest_supported_row",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("dumbbell_curl", "Dumbbell Curl", "dumbbell_curl",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("hammer_curl", "Hammer Curl", "hammer_curl",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("skull_crusher", "Skull Crusher", "skull_crusher",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("overhead_triceps_extension", "Overhead Triceps Extension", "overhead_triceps_extension",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),

        // ------------------------------------------- free weights: carries and core
        Seed("farmer_carry", "Dumbbell Farmer Carry", "farmer_carry",
            MachineGroup.CORE, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("suitcase_carry", "Suitcase Carry", "suitcase_carry",
            MachineGroup.CORE, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("weighted_plank", "Weighted Plank", "weighted_plank",
            MachineGroup.CORE, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("russian_twist", "Russian Twist", "russian_twist",
            MachineGroup.CORE, visible = false, equipment = Equipment.FREE_WEIGHT),

        // ------------------------------------------ free weights: added later
        Seed("dumbbell_lateral_raise", "Dumbbell Lateral Raise", "dumbbell_lateral_raise",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("straight_bar_biceps_curl", "Straight-Bar Biceps Curl", "straight_bar_biceps_curl",
            MachineGroup.UPPER, visible = false, equipment = Equipment.FREE_WEIGHT),
        Seed("barbell_good_morning", "Barbell Good Morning", "barbell_good_morning",
            MachineGroup.LOWER, visible = false, equipment = Equipment.FREE_WEIGHT),
    )

    /** Every icon a custom machine can pick from. */
    val ICON_KEYS: List<String> = listOf(
        "dumbbell", "barbell", "kettlebell", "cable", "plate", "machine", "run",
        "assist_dip_chin", "bench_press", "chest_press", "dip", "fixed_pulldown",
        "incline_press", "lat_pulldown", "lateral_raise", "pec_fly",
        "pec_fly_rear_delt", "preacher_curl", "pullup", "rear_delt", "seated_row",
        "shoulder_press", "shrug", "tbar_row", "triceps_extension",
        "triceps_pushdown", "biceps_curl",
        "standing_calf", "hip_and_glute", "hack_squat", "hip_abduction",
        "hip_adduction", "horizontal_calf", "leg_curl", "leg_extension",
        "seated_leg_press", "seated_leg_curl", "squat",
        "ab_crunch", "back_extension", "leg_lift", "torso_rotation", "woodchop",
        "farmers_carry", "shuttle_run", "cable_face_pull",
        // free weights
        "back_squat", "front_squat", "goblet_squat",
        "deadlift", "romanian_deadlift", "kettlebell_deadlift",
        "forward_lunge", "reverse_lunge", "split_squat",
        "step_up", "hip_thrust", "weighted_glute_bridge",
        "standing_calf_raise", "barbell_bench_press", "dumbbell_bench_press",
        "floor_press", "standing_overhead_press", "dumbbell_shoulder_press",
        "barbell_bent_over_row", "one_arm_dumbbell_row", "chest_supported_row",
        "dumbbell_curl", "hammer_curl", "skull_crusher",
        "overhead_triceps_extension", "farmer_carry", "suitcase_carry",
        "weighted_plank", "russian_twist",
        "dumbbell_lateral_raise", "straight_bar_biceps_curl",
        "barbell_good_morning",
    )

    fun defaults(): List<Machine> = SEEDS.mapIndexed { index, seed ->
        Machine(
            id = seed.id,
            name = seed.name,
            iconKey = seed.icon,
            group = seed.group,
            equipment = seed.equipment,
            visible = seed.visible,
            custom = false,
            sortOrder = index,
        )
    }

    /**
     * Machines added to the catalog after a user's install already exists get
     * merged in on launch, so an app update never silently drops new equipment.
     * They arrive hidden, so an update never rearranges the grid either.
     */
    fun mergeNewSeeds(existing: List<Machine>): List<Machine> {
        val known = existing.map { it.id }.toSet()
        val additions = defaults().filter { it.id !in known }
        if (additions.isEmpty()) return existing
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        return existing + additions.mapIndexed { i, m ->
            m.copy(sortOrder = nextOrder + i, visible = false)
        }
    }
}
