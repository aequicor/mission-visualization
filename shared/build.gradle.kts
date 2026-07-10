import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    packageOfResClass = "io.aequicor.visualization.shared.generated.resources"
}

val appVersionProvider = providers.gradleProperty("mvAppVersion")
    .orElse(providers.environmentVariable("MV_APP_VERSION"))
    .orElse("0.1.0-dev")

val generatedAppBuildInfoDir = layout.buildDirectory.dir("generated/appBuildInfo/commonMain/kotlin")

val generateAppBuildInfo by tasks.registering {
    val appVersion = appVersionProvider
    inputs.property("version", appVersion)
    outputs.dir(generatedAppBuildInfoDir)

    doLast {
        fun String.asKotlinStringLiteral(): String = buildString {
            this@asKotlinStringLiteral.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\${'$'}")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }

        val version = inputs.properties["version"]?.toString() ?: "0.1.0-dev"
        val file = outputs.files.singleFile.resolve("io/aequicor/visualization/AppBuildInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.aequicor.visualization

            object AppBuildInfo {
                const val VERSION: String = "${version.asKotlinStringLiteral()}"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "io.aequicor.visualization.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain {
            kotlin.srcDir(generatedAppBuildInfoDir)
            dependencies {
                api(projects.engine.ir)
                implementation(projects.engine.backendCompose)
                // api: editor state / use-case API exposes SlmCompileResult.
                api(projects.engine.frontend)

                // Anchoring/snapping subsystem: pure engine (api — result types cross the UI boundary)
                // + Compose overlay renderer (implementation — used only inside editor.ui).
                api(projects.subsystems.anchoring)
                implementation(projects.subsystems.anchoringCompose)

                // Typography subsystem: pure span algebra / selection contracts (api — reducer and
                // intents expose its types) + Compose selection geometry and font provider.
                api(projects.subsystems.typography)
                implementation(projects.subsystems.typographyCompose)

                // Local draft persistence: coroutines for autosave flow, serialization for the envelope.
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.compose.components.resources)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateAppBuildInfo)
}
