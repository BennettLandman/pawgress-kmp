import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release upload-key signing. `androidApp/keystore.properties` is gitignored
// and deliberately not checked in -- it points at `release-upload-key.jks`
// (also gitignored, matched by the existing `*.jks` pattern) plus the store/
// key passwords generated alongside it. Both files only exist locally, so a
// fresh clone builds `release` unsigned rather than failing Gradle sync --
// same "graceful with nothing present" pattern used elsewhere in this repo
// (MascotCatalog, CoachOutfitArt) rather than a hard requirement.
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.balandman.pawgress"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.balandman.pawgress"
        minSdk = 26
        // Google requires new submissions/updates to target API 36
        // (Android 16) as of August 31, 2026 -- was 35 (Android 15) until
        // this bump, which would have been rejected by Play Console.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// `android { kotlinOptions { jvmTarget = "17" } }` (the old form, still
// present until this Kotlin bump) hit a hard compile ERROR once the Kotlin
// plugin version moved to 2.3.21 -- `var jvmTarget: String` is deprecated at
// error level as of this Kotlin version, not just a warning, so the script
// itself failed to compile. Replaced with the modern `compilerOptions` DSL,
// matching the exact same pattern shared/build.gradle.kts already uses for
// its androidTarget block.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The whole point of this module: everything ported into shared/ is
    // available here unchanged.
    implementation(project(":shared"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // viewModelFactory { initializer { ... } } -- lets MainActivity construct
    // MainViewModel with real constructor args (a LiftRepository + SyncManager
    // built from Android-only types) via `by viewModels { ... }`, matching the
    // version already pinned for lifecycle-viewmodel in shared/build.gradle.kts.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
