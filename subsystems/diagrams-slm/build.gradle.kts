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
        namespace = "io.aequicor.visualization.subsystems.diagrams.slm"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.subsystems.diagrams)
            // Parse/write-back adapter: registers a `diagram:` typed block in the frontend
            // extension registry and patches *.layout.md sources. No Compose here.
            api(projects.engine.frontend)
            api(projects.engine.ir)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
