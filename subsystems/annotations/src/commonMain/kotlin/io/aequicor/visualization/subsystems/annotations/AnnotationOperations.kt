package io.aequicor.visualization.subsystems.annotations

/**
 * Pure layer operations, in the spirit of `reduceDesignEditor`: every operation is
 * `(AnnotationLayer, args) -> AnnotationLayer`, no side effects. Operations targeting
 * an unknown annotation id return the layer unchanged.
 */

/** Adds [annotation]; if an annotation with the same id exists it is replaced in place. */
public fun AnnotationLayer.addAnnotation(annotation: Annotation): AnnotationLayer {
    val index = annotations.indexOfFirst { it.id == annotation.id }
    val updated = if (index >= 0) {
        annotations.toMutableList().apply { this[index] = annotation }
    } else {
        annotations + annotation
    }
    return copy(annotations = updated)
}

/**
 * Replaces the plain-text body of the annotation with [id]. The text is canonicalized
 * via [normalizeAnnotationBodyText] (blank-line framing dropped), so the in-memory
 * layer always matches what a sidecar save/reload round-trip yields.
 */
public fun AnnotationLayer.updateAnnotationText(id: String, text: String): AnnotationLayer =
    mapAnnotation(id) { it.copy(body = AnnotationBody(normalizeAnnotationBodyText(text))) }

/** Switches the annotation between note and issue. */
public fun AnnotationLayer.setAnnotationKind(id: String, kind: AnnotationKind): AnnotationLayer =
    mapAnnotation(id) { it.copy(kind = kind) }

/** Attaches (or replaces) the embedded image of the annotation. */
public fun AnnotationLayer.attachAnnotationImage(id: String, image: AnnotationImage): AnnotationLayer =
    mapAnnotation(id) { it.copy(image = image) }

/** Removes the embedded image of the annotation. */
public fun AnnotationLayer.detachAnnotationImage(id: String): AnnotationLayer =
    mapAnnotation(id) { it.copy(image = null) }

/**
 * Moves the annotation: for a [AnnotationAnchor.NodeAnchor] the pair becomes the new
 * offset from the node's top-center; for a [AnnotationAnchor.FreePoint] it becomes the
 * new absolute point.
 */
public fun AnnotationLayer.moveAnnotation(id: String, x: Double, y: Double): AnnotationLayer =
    mapAnnotation(id) { annotation ->
        val moved = when (val anchor = annotation.anchor) {
            is AnnotationAnchor.NodeAnchor -> anchor.copy(offsetX = x.canonical(), offsetY = y.canonical())
            is AnnotationAnchor.FreePoint -> AnnotationAnchor.FreePoint(x.canonical(), y.canonical())
        }
        annotation.copy(anchor = moved)
    }

/**
 * Re-pins the annotation to [nodeId] with the given offset from the node's top-center.
 * A now-redundant extra reference to [nodeId] is dropped — the anchor already carries
 * that node's context.
 */
public fun AnnotationLayer.attachAnnotationToNode(
    id: String,
    nodeId: String,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0,
): AnnotationLayer =
    mapAnnotation(id) { annotation ->
        annotation.copy(
            anchor = AnnotationAnchor.NodeAnchor(nodeId, offsetX.canonical(), offsetY.canonical()),
            references = annotation.references - nodeId,
        )
    }

/**
 * Detaches a node-anchored annotation into a free point at [resolvedPosition] — the
 * badge position the caller resolved from the current node bounds, so the annotation
 * stays visually in place. A free-point annotation is returned unchanged.
 */
public fun AnnotationLayer.detachAnnotationAnchor(
    id: String,
    resolvedPosition: AnnotationPoint,
): AnnotationLayer =
    mapAnnotation(id) { annotation ->
        when (annotation.anchor) {
            is AnnotationAnchor.NodeAnchor ->
                annotation.copy(
                    anchor = AnnotationAnchor.FreePoint(resolvedPosition.x.canonical(), resolvedPosition.y.canonical()),
                )
            is AnnotationAnchor.FreePoint -> annotation
        }
    }

/**
 * Converts every annotation anchored to a node in [nodeIds] into a free point frozen
 * at the position [resolvedPosition] returns for it — call this when nodes are deleted,
 * with the badge positions resolved from the pre-delete bounds (see
 * [annotationBadgePosition]), so badges keep their on-canvas spot instead of falling
 * back to the dangling near-origin fallback. A null [resolvedPosition] result keeps
 * the node anchor as-is (the keep-not-lose dangling behavior).
 */
public fun AnnotationLayer.detachAnnotationsFromNodes(
    nodeIds: Set<String>,
    resolvedPosition: (Annotation) -> AnnotationPoint?,
): AnnotationLayer {
    if (nodeIds.isEmpty()) return this
    var changed = false
    val detached = annotations.map { annotation ->
        val anchor = annotation.anchor
        if (anchor !is AnnotationAnchor.NodeAnchor || anchor.nodeId !in nodeIds) return@map annotation
        val position = resolvedPosition(annotation) ?: return@map annotation
        changed = true
        annotation.copy(anchor = AnnotationAnchor.FreePoint(position.x.canonical(), position.y.canonical()))
    }
    return if (changed) copy(annotations = detached) else this
}

/**
 * Adds an extra node reference; duplicates are ignored, and so is the anchor's own
 * node — the anchor already carries that node's context.
 */
public fun AnnotationLayer.addAnnotationReference(id: String, nodeId: String): AnnotationLayer =
    mapAnnotation(id) { annotation ->
        val anchorNodeId = (annotation.anchor as? AnnotationAnchor.NodeAnchor)?.nodeId
        if (nodeId == anchorNodeId || nodeId in annotation.references) annotation
        else annotation.copy(references = annotation.references + nodeId)
    }

/** Removes an extra node reference; absent references are ignored. */
public fun AnnotationLayer.removeAnnotationReference(id: String, nodeId: String): AnnotationLayer =
    mapAnnotation(id) { it.copy(references = it.references - nodeId) }

/** Deletes the annotation with [id]. */
public fun AnnotationLayer.deleteAnnotation(id: String): AnnotationLayer {
    val remaining = annotations.filterNot { it.id == id }
    return if (remaining.size == annotations.size) this else copy(annotations = remaining)
}

/** Folds `-0.0` to `0.0` so anchors compare (and serialize) sign-canonically. */
private fun Double.canonical(): Double = this + 0.0

private inline fun AnnotationLayer.mapAnnotation(
    id: String,
    transform: (Annotation) -> Annotation,
): AnnotationLayer {
    val index = annotations.indexOfFirst { it.id == id }
    if (index < 0) return this
    val transformed = transform(annotations[index])
    if (transformed == annotations[index]) return this
    return copy(annotations = annotations.toMutableList().apply { this[index] = transformed })
}
