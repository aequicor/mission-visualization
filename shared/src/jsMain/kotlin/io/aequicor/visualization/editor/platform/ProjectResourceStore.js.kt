package io.aequicor.visualization.editor.platform

// Legacy JS target: project I/O is stubbed here (wasmJs is the shipped web surface), so the
// resource store is in-memory too.
actual fun createProjectResourceStore(): ProjectResourceStore = InMemoryProjectResourceStore()
