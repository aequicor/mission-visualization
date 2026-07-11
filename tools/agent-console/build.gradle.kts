import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    application
}

dependencies {
    implementation(projects.shared)
    // Explicit engine deps so the console never relies on :shared's transitive api surface.
    implementation(projects.engine.ir)
    implementation(projects.engine.frontend)
    implementation(projects.engine.backendCompose)
    implementation(projects.subsystems.typographyCompose)

    // Skiko natives + ImageComposeScene for the headless render path.
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)

    implementation(libs.kotlinx.coroutinesSwing)
    // JSON output via runtime builders (buildJsonObject) — no serialization plugin needed.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
}

application {
    mainClass.set("io.aequicor.visualization.agent.MainKt")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}
