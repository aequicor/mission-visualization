package io.aequicor.visualization.editor.platform

// iOS project I/O is stubbed in v1 (web-only ingestion); a dropped/pasted resource is
// session-scoped until security-scoped project folders land.
actual fun createProjectResourceStore(): ProjectResourceStore = InMemoryProjectResourceStore()
