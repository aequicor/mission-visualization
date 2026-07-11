package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.frontend.cnl.CnlGrammar
import io.aequicor.visualization.engine.ir.model.DesignInteraction

/**
 * Whether [interaction] can be written back into the SLM source as a CNL interaction phrase.
 *
 * Structural write-back regenerates a subtree through [CnlGrammar]/`CnlEmitter`, whose interactions
 * descriptor yields `null` — and thus falls the node back to an ir-splice — for any interaction the
 * CNL grammar cannot round-trip (a `CubicBezier` easing, a `DesignAction.Unknown`, a `PropRef`
 * `SetVariable` value, an overlay with an unrenderable background, …). The structural gate
 * (`DesignNode.isStructurallyExpressible`) consults this helper so a subtree carrying such an
 * interaction stays in-memory rather than persisting a behavior-stripped section: it reports
 * exactly the cases the CNL emitter cannot express, so the gate and the emitter never diverge.
 */
fun isInteractionExpressibleInSlm(interaction: DesignInteraction): Boolean =
    CnlGrammar.canRenderInteraction(interaction)
