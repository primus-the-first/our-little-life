import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: the game rules know nothing about Android, so they can be tested without a phone.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
