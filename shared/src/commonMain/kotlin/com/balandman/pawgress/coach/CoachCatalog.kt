package com.balandman.pawgress.coach

import com.balandman.pawgress.data.CoachTheme

/**
 * One selectable coach. [id] matches the existing mascot numbering (1-20), so
 * a coach's base portrait is simply `mascot_<id padded to 2 digits>` — see
 * `MascotCatalog.forNumber` in the ui package.
 */
data class Coach(
    val id: Int,
    val breed: String,
    val name: String,
    val personality: String,
    val unlockCost: Int,
)

/**
 * The full coach roster. Coach 1 is free; every other coach costs pawprints
 * to unlock, climbing by 5 per coach and capping at 100 for coach 20.
 */
object CoachCatalog {
    /** Flat price for any outfit, on any coach, regardless of theme. */
    const val OUTFIT_COST = 15

    val ALL: List<Coach> = listOf(
        Coach(1, "Maine Coon", "Coach Moose", "Gentle giant — patient, steady, big-brother hype", 0),
        Coach(2, "Ragdoll", "Coach Noodle", "Zen and floppy-chill — consistency over intensity", 10),
        Coach(3, "Persian", "Duchess Marmalade", "Glamorous diva — every set is a red-carpet moment", 15),
        Coach(4, "Exotic Shorthair", "Coach Pancake", "Cozy teddy bear — big on rest days", 20),
        Coach(5, "Devon Rex", "Coach Sprocket", "Mischievous pixie — silly dares, acrobatic energy", 25),
        Coach(6, "Abyssinian", "Coach Blaze", "Intense athlete — competitive, PR-obsessed", 30),
        Coach(7, "British Shorthair", "Sir Reggie", "Dry, dignified stoic — deadpan one-liners", 35),
        Coach(8, "Siberian", "Coach Tundra", "Rugged outdoorsman — toughen-up grit", 40),
        Coach(9, "American Shorthair", "Coach Buck", "Reliable everyday buddy — \"just show up\"", 45),
        Coach(10, "Russian Blue", "Coach Sasha", "Quiet confidant — soft-spoken, thoughtful", 50),
        Coach(11, "Siamese", "Coach Mimi", "Loud chatterbox — nonstop hype", 55),
        Coach(12, "Bengal", "Coach Rajah", "Wild jungle athlete — primal, adventurous", 60),
        Coach(13, "Sphynx", "Coach Nova", "Bold extrovert — no filter, confident, goofy", 65),
        Coach(14, "Birman", "Coach Lotus", "Serene temple sage — mystical calm", 70),
        Coach(15, "Scottish Fold", "Coach Owlie", "Patient listener — gently funny", 75),
        Coach(16, "Norwegian Forest Cat", "Coach Ragnar", "Epic Viking bravado", 80),
        Coach(17, "Oriental Shorthair", "Coach Cleo", "Clever wit — puzzles and wordplay", 85),
        Coach(18, "Burmese", "Coach Bo", "Loyal best friend — \"we're in this together\"", 90),
        Coach(19, "Tonkinese", "Coach Tonka", "Playful all-rounder — upbeat challenges", 95),
        Coach(20, "Turkish Angora", "Coach Sultan", "Graceful perfectionist — technique over ego", 100),
        // Coaches 21 and 22 arrived after the original roster of twenty and are
        // deliberately priced as a tier above it (125/150) rather than
        // continuing the +5 ladder, so they read as something to work toward
        // rather than two more rungs.
        Coach(21, "Domestic Shorthair", "Coach Yip Yip", "Bouncing livewire — scattered until he locks on, then all in", 125),
        // Working name, kept short on purpose: the full version is
        // "Tiny One Fuzzy Pants Catnip Everpe", which is four times the length
        // of any other name here and wraps to three lines on a coach card.
        // Changing the display name is this one string.
        Coach(22, "Domestic Longhair", "Tiny1 Fuzzy Pants", "Ancient and unimpressed — but her praise means everything", 150),
    )

    fun byId(id: Int): Coach? = ALL.firstOrNull { it.id == id }
}

/** Key used in [Profile.unlockedOutfits] for one coach's ownership of one theme. */
fun outfitKey(coachId: Int, theme: CoachTheme): String = "$coachId:${theme.slug}"
