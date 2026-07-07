package io.aequicor.visualization.engine.frontend.expr

import kotlin.test.Test
import kotlin.test.assertEquals

class SlmExpressionParserTest {
    private fun path(vararg segments: String) = SlmExpression.Path(segments.toList())

    @Test
    fun parsesSingleSegmentPath() {
        assertEquals(path("missions"), parseSlmExpression("missions"))
    }

    @Test
    fun parsesDottedPath() {
        assertEquals(path("mission", "status"), parseSlmExpression("mission.status"))
        assertEquals(path("query", "search"), parseSlmExpression("query.search"))
        assertEquals(path("missions", "length"), parseSlmExpression("missions.length"))
    }

    @Test
    fun parsesRepeat() {
        assertEquals(
            SlmExpression.Repeat("mission", path("missions")),
            parseSlmExpression("mission in missions"),
        )
    }

    @Test
    fun parsesRepeatWithDottedCollection() {
        assertEquals(
            SlmExpression.Repeat("item", path("data", "items")),
            parseSlmExpression("item in data.items"),
        )
    }

    @Test
    fun parsesComparisonAgainstNumber() {
        assertEquals(
            SlmExpression.Comparison(
                path("missions", "length"),
                ComparisonOp.Eq,
                SlmExpression.Literal.Num(0.0),
            ),
            parseSlmExpression("missions.length == 0"),
        )
    }

    @Test
    fun parsesComparisonAgainstString() {
        assertEquals(
            SlmExpression.Comparison(
                path("mission", "status"),
                ComparisonOp.Neq,
                SlmExpression.Literal.Str("archived"),
            ),
            parseSlmExpression("mission.status != 'archived'"),
        )
    }

    @Test
    fun parsesComparisonAgainstBool() {
        assertEquals(
            SlmExpression.Comparison(
                path("mission", "active"),
                ComparisonOp.Eq,
                SlmExpression.Literal.Bool(true),
            ),
            parseSlmExpression("mission.active == true"),
        )
    }

    @Test
    fun parsesAllComparisonOperators() {
        val operators = mapOf(
            "==" to ComparisonOp.Eq,
            "!=" to ComparisonOp.Neq,
            "<" to ComparisonOp.Lt,
            "<=" to ComparisonOp.Lte,
            ">" to ComparisonOp.Gt,
            ">=" to ComparisonOp.Gte,
        )
        operators.forEach { (symbol, op) ->
            assertEquals(
                SlmExpression.Comparison(path("a"), op, SlmExpression.Literal.Num(5.0)),
                parseSlmExpression("a $symbol 5"),
                "operator $symbol",
            )
        }
    }

    @Test
    fun parsesPathToPathComparison() {
        assertEquals(
            SlmExpression.Comparison(path("a", "b"), ComparisonOp.Lt, path("c", "d")),
            parseSlmExpression("a.b < c.d"),
        )
    }

    @Test
    fun parsesStandaloneLiterals() {
        assertEquals(SlmExpression.Literal.Num(42.0), parseSlmExpression("42"))
        assertEquals(SlmExpression.Literal.Num(-1.5), parseSlmExpression("-1.5"))
        assertEquals(SlmExpression.Literal.Str("hi"), parseSlmExpression("'hi'"))
        assertEquals(SlmExpression.Literal.Bool(true), parseSlmExpression("true"))
        assertEquals(SlmExpression.Literal.Bool(false), parseSlmExpression("false"))
    }

    @Test
    fun garbageBecomesRaw() {
        assertEquals(SlmExpression.Raw("mission +"), parseSlmExpression("mission +"))
        assertEquals(SlmExpression.Raw("1 2 3"), parseSlmExpression("1 2 3"))
        assertEquals(SlmExpression.Raw("a =="), parseSlmExpression("a == "))
        assertEquals(SlmExpression.Raw(""), parseSlmExpression(""))
        assertEquals(SlmExpression.Raw("a in b c"), parseSlmExpression("a in b c"))
        assertEquals(SlmExpression.Raw("a.b."), parseSlmExpression("a.b."))
        assertEquals(SlmExpression.Raw("'unterminated"), parseSlmExpression("'unterminated"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(path("missions"), parseSlmExpression("  missions  "))
    }
}
