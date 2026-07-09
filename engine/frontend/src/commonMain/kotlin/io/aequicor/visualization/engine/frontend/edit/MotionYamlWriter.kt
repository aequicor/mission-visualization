package io.aequicor.visualization.engine.frontend.edit

import io.aequicor.visualization.engine.ir.model.DesignMotion

/**
 * Serializes a [DesignMotion] back into a `motion:` [YamlPayload.Mapping] — the faithful inverse of
 * `readMotionBlock`. A non-blank `ref` is preserved; an inline keyframes `fallback` is emitted with
 * its frames (`at` plus `opacity`/`x`/`y`/`scale`/`rotation`). Motion has no inexpressible case.
 */
internal object MotionYamlWriter {

    fun motion(motion: DesignMotion): YamlPayload.Mapping = YamlPayload.Mapping(
        buildList {
            if (motion.ref.isNotEmpty()) add("ref" to str(motion.ref))
            motion.fallback?.let { keyframes ->
                add(
                    "fallback" to YamlPayload.Mapping(
                        buildList {
                            add("type" to str("keyframes"))
                            add("durationMs" to numOf(keyframes.durationMs))
                            if (keyframes.loop) add("loop" to bool(true))
                            add(
                                "frames" to YamlPayload.Sequence(
                                    keyframes.frames.map { frame ->
                                        YamlPayload.Mapping(
                                            listOf("at" to numOf(frame.at)) +
                                                frame.properties.map { (key, value) -> key to numOf(value) },
                                        )
                                    },
                                ),
                            )
                        },
                    ),
                )
            }
        },
    )

    private fun str(value: String): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Str(value))
    private fun bool(value: Boolean): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Bool(value))
    private fun numOf(value: Double): YamlPayload = YamlPayload.Scalar(YamlScalarValue.Num(value))
}
