package io.aequicor.visualization.editor.platform

// Desktop has no persistent project-folder concept yet (project I/O is stubbed); a dropped
// resource lives for the session. A file-backed res/ store lands with desktop project folders.
actual fun createProjectResourceStore(): ProjectResourceStore = InMemoryProjectResourceStore()
