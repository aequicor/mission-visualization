package io.aequicor.visualization.subsystems.annotations

/**
 * Where to place the collapsed badge (and the expanded card's anchor point) for an
 * annotation:
 * - [AnnotationAnchor.NodeAnchor] with resolved [nodeBounds] — node top-center plus
 *   the anchor offset;
 * - dangling node anchor (`nodeBounds == null`, node deleted/unresolved) — a
 *   deterministic fallback: the raw offset treated as an absolute point, so the badge
 *   stays stable and hit-testable instead of disappearing;
 * - [AnnotationAnchor.FreePoint] — the point itself.
 */
public fun annotationBadgePosition(
    anchor: AnnotationAnchor,
    nodeBounds: AnnotationRect?,
): AnnotationPoint = when (anchor) {
    is AnnotationAnchor.NodeAnchor ->
        if (nodeBounds != null) {
            AnnotationPoint(nodeBounds.centerX + anchor.offsetX, nodeBounds.top + anchor.offsetY)
        } else {
            AnnotationPoint(anchor.offsetX, anchor.offsetY)
        }
    is AnnotationAnchor.FreePoint -> AnnotationPoint(anchor.x, anchor.y)
}
