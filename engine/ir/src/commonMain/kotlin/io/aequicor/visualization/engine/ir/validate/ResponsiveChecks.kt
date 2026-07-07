package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignNode
import io.aequicor.visualization.engine.ir.model.DesignNodePatch
import io.aequicor.visualization.engine.ir.model.ResponsiveDimension
import io.aequicor.visualization.engine.ir.model.ResponsiveVariant

/**
 * IR-RESP — responsive variants, breakpoints, device presets.
 *
 * - IR-RESP-001 (error): selector references an undeclared breakpoint id.
 * - IR-RESP-002 (error): selector references an undeclared device preset id.
 * - IR-RESP-003 (error): selector references an undeclared locale.
 * - IR-RESP-004 (error): direction selector value other than "ltr"/"rtl".
 * - IR-RESP-005 (error): two equal-specificity variants that can be active together
 *   patch the same property group — resolution is ambiguous (the resolver keeps the
 *   first declared and warns; here it is an authoring error).
 * - IR-RESP-006 (warning): breakpoint ranges overlap or leave gaps.
 * - IR-RESP-007 (warning, info-level): device preset width not covered by any breakpoint.
 *
 * Theme/Platform/Density/Brand/State selector values are free vocabulary and are
 * not validated.
 */
internal object ResponsiveChecks {

    fun check(ctx: ValidationContext): List<DesignDiagnostic> = buildList {
        ctx.entries.forEach { entry ->
            checkSelectors(this, ctx, entry.node)
            checkAmbiguity(this, ctx, entry.node)
        }
        checkBreakpointRanges(this, ctx)
        checkDevicePresetCoverage(this, ctx)
    }

    private fun checkSelectors(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        node.responsive.forEach { variant ->
            variant.selectors.forEach { (dimension, value) ->
                when (dimension) {
                    ResponsiveDimension.Breakpoint -> if (value !in ctx.breakpointIds) {
                        sink += validationError(
                            "IR-RESP-001",
                            "Responsive selector on '${node.id}' references unknown breakpoint '$value'",
                            variant.sourceMap ?: ctx.location(node),
                        )
                    }
                    ResponsiveDimension.DevicePreset -> if (value !in ctx.devicePresetIds) {
                        sink += validationError(
                            "IR-RESP-002",
                            "Responsive selector on '${node.id}' references unknown device preset '$value'",
                            variant.sourceMap ?: ctx.location(node),
                        )
                    }
                    ResponsiveDimension.Locale -> if (ctx.declaredLocales.isNotEmpty() && value !in ctx.declaredLocales) {
                        sink += validationError(
                            "IR-RESP-003",
                            "Responsive selector on '${node.id}' references undeclared locale '$value'",
                            variant.sourceMap ?: ctx.location(node),
                        )
                    }
                    ResponsiveDimension.Direction -> if (value !in setOf("ltr", "rtl")) {
                        sink += validationError(
                            "IR-RESP-004",
                            "Responsive selector on '${node.id}' uses direction '$value'; expected ltr or rtl",
                            variant.sourceMap ?: ctx.location(node),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun checkAmbiguity(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext, node: DesignNode) {
        val variants = node.responsive
        for (first in variants.indices) {
            for (second in first + 1 until variants.size) {
                val a = variants[first]
                val b = variants[second]
                if (a.selectors.size != b.selectors.size) continue
                if (!canBeActiveTogether(a, b)) continue
                val overlap = patchGroups(a.patch) intersect patchGroups(b.patch)
                if (overlap.isNotEmpty()) {
                    sink += validationError(
                        "IR-RESP-005",
                        "Responsive variants #$first and #$second on '${node.id}' have equal " +
                            "specificity and both patch $overlap; resolution is ambiguous",
                        ctx.location(node),
                    )
                }
            }
        }
    }

    /** Variants conflict only when no shared dimension forces different values. */
    private fun canBeActiveTogether(a: ResponsiveVariant, b: ResponsiveVariant): Boolean =
        a.selectors.all { (dimension, value) ->
            val other = b.selectors[dimension]
            other == null || other == value
        }

    private fun patchGroups(patch: DesignNodePatch): Set<String> = buildSet {
        if (patch.visible != null) add("visible")
        if (patch.opacity != null) add("opacity")
        if (patch.layout != null) add("layout")
        if (patch.layoutChild != null) add("layoutChild")
        if (patch.gridPlacement != null) add("gridPlacement")
        if (patch.sizing != null) add("sizing")
        if (patch.size != null) add("size")
        if (patch.minSize != null) add("minSize")
        if (patch.maxSize != null) add("maxSize")
        if (patch.fills != null) add("fills")
        if (patch.strokes != null) add("strokes")
        if (patch.effects != null) add("effects")
        if (patch.cornerRadius != null) add("cornerRadius")
        if (patch.textStyle != null) add("textStyle")
        if (patch.scroll != null) add("scroll")
    }

    private fun checkBreakpointRanges(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext) {
        val sorted = ctx.document.breakpoints.sortedBy { it.minWidth ?: 0.0 }
        sorted.zipWithNext().forEach { (current, next) ->
            val currentMax = current.maxWidth
            val nextMin = next.minWidth
            if (currentMax == null || nextMin == null) return@forEach
            if (nextMin <= currentMax) {
                sink += validationWarning(
                    "IR-RESP-006",
                    "Breakpoints '${current.id}' and '${next.id}' overlap ($nextMin <= $currentMax)",
                )
            } else if (nextMin > currentMax + 1.0) {
                sink += validationWarning(
                    "IR-RESP-006",
                    "Breakpoints '${current.id}' and '${next.id}' leave a gap ($currentMax..$nextMin)",
                )
            }
        }
    }

    private fun checkDevicePresetCoverage(sink: MutableList<DesignDiagnostic>, ctx: ValidationContext) {
        if (ctx.document.breakpoints.isEmpty()) return
        ctx.document.devicePresets.forEach { preset ->
            val covered = ctx.document.breakpoints.any { breakpoint ->
                (breakpoint.minWidth == null || preset.width >= breakpoint.minWidth) &&
                    (breakpoint.maxWidth == null || preset.width <= breakpoint.maxWidth)
            }
            if (!covered) {
                sink += validationWarning(
                    "IR-RESP-007",
                    "Device preset '${preset.id}' (width ${preset.width}) matches no breakpoint",
                )
            }
        }
    }
}
