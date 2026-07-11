package io.aequicor.visualization.agent

import io.aequicor.visualization.engine.ir.model.DesignSeverity
import java.io.File

/**
 * Command-line surface of the automation subsystem. Every command loads sources
 * (a `--project` directory of `*.layout.md`, or the bundled `--samples`), runs the
 * pure pipeline, and emits PNG files or JSON on stdout. Exit code 0 = success.
 */
object AgentCli {

    private const val USAGE = """mission-visualization agent console

Usage:
  render         (--project DIR | --samples) (--screen ID | --all) [--out FILE] [--out-dir DIR] [--scale N]
  screens        (--project DIR | --samples)
  inspect        (--project DIR | --samples) --screen ID [--node ID]
  validate       (--project DIR | --samples) [--screen ID]
  export-samples --to DIR
  create-screen  --project DIR --preset (mobile|tablet|desktop|square) --title TITLE

The agent loop: edit *.layout.md (CNL) -> validate -> render/inspect -> repeat."""

    fun run(args: Array<String>): Int {
        if (args.isEmpty() || args[0] == "--help" || args[0] == "-h" || args[0] == "help") {
            println(USAGE)
            return if (args.isEmpty()) 1 else 0
        }
        val command = args[0]
        val options = parseOptions(args.drop(1))
        return try {
            when (command) {
                "render" -> renderCommand(options)
                "screens" -> screensCommand(options)
                "inspect" -> inspectCommand(options)
                "validate" -> validateCommand(options)
                "export-samples" -> exportSamplesCommand(options)
                "create-screen" -> createScreenCommand(options)
                else -> {
                    System.err.println("Unknown command: $command\n\n$USAGE")
                    1
                }
            }
        } catch (failure: IllegalArgumentException) {
            System.err.println("error: ${failure.message}")
            1
        } catch (failure: IllegalStateException) {
            System.err.println("error: ${failure.message}")
            1
        }
    }

    private fun renderCommand(options: Map<String, String>): Int {
        val session = sessionFor(options)
        val scale = options["scale"]?.let {
            requireNotNull(it.toFloatOrNull()) { "--scale must be a number, got '$it'" }
        } ?: HeadlessRenderer.DEFAULT_SCALE
        val document = requireNotNull(session.document) { failedCompileMessage(session) }

        val targets = if (options.containsKey("all")) {
            session.screens().map { it.id }
        } else {
            listOf(requireScreen(options))
        }
        require(targets.isNotEmpty()) { "Document has no screens" }

        val outDir = options["out-dir"]?.let { File(it) }
        outDir?.let { require(it.isDirectory || it.mkdirs()) { "Cannot create directory: ${it.absolutePath}" } }
        require(!(targets.size > 1 && options.containsKey("out"))) {
            "--out names a single file; use --out-dir with --all"
        }

        targets.forEach { screenId ->
            val png = HeadlessRenderer.renderPng(document, screenId, scale)
            val target = when {
                options.containsKey("out") -> File(options.getValue("out"))
                else -> File(outDir ?: File("."), "$screenId.png")
            }
            target.parentFile?.let { parent -> if (!parent.isDirectory) parent.mkdirs() }
            target.writeBytes(png)
            println(target.absolutePath)
        }
        return 0
    }

    private fun screensCommand(options: Map<String, String>): Int {
        val session = sessionFor(options)
        requireNotNull(session.document) { failedCompileMessage(session) }
        println(AgentJson.render(AgentJson.screens(session.screens())))
        return 0
    }

    private fun inspectCommand(options: Map<String, String>): Int {
        val session = sessionFor(options)
        requireNotNull(session.document) { failedCompileMessage(session) }
        val screenId = requireScreen(options)
        val box = requireNotNull(session.inspect(screenId, options["node"])) {
            val node = options["node"]
            if (node.isNullOrBlank()) "Unknown screen: $screenId" else "Node '$node' not found on screen $screenId"
        }
        println(AgentJson.render(AgentJson.layoutTree(box)))
        return 0
    }

    private fun validateCommand(options: Map<String, String>): Int {
        val session = sessionFor(options)
        val diagnostics = session.validate(options["screen"])
        println(AgentJson.render(AgentJson.diagnostics(diagnostics)))
        // Compile failure or error diagnostics fail the command so agents can gate on it.
        val hasErrors = session.document == null ||
            diagnostics.any { it.severity == DesignSeverity.Error }
        return if (hasErrors) 2 else 0
    }

    private fun exportSamplesCommand(options: Map<String, String>): Int {
        val target = File(requireNotNull(options["to"]) { "export-samples requires --to DIR" })
        AgentProject.write(target, AgentProject.samples()).forEach { println(it.absolutePath) }
        return 0
    }

    private fun createScreenCommand(options: Map<String, String>): Int {
        val projectDir = File(requireNotNull(options["project"]) { "create-screen requires --project DIR" })
        val presetWord = requireNotNull(options["preset"]) { "create-screen requires --preset" }
        val preset = requireNotNull(AgentSession.presetFor(presetWord)) {
            "Unknown preset '$presetWord'; expected one of mobile|tablet|desktop|square"
        }
        val title = requireNotNull(options["title"]) { "create-screen requires --title" }

        val session = AgentSession(AgentProject.load(projectDir))
        requireNotNull(session.document) { failedCompileMessage(session) }
        val sourcesBefore = session.sources().size
        val pageId = session.createScreen(preset, title)
        check(session.sources().size > sourcesBefore) { "Screen creation did not produce a new source" }
        AgentProject.write(projectDir, session.sources())
        println(File(projectDir, session.sources().last().fileName).absolutePath)
        System.err.println("created screen: $pageId")
        return 0
    }

    private fun sessionFor(options: Map<String, String>): AgentSession = when {
        options.containsKey("samples") -> AgentSession.fromSamples()
        options.containsKey("project") -> AgentSession(AgentProject.load(File(options.getValue("project"))))
        else -> throw IllegalArgumentException("Provide a source: --project DIR or --samples")
    }

    private fun requireScreen(options: Map<String, String>): String =
        requireNotNull(options["screen"]) { "Missing --screen ID (see `screens` for the list)" }

    private fun failedCompileMessage(session: AgentSession): String {
        val errors = session.state.diagnostics.joinToString("\n") { "  ${it.code} ${it.message}" }
        return "Sources failed to compile:\n$errors"
    }

    /** `--flag` -> ("flag" to ""), `--key value` -> ("key" to value). */
    private fun parseOptions(args: List<String>): Map<String, String> {
        val options = linkedMapOf<String, String>()
        var index = 0
        while (index < args.size) {
            val raw = args[index]
            require(raw.startsWith("--")) { "Unexpected argument: $raw" }
            val key = raw.removePrefix("--")
            val next = args.getOrNull(index + 1)
            if (next != null && !next.startsWith("--")) {
                options[key] = next
                index += 2
            } else {
                options[key] = ""
                index += 1
            }
        }
        return options
    }
}
