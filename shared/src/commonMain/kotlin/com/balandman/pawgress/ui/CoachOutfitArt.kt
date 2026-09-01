package com.balandman.pawgress.ui

import com.balandman.pawgress.resources.Res
import com.balandman.pawgress.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource

/**
 * Seasonal outfit portraits live in `shared/src/commonMain/composeResources/drawable/`,
 * one full-replacement image per coach per theme, named
 * `coach_look_<coachId two digits>_<themeSlug>` — e.g. `coach_look_07_halloween`.
 *
 * Found by scanning [Res.allDrawableResources], exactly like [MascotCatalog], so
 * any coach+theme combination the user hasn't drawn yet is simply absent — no
 * compile-time reference, no crash, nothing to update in code as art gets
 * added incrementally. See [MascotCatalog]'s doc comment for how this replaces
 * the Android-only original's reflection-over-`R.drawable` trick.
 */
object CoachOutfitArt {

    private val NAME_PATTERN = Regex("""^coach_look_(\d+)_([a-z]+)$""")

    /** "coachId:themeSlug" -> drawable resource. */
    private val byKey: Map<String, DrawableResource> by lazy {
        Res.allDrawableResources
            .mapNotNull { (name, resource) ->
                val match = NAME_PATTERN.find(name) ?: return@mapNotNull null
                val coachId = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val slug = match.groupValues[2]
                "$coachId:$slug" to resource
            }
            .toMap()
    }

    /** The outfit portrait for this coach+theme, or null if that art hasn't been supplied. */
    fun resFor(coachId: Int, themeSlug: String): DrawableResource? = byKey["$coachId:$themeSlug"]

    /** True once at least one outfit portrait exists for this coach, any theme. */
    fun hasAny(coachId: Int): Boolean = byKey.keys.any { it.startsWith("$coachId:") }
}
