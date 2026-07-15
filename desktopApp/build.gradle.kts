import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val releaseVersion = providers.gradleProperty("missionVisualizationVersion").get()

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
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            optimize.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Mission Visualization"
            packageVersion = releaseVersion
            description = "Semantic Layout Markdown visual editor"
            vendor = "Aequicor"

            windows {
                iconFile.set(project.file("src/main/resources/icons/mission-logo.ico"))
            }

            macOS {
                bundleID = "io.aequicor.visualization"
                iconFile.set(project.file("src/main/resources/icons/mission-logo.icns"))
            }

            linux {
                iconFile.set(project.file("src/main/resources/icons/mission-logo.png"))
            }
        }
    }
}

val innoSetupCompiler = providers.gradleProperty("innoSetupCompiler")
    .orElse(providers.environmentVariable("INNO_SETUP_COMPILER"))
    .orElse("ISCC.exe")
val windowsInstallerScript = layout.projectDirectory.file(
    "src/main/installer/windows/MissionVisualization.iss",
)
val windowsAppImageDirectory = layout.buildDirectory.dir(
    "compose/binaries/main-release/app/Mission Visualization",
)
val windowsInstallerOutputDirectory = layout.buildDirectory.dir(
    "compose/binaries/main-release/inno",
)
val windowsInstallerFile = windowsInstallerOutputDirectory.map { directory ->
    directory.file("Mission Visualization-$releaseVersion-setup.exe")
}

tasks.register<Exec>("packageReleaseWindowsInstaller") {
    group = "compose desktop"
    description = "Builds the current-user Windows installer with Inno Setup."
    dependsOn("createReleaseDistributable")
    inputs.file(windowsInstallerScript)
    inputs.dir(windowsAppImageDirectory)
    inputs.property("appVersion", releaseVersion)
    inputs.property("innoSetupCompiler", innoSetupCompiler)
    outputs.file(windowsInstallerFile)
    commandLine(
        innoSetupCompiler.get(),
        "/DAppVersion=$releaseVersion",
        "/DSourceDir=${windowsAppImageDirectory.get().asFile.absolutePath}",
        "/DOutputDir=${windowsInstallerOutputDirectory.get().asFile.absolutePath}",
        windowsInstallerScript.asFile.absolutePath,
    )
}

@DisableCachingByDefault(because = "Uses macOS disk image tools and a mounted writable volume.")
abstract class BrandDmgVolumeIconTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDmg: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val volumeIcon: RegularFileProperty

    @get:Input
    abstract val outputFileName: Property<String>

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @TaskAction
    fun brandVolume() {
        val workingDirectory = temporaryDir.toPath()
        fileSystemOperations.delete { delete(workingDirectory) }
        Files.createDirectories(workingDirectory)
        val writableImage = workingDirectory.resolve("writable.dmg")
        exec(
            "/usr/bin/hdiutil",
            "convert",
            inputDmg.get().asFile.absolutePath,
            "-format",
            "UDRW",
            "-o",
            writableImage.toString(),
        )

        val attachOutput = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(
                "/usr/bin/hdiutil",
                "attach",
                "-readwrite",
                "-noverify",
                "-noautoopen",
                "-nobrowse",
                writableImage.toString(),
            )
            standardOutput = attachOutput
        }.assertNormalExitValue()
        val mounted = parseMountedImage(attachOutput.toString(Charsets.UTF_8))
        try {
            Files.copy(
                volumeIcon.get().asFile.toPath(),
                mounted.path.resolve(".VolumeIcon.icns"),
                StandardCopyOption.REPLACE_EXISTING,
            )
            exec("/usr/bin/SetFile", "-a", "C", mounted.path.toString())
            removeUnexpectedVolumeEntries(mounted.path)
        } finally {
            exec("/usr/bin/hdiutil", "detach", mounted.device)
        }

        val destination = destinationDirectory.get().asFile.toPath()
        fileSystemOperations.delete { delete(destination) }
        Files.createDirectories(destination)
        exec(
            "/usr/bin/hdiutil",
            "convert",
            writableImage.toString(),
            "-format",
            "UDZO",
            "-imagekey",
            "zlib-level=9",
            "-o",
            destination.resolve(outputFileName.get()).toString(),
        )
    }

    private fun exec(vararg command: String) {
        execOperations.exec { commandLine(*command) }.assertNormalExitValue()
    }

    private fun parseMountedImage(output: String): MountedImage {
        val line = output.lineSequence().firstOrNull { it.contains(VOLUME_MOUNT_PREFIX) }
            ?: error("Could not find a mounted volume in hdiutil output:\n$output")
        val mountIndex = line.lastIndexOf(VOLUME_MOUNT_PREFIX)
        val device = line.trimStart().takeWhile { character -> !character.isWhitespace() }
        return MountedImage(
            device = device,
            path = Path.of(line.substring(mountIndex).trim()),
        )
    }

    private fun removeUnexpectedVolumeEntries(volumeRoot: Path) {
        Files.list(volumeRoot).use { entries ->
            entries
                .filter { entry -> entry.fileName.toString() !in REQUIRED_VOLUME_ENTRIES }
                .forEach { entry -> fileSystemOperations.delete { delete(entry) } }
        }
    }

    private data class MountedImage(
        val device: String,
        val path: Path,
    )

    private companion object {
        const val VOLUME_MOUNT_PREFIX = "/Volumes/"
        val REQUIRED_VOLUME_ENTRIES = setOf(
            ".DS_Store",
            ".VolumeIcon.icns",
            "Applications",
            "Mission Visualization.app",
        )
    }
}

val rawReleaseDmgDirectory = layout.buildDirectory.dir(
    "compose/binaries/main-release/dmg-raw",
)
val finalReleaseDmgDirectory = layout.buildDirectory.dir(
    "compose/binaries/main-release/dmg",
)
val releaseDmgFileName = "Mission Visualization-$releaseVersion.dmg"
val brandReleaseDmgVolumeIcon = tasks.register<BrandDmgVolumeIconTask>(
    "brandReleaseDmgVolumeIcon",
) {
    inputDmg.set(rawReleaseDmgDirectory.map { directory -> directory.file(releaseDmgFileName) })
    volumeIcon.set(layout.projectDirectory.file("src/main/resources/icons/mission-logo.icns"))
    outputFileName.set(releaseDmgFileName)
    destinationDirectory.set(finalReleaseDmgDirectory)
    onlyIf("DMG branding is available only on macOS") {
        System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)
    }
}

afterEvaluate {
    (tasks.findByName("packageReleaseDmg") as? AbstractJPackageTask)?.apply {
        destinationDir.set(rawReleaseDmgDirectory)
        outputs.dir(rawReleaseDmgDirectory)
        finalizedBy(brandReleaseDmgVolumeIcon)
    }
}
