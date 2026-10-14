import java.util.Properties
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
        // tools/release.sh publishes this version to friends' phones; bump both before each release.
        versionCode = 31
        versionName = "0.24.0"
    }

    // Release builds are signed with the key in keystore.properties (never committed; see CLAUDE.md).
    // Every update must use the same key, or phones refuse to install it. tools/release.sh then adds the
    // proof that this key replaces the debug key the first phones were on (see CLAUDE.md).
    val signing = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Shrinks the code a lot, so updates are a smaller download for friends on mobile data.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
    // Camp news: a check every 15 minutes while the game is closed.
    implementation(libs.androidx.work.runtime)
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
