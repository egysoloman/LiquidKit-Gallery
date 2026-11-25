import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    `maven-publish`
}

group = "io.liquidkit"
version = "1.0.0"

kotlin {
    android {
        minSdk = 21
        compileSdk = 37
        buildToolsVersion = "37.0.0"
        namespace = "io.liquidkit"
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    applyDefaultHierarchyTemplate()

    jvm("desktop")

    js {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    macosArm64()
    iosArm64("iosArm64")
    iosSimulatorArm64("iosSimulatorArm64")

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                api(project(":backdrop"))
                implementation(libs.compose.animation)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.ui.graphics)
                implementation(libs.kyant.shapes)
            }
        }

        val skikoMain = create("skikoMain") {
            dependsOn(commonMain)
        }

        getByName("desktopMain").dependsOn(skikoMain)
        getByName("desktopTest").dependencies {
            implementation("junit:junit:4.13.2")
            runtimeOnly(compose.desktop.currentOs)
        }
        getByName("macosArm64Main").dependsOn(skikoMain)
        getByName("iosMain").dependsOn(skikoMain)
        getByName("jsMain").dependsOn(skikoMain)
        getByName("wasmJsMain").dependsOn(skikoMain)
    }
}
