import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
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
