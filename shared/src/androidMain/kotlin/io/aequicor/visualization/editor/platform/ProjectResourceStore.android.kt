package io.aequicor.visualization.editor.platform

// Android project I/O is stubbed in v1 (web-only ingestion); a dropped/pasted resource is
// session-scoped until SAF-backed project folders land.
actual fun createProjectResourceStore(): ProjectResourceStore = InMemoryProjectResourceStore()
