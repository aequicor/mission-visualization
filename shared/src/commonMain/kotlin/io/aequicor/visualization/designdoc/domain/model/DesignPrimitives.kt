package io.aequicor.visualization.designdoc.domain.model

/**
 * A scalar that is either a literal value, a design-token reference (`{"$var": id}`),
 * or a component-property reference (`{"$prop": name}`). One mechanism for themes,
 * modes, and component parameterization.
 */
sealed interface Bindable<out T> {
    data class Value<T>(val value: T) : Bindable<T>

    data class VarRef(val id: String) : Bindable<Nothing>

    data class PropRef(val name: String) : Bindable<Nothing>
}

fun <T> T.bindable(): Bindable<T> = Bindable.Value(this)

fun <T> Bindable<T>.literalOrNull(): T? = (this as? Bindable.Value<T>)?.value

/** ARGB color; parsed from `#RRGGBB` or `#RRGGBBAA` hex strings. */
data class DesignColor(val argb: Long) {
    val alpha: Int get() = ((argb shr 24) and 0xFF).toInt()
    val red: Int get() = ((argb shr 16) and 0xFF).toInt()
    val green: Int get() = ((argb shr 8) and 0xFF).toInt()
    val blue: Int get() = (argb and 0xFF).toInt()

    companion object {
        val Transparent: DesignColor = DesignColor(0x00000000)
        val Black: DesignColor = DesignColor(0xFF000000)

        fun fromHex(hex: String): DesignColor? {
            val digits = hex.removePrefix("#").trim()
            if (digits.isEmpty() || digits.any { it.digitToIntOrNull(16) == null }) return null
            return when (digits.length) {
                6 -> DesignColor(0xFF000000 or digits.toLong(16))
                8 -> {
                    val rgb = digits.take(6).toLong(16)
                    val alpha = digits.drop(6).toLong(16)
                    DesignColor((alpha shl 24) or rgb)
                }
                3 -> {
                    val expanded = digits.flatMap { listOf(it, it) }.joinToString("")
                    DesignColor(0xFF000000 or expanded.toLong(16))
                }
                else -> null
            }
        }
    }
}

data class DesignPoint(
    val x: Double = 0.0,
    val y: Double = 0.0,
)

data class DesignSize(
    val width: Double? = null,
    val height: Double? = null,
)

data class DesignInsets(
    val top: Bindable<Double> = 0.0.bindable(),
    val right: Bindable<Double> = 0.0.bindable(),
    val bottom: Bindable<Double> = 0.0.bindable(),
    val left: Bindable<Double> = 0.0.bindable(),
)

/** A measured quantity with an explicit unit, e.g. line height `{unit: percent, value: 135}`. */
data class UnitValue(
    val unit: DesignUnit,
    val value: Double,
)

enum class DesignUnit { Px, Percent }

data class SourceLocation(
    val pointer: String,
)

enum class DesignSeverity { Error, Warning }

data class DesignDiagnostic(
    val severity: DesignSeverity,
    val message: String,
    val location: SourceLocation? = null,
)
