plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.android.library") version "9.3.2" apply false
    // Kotlin 2.2.10 (this project's original post-Android-Studio-upgrade
    // pin) turned out to be too OLD for real published iOS artifacts: both
    // Ktor 3.4.0+ and Compose Multiplatform 1.12.0's iosSimulatorArm64
    // klibs are built with Kotlin 2.3.x, and a compiler can only read klibs
    // at its own ABI version or older -- 2.2.10's compiler tops out at ABI
    // 2.2.0, one short of what these actually need (ABI 2.3.0). First hit
    // as `KLIB resolver: ... incompatible ABI version '2.3.0'` during
    // Phase 6's first-ever Kotlin/Native compile attempts (once for Ktor,
    // then again for Compose Multiplatform's components-resources). Bumped
    // to 2.3.21 -- the exact version Ktor 3.5.2 itself is built with, and
    // one patch ahead of the 2.3.20 Compose Multiplatform 1.12.0's iOS
    // resources artifact was built with -- rather than pinning dependencies
    // down, since a newer compiler reading older-ABI klibs is the safe
    // direction (the reverse isn't). kotlin.plugin.compose must track this
    // exact same version (JetBrains' own compatibility requirement: the
    // Compose Compiler plugin version must match the Kotlin version it's
    // applied alongside). kotlin.plugin.serialization stays at its existing
    // 2.0.21 pin below -- already a couple of minor versions behind before
    // this change and apparently tolerant of the gap (this project's real
    // Android builds already proved that combination compiles), so left
    // alone rather than changed pre-emptively; revisit only if a build
    // error actually implicates it.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // Compose Multiplatform itself (resources, and the compose.* dependency
    // aliases) -- new in Phase 5, applied for real in shared/build.gradle.kts.
    id("org.jetbrains.compose") version "1.12.0" apply false
}
