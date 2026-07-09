package io.aequicor.visualization.engine.scene.sample

/** A rectangle in the layer's own coordinate space for transition/scene clipping. */
data class ClipRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/**
 * A visual-only override applied at draw time — never a document edit. Translation is additive
 * in root-frame units; [rotationDeg] is additive to any authored rotation; scale is multiplicative.
 * Sampling animations produces these; the renderer applies them; the authored document is untouched.
 */
data class VisualOverride(
    val opacity: Double = 1.0,
    val translateX: Double = 0.0,
    val translateY: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDeg: Double = 0.0,
    val clip: ClipRect? = null,
) {
    val isIdentity: Boolean
        get() = this == Identity

    companion object {
        val Identity = VisualOverride()
    }
}

/** A [VisualOverride] scoped to one node (by source id) — used by motion clips. */
data class NodeOverride(
    val nodeId: String,
    val override: VisualOverride,
)
