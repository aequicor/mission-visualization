package io.aequicor.visualization.engine.ir.validate

import io.aequicor.visualization.engine.ir.layout.ApproximateTextMeasurer
import io.aequicor.visualization.engine.ir.layout.DesignTextMeasurer
import io.aequicor.visualization.engine.ir.model.DesignDiagnostic
import io.aequicor.visualization.engine.ir.model.DesignDocument
import io.aequicor.visualization.engine.ir.resolve.ResolveContext

/**
 * Options for [validateDesignDocument].
 *
 * The static check groups always run; the resolved checks (IR-RESOLVE) are opt-in:
 * they run per locale from [locales] (empty = the document's `i18n.targetLocales`)
 * and, with [layoutProbe], additionally lay every resolved page out with
 * [textMeasurer] to flag text overflow and min-size violations.
 */
data class ValidationOptions(
    val locales: List<String> = emptyList(),
    val layoutProbe: Boolean = false,
    val textMeasurer: DesignTextMeasurer = ApproximateTextMeasurer(),
)

/**
 * Statically validates [document] and returns diagnostics with stable codes.
 *
 * One check group per file, one code prefix per group (numbering documented at the
 * top of each file):
 * - IR-STRUCT   [StructureChecks] — ids, schema version, node types, sibling order;
 * - IR-LAYOUT   [LayoutChecks] — auto-layout params, grid placement, sizing conflicts;
 * - IR-STYLE    [StyleChecks] — style refs, variables per mode, gradients, ranges;
 * - IR-I18N     [TextI18nChecks] — resource keys, ICU syntax, plurals, spans, truncation;
 * - IR-COMP     [ComponentChecks] — component/library refs, variants, props, slots, overrides;
 * - IR-ASSET    [MediaAssetChecks] — assets, focal points, vector paths, masks;
 * - IR-PROTO    [InteractionChecks] — triggers, action targets, prototype variables, motion;
 * - IR-RESP     [ResponsiveChecks] — selector vocabulary, ambiguity, breakpoint ranges;
 * - IR-DATA     [DataChecks] — `{{...}}` expression syntax, repeat scopes, data paths;
 * - IR-HANDOFF  [HandoffExportChecks] — annotation/measurement targets, export settings;
 * - IR-RESOLVE  [ResolvedChecks] — opt-in per-locale resolve + layout probes (see [ValidationOptions]).
 *
 * [context] supplies the resolve environment the document is validated against
 * (libraries, overlay resources, runtime data, prototype state).
 */
fun validateDesignDocument(
    document: DesignDocument,
    context: ResolveContext = ResolveContext(),
    options: ValidationOptions = ValidationOptions(),
): List<DesignDiagnostic> {
    val ctx = ValidationContext(document, context, options)
    return buildList {
        addAll(StructureChecks.check(ctx))
        addAll(LayoutChecks.check(ctx))
        addAll(StyleChecks.check(ctx))
        addAll(TextI18nChecks.check(ctx))
        addAll(ComponentChecks.check(ctx))
        addAll(MediaAssetChecks.check(ctx))
        addAll(InteractionChecks.check(ctx))
        addAll(ResponsiveChecks.check(ctx))
        addAll(DataChecks.check(ctx))
        addAll(HandoffExportChecks.check(ctx))
        addAll(ResolvedChecks.check(ctx))
    }.distinct()
}
