plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.android.library") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    // Was pinned at 2.0.21 (left behind by an earlier Android Studio upgrade
    // that only bumped kotlin.android/kotlin.plugin.compose) -- bumped to
    // match, and because Compose Multiplatform 1.12.0 needs Kotlin 2.1.0+.
    id("org.jetbrains.kotlin.multiplatform") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // Compose Multiplatform itself (resources, and the compose.* dependency
    // aliases) -- new in Phase 5, applied for real in shared/build.gradle.kts.
    id("org.jetbrains.compose") version "1.12.0" apply false
}
