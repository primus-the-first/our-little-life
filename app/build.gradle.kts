import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Reads app/google-services.json (not in Git) and generates the Firebase config and web client ID.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ourlittlelife.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ourlittlelife.game"
        minSdk = 26
        targetSdk = 35
        // Real phones only: 64-bit ARM (nearly every phone of the last several years) and older 32-bit ARM.
        // The 3D engine is native code, so each extra chip type adds several megabytes.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        versionCode = 18
        versionName = "0.16.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Filament's compiled materials and textures must stay uncompressed inside the APK.
    androidResources {
        noCompress += listOf("filamat", "ktx")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:rules"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // The 3D view: SceneView on Filament (Apache 2.0).
    implementation(libs.sceneview)

    // Shared world: sign-in and the database. All on Firebase's free plan.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
