package com.balandman.pawgress.ui

import com.balandman.pawgress.resources.Res
import com.balandman.pawgress.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource

/**
 * The motivational cat images live in `shared/src/commonMain/composeResources/drawable/`,
 * named mascot_01, mascot_02, and so on, each a transparent-background PNG.
 *
 * Found here by scanning [Res.allDrawableResources] -- Compose Multiplatform's
 * generated map of every resource name to its [DrawableResource] -- rather than
 * a hardcoded list, exactly mirroring the Android-only original's trick of
 * reflecting over the generated `R.drawable` class's fields. Same result:
 * dropping new mascot_NN files into the resources folder is all it takes to
 * add them; no code change, and no compile error while zero of them exist yet.
 * (Compose Multiplatform's resource codegen is compile-time, not reflection --
 * `allDrawableResources` is a real generated map, not something built by
 * introspecting classes at runtime -- but it plays the identical role here.)
 */
object MascotCatalog {

    private val ids: List<DrawableResource> by lazy {
        Res.allDrawableResources
            .filterKeys { it.startsWith("mascot_") }
            .values
            .toList()
    }

    /** mascot_01 -> coach id 1, and so on — a coach's base look before any outfit. */
    private val byNumber: Map<Int, DrawableResource> by lazy {
        Res.allDrawableResources
            .mapNotNull { (name, resource) ->
                val number = Regex("""^mascot_(\d+)$""").find(name)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@mapNotNull null
                number to resource
            }
            .toMap()
    }

    val hasMascots: Boolean get() = ids.isNotEmpty()

    /** A random mascot drawable, or null until the first mascot_NN image exists. */
    fun random(): DrawableResource? = ids.randomOrNull()

    /** The specific coach's base portrait (mascot_NN), or null if not supplied. */
    fun forNumber(number: Int): DrawableResource? = byNumber[number]
}
