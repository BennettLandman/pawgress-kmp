plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    // Phase 5: Compose Multiplatform, so the ported UI screens can live here
    // instead of duplicated per platform. org.jetbrains.compose provides the
    // compose.* dependency aliases and the resource-generation pipeline
    // (composeResources/ -> the generated Res object); plugin.compose is the
    // actual Compose compiler transform, required as a separate plugin since
    // Kotlin 2.0 (androidApp already applies it for its own Jetpack Compose
    // usage -- this is the same plugin, now also needed here).
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // MainViewModel.kt (Phase 5) extends androidx.lifecycle.ViewModel,
            // now a genuine multiplatform artifact -- export() is needed so
            // Swift/Xcode callers (Phase 6) can actually see that type through
            // the compiled framework, not just Kotlin consumers.
            export("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: LiftRepository's public surface (StateFlow<Profile>,
            // CoachTheme.isActiveOn(LocalDate), etc.) exposes these types to androidApp/iosApp,
            // so consumers need them on their own compile classpath too. Same reasoning for
            // ktor-client-core now that SheetsApi's constructor takes an HttpClient.
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            api("io.ktor:ktor-client-core:3.5.2")
            // The cross-platform ViewModel/viewModelScope base class for
            // MainViewModel.kt (Phase 5) -- a genuine multiplatform artifact
            // (androidx.lifecycle, not a JetBrains fork) since 2.8.0. api,
            // not implementation, per Android's own KMP docs: the type must
            // be exported to the iOS binary framework (see the export() call
            // in the iosTarget.binaries.framework block above).
            api("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
            // Serialization is purely an internal persistence/networking detail (private DTOs
            // in LiftRepository.kt, dynamic JSON building in SheetsApi.kt), so this stays
            // implementation-only.
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            // Compose Multiplatform (Phase 5) -- the actual UI screens ported
            // from LiftLog's ui/*.kt live here now, so androidApp/iosApp just
            // host a single shared @Composable entry point apiece.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // MainScreen.kt's done-badge/settings-gear icons use the same
            // androidx.compose.material.icons.filled.* classes LiftLog used
            // on Android -- this CMP artifact publishes the identical
            // package, so the icon imports needed zero changes when ported.
            implementation(compose.materialIconsExtended)
            // api, not implementation: MascotCatalog/CoachOutfitArt/CoachArt/
            // MachineIcons return DrawableResource in their public signatures,
            // and androidApp's own MainActivity.kt calls painterResource()
            // directly on the result (Phase 5 smoke test) -- unlike compose.ui
            // above, this Compose-Multiplatform-only artifact has no other
            // route onto androidApp's classpath (androidApp's own Jetpack
            // Compose dependency supplies the real androidx.compose.ui classes
            // directly, since those are binary-identical on the Android
            // target, but components-resources is CMP-specific).
            api(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // Ktor's Android/JVM HTTP engine — auto-discovered by the bare
            // HttpClient() call in SheetsApi.kt, no explicit wiring needed here.
            implementation("io.ktor:ktor-client-okhttp:3.5.2")
            // Google's Identity Authorization API, used by AndroidAuthProvider.kt.
            // Same version already proven working in the original LiftLog app.
            implementation("com.google.android.gms:play-services-auth:21.3.0")
            // ActivityResultLauncher/ActivityResult/IntentSenderRequest, used by
            // AndroidConsentLauncher.kt. shared is a separate Gradle module from
            // androidApp, so androidApp's own activity-compose dependency doesn't
            // reach code compiled here -- this needs its own declaration. Same
            // version LiftLog already uses (there, transitively, via activity-compose).
            implementation("androidx.activity:activity-ktx:1.9.3")
        }
        iosMain.dependencies {
            // Ktor's engine backed by NSURLSession — same auto-discovery as above.
            implementation("io.ktor:ktor-client-darwin:3.5.2")
        }
    }
}

android {
    namespace = "com.balandman.pawgress.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Needed for Compose to actually run on the Android target -- androidApp's
    // own module already sets this for its (now-placeholder) Jetpack Compose
    // usage; shared needs it too now that the real screens live here.
    buildFeatures {
        compose = true
    }
}

// Pinned explicitly rather than left to the default {group}.{module}.generated.resources
// derivation -- that default depends on this project's Gradle `group`, which is unset
// here, a documented source of inconsistent/malformed generated package names
// (JetBrains/compose-multiplatform#4320). Explicit is one less thing to get wrong.
compose.resources {
    packageOfResClass = "com.balandman.pawgress.resources"
}
