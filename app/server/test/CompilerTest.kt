package dbest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CompilerTest {

    private fun emp(name: String, keyed: Boolean): MemorySpec =
        MemorySpec(
            name,
            listOf(
                column("id", ColumnType.INT, primaryKey = keyed),
                column("name", ColumnType.STRING),
                column("dept", ColumnType.INT),
                column("tags", ColumnType.STRING)),
            listOf(
                mapOf("id" to 1, "name" to "ana", "dept" to 10, "tags" to "x;y"),
                mapOf("id" to 2, "name" to "bob", "dept" to 20, "tags" to "z"),
                mapOf("id" to 3, "name" to "cid", "dept" to 10, "tags" to "w"),
                mapOf("id" to 4, "name" to "dan", "dept" to 40, "tags" to "q"),
            ),
        )

    private fun dep(name: String, keyed: Boolean): MemorySpec =
        MemorySpec(
            name,
            listOf(
                column("id", ColumnType.INT, primaryKey = keyed),
                column("city", ColumnType.STRING)),
            listOf(
                mapOf("id" to 10, "city" to "sp"),
                mapOf("id" to 20, "city" to "rj"),
                mapOf("id" to 30, "city" to "bh")),
        )

    private val tables = mapOf("emp" to 1, "dep" to 2, "empKey" to 3, "depKey" to 4)

    private inner class Build {
        val commands =
            ArrayList<Command>(
                listOf(
                    AddTable(TableId(1), emp("emp", false)),
                    AddTable(TableId(2), dep("dep", false)),
                    AddTable(TableId(3), emp("empkey", true)),
                    AddTable(TableId(4), dep("depkey", true)),
                ))
        private var next = 0

        fun add(node: Node, vararg inputs: Int): Int {
            next += 1
            commands.add(AddNode(NodeId(next), node, Position(next * 70.0, next * 50.0)))
            val ports = inputPorts(node)
            assertEquals(ports.size, inputs.size, "${kindOf(node)} takes ${ports.size} input(s)")
            for ((index, port) in ports.withIndex()) {
                commands.add(Connect(Edge(NodeId(inputs[index]), NodeId(next), port)))
            }
            return next
        }

        fun table(name: String, alias: String): Int =
            add(TableNode(TableId(tables.getValue(name)), alias))

        fun sorted(input: Int, column: String): Int =
            add(SortNode(listOf(SortKey(column, true))), input)

        fun filter(input: Int, condition: String): Int =
            add(node("""{"@type": "filter", "condition": $condition}"""), input)
    }

    private class Case(
        val name: String,
        val chip: String,
        val fields: String,
        val build: (Build, Node) -> Int,
        val show: List<String>,
        val expect: List<String>,
        val keys: List<String>? = null,
        val ordered: Boolean = false,
        val broken: String? = null,
    )

    private fun node(json: String): Node = wire.decodeFromString(Node.serializer(), json)

    private fun cmp(left: String, op: String, right: Int): String =
        """{"@type": "cmp", "left": "$left", "op": "$op", "right": {"int": $right}}"""

    private fun cmpColumn(left: String, op: String, right: String): String =
        """{"@type": "cmp", "left": "$left", "op": "$op", "right": {"ref": "$right"}}"""

    private fun deptOfEmp(build: Build): Int =
        build.filter(build.table("dep", "d"), cmpColumn("d.id", "EQ", "e.dept"))

    private fun semiOverEmp(build: Build, right: Int): Int =
        build.add(
            JoinNode(JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP, emptyList()),
            build.table("emp", "e"),
            right)

    private fun overJoin(build: Build, node: Node): Int =
        build.add(
            node,
            build.sorted(build.table("emp", "e"), "e.dept"),
            build.sorted(build.table("dep", "d"), "d.id"))

    private fun overSets(build: Build, node: Node): Int =
        build.add(
            node,
            build.filter(build.table("emp", "e"), cmp("e.dept", "EQ", 10)),
            build.filter(build.table("emp", "e2"), cmp("e2.id", "GT", 2)))

    private fun overSides(build: Build, node: Node, rightDept: Int): Int =
        build.add(
            node,
            build.filter(build.table("emp", "e"), cmp("e.dept", "EQ", 10)),
            build.filter(build.table("emp", "e2"), cmp("e2.dept", "EQ", rightDept)))

    private fun overEmp(build: Build, node: Node): Int = build.add(node, build.table("emp", "e"))

    private val names = listOf("ana", "bob", "cid", "dan")
    private val pairs =
        names.flatMap({ name -> listOf("sp", "rj", "bh").map({ city -> "$name/$city" }) })
    private val inner = listOf("ana/sp", "cid/sp", "bob/rj")
    private val joinOn = """{"on": [{"left": "e.dept", "right": "d.id"}]}"""
    private val pair = listOf("e.name", "d.city")

    private fun cases(): List<Case> {
        val cases =
            arrayListOf(
                Case(
                    "filter keeps the rows that satisfy the condition",
                    "filter",
                    """{"condition": ${cmp("e.dept", "EQ", 10)}}""",
                    { b, n -> overEmp(b, n) },
                    listOf("e.name"),
                    listOf("ana", "cid")),
                Case(
                    "filter on a primary-key column",
                    "filter",
                    """{"condition": ${cmp("k.id", "EQ", 1)}}""",
                    { b, n -> b.add(n, b.table("empKey", "k")) },
                    listOf("k.name"),
                    listOf("ana"),
                    broken =
                        "IndexScan swallows MemoryTable's unimplemented PK lookup and then reads a null iterator"),
                Case(
                    "projection keeps only the named columns",
                    "projection",
                    """{"columns": ["e.name"]}""",
                    { b, n -> overEmp(b, n) },
                    listOf("e.name"),
                    names,
                    keys = listOf("e.name")),
                Case(
                    "column selection drops the named columns and re-qualifies the rest",
                    "selectColumns",
                    """{"columns": ["e.tags"], "alias": "P"}""",
                    { b, n -> overEmp(b, n) },
                    listOf("P.name"),
                    names,
                    keys = listOf("P.id", "P.name", "P.dept")),
                Case(
                    "rename re-qualifies a source",
                    "rename",
                    """{"from": "e", "to": "x"}""",
                    { b, n -> overEmp(b, n) },
                    listOf("x.name"),
                    names,
                    keys = listOf("x.id", "x.name", "x.dept", "x.tags")),
                Case(
                    "rename applied to a column, which is what the form offers",
                    "rename",
                    """{"from": "e.name", "to": "nome"}""",
                    { b, n -> overEmp(b, n) },
                    listOf("e.nome"),
                    names,
                    keys = listOf("e.id", "e.nome", "e.dept", "e.tags"),
                    broken =
                        "the catalog types rename.from as a column, but the operator renames a source"),
                Case(
                    "sort orders by the key",
                    "sort",
                    """{"keys": [{"column": "e.dept", "ascending": true}]}""",
                    { b, n -> overEmp(b, n) },
                    listOf("e.name"),
                    listOf("ana", "cid", "bob", "dan"),
                    ordered = true),
                Case(
                    "limit takes a window of its input",
                    "limit",
                    """{"count": 2, "offset": 1}""",
                    { b, n -> overEmp(b, n) },
                    listOf("e.name"),
                    listOf("bob", "cid"),
                    ordered = true),
                Case(
                    "limit takes a window when it is not the node being run",
                    "limit",
                    """{"count": 2, "offset": 1}""",
                    { b, n -> b.add(ProjectNode(listOf("e.name")), overEmp(b, n)) },
                    listOf("e.name"),
                    listOf("bob", "cid"),
                    ordered = true),
                Case(
                    "duplicate removal collapses equal neighbours in sorted input",
                    "duplicateRemoval",
                    "{}",
                    { b, n ->
                        b.add(
                            n,
                            b.add(
                                ProjectNode(listOf("e.dept")),
                                b.sorted(b.table("emp", "e"), "e.dept")))
                    },
                    listOf("e.dept"),
                    listOf("10", "20", "40"),
                    ordered = true),
                Case(
                    "hashed duplicate removal collapses equal rows anywhere",
                    "hashDuplicateRemoval",
                    "{}",
                    { b, n -> b.add(n, b.add(ProjectNode(listOf("e.dept")), b.table("emp", "e"))) },
                    listOf("e.dept"),
                    listOf("10", "20", "40")),
                Case(
                    "aggregation counts per group",
                    "aggregation",
                    """{"alias": "g", "by": "e.dept", "aggregates": [{"function": "COUNT", "column": "e.id"}], "hashed": true}""",
                    { b, n -> overEmp(b, n) },
                    listOf("g.dept", "g.COUNT_id"),
                    listOf("10/2", "20/1", "40/1")),
                Case(
                    "aggregation without a group counts the whole input",
                    "aggregation",
                    """{"alias": "g", "by": null, "aggregates": [{"function": "COUNT_ALL", "column": "e.id"}], "hashed": true}""",
                    { b, n -> overEmp(b, n) },
                    listOf("g.COUNT_ALL_id"),
                    listOf("4"),
                    broken =
                        "ungrouped aggregation returns null for COUNT_ALL, COUNT_NULL, FIRST and LAST"),
                Case(
                    "collapse merges every source under one alias",
                    "collapse",
                    """{"alias": "tudo"}""",
                    { b, n ->
                        b.add(n, b.add(CrossNode, b.table("emp", "e"), b.table("dep", "d")))
                    },
                    listOf("tudo.name", "tudo.city"),
                    pairs),
                Case(
                    "explode splits a delimited column into one row per part",
                    "explode",
                    """{"column": "e.tags", "delimiter": ";"}""",
                    { b, n -> overEmp(b, n) },
                    listOf("e.name", "e.tags"),
                    listOf("ana/x", "ana/y", "bob/z", "cid/w", "dan/q")),
                Case(
                    "auto increment numbers the tuples from its start",
                    "autoInc",
                    """{"alias": "r", "column": "n", "start": 1}""",
                    { b, n -> overEmp(b, n) },
                    listOf("r.n", "e.name"),
                    listOf("1/ana", "2/bob", "3/cid", "4/dan"),
                    ordered = true),
                Case(
                    "scan suppresses the primary-key lookup a filter above it would delegate",
                    "scan",
                    "{}",
                    { b, n -> b.filter(b.add(n, b.table("empKey", "k")), cmp("k.id", "EQ", 1)) },
                    listOf("k.name"),
                    listOf("ana")),
                Case(
                    "cartesian product pairs every row with every row",
                    "cartesianProduct",
                    "{}",
                    { b, n -> b.add(n, b.table("emp", "e"), b.table("dep", "d")) },
                    pair,
                    pairs),
                Case(
                    "nested loop join onto a primary key",
                    "join",
                    """{"on": [{"left": "e.dept", "right": "k.id"}]}""",
                    { b, n -> b.add(n, b.table("emp", "e"), b.table("depKey", "k")) },
                    listOf("e.name", "k.city"),
                    inner,
                    broken =
                        "the same MemoryTable PK lookup gap, reached through the join's delegated filter"),
                Case(
                    "append concatenates both sides, duplicates and all",
                    "append",
                    "{}",
                    { b, n -> overSets(b, n) },
                    listOf("e.name"),
                    listOf("ana", "cid", "cid", "dan")),
                Case(
                    "union keeps the source qualifier its inputs carry",
                    "union",
                    "{}",
                    { b, n -> overSets(b, n) },
                    listOf("e.name"),
                    listOf("ana", "cid", "dan"),
                    keys = listOf("e.id", "e.name", "e.dept", "e.tags")),
                Case(
                    "unilateral existence answers with one witness from the left",
                    "unilateralExistence",
                    "{}",
                    { b, n -> b.add(n, b.table("emp", "e"), b.table("dep", "d")) },
                    pair,
                    listOf("ana/-")),
                Case(
                    "bilateral existence answers with one witness from each side",
                    "bilateralExistence",
                    "{}",
                    { b, n -> b.add(n, b.table("emp", "e"), b.table("dep", "d")) },
                    pair,
                    listOf("ana/sp")),
                Case(
                    "and is false when one side has no rows",
                    "logicalAnd",
                    "{}",
                    { b, n -> overSides(b, n, 99) },
                    listOf("condition.EVAL"),
                    listOf("false")),
                Case(
                    "and is true when both sides have rows",
                    "logicalAnd",
                    "{}",
                    { b, n -> overSides(b, n, 20) },
                    listOf("condition.EVAL"),
                    listOf("true")),
                Case(
                    "or is true when one side has rows",
                    "logicalOr",
                    "{}",
                    { b, n -> overSides(b, n, 99) },
                    listOf("condition.EVAL"),
                    listOf("true")),
                Case(
                    "xor counts a bare source as satisfied, so it cannot tell these two apart",
                    "logicalXor",
                    "{}",
                    { b, n -> overSides(b, n, 99) },
                    listOf("condition.EVAL"),
                    listOf("false")),
                Case(
                    "a condition joins a correlated filter as a boolean, under or",
                    "condition",
                    """{"condition": ${cmp("e.dept", "GT", 25)}}""",
                    { b, n ->
                        semiOverEmp(
                            b,
                            b.add(LogicalOpNode(LogicalKind.OR, "decisao"), b.add(n), deptOfEmp(b)))
                    },
                    listOf("e.name"),
                    names),
                Case(
                    "a condition joins a correlated filter as a boolean, under and",
                    "condition",
                    """{"condition": ${cmp("e.id", "GT", 1)}}""",
                    { b, n ->
                        semiOverEmp(
                            b,
                            b.add(
                                LogicalOpNode(LogicalKind.AND, "decisao"), b.add(n), deptOfEmp(b)))
                    },
                    listOf("e.name"),
                    listOf("bob", "cid")),
                Case(
                    "a condition right under a semi join acts as its where clause",
                    "condition",
                    """{"condition": ${cmp("e.dept", "GT", 15)}}""",
                    { b, n -> semiOverEmp(b, b.add(n)) },
                    listOf("e.name"),
                    listOf("bob", "dan")),
                Case(
                    "a reference echoes the left row it is joined to, typed like its column",
                    "reference",
                    """{"columns": ["e.dept", "e.name"]}""",
                    { b, n -> b.add(JoinNode(on = emptyList()), b.table("emp", "e"), b.add(n)) },
                    listOf("e.name", "ref.e_dept"),
                    listOf("ana/10", "bob/20", "cid/10", "dan/40"),
                    keys =
                        listOf("e.id", "e.name", "e.dept", "e.tags", "ref.e_dept", "ref.e_name")),
                Case(
                    "a cross join reads the left row on its right side",
                    "cartesianProduct",
                    "{}",
                    { b, n -> b.add(n, b.table("emp", "e"), deptOfEmp(b)) },
                    pair,
                    inner),
            )
        val termless =
            listOf(
                Triple("join", pair, inner),
                Triple("semiJoin", listOf("e.name"), listOf("ana", "cid", "bob")),
                Triple("antiJoin", listOf("e.name"), listOf("dan")),
                Triple("leftOuterJoin", pair, inner + "dan/-"),
            )
        for ((chip, show, expect) in termless) {
            cases.add(
                Case(
                    "$chip without terms reads the left row on its right side",
                    chip,
                    """{"on": []}""",
                    { b, n -> b.add(n, b.table("emp", "e"), deptOfEmp(b)) },
                    show,
                    expect))
        }
        cases.add(
            Case(
                "rightOuterJoin without terms reads the right row on its left side, the engine's sides being swapped",
                "rightOuterJoin",
                """{"on": []}""",
                { b, n ->
                    b.add(
                        n,
                        b.filter(b.table("emp", "e"), cmpColumn("e.dept", "EQ", "d.id")),
                        b.table("dep", "d"))
                },
                pair,
                inner + "-/bh"))
        for (chip in listOf("hash", "memoize", "materialization")) {
            cases.add(
                Case(
                    "$chip passes its input through unchanged",
                    chip,
                    "{}",
                    { b, n -> overEmp(b, n) },
                    listOf("e.name"),
                    names,
                    ordered = true))
        }
        val joins =
            listOf(
                Triple("join", inner, null),
                Triple("mergeJoin", inner, null),
                Triple("hashJoin", inner, null),
                Triple("leftOuterJoin", inner + "dan/-", null),
                Triple("hashLeftOuterJoin", inner + "dan/-", null),
                Triple(
                    "mergeLeftOuterJoin",
                    inner + "dan/-",
                    "MergeLeftOuterJoin reads past its data sources"),
                Triple("rightOuterJoin", inner + "-/bh", null),
                Triple("hashRightOuterJoin", inner + "-/bh", null),
                Triple(
                    "mergeRightOuterJoin",
                    inner + "-/bh",
                    "MergeRightOuterJoin reads past its data sources"),
                Triple("hashFullOuterJoin", inner + listOf("dan/-", "-/bh"), null),
                Triple(
                    "mergeFullOuterJoin",
                    inner + listOf("dan/-", "-/bh"),
                    "MergeFullOuterJoin reads past its data sources"),
            )
        for ((chip, expect, broken) in joins) {
            cases.add(
                Case(
                    "$chip correlates the sorted inputs",
                    chip,
                    joinOn,
                    { b, n -> overJoin(b, n) },
                    pair,
                    expect,
                    broken = broken))
        }
        val sided =
            listOf(
                Triple("semiJoin", "e.name", listOf("ana", "cid", "bob")),
                Triple("hashLeftSemiJoin", "e.name", listOf("ana", "cid", "bob")),
                Triple("mergeLeftSemiJoin", "e.name", listOf("ana", "cid", "bob")),
                Triple("hashRightSemiJoin", "d.city", listOf("sp", "rj")),
                Triple("mergeRightSemiJoin", "d.city", listOf("sp", "rj")),
                Triple("antiJoin", "e.name", listOf("dan")),
                Triple("hashLeftAntiJoin", "e.name", listOf("dan")),
                Triple("mergeLeftAntiJoin", "e.name", listOf("dan")),
                Triple("hashRightAntiJoin", "d.city", listOf("bh")),
                Triple("mergeRightAntiJoin", "d.city", listOf("bh")),
            )
        val sidedBroken =
            mapOf(
                "mergeLeftSemiJoin" to
                    "MergeLeftSemiJoin emits one row per key, losing the second row of a repeated key (cid)",
                "mergeLeftAntiJoin" to
                    "MergeLeftAntiJoin returns a matched row (cid) and drops the unmatched one (dan)",
            )
        for ((chip, show, expect) in sided) {
            cases.add(
                Case(
                    "$chip keeps only the side it is named for",
                    chip,
                    joinOn,
                    { b, n -> overJoin(b, n) },
                    listOf(show),
                    expect,
                    broken = sidedBroken.get(chip)))
        }
        val sets =
            listOf(
                Pair("union", listOf("ana", "cid", "dan")),
                Pair("hashUnion", listOf("ana", "cid", "dan")),
                Pair("intersection", listOf("cid")),
                Pair("hashIntersection", listOf("cid")),
                Pair("difference", listOf("ana")),
                Pair("hashDifference", listOf("ana")),
            )
        for ((chip, expect) in sets) {
            cases.add(
                Case(
                    "$chip combines the filtered sides",
                    chip,
                    "{}",
                    { b, n -> overSets(b, n) },
                    listOf("e.name"),
                    expect))
        }
        return cases
    }

    private fun configured(entry: Case): Node {
        val chip = CHIPS.first({ candidate -> candidate.key == entry.chip })
        val template = wire.encodeToJsonElement(Node.serializer(), chip.template).jsonObject
        val fields = wire.parseToJsonElement(entry.fields).jsonObject
        return wire.decodeFromJsonElement(Node.serializer(), JsonObject(template + fields))
    }

    private fun attempt(entry: Case): String? {
        val build = Build()
        val root = entry.build(build, configured(entry))
        val batch =
            wire.decodeFromString(
                Command.serializer(),
                wire.encodeToString(Command.serializer(), Batch(build.commands)))
        val session = apply(Session(), batch)
        val workspace = Workspace("case", Path.of("case.dbest"), EditorState(session))
        try {
            val found = problems(session, workspace.engineState.tables)
            if (found.isNotEmpty()) {
                return "the plan has problems: $found"
            }
            val query = planForNodeById(session, NodeId(root))
            val keys =
                schema(query, workspace.engineState.tables)
                    .map({ column -> "${column.source}.${column.name}" })
            if (entry.keys != null && keys != entry.keys) {
                return "columns $keys, expected ${entry.keys}"
            }
            val produced =
                workspace.engineState
                    .page(query, 0, 100)
                    .map({ row ->
                        entry.show.map({ key -> shown(row, keys.indexOf(key)) }).joinToString("/")
                    })
            val same =
                if (entry.ordered) produced == entry.expect
                else produced.sorted() == entry.expect.sorted()
            return if (same) null else "rows $produced, expected ${entry.expect}"
        } catch (failure: RuntimeException) {
            return "${failure.javaClass.simpleName}: ${failure.message}"
        } finally {
            workspace.engineState.close()
        }
    }

    private fun shown(row: List<Any?>, index: Int): String {
        val value = if (index < 0) null else row[index]
        return if (value == null || value.toString().isEmpty()) "-" else value.toString()
    }

    @Test
    fun `every palette chip runs, and the engine defects on record still fail`() {
        val cases = cases()
        for (chip in CHIPS) {
            assertTrue(
                cases.any({ entry -> entry.chip == chip.key }),
                "the palette offers ${chip.key} and no case runs it")
        }
        val failures = ArrayList<String>()
        for (entry in cases) {
            val problem = attempt(entry)
            if (entry.broken == null && problem != null) {
                failures.add("${entry.chip}: ${entry.name}\n    $problem")
            }
            if (entry.broken != null && problem == null) {
                failures.add(
                    "${entry.chip}: ${entry.name}\n    marked broken (${entry.broken}) but now passes")
            }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }

    @Test
    fun `every root of the v2 fixture yields master's golden columns, rows, errors and problems`() {
        val session = load(Path.of("test/fixture-v2.dbest")).session
        val golden =
            wire
                .parseToJsonElement(Files.readString(Path.of("test/golden/rows-v2.json")))
                .jsonObject
        val opened = OpenTables()
        try {
            for (root in roots(session)) {
                val expected = golden.getValue(root.value.toString()).jsonObject
                val actual =
                    try {
                        val query = planForNodeById(session, root)
                        val columns = schema(query, opened)
                        val cursor = Cursor(query, opened)
                        val rows = ArrayList<List<Any?>>()
                        while (cursor.hasNext()) {
                            rows.add(cursor.next())
                        }
                        cursor.close()
                        JsonObject(
                            mapOf(
                                "columns" to
                                    JsonArray(
                                        columns.map({ column ->
                                            JsonPrimitive(
                                                "${column.source}.${column.name}:${engineTypeName(column.type)}:${column.primaryKey}")
                                        })),
                                "rows" to
                                    JsonArray(
                                        rows.map({ row ->
                                            JsonArray(row.map({ value -> cell(value) }))
                                        })),
                            ))
                    } catch (failure: RuntimeException) {
                        JsonObject(
                            mapOf(
                                "error" to
                                    JsonPrimitive(
                                        "${failure.javaClass.simpleName}: ${failure.message}")))
                    }
                assertEquals(comparable(expected), comparable(actual), "root #${root.value}")
            }
            val problems =
                golden
                    .getValue("problems")
                    .jsonArray
                    .map({ element ->
                        Problem(
                            NodeId(
                                element.jsonObject.getValue("node").jsonPrimitive.content.toInt()),
                            element.jsonObject.getValue("message").jsonPrimitive.content)
                    })
            assertEquals(problems, problems(session, opened))
        } finally {
            closeTables(opened)
        }
    }

    private fun comparable(outcome: JsonObject): JsonObject {
        val error = outcome["error"]?.jsonPrimitive?.content
        return if (error != null && error.startsWith("EngineFailure"))
            JsonObject(mapOf("error" to JsonPrimitive("EngineFailure")))
        else outcome
    }

    private fun engineTypeName(type: ColumnType): String =
        if (type == ColumnType.INT) "INTEGER" else type.name

    @Test
    fun `hash aggregation groups unsorted input while the sort-based one fragments it`() {
        val interleaved =
            MemorySpec(
                "orders",
                listOf(
                    column("id", ColumnType.INT, primaryKey = true),
                    column("user_id", ColumnType.INT),
                    column("total", ColumnType.INT)),
                listOf(
                    mapOf("id" to 10, "user_id" to 1, "total" to 250),
                    mapOf("id" to 11, "user_id" to 3, "total" to 900),
                    mapOf("id" to 12, "user_id" to 1, "total" to 50)),
            )
        val source = Query(TableNode(TableId(1), "o"), emptyList(), interleaved)
        val opened = OpenTables()
        try {
            val sum = listOf(Agg(AggFunction.SUM, "total"))
            val hashed = Query(AggNode("g", "o.user_id", sum, hashed = true), listOf(source), null)
            assertEquals(
                setOf(listOf<Any?>(1, 300), listOf<Any?>(3, 900)), rows(hashed, opened).toSet())
            val ordered =
                Query(AggNode("g", "o.user_id", sum, hashed = false), listOf(source), null)
            assertEquals(
                listOf(listOf<Any?>(1, 250), listOf<Any?>(3, 900), listOf<Any?>(1, 50)),
                rows(ordered, opened))
        } finally {
            closeTables(opened)
        }
    }

    @Test
    fun `a union, an append, a logical operator and a condition name their output with their alias`() {
        val build = Build()
        val union = overSets(build, SetOpNode(SetKind.UNION, alias = "equipe"))
        val append = overSets(build, SetOpNode(SetKind.APPEND, alias = "todos"))
        val unnamed = overSets(build, SetOpNode(SetKind.UNION))
        val intersection = overSets(build, SetOpNode(SetKind.INTERSECT, alias = "ignored"))
        val and = overSides(build, LogicalOpNode(LogicalKind.AND, "ambos"), 99)
        val condition = build.add(ConditionNode(IsNull("e.tags"), "vazio"))
        val session = build.commands.fold(Session(), { state, command -> apply(state, command) })
        val opened = OpenTables()
        try {
            val sources = { id: Int ->
                schema(planForNodeById(session, NodeId(id)), opened)
                    .map({ column -> column.source })
                    .toSet()
            }
            assertEquals(setOf("equipe"), sources(union))
            assertEquals(setOf("todos"), sources(append))
            assertEquals(setOf("e"), sources(unnamed))
            assertEquals(setOf("e"), sources(intersection))
            assertEquals(setOf("ambos"), sources(and))
            assertEquals(setOf("vazio"), sources(condition))
        } finally {
            closeTables(opened)
        }
    }

    @Test
    fun `set operations fall back to the engine class as alias when the left side mixes qualifiers`() {
        val crossed =
            Query(
                CrossNode,
                listOf(
                    Query(TableNode(TableId(1), "u"), emptyList(), users()),
                    Query(TableNode(TableId(2), "o"), emptyList(), orders())),
                null)
        val opened = OpenTables()
        try {
            val union = Query(SetOpNode(SetKind.UNION, false), listOf(crossed, crossed), null)
            assertEquals(
                setOf("Union"), schema(union, opened).map({ column -> column.source }).toSet())
        } finally {
            closeTables(opened)
        }
    }

    @Test
    fun `csv and xml tables read their files with typed columns`() {
        val csv = Files.createTempFile("people", ".csv")
        val xml = Files.createTempFile("employees", ".xml")
        Files.writeString(csv, "id,name,age\n1,Ana,22\n2,Bruno,17\n3,Carla,34\n")
        Files.writeString(
            xml,
            "<employees><employee><name>Ana</name><age>22</age></employee><employee><name>Bruno</name><age>17</age></employee></employees>")
        val opened = OpenTables()
        try {
            val people =
                CsvSpec(
                    "people",
                    csv.toString(),
                    listOf(
                        column("id", ColumnType.INT, primaryKey = true),
                        column("name", ColumnType.STRING),
                        column("age", ColumnType.INT)))
            val adults =
                Query(
                    FilterNode(Comparison("p.age", CompareOp.GTE, 18)),
                    listOf(Query(TableNode(TableId(1), "p"), emptyList(), people)),
                    null)
            assertEquals(listOf("Ana", "Carla"), rows(adults, opened).map({ row -> row[1] }))

            val employees =
                XmlSpec(
                    "employees",
                    xml.toString(),
                    listOf(column("name", ColumnType.STRING), column("age", ColumnType.INT)))
            assertEquals(
                listOf(listOf<Any?>("Ana", 22), listOf<Any?>("Bruno", 17)),
                rows(Query(TableNode(TableId(2), "e"), emptyList(), employees), opened))
        } finally {
            closeTables(opened)
            Files.deleteIfExists(csv)
            Files.deleteIfExists(xml)
        }
    }

    @Test
    fun `an unknown column is a plan error and an unsupported join has no engine class`() {
        val opened = OpenTables()
        try {
            val bad =
                Query(
                    FilterNode(Comparison("u.salary", CompareOp.GT, 1)),
                    listOf(Query(TableNode(TableId(1), "u"), emptyList(), users())),
                    null)
            assertFailsWith<PlanError>(block = { rows(bad, opened) })
        } finally {
            closeTables(opened)
        }
        for (type in JoinType.entries) {
            for (algorithm in JoinAlgorithm.entries) {
                val refused =
                    algorithm == JoinAlgorithm.NESTED_LOOP &&
                        type in setOf(JoinType.FULL, JoinType.RIGHT_SEMI, JoinType.RIGHT_ANTI)
                assertEquals(
                    refused,
                    engineClass(JoinNode(type, algorithm, listOf(JoinTerm("a.x", "b.y")))) == null,
                    "$type/$algorithm")
            }
        }
    }

    @Test
    fun `a join passes its current row to exactly the side lookupSides names`() {
        val joins =
            JoinType.entries
                .flatMap({ type ->
                    JoinAlgorithm.entries.map({ algorithm ->
                        JoinNode(type, algorithm, listOf(JoinTerm("e.dept", "d.id")))
                    })
                })
                .filter({ node -> engineClass(node) != null })
        val emp = Query(TableNode(TableId(1), "e"), emptyList(), emp("emp", false))
        val dep = Query(TableNode(TableId(2), "d"), emptyList(), dep("dep", false))
        val empReadingDep =
            Query(FilterNode(Comparison("e.dept", CompareOp.EQ, Ref("d.id"))), listOf(emp), null)
        val depReadingEmp =
            Query(FilterNode(Comparison("d.id", CompareOp.EQ, Ref("e.dept"))), listOf(dep), null)
        val opened = OpenTables()
        try {
            for (node in joins + CrossNode) {
                for (side in listOf(Port.LEFT, Port.RIGHT)) {
                    val inputs =
                        if (side == Port.LEFT) listOf(empReadingDep, dep)
                        else listOf(emp, depReadingEmp)
                    val ran =
                        try {
                            rows(Query(node, inputs, null), opened)
                            true
                        } catch (failure: PlanError) {
                            false
                        }
                    assertEquals(
                        side == lookupSides(node)?.receiver,
                        ran,
                        "$node reading the other input on its $side side")
                }
            }
        } finally {
            closeTables(opened)
        }
    }

    @Test
    fun `a reference types a column from the join feeding it, and runs as text on its own`() {
        val emp = Query(TableNode(TableId(1), "e"), emptyList(), emp("emp", false))
        val reference = Query(ReferenceNode(listOf("e.dept")), emptyList(), null)
        val opened = OpenTables()
        try {
            assertEquals(
                ColumnType.INT,
                schema(Query(JoinNode(on = emptyList()), listOf(emp, reference), null), opened)
                    .last()
                    .type)
            assertEquals(
                listOf(SchemaColumn("ref", "e_dept", ColumnType.STRING, false)),
                schema(reference, opened))
            assertFailsWith<PlanError>(block = { rows(reference, opened) })
        } finally {
            closeTables(opened)
        }
    }

    private fun rows(query: Query, tables: OpenTables): List<List<Any?>> {
        val cursor = Cursor(query, tables)
        try {
            val rows = ArrayList<List<Any?>>()
            while (cursor.hasNext()) {
                rows.add(cursor.next())
            }
            return rows
        } finally {
            cursor.close()
        }
    }
}
