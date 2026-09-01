package com.balandman.pawgress.ui

import com.balandman.pawgress.data.CoachTheme
import com.balandman.pawgress.data.GymDay
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.DrawableResource

/**
 * Resolves what a coach actually looks like right now: the seasonal outfit if
 * one is equipped, currently in season, and its art has been supplied — the
 * plain base portrait otherwise. Every fallback step is graceful, so this
 * never crashes or shows nothing just because an outfit image hasn't been
 * drawn yet.
 *
 * Ported unchanged from the Android-only original apart from `Int` (an
 * `@DrawableRes` id) becoming `DrawableResource` and `java.time.LocalDate`
 * becoming `kotlinx.datetime.LocalDate`.
 */
object CoachArt {

    /** The coach's un-costumed look, or null until its mascot_NN image exists. */
    fun base(coachId: Int): DrawableResource? = MascotCatalog.forNumber(coachId)

    /**
     * The coach's current look. [equippedThemeSlug] is whatever the profile has
     * stored for this coach (from [com.balandman.pawgress.data.Profile.equippedOutfits]),
     * independent of season — this is where that preference actually gets
     * gated by the calendar and by whether the art exists.
     */
    fun current(
        coachId: Int,
        equippedThemeSlug: String?,
        today: LocalDate = GymDay.today(),
    ): DrawableResource? {
        val theme = CoachTheme.fromSlug(equippedThemeSlug)
        if (theme != null && theme.isActiveOn(today)) {
            CoachOutfitArt.resFor(coachId, theme.slug)?.let { return it }
        }
        return base(coachId)
    }
}
