package io.aequicor.visualization.engine.ir.resolve

import io.aequicor.visualization.engine.ir.model.DataValue
import io.aequicor.visualization.engine.ir.model.DesignExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExpressionEvaluatorTest {

    private val scope = EvalScope(
        bindings = mapOf(
            "user" to DataValue.MapValue(
                mapOf(
                    "name" to DataValue.Str("Ada"),
                    "age" to DataValue.Num(36.0),
                    "premium" to DataValue.Bool(true),
                ),
            ),
            "missions" to DataValue.ListValue(
                listOf(
                    DataValue.MapValue(mapOf("id" to DataValue.Str("m1"))),
                    DataValue.MapValue(mapOf("id" to DataValue.Str("m2"))),
                ),
            ),
        ),
    )

    private fun eval(raw: String, scope: EvalScope = this.scope): DataValue? =
        ExpressionEvaluator.evaluate(DesignExpression(raw), scope)

    @Test
    fun resolvesDottedPathsAndIndexes() {
        assertEquals(DataValue.Str("Ada"), eval("user.name"))
        assertEquals(DataValue.Num(36.0), eval("user.age"))
        assertEquals(DataValue.Str("m2"), eval("missions[1].id"))
    }

    @Test
    fun lengthWorksOnListsAndStrings() {
        assertEquals(DataValue.Num(2.0), eval("missions.length"))
        assertEquals(DataValue.Num(3.0), eval("user.name.length"))
    }

    @Test
    fun anExplicitMapEntryNamedLengthWinsOverTheBuiltin() {
        val withLength = EvalScope(
            bindings = mapOf("box" to DataValue.MapValue(mapOf("length" to DataValue.Num(42.0)))),
        )
        assertEquals(DataValue.Num(42.0), eval("box.length", withLength))
    }

    @Test
    fun comparesAgainstLiterals() {
        assertEquals(DataValue.Bool(true), eval("user.name == 'Ada'"))
        assertEquals(DataValue.Bool(false), eval("user.name != \"Ada\""))
        assertEquals(DataValue.Bool(true), eval("user.age > 30"))
        assertEquals(DataValue.Bool(false), eval("user.age < 36"))
        assertEquals(DataValue.Bool(true), eval("user.age <= 36"))
        assertEquals(DataValue.Bool(true), eval("user.age >= 36"))
        assertEquals(DataValue.Bool(true), eval("user.premium == true"))
        assertEquals(DataValue.Bool(true), eval("missions.length == 2"))
    }

    @Test
    fun mismatchedTypesAreNeverEqual() {
        assertEquals(DataValue.Bool(false), eval("user.age == 'Ada'"))
        assertEquals(DataValue.Bool(true), eval("user.age != 'Ada'"))
    }

    @Test
    fun negationInvertsBooleans() {
        assertEquals(DataValue.Bool(false), eval("!user.premium"))
        assertEquals(DataValue.Bool(false), eval("!user.premium == true"), "'!' negates the whole rest")
    }

    @Test
    fun itemBindingsShadowTheParentScope() {
        val item = EvalScope(scope, mapOf("user" to DataValue.Str("shadowed"), "i" to DataValue.Num(1.0)))
        assertEquals(DataValue.Str("shadowed"), eval("user", item))
        assertEquals(DataValue.Num(1.0), eval("i", item))
        assertEquals(DataValue.Num(2.0), eval("missions.length", item), "parent bindings stay reachable")
    }

    @Test
    fun failuresReturnNullWithDiagnostic() {
        var message: String? = null
        assertNull(ExpressionEvaluator.evaluate(DesignExpression("ghost.name"), scope) { message = it })
        assertNotNull(message, "unknown root name reports a diagnostic")

        assertNull(eval("user.unknown"), "unknown member fails")
        assertNull(eval("missions[5].id"), "index out of bounds fails")
        assertNull(eval("user.name[0]"), "indexing a string fails")
        assertNull(eval("user..name"), "malformed path fails")
        assertNull(eval("user.age > 'x'"), "ordering a number against a string fails")
        assertNull(eval("user.age == nope"), "unquoted junk literal fails")
        assertNull(eval("!user.name"), "negating a string fails")
        assertNull(eval(""), "empty expression fails")
    }
}
