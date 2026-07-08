package io.aequicor.visualization.engine.ir.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** HSV color with alpha: [hue] 0f..360f, [saturation]/[value]/[alpha] 0f..1f. */
data class Hsva(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val alpha: Float,
)

/** Converts this RGB color to [Hsva]; achromatic colors report hue = 0. */
fun DesignColor.toHsva(): Hsva {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val cMax = max(r, max(g, b))
    val cMin = min(r, min(g, b))
    val delta = cMax - cMin
    val hue = when {
        delta == 0f -> 0f
        cMax == r -> 60f * (((g - b) / delta) % 6f)
        cMax == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (cMax == 0f) 0f else delta / cMax
    return Hsva(hue = hue, saturation = saturation, value = cMax, alpha = alpha / 255f)
}

/** Converts this [Hsva] back to a packed ARGB [DesignColor]; inputs are clamped and channels rounded to 0..255. */
fun Hsva.toDesignColor(): DesignColor {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val a = alpha.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val rr = channel(r1 + m)
    val gg = channel(g1 + m)
    val bb = channel(b1 + m)
    val aa = channel(a)
    return DesignColor((aa shl 24) or (rr shl 16) or (gg shl 8) or bb)
}

/** Rounds a normalized 0f..1f channel to a 0..255 [Long] byte. */
private fun channel(value: Float): Long = (value * 255f).roundToInt().toLong().coerceIn(0L, 255L)
