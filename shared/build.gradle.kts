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

val agentSkillSources = listOf(
    Triple("SLM", "SLM", "SKILLS/SLM.md"),
    Triple("DIAGRAMS", "SLM diagrams", "SKILLS/SLM-diagrams.md"),
    Triple("VECTOR_GRAPHICS", "SLM vector graphics", "SKILLS/SLM-vector-graphics.md"),
    Triple("TYPOGRAPHY", "SLM typography", "SKILLS/SLM-typography.md"),
    Triple("ANNOTATIONS", "SLM annotations", "SKILLS/SLM-annotations.md"),
    Triple("EDITOR", "SLM editor", "SKILLS/SLM-editor.md"),
)
val agentSkillMarkdownFiles = agentSkillSources.map { (_, _, sourcePath) ->
    rootProject.layout.projectDirectory.file(sourcePath)
}
val generatedAgentSkillCatalogDir = layout.buildDirectory.dir("generated/agentSkills/commonMain/kotlin")

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

val generateAgentSkillCatalog by tasks.registering {
    inputs.files(agentSkillMarkdownFiles)
        .withPropertyName("agentSkillMarkdownFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property(
        "agentSkillSources",
        agentSkillSources.joinToString("\n") { (id, title, sourcePath) ->
            "$id\t$title\t$sourcePath"
        },
    )
    outputs.dir(generatedAgentSkillCatalogDir)

    doLast {
        // Keep the action configuration-cache safe: use task inputs/outputs only and do not
        // capture Project, Provider, or Kotlin build-script objects from the configuration phase.
        val sources = inputs.properties.getValue("agentSkillSources").toString()
            .lineSequence()
            .filter(String::isNotBlank)
            .map { metadataLine ->
                val parts = metadataLine.split('\t', limit = 3)
                if (parts.size != 3) {
                    throw GradleException("Invalid agent skill metadata: $metadataLine")
                }
                Triple(parts[0], parts[1], parts[2])
            }
            .toList()
        val inputFilesByName = inputs.files.files.associateBy { it.name }

        fun String.asKotlinStringLiteral(): String = buildString {
            this@asKotlinStringLiteral.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\${'$'}")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }

        fun appendChunkedMarkdown(target: StringBuilder, markdown: String) {
            target.appendLine("                markdown = buildString {")
            markdown.chunked(8_000).forEach { chunk ->
                target.appendLine("                    append(\"${chunk.asKotlinStringLiteral()}\")")
            }
            target.appendLine("                },")
        }

        val outputFile = outputs.files.singleFile.resolve(
            "io/aequicor/visualization/editor/data/AgentSkillCatalog.generated.kt",
        )
        outputFile.parentFile.mkdirs()

        val generatedSource = buildString {
            appendLine("package io.aequicor.visualization.editor.data")
            appendLine()
            appendLine("import io.aequicor.visualization.editor.domain.AgentSkill")
            appendLine("import io.aequicor.visualization.editor.domain.AgentSkillId")
            appendLine()
            appendLine("/** Generated from the canonical Markdown files in SKILLS. Do not edit. */")
            appendLine("internal object AgentSkillCatalog {")
            appendLine("    val all: List<AgentSkill> = listOf(")
            sources.forEachIndexed { index, (id, title, sourcePath) ->
                val fileName = sourcePath.substringAfterLast('/')
                val sourceFile = inputFilesByName[fileName]
                    ?.takeIf { it.isFile }
                    ?: throw GradleException("Required agent skill source is missing: $sourcePath")
                val markdown = sourceFile.readText(Charsets.UTF_8)
                appendLine("        AgentSkill(")
                appendLine("            id = AgentSkillId.$id,")
                appendLine("            title = \"${title.asKotlinStringLiteral()}\",")
                appendLine("            sourcePath = \"${sourcePath.asKotlinStringLiteral()}\",")
                appendChunkedMarkdown(this, markdown)
                appendLine("            isRequired = ${index == 0},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine()
            appendLine("    val base: AgentSkill = all.first { it.id == AgentSkillId.SLM }")
            appendLine("    val specialists: List<AgentSkill> = all.filterNot(AgentSkill::isRequired)")
            appendLine("}")
        }
        outputFile.writeText(generatedSource, Charsets.UTF_8)
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
            kotlin.srcDir(generatedAgentSkillCatalogDir)
            dependencies {
                api(projects.engine.ir)
                implementation(projects.engine.backendCompose)
                // api: editor state / use-case API exposes SlmCompileResult.
                api(projects.engine.frontend)

                // Anchoring/snapping subsystem: pure engine (api — result types cross the UI boundary)
                // + Compose overlay renderer (implementation — used only inside editor.ui).
                api(projects.subsystems.anchoring)
                implementation(projects.subsystems.anchoringCompose)

                // Annotations subsystem: pure model / ops / prompt export + sidecar SLM format (api —
                // their types cross editor state/intents) + Compose overlay renderer (implementation).
                api(projects.subsystems.annotations)
                api(projects.subsystems.annotationsSlm)
                implementation(projects.subsystems.annotationsCompose)

                // Figures subsystem: pure geometry / vector-network model + editing ops (api — its
                // types cross into editor state/intents) + Compose overlay & previews (implementation).
                api(projects.subsystems.figures)
                implementation(projects.subsystems.figuresCompose)
                // Diagrams subsystem: pure graph model/routing/editing ops (api — editor state and
                // intents expose its types) + Compose canvas/preview renderer (implementation)
                // + SLM parse/write-back adapter wired at the composition root.
                api(projects.subsystems.diagrams)
                implementation(projects.subsystems.diagramsCompose)
                api(projects.subsystems.diagramsSlm)
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
    dependsOn(generateAgentSkillCatalog)
}
