package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodeKind
import io.aequicor.visualization.engine.ir.model.DesignPaint
import io.aequicor.visualization.engine.ir.model.DesignPoint
import io.aequicor.visualization.engine.ir.model.isSelfDescribingAssetRef
import io.aequicor.visualization.engine.ir.model.orZero
import io.aequicor.visualization.engine.ir.model.MediaKind
import io.aequicor.visualization.engine.ir.model.literalOrNull

/**
 * IR-ASSET — assets, media, vector paths, boolean operations, masks.
 *
 * - IR-ASSET-001 (error): reference to an unknown asset id (media, poster, image/video
 *   paints, shape pathRef).
 * - IR-ASSET-002 (warning): asset type does not match its usage (image media over a
 *   video asset, pathRef over a non-svg asset, ...).
 * - IR-ASSET-003 (error): focal point outside 0..1.
 * - IR-ASSET-004 (error): inline vector path data that does not parse (local grammar
 *   check; the rendering parser lives in backend-compose and is not a dependency here).
 * - IR-ASSET-005 (warning): boolean operation child that is not shape-like.
 * - IR-ASSET-006 (error): mask appliesTo id that does not exist.
 * - IR-ASSET-007 (warning): mask appliesTo id that is not a sibling of the mask node.
 *
 * `iconRef` names a design-system icon; no icon registry exists in the document
 * model, so icon references are not resolvable statically and are left unchecked.
 */
internal object MediaAssetChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.entries.forEach { entry ->
            val node = entry.node
            checkMedia(this, ctx, node)
            checkPaints(this, ctx, node)
            checkShape(this, ctx, node)
            checkBooleanOperation(this, ctx, node)
            checkMask(this, ctx, entry)
        }
    }

    private fun assetExists(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        assetId: String,
        slot: String,
        expectedType: String? = null,
    ) {
        if (assetId.isEmpty()) return
        // A self-describing resource ref (res/…, absolute URL, data URI) needs no registry entry:
        // the app dereferences it at render time, so it is neither unknown nor type-checkable here.
        if (isSelfDescribingAssetRef(assetId)) return
        val asset = ctx.document.assets[assetId]
        if (asset == null) {
            sink += validationError(
                "IR-ASSET-001",
                "Unknown asset '$assetId' referenced by $slot of '${node.id}'",
                ctx.location(node),
            )
            return
        }
        if (expectedType != null && asset.type != expectedType) {
            sink += validationWarning(
                "IR-ASSET-002",
                "Asset '$assetId' is '${asset.type}' but $slot of '${node.id}' expects '$expectedType'",
                ctx.location(node),
            )
        }
    }

    private fun checkFocalPoint(
        sink: MutableList<DesignDiagnostic>,
        ctx: ValidationContext,
        node: DesignNode,
        focalPoint: DesignPoint?,
        slot: String,
    ) {
        if (focalPoint == null) return
        if (focalPoint.x.orZero !in 0.0..1.0 || focalPoint.y.orZero !in 0.0..1.0) {
            sink += validationError(
                "IR-ASSET-003",
                "Focal point (${focalPoint.x.orZero}, ${focalPoint.y.orZero}) of $slot on '${node.id}' " +
                    "must be normalized to 0..1",
                ctx.location(node),
            )
        }
    }

    private fun checkMedia(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        val media = (node.kind as? DesignNodeKind.Media)?.media ?: return
        val expectedType = if (media.kind == MediaKind.Video) "video" else "image"
        // Static validation runs on unresolved IR: only literal ids can be checked
        // against the asset table; a $var / {{expr}} binding resolves at runtime and is skipped.
        media.assetId.literalOrNull()?.let { assetExists(sink, ctx, node, it, "media", expectedType) }
        media.posterAssetId.literalOrNull()?.let { assetExists(sink, ctx, node, it, "media.poster", "image") }
        checkFocalPoint(sink, ctx, node, media.focalPoint, "media")
    }

    private fun checkPaints(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        val paints = node.fills.orEmpty() + node.strokes?.paints.orEmpty()
        paints.forEachIndexed { index, paint ->
            when (paint) {
                is DesignPaint.Image -> {
                    assetExists(sink, ctx, node, paint.assetId, "paint[$index]", "image")
                    checkFocalPoint(sink, ctx, node, paint.focalPoint, "paint[$index]")
                }
                is DesignPaint.Video -> {
                    assetExists(sink, ctx, node, paint.assetId, "paint[$index]", "video")
                    assetExists(sink, ctx, node, paint.posterAssetId, "paint[$index].poster", "image")
                    checkFocalPoint(sink, ctx, node, paint.focalPoint, "paint[$index]")
                }
                else -> Unit
            }
        }
    }

    private fun checkShape(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        val shape = node.kind as? DesignNodeKind.Shape ?: return
        assetExists(sink, ctx, node, shape.pathRef, "pathRef", "svg")
        shape.paths.forEachIndexed { index, path ->
            svgPathSyntaxError(path.d)?.let { failure ->
                sink += validationError(
                    "IR-ASSET-004",
                    "Vector path $index of '${node.id}' does not parse: $failure",
                    ctx.location(node),
                )
            }
        }
    }

    private fun checkBooleanOperation(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        if (node.kind !is DesignNodeKind.BooleanOperation) return
        node.children.forEach { child ->
            val shapeLike = child.kind is DesignNodeKind.Shape || child.kind is DesignNodeKind.BooleanOperation
            if (!shapeLike) {
                sink += validationWarning(
                    "IR-ASSET-005",
                    "Boolean operation '${node.id}' contains non-shape child '${child.id}' (${child.type})",
                    ctx.location(child),
                )
            }
        }
    }

    private fun checkMask(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, entry: NodeEntry) {
        val node = entry.node
        val mask = node.mask ?: return
        mask.appliesTo.forEach { targetId ->
            when {
                targetId !in ctx.allNodeIds -> sink += validationError(
                    "IR-ASSET-006",
                    "Mask on '${node.id}' applies to unknown node '$targetId'",
                    ctx.location(node),
                )
                targetId !in entry.siblingIds -> sink += validationWarning(
                    "IR-ASSET-007",
                    "Mask on '${node.id}' applies to '$targetId', which is not a sibling",
                    ctx.location(node),
                )
            }
        }
    }
}
