package io.aequicor.visualization.engine.frontend.cnl

/**
 * Bilingual (RU + EN) CNL keyword tables — the single source of truth for the grammar's
 * words. Everything here is language-neutral once compiled: nouns map to node types,
 * keywords to [CnlPropertyKind], enum words to IR spellings. Extend the maps to grow the
 * vocabulary; the parser and the DeepSeek skill both read from these.
 */
internal object CnlVocabulary {
    private fun shape(kind: String) = CnlNoun(nodeType = "shape", shapeKind = kind)
    private fun node(type: String) = CnlNoun(nodeType = type)

    /** Element nouns (lowercased) → node identity. */
    val nouns: Map<String, CnlNoun> = mapOf(
        "прямоугольник" to shape("rectangle"), "rect" to shape("rectangle"), "rectangle" to shape("rectangle"),
        "эллипс" to shape("ellipse"), "круг" to shape("ellipse"), "ellipse" to shape("ellipse"), "circle" to shape("ellipse"),
        "линия" to shape("line"), "line" to shape("line"),
        "звезда" to shape("star"), "star" to shape("star"),
        "многоугольник" to shape("polygon"), "polygon" to shape("polygon"),
        "стрелка" to shape("arrow"), "arrow" to shape("arrow"),
        "текст" to node("text"), "надпись" to node("text"), "text" to node("text"), "label" to node("text"),
        "кнопка" to CnlNoun("text", role = "button"), "button" to CnlNoun("text", role = "button"),
        "контейнер" to node("frame"), "фрейм" to node("frame"), "frame" to node("frame"), "container" to node("frame"),
        "группа" to node("group"), "group" to node("group"),
        "изображение" to node("media"), "картинка" to node("media"), "image" to node("media"),
        "иконка" to node("vector"), "icon" to node("vector"),
    )

    /**
     * Property keyword phrases (lowercased) → kind. Multi-word phrases (e.g. `угол радиус`,
     * `родительский контейнер`) are matched greedily longest-first by the parser.
     */
    val propertyKeywords: Map<String, CnlPropertyKind> = mapOf(
        "цвет" to CnlPropertyKind.Fill, "заливка" to CnlPropertyKind.Fill,
        "color" to CnlPropertyKind.Fill, "fill" to CnlPropertyKind.Fill,
        "обводка" to CnlPropertyKind.Stroke, "граница" to CnlPropertyKind.Stroke,
        "stroke" to CnlPropertyKind.Stroke, "border" to CnlPropertyKind.Stroke,
        "радиус" to CnlPropertyKind.Radius, "скругление" to CnlPropertyKind.Radius,
        "угол радиус" to CnlPropertyKind.Radius, "радиус угла" to CnlPropertyKind.Radius,
        "corner radius" to CnlPropertyKind.Radius, "radius" to CnlPropertyKind.Radius,
        "поворот" to CnlPropertyKind.Rotation, "rotate" to CnlPropertyKind.Rotation,
        "rotation" to CnlPropertyKind.Rotation,
        "паддинг" to CnlPropertyKind.Padding, "паддинги" to CnlPropertyKind.Padding,
        "внутренние отступы" to CnlPropertyKind.Padding, "padding" to CnlPropertyKind.Padding,
        "отступ" to CnlPropertyKind.Gap, "промежуток" to CnlPropertyKind.Gap,
        "интервал" to CnlPropertyKind.Gap, "gap" to CnlPropertyKind.Gap,
        "ширина" to CnlPropertyKind.Width, "width" to CnlPropertyKind.Width,
        "высота" to CnlPropertyKind.Height, "height" to CnlPropertyKind.Height,
        "размер" to CnlPropertyKind.Size, "size" to CnlPropertyKind.Size,
        "позиция" to CnlPropertyKind.Position, "координаты" to CnlPropertyKind.Position,
        "at" to CnlPropertyKind.Position, "position" to CnlPropertyKind.Position,
        "прозрачность" to CnlPropertyKind.Opacity, "непрозрачность" to CnlPropertyKind.Opacity,
        "opacity" to CnlPropertyKind.Opacity,
        "родительский контейнер" to CnlPropertyKind.AlignParent,
        "выравнивание" to CnlPropertyKind.AlignParent, "align" to CnlPropertyKind.AlignParent,
    )

    /** Longest keyword phrase (in words), so the parser tries 3-, 2-, 1-word matches. */
    val maxKeywordWords: Int = propertyKeywords.keys.maxOf { it.split(' ').size }

    /** Standalone direction words → `layout.mode` (no value token follows). */
    val directions: Map<String, String> = mapOf(
        "колонка" to "column", "вертикально" to "column", "column" to "column",
        "строка" to "row", "ряд" to "row", "горизонтально" to "row", "row" to "row",
        "сетка" to "grid", "grid" to "grid",
        "свободно" to "none", "free" to "none",
    )

    /** Standalone font-weight words → numeric weight. */
    val fontWeights: Map<String, Int> = mapOf(
        "жирный" to 700, "полужирный" to 600, "тонкий" to 300,
        "bold" to 700, "semibold" to 600, "thin" to 300,
    )

    /** Standalone font-style (italic) words. */
    val italicWords: Set<String> = setOf("курсив", "italic")

    /** `родительский контейнер <dir>` / `align <dir>` → node constraint spelling. */
    val alignDirections: Map<String, Pair<String, String>> = mapOf(
        // word -> (axis, constraint value)
        "вверх" to ("vertical" to "top"), "top" to ("vertical" to "top"), "верх" to ("vertical" to "top"),
        "вниз" to ("vertical" to "bottom"), "bottom" to ("vertical" to "bottom"), "низ" to ("vertical" to "bottom"),
        "влево" to ("horizontal" to "left"), "left" to ("horizontal" to "left"), "лево" to ("horizontal" to "left"),
        "вправо" to ("horizontal" to "right"), "right" to ("horizontal" to "right"), "право" to ("horizontal" to "right"),
        "центр" to ("both" to "center"), "center" to ("both" to "center"),
    )

    /** Stroke alignment words after `обводка <color> [weight] <align>`. */
    val strokeAligns: Map<String, String> = mapOf(
        "inside" to "inside", "внутри" to "inside",
        "outside" to "outside", "снаружи" to "outside",
        "center" to "center", "поцентру" to "center",
    )

    /** Size connectors in `<w> на <h>`. */
    val sizeConnectors: Set<String> = setOf("на", "x", "×", "*", "by")

    /** Degree markers in `<n> градусов`. */
    val degreeWords: Set<String> = setOf(
        "градус", "градуса", "градусов", "градусы", "°", "deg", "deg.", "degrees",
    )
}
