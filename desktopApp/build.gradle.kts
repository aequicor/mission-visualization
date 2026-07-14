import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(projects.engine.backendCompose)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.serialization.json)

    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.14.0")
    implementation("io.ktor:ktor-server-cio:3.4.3")

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.components.resources)

    testImplementation(libs.kotlin.testJunit)
    testImplementation("io.modelcontextprotocol:kotlin-sdk-client:0.14.0")
    testImplementation("io.ktor:ktor-client-cio:3.4.3")
}

compose.desktop {
    application {
        mainClass = "io.aequicor.visualization.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "mission-visualization"
            packageVersion = "1.0.0"

            windows {
                iconFile.set(project.file("src/main/resources/icons/mission-logo.ico"))
            }

            macOS {
                iconFile.set(project.file("src/main/resources/icons/mission-logo.icns"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/icons/mission-logo.png"))
            }
        }
    }
}
