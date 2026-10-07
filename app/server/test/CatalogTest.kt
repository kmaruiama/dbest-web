package dbest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogTest {

    private val added =
        mapOf(
            "condition" to
                OperatorType(
                    Arity.SOURCE,
                    true,
                    listOf(
                        FieldSpec(
                            "condition",
                            Widget.CONDITION,
                            options = CompareOp.entries.map({ op -> op.name }))),
                ),
            "reference" to
                OperatorType(
                    Arity.SOURCE,
                    true,
                    listOf(FieldSpec("columns", Widget.LIST, item = Widget.COLUMN)),
                ),
        )

    @Test
    fun `the catalog derived from the node classes equals the one master wrote by hand, plus what was added since`() {
        val text =
            Files.readString(Path.of("test/golden/operators.json"))
                .replace("\"qualified\"", "\"column\"")
        val golden =
            wire.decodeFromJsonElement(
                Catalog.serializer(), migrate(2, wire.parseToJsonElement(text)))
        assertEquals(golden.categories, CATALOG.categories)
        assertEquals(golden.operators, CATALOG.operators.filter({ chip -> chip.type !in added }))
        for ((kind, type) in golden.types) {
            assertEquals(type, CATALOG.types.get(kind), kind)
        }
        for ((kind, type) in added) {
            assertEquals(type, CATALOG.types.get(kind), kind)
        }
        assertEquals(golden.types.keys + added.keys, CATALOG.types.keys)
    }

    @Test
    fun `every palette operator names its engine class`() {
        for (chip in CHIPS) {
            assertNotNull(engineClass(chip.template), chip.key)
        }
    }

    private fun expression(node: Node): String =
        expression(
            Session(
                tables =
                    mapOf(
                        TableId(0) to MemorySpec("orders", listOf(column("id", ColumnType.INT))))),
            node)

    @Test
    fun `captions read like the expression a student would write`() {
        val older = Comparison("u.age", CompareOp.GT, 30)
        val named = Comparison("u.name", CompareOp.EQ, "Ana")
        assertEquals("u.age ≤ 30", expression(FilterNode(Comparison("u.age", CompareOp.LTE, 30))))
        assertEquals(
            "u.age = o.age",
            expression(FilterNode(Comparison("u.age", CompareOp.EQ, Ref("o.age")))))
        assertEquals("u.age = 1.5", expression(FilterNode(Comparison("u.age", CompareOp.EQ, 1.5))))
        assertEquals("u.age IS NOT NULL", expression(FilterNode(IsNotNull("u.age"))))
        assertEquals(
            "u.age > 30 OR (u.age > 30 AND u.name = 'Ana')",
            expression(FilterNode(Or(listOf(older, And(listOf(older, named)))))))
        assertEquals("orders", expression(TableNode(TableId(0), "o")))
        assertEquals("m1.id = m2.id", expression(JoinNode(on = listOf(JoinTerm("m1.id", "m2.id")))))
        assertEquals(
            "age ASC, name ASC",
            expression(SortNode(listOf(SortKey("age", true), SortKey("name", true)))))
        assertEquals("10, 20", expression(LimitNode(10, 20)))
        assertEquals("u → users", expression(AliasNode("u", "users")))
        assertEquals("id from 1", expression(RowNumberNode("n", "id")))
        assertEquals(
            "u.dept: count(id)",
            expression(AggNode("a", "u.dept", listOf(Agg(AggFunction.COUNT, "id")))))
        assertEquals("", expression(CrossNode))
        assertEquals(
            "e.dept > 25", expression(ConditionNode(Comparison("e.dept", CompareOp.GT, 25))))
        assertEquals("e.dept, e.name", expression(ReferenceNode(listOf("e.dept", "e.name"))))
    }

    @Test
    fun `the nodes that make a source of their own are the ones with an alias, which no form edits`() {
        assertEquals("o", aliasOf(TableNode(TableId(0), "o")))
        assertEquals("g", aliasOf(AggNode("g", null, listOf(Agg(AggFunction.COUNT, "id")))))
        assertEquals("tudo", aliasOf(CollapseNode("tudo")))
        assertEquals("r", aliasOf(RowNumberNode("r", "n")))
        assertEquals("Projection", aliasOf(RemoveColumnsNode(listOf("e.tags"))))
        assertEquals("ref", aliasOf(ReferenceNode(listOf("e.dept"))))
        assertEquals("condition", aliasOf(ConditionNode(IsNull("e.tags"))))
        assertEquals("decisao", aliasOf(LogicalOpNode(LogicalKind.AND, "decisao")))
        assertEquals("filmes", aliasOf(SetOpNode(SetKind.UNION, alias = "filmes")))
        assertEquals("", aliasOf(SetOpNode(SetKind.APPEND)))
        assertEquals(null, aliasOf(SetOpNode(SetKind.INTERSECT, alias = "ignored")))
        assertEquals(null, aliasOf(AliasNode("u", "people")))
        for ((kind, type) in CATALOG.types) {
            assertTrue(type.fields.none({ field -> field.at == "alias" }), kind)
        }
        assertEquals(false, CATALOG.types.getValue("collapse").editable)
    }

    @Test
    fun `an unconfigured palette chip still has a caption`() {
        for (chip in CHIPS) {
            expression(chip.template)
        }
    }
}
