package dbest

import ibd.query.QueryStats
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.Method
import org.http4k.core.Status

class ExecutionTest {

    private fun numbers(count: Int): MemorySpec =
        MemorySpec(
            "nums",
            listOf(column("id", ColumnType.INT, primaryKey = true)),
            (1..count).map({ id -> mapOf<String, Any?>("id" to id) }),
        )

    private fun scan(count: Int, alias: String = "n"): Query =
        Query(TableNode(TableId(1), alias), emptyList(), numbers(count))

    private fun ids(rows: List<List<Any?>>): List<Any?> = rows.map({ row -> row[0] })

    private fun scratch(): Workspace = Workspace("t", Path.of("t.dbest"), EditorState())

    private fun graph(nodes: List<Node>, edges: List<Edge>): Session {
        val commands =
            ArrayList<Command>(
                listOf(AddTable(TableId(1), users()), AddTable(TableId(2), orders())))
        for ((index, node) in nodes.withIndex()) {
            commands.add(AddNode(NodeId(index + 1), node, Position(0.0, 0.0)))
        }
        return apply(Session(), Batch(commands + edges.map({ edge -> Connect(edge) })))
    }

    private fun edge(from: Int, to: Int, port: Port): Edge = Edge(NodeId(from), NodeId(to), port)

    private fun outerNames(session: Session, id: Int): List<String> {
        val workspace = scratch()
        try {
            return outerColumns(session, NodeId(id), workspace.engineState.tables)
                .map({ column -> "${column.source}.${column.name}" })
        } finally {
            workspace.engineState.close()
        }
    }

    private val semi = JoinNode(JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP, emptyList())
    private val userColumns = listOf("u.id", "u.name", "u.age")
    private val orderColumns = listOf("o.id", "o.user_id", "o.total")

    @Test
    fun `paging forward continues the open cursor and paging back starts over`() {
        val workspace = scratch()
        try {
            val query = scan(50)
            QueryStats.NEXT_CALLS = 0
            assertEquals((1..10).toList(), ids(workspace.engineState.page(query, 0, 10)))
            val afterFirst = QueryStats.NEXT_CALLS
            assertEquals((11..20).toList(), ids(workspace.engineState.page(query, 10, 10)))
            assertTrue(QueryStats.NEXT_CALLS - afterFirst < 20, "the second page should not rescan")
            assertEquals((1..10).toList(), ids(workspace.engineState.page(query, 0, 10)))
        } finally {
            workspace.engineState.close()
        }
    }

    @Test
    fun `an exhausted cursor is closed and the least recently used ones are evicted`() {
        val workspace = scratch()
        try {
            workspace.engineState.page(scan(5), 0, 10)
            assertTrue(workspace.engineState.cursors.isEmpty())
            for (i in 0..CURSOR_CAPACITY) {
                workspace.engineState.page(scan(50, "p$i"), 0, 5)
            }
            assertEquals(CURSOR_CAPACITY, workspace.engineState.cursors.size)
            assertTrue(scan(50, "p0") !in workspace.engineState.cursors)
        } finally {
            workspace.engineState.close()
        }
    }

    @Test
    fun `problems reports missing inputs and engine errors, root by root`() {
        val workspace = scratch()
        try {
            val incomplete =
                edit(demo(), Disconnect(Edge(NodeId(2), NodeId(3), Port.RIGHT))).session
            assertEquals(
                listOf(Problem(NodeId(3), "falta a entrada RIGHT")),
                problems(incomplete, workspace.engineState.tables))

            val badColumn =
                edit(
                        demo(),
                        SetNode(NodeId(4), FilterNode(Comparison("salary", CompareOp.GT, 100))))
                    .session
            val reported = problems(badColumn, workspace.engineState.tables)
            assertEquals(listOf(NodeId(4)), reported.map({ problem -> problem.node }))
            assertTrue(reported.single().message.contains("not found"))

            assertTrue(problems(demo().session, workspace.engineState.tables).isEmpty())
        } finally {
            workspace.engineState.close()
        }
    }

    @Test
    fun `a node reads the provider row of every join whose receiver side it feeds, nearest first, through any operator`() {
        val session =
            graph(
                listOf(
                    TableNode(TableId(1), "u"),
                    TableNode(TableId(2), "o"),
                    TableNode(TableId(1), "v"),
                    FilterNode(Comparison("v.id", CompareOp.EQ, Ref("o.user_id"))),
                    TableNode(TableId(1), "w"),
                    JoinNode(JoinType.INNER, JoinAlgorithm.HASH, listOf(JoinTerm("v.id", "w.id"))),
                    TableNode(TableId(2), "p"),
                    LogicalOpNode(LogicalKind.OR),
                    semi,
                    semi,
                ),
                listOf(
                    edge(3, 4, Port.ONLY),
                    edge(4, 6, Port.LEFT),
                    edge(5, 6, Port.RIGHT),
                    edge(6, 8, Port.LEFT),
                    edge(7, 8, Port.RIGHT),
                    edge(2, 9, Port.LEFT),
                    edge(8, 9, Port.RIGHT),
                    edge(1, 10, Port.LEFT),
                    edge(9, 10, Port.RIGHT),
                ),
            )
        assertEquals(orderColumns + userColumns, outerNames(session, 4))
        assertEquals(
            orderColumns + userColumns,
            outerNames(session, 5),
            "the hash join adds nothing, but passes on what it reads")
        assertEquals(
            userColumns, outerNames(session, 2), "the provider side reads only what its join reads")
        assertEquals(emptyList(), outerNames(session, 1))
        assertEquals(listOf(NodeId(9), NodeId(10)), receivingJoins(session, NodeId(3)))
    }

    @Test
    fun `a node feeding two joins reads both providers, and a provider still missing an input offers nothing`() {
        val session =
            graph(
                listOf(
                    TableNode(TableId(1), "u"),
                    TableNode(TableId(2), "o"),
                    TableNode(TableId(1), "x"),
                    CrossNode,
                    semi,
                    FilterNode(Comparison("u.age", CompareOp.GT, 18)),
                    TableNode(TableId(1), "y"),
                    semi,
                ),
                listOf(
                    edge(1, 4, Port.LEFT),
                    edge(3, 4, Port.RIGHT),
                    edge(2, 5, Port.LEFT),
                    edge(3, 5, Port.RIGHT),
                    edge(6, 8, Port.LEFT),
                    edge(7, 8, Port.RIGHT),
                ),
            )
        assertEquals((userColumns + orderColumns).toSet(), outerNames(session, 3).toSet())
        assertEquals(emptyList(), outerNames(session, 7))
        assertEquals(listOf(NodeId(8)), receivingJoins(session, NodeId(7)))
    }

    @Test
    fun `the engine rejects a concurrent run and frees up afterwards`() {
        val engine = Engine()
        try {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val worker =
                Thread({
                    engine.run({
                        started.countDown()
                        release.await()
                    })
                })
            worker.start()
            started.await()
            assertFailsWith<EngineBusyException>(block = { engine.run({ 0 }) })
            release.countDown()
            worker.join()
            assertEquals(42, engine.run({ 42 }))
        } finally {
            engine.close()
        }
    }

    @Test
    fun `schema, paged rows and streamed rows agree over HTTP`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                for (command in demoCommands()) {
                    harness.command(sid, command)
                }
                val schema =
                    wire.decodeFromString(
                        ListSerializer(SchemaColumn.serializer()),
                        harness.send(Method.GET, "/sessions/$sid/nodes/1/schema").bodyString())
                assertEquals(listOf("id", "name", "age"), schema.map({ column -> column.name }))
                assertEquals(ColumnType.INT, schema[0].type)

                val paged =
                    wire
                        .parseToJsonElement(
                            harness
                                .send(Method.GET, "/sessions/$sid/nodes/4/rows?offset=0&limit=200")
                                .bodyString())
                        .jsonObject
                val streamed = harness.send(Method.GET, "/sessions/$sid/nodes/4/rows")
                assertEquals("application/x-ndjson; charset=utf-8", streamed.header("Content-Type"))
                val lines = streamed.bodyString().split("\n").filter({ line -> line.isNotBlank() })
                assertEquals(
                    paged.getValue("rows").jsonArray.map({ row -> row.toString() }),
                    lines.map({ line -> wire.parseToJsonElement(line).toString() }))
                assertEquals(2, lines.size)
            })
    }

    @Test
    fun `offset without limit, or limit without offset, is refused`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                for (command in demoCommands()) {
                    harness.command(sid, command)
                }
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.GET, "/sessions/$sid/nodes/4/rows?limit=100").status)
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.GET, "/sessions/$sid/nodes/4/rows?offset=0").status)
            })
    }

    @Test
    fun `a stream reads its rows lazily and closing it early frees the engine`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                harness.command(sid, AddTable(TableId(1), numbers(25_000)))
                harness.command(
                    sid, AddNode(NodeId(1), TableNode(TableId(1), "n"), Position(0.0, 0.0)))
                QueryStats.NEXT_CALLS = 0

                val response = harness.send(Method.GET, "/sessions/$sid/nodes/1/rows")
                assertEquals(1, response.body.stream.read(ByteArray(1)))
                assertTrue(QueryStats.NEXT_CALLS < 25_000, "one read should pull only one chunk")
                assertEquals(
                    Status.CONFLICT,
                    harness.send(Method.GET, "/sessions/$sid/nodes/1/rows?offset=0&limit=1").status)

                val closer = Thread({ response.close() })
                closer.start()
                closer.join()
                assertEquals(
                    Status.OK,
                    harness.send(Method.GET, "/sessions/$sid/nodes/1/rows?offset=0&limit=1").status)
            })
    }

    @Test
    fun `a slow stream hands back what it has after a slice of time, so closing it frees the engine soon`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                val values =
                    MemorySpec(
                        "v",
                        listOf(column("v", ColumnType.INT)),
                        (1..12_000).map({ v -> mapOf<String, Any?>("v" to v) }))
                harness.command(sid, AddTable(TableId(1), values))
                harness.command(
                    sid, AddNode(NodeId(1), TableNode(TableId(1), "a"), Position(0.0, 0.0)))
                harness.command(
                    sid, AddNode(NodeId(2), TableNode(TableId(1), "b"), Position(0.0, 0.0)))
                harness.command(
                    sid,
                    AddNode(
                        NodeId(3),
                        JoinNode(on = listOf(JoinTerm("a.v", "b.v"))),
                        Position(0.0, 0.0)))
                harness.command(sid, Connect(Edge(NodeId(1), NodeId(3), Port.LEFT)))
                harness.command(sid, Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)))

                val response = harness.send(Method.GET, "/sessions/$sid/nodes/3/rows")
                val first = response.body.stream.read(ByteArray(1_000_000))
                assertTrue(
                    first in 1 until 64 * 1024,
                    "the first chunk held $first bytes; it should end after a slice of time")

                val closer = Thread({ response.close() })
                closer.start()
                closer.join()
                assertEquals(
                    Status.OK,
                    harness.send(Method.GET, "/sessions/$sid/nodes/1/rows?offset=0&limit=1").status)
            })
    }

    private fun correlated(harness: Harness, sid: String) {
        harness.command(sid, AddTable(TableId(1), users()))
        harness.command(sid, AddTable(TableId(2), orders()))
        harness.command(sid, AddNode(NodeId(1), TableNode(TableId(1), "u"), Position(0.0, 0.0)))
        harness.command(sid, AddNode(NodeId(2), TableNode(TableId(2), "o"), Position(0.0, 0.0)))
        harness.command(
            sid,
            AddNode(
                NodeId(3),
                FilterNode(Comparison("o.user_id", CompareOp.EQ, Ref("u.id"))),
                Position(0.0, 0.0)))
        harness.command(
            sid,
            AddNode(
                NodeId(4),
                JoinNode(
                    JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP, listOf(JoinTerm("?.?", "?.?"))),
                Position(0.0, 0.0)))
        harness.command(sid, Connect(Edge(NodeId(2), NodeId(3), Port.ONLY)))
        harness.command(sid, Connect(Edge(NodeId(1), NodeId(4), Port.LEFT)))
        harness.command(sid, Connect(Edge(NodeId(3), NodeId(4), Port.RIGHT)))
    }

    @Test
    fun `only a nested loop join can drop its terms, and a blank column reference is refused`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                correlated(harness, sid)
                for (algorithm in listOf(JoinAlgorithm.HASH, JoinAlgorithm.MERGE)) {
                    val refused =
                        harness.send(
                            Method.POST,
                            "/sessions/$sid/commands",
                            """{"@type": "setNode", "id": 4, "node": {"@type": "join", "type": "LEFT_SEMI", "algorithm": "$algorithm", "on": []}}""")
                    assertEquals(Status.BAD_REQUEST, refused.status, "$algorithm")
                }
                assertEquals(
                    Status.OK,
                    harness
                        .command(
                            sid,
                            SetNode(
                                NodeId(4),
                                JoinNode(
                                    JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP, emptyList())))
                        .status)
                val blank =
                    """{"@type": "setNode", "id": 3, "node": {"@type": "filter", "condition": {"@type": "cmp", "left": "o.user_id", "op": "EQ", "right": {"ref": " "}}}}"""
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.POST, "/sessions/$sid/commands", blank).status)
            })
    }

    @Test
    fun `a form is offered each input's columns and the columns it reads from outside`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                correlated(harness, sid)
                val filter =
                    wire.decodeFromString(
                        Columns.serializer(),
                        harness.send(Method.GET, "/sessions/$sid/nodes/3/columns").bodyString())
                assertEquals(Columns(mapOf(Port.ONLY to orderColumns), userColumns), filter)
                val join =
                    wire.decodeFromString(
                        Columns.serializer(),
                        harness.send(Method.GET, "/sessions/$sid/nodes/4/columns").bodyString())
                assertEquals(
                    Columns(
                        mapOf(Port.LEFT to userColumns, Port.RIGHT to orderColumns), emptyList()),
                    join)
            })
    }

    @Test
    fun `a node that reads its join's rows runs in the join, and alone says which join to run`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                correlated(harness, sid)
                harness.command(
                    sid,
                    SetNode(
                        NodeId(4),
                        JoinNode(JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP, emptyList())))
                val joined =
                    wire.decodeFromString(
                        RowsPage.serializer(),
                        harness
                            .send(Method.GET, "/sessions/$sid/nodes/4/rows?offset=0&limit=10")
                            .bodyString())
                assertEquals(listOf("Ana", "Carla"), joined.rows.map({ row -> row[1].content }))
                for (path in listOf("rows?offset=0&limit=10", "rows", "export?format=csv")) {
                    val alone = harness.send(Method.GET, "/sessions/$sid/nodes/3/$path")
                    assertEquals(Status.UNPROCESSABLE_ENTITY, alone.status, path)
                    val message =
                        wire.decodeFromString(Failure.serializer(), alone.bodyString()).error
                    assertTrue(
                        message.startsWith("o node #3 le colunas de fora dele (vindas do join #4)"),
                        message)
                    assertTrue("not found" in message, message)
                }
            })
    }

    @Test
    fun `a broken plan fails before the stream starts, and reading an unknown node is a 404`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                for (command in demoCommands()) {
                    harness.command(sid, command)
                }
                harness.command(
                    sid, SetNode(NodeId(4), FilterNode(Comparison("o.salary", CompareOp.GT, 1))))
                assertEquals(
                    Status.UNPROCESSABLE_ENTITY,
                    harness.send(Method.GET, "/sessions/$sid/nodes/4/rows").status)
                assertEquals(
                    Status.NOT_FOUND,
                    harness.send(Method.GET, "/sessions/$sid/nodes/7/schema").status)
                val problems =
                    wire
                        .parseToJsonElement(
                            harness.send(Method.GET, "/sessions/$sid/problems").bodyString())
                        .jsonArray
                assertEquals(
                    "4", problems.single().jsonObject.getValue("node").jsonPrimitive.content)
            })
    }
}
