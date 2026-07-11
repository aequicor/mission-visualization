import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "io.aequicor.visualization.engine.frontend"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.engine.ir)
            // Figures types (VectorNetwork, ShapeType, ...) appear in SLM patch/edit public surface.
            api(projects.subsystems.figures)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Round-trip tests serialize IR to JSON via engine:ir's writer, which returns JsonObject.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
