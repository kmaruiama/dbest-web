package dbest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class GraphTest {

    private fun fixtureHistory(): EditorState {
        val users =
            MemorySpec(
                "users",
                listOf(
                    column("id", ColumnType.INT, primaryKey = true),
                    column("name", ColumnType.STRING),
                    column("visits", ColumnType.LONG),
                    column("score", ColumnType.FLOAT),
                    column("ratio", ColumnType.DOUBLE),
                    column("active", ColumnType.BOOLEAN),
                    column("nick", ColumnType.STRING, nullable = true),
                ),
                listOf(
                    mapOf(
                        "id" to 1,
                        "name" to "Ana",
                        "visits" to 10L,
                        "score" to 1.5f,
                        "ratio" to 0.25,
                        "active" to true,
                        "nick" to null),
                    mapOf(
                        "id" to 2,
                        "name" to "Bruno",
                        "visits" to 3L,
                        "score" to 2.5f,
                        "ratio" to 0.5,
                        "active" to false,
                        "nick" to "bru"),
                ),
            )
        val ordersFile =
            CsvSpec(
                "orders",
                "data/orders.csv",
                listOf(
                    column("id", ColumnType.INT, primaryKey = true),
                    column("user_id", ColumnType.INT),
                    column("total", ColumnType.INT)),
                separator = ';',
                delimiter = '\'',
                headerLine = 2,
            )
        val condition =
            And(
                listOf(
                    Comparison("o.total", CompareOp.GTE, 100),
                    Or(listOf(Comparison("u.active", CompareOp.EQ, true), IsNull("u.nick"))),
                    IsNotNull("u.name"),
                    Comparison("u.id", CompareOp.NEQ, Ref("o.user_id")),
                    Comparison("u.score", CompareOp.GT, 1.5f),
                    Comparison("u.ratio", CompareOp.LT, 0.9),
                    Comparison("u.visits", CompareOp.LTE, 100L),
                    Comparison("u.name", CompareOp.EQ, "Ana"),
                ))
        val totals =
            listOf(
                Agg(AggFunction.SUM, "total"),
                Agg(AggFunction.COUNT, "id"),
                Agg(AggFunction.MAX, "total"),
                Agg(AggFunction.MIN, "total"),
                Agg(AggFunction.AVG, "total"),
                Agg(AggFunction.FIRST, "id"),
                Agg(AggFunction.LAST, "id"),
                Agg(AggFunction.COUNT_ALL, "id"),
                Agg(AggFunction.COUNT_NULL, "id"),
            )
        val commands =
            listOf(
                AddTable(TableId(1), users),
                AddTable(TableId(2), ordersFile),
                AddTable(TableId(3), BTreeSpec("archive", "data/archive.btree", cacheSize = 512)),
                AddTable(TableId(4), BTreeSpec("scratch", "data/scratch.btree")),
                RemoveTable(TableId(4)),
                AddNode(NodeId(1), TableNode(TableId(1), "u"), Position(0.0, 0.0)),
                AddNode(NodeId(2), TableNode(TableId(2), "o"), Position(0.0, 120.0)),
                AddNode(
                    NodeId(3),
                    JoinNode(
                        JoinType.LEFT, JoinAlgorithm.HASH, listOf(JoinTerm("u.id", "o.user_id"))),
                    Position(160.0, 60.0)),
                AddNode(NodeId(4), FilterNode(condition), Position(320.0, 60.0)),
                AddNode(NodeId(5), ProjectNode(listOf("u.name", "o.total")), Position(480.0, 60.0)),
                AddNode(
                    NodeId(6),
                    SortNode(listOf(SortKey("u.name", true), SortKey("o.total", true))),
                    Position(0.0, 240.0)),
                AddNode(NodeId(7), DistinctNode(hashed = false), Position(0.0, 300.0)),
                AddNode(NodeId(8), LimitNode(10, offset = 5), Position(0.0, 360.0)),
                AddNode(NodeId(9), AliasNode("u", "people"), Position(0.0, 420.0)),
                AddNode(NodeId(10), CollapseNode("all"), Position(0.0, 480.0)),
                AddNode(NodeId(11), ExplodeNode("u.tags", ";"), Position(0.0, 540.0)),
                AddNode(NodeId(12), RowNumberNode("r", "row", start = 5), Position(0.0, 600.0)),
                AddNode(NodeId(13), AggNode("byUser", "o.user_id", totals), Position(0.0, 660.0)),
                AddNode(
                    NodeId(14),
                    AggNode("overall", null, listOf(Agg(AggFunction.SUM, "total"))),
                    Position(0.0, 720.0)),
                AddNode(NodeId(15), MaterializeNode, Position(0.0, 780.0)),
                AddNode(NodeId(16), MemoizeNode, Position(0.0, 840.0)),
                AddNode(NodeId(17), HashIndexNode, Position(0.0, 900.0)),
                AddNode(NodeId(18), CrossNode, Position(0.0, 960.0)),
                AddNode(
                    NodeId(19), SetOpNode(SetKind.UNION, hashed = false), Position(0.0, 1020.0)),
                AddNode(NodeId(20), SetOpNode(SetKind.APPEND), Position(0.0, 1080.0)),
                Connect(Edge(NodeId(1), NodeId(3), Port.LEFT)),
                Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)),
                Connect(Edge(NodeId(3), NodeId(4), Port.ONLY)),
                Connect(Edge(NodeId(4), NodeId(5), Port.ONLY)),
                Move(NodeId(5), Position(500.0, 61.0)),
                SetNode(NodeId(8), LimitNode(20)),
                Batch(
                    listOf(
                        AddNode(NodeId(21), DistinctNode(), Position(640.0, 60.0)),
                        Connect(Edge(NodeId(5), NodeId(21), Port.ONLY)))),
                Disconnect(Edge(NodeId(4), NodeId(5), Port.ONLY)),
                RemoveNode(NodeId(20)),
            )
        return undo(
            commands.fold(EditorState(), { editorState, command -> edit(editorState, command) }))
    }

    private fun golden(name: String): JsonObject =
        migrate(2, wire.parseToJsonElement(Files.readString(Path.of("test/golden/$name"))))
            .jsonObject

    private fun assertMatchesGolden(editorState: EditorState, golden: String) {
        val view = golden(golden)
        assertEquals(
            wire.decodeFromJsonElement(Session.serializer(), view.getValue("session")),
            editorState.session)
        val captions =
            wire.decodeFromJsonElement(
                MapSerializer(NodeId.serializer(), Caption.serializer()), view.getValue("captions"))
        assertEquals(captions, captions(editorState.session))
    }

    @Test
    fun `a v1 file written by the first kotlinx codec still loads, and matches master's view`() {
        val loaded = load(Path.of("test/fixture-v1.dbest"))
        assertEquals(fixtureHistory(), loaded)
        assertMatchesGolden(loaded, "view-v1.json")
    }

    @Test
    fun `a v2 file with every operator loads and matches master's view`() {
        assertMatchesGolden(load(Path.of("test/fixture-v2.dbest")), "view-v2.json")
    }

    @Test
    fun `a session round-trips through the current file format`() {
        val editorState = load(Path.of("test/fixture-v2.dbest"))
        val file = Files.createTempFile("session", ".dbest")
        try {
            save(file, editorState, false)
            assertTrue(Files.readString(file).contains("\"version\": $SESSION_VERSION"))
            assertEquals(editorState, load(file))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a saved session is written exactly as the golden file`() {
        val file = Files.createTempFile("session", ".dbest")
        try {
            save(file, load(Path.of("test/fixture-v2.dbest")), false)
            assertEquals(
                Files.readString(Path.of("test/golden/session-v3.dbest")), Files.readString(file))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a v1 scan-tagged table source migrates to a table node, a bare scan stays a scan`() {
        val legacy = wire.parseToJsonElement("""{"@type": "scan", "table": 7, "alias": "u"}""")
        assertEquals(
            TableNode(TableId(7), "u"),
            wire.decodeFromJsonElement(Node.serializer(), migrate(1, legacy)))
        val scan = wire.parseToJsonElement("""{"@type": "scan"}""")
        assertEquals(ScanNode, wire.decodeFromJsonElement(Node.serializer(), migrate(1, scan)))
    }

    @Test
    fun `a v2 hash join saved without terms loads with the placeholder term, a nested loop one stays term-less`() {
        val hash =
            wire.parseToJsonElement(
                """{"@type": "join", "on": [], "type": "INNER", "algorithm": "HASH"}""")
        val placeholder = JoinTerm("$PLACEHOLDER.$PLACEHOLDER", "$PLACEHOLDER.$PLACEHOLDER")
        assertEquals(
            JoinNode(JoinType.INNER, JoinAlgorithm.HASH, listOf(placeholder)),
            wire.decodeFromJsonElement(Node.serializer(), migrate(2, hash)),
        )
        val nested =
            wire.parseToJsonElement(
                """{"@type": "join", "on": [], "type": "LEFT_SEMI", "algorithm": "NESTED_LOOP"}""")
        assertEquals(
            JoinNode(JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP, emptyList()),
            wire.decodeFromJsonElement(Node.serializer(), migrate(2, nested)),
        )
    }

    @Test
    fun `a source name with a dot is refused on edit, though a file holding one still loads`() {
        val union = AddNode(NodeId(9), SetOpNode(SetKind.UNION, true, "a.b"), Position(0.0, 0.0))
        assertFailsWith<IllegalArgumentException>(block = { edit(demo(), union) })
        assertFailsWith<IllegalArgumentException>(
            block = { edit(demo(), SetNode(NodeId(1), TableNode(TableId(1), "u.x"))) })
        assertFailsWith<IllegalArgumentException>(
            block = {
                edit(demo(), AddNode(NodeId(9), AliasNode("u.id", "v.w"), Position(0.0, 0.0)))
            })
        edit(demo(), SetNode(NodeId(1), TableNode(TableId(1), "minha tabela")))

        val saved =
            wire.encodeToString(
                EditorState.serializer(),
                EditorState(apply(demo().session, SetNode(NodeId(1), TableNode(TableId(1), "u")))))
        val dotted = wire.parseToJsonElement(saved.replace("\"alias\":\"u\"", "\"alias\":\"u.x\""))
        assertEquals(
            "u.x",
            (wire
                    .decodeFromJsonElement(EditorState.serializer(), migrate(3, dotted))
                    .session
                    .nodes
                    .getValue(NodeId(1)) as TableNode)
                .alias)
    }

    @Test
    fun `one tree may not name two sources alike, though two trees may`() {
        val tables = listOf<Command>(AddTable(TableId(1), users()), AddTable(TableId(2), orders()))
        val nodes =
            listOf<Command>(
                AddNode(NodeId(1), TableNode(TableId(1), "u"), Position(0.0, 0.0)),
                AddNode(NodeId(2), TableNode(TableId(2), "u"), Position(0.0, 0.0)),
                AddNode(
                    NodeId(3),
                    JoinNode(on = listOf(JoinTerm("u.id", "u.user_id"))),
                    Position(0.0, 0.0)),
                Connect(Edge(NodeId(1), NodeId(3), Port.LEFT)),
            )
        val apart = apply(Session(), Batch(tables + nodes))
        val joined =
            assertFailsWith<IllegalArgumentException>(
                block = { apply(apart, Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT))) })
        assertTrue("o alias 'u' ja nomeia o node #1" in joined.message.orEmpty(), joined.message)
        assertEquals(emptyList(), sourceClashes(apart), "node 2 stands in a tree of its own")

        val renamed = apply(apart, SetNode(NodeId(2), TableNode(TableId(2), "o")))
        val linked = apply(renamed, Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)))
        assertFailsWith<IllegalArgumentException>(
            block = { apply(linked, SetNode(NodeId(2), TableNode(TableId(2), "u"))) })
    }

    @Test
    fun `a blank union names nothing, and one table feeding two nodes is one source`() {
        val union =
            apply(
                demo().session,
                Batch(
                    listOf(
                        AddNode(
                            NodeId(5),
                            FilterNode(Comparison("u.age", CompareOp.GT, 18)),
                            Position(0.0, 0.0)),
                        AddNode(NodeId(6), SetOpNode(SetKind.UNION), Position(0.0, 0.0)),
                        Connect(Edge(NodeId(1), NodeId(5), Port.ONLY)),
                        Connect(Edge(NodeId(4), NodeId(6), Port.LEFT)),
                    )))
        assertEquals(emptyList(), sourceClashes(union))
    }

    @Test
    fun `a clash a file already has is reported, and edits that leave it be are allowed`() {
        val clashing =
            Session(
                tables = mapOf(TableId(1) to users()),
                nodes =
                    mapOf(
                        NodeId(1) to TableNode(TableId(1), "u"),
                        NodeId(2) to TableNode(TableId(1), "u"),
                        NodeId(3) to SetOpNode(SetKind.UNION),
                        NodeId(4) to FilterNode(Comparison("u.age", CompareOp.GT, 18)),
                    ),
                edges =
                    setOf(
                        Edge(NodeId(1), NodeId(3), Port.LEFT),
                        Edge(NodeId(2), NodeId(3), Port.RIGHT),
                        Edge(NodeId(3), NodeId(4), Port.ONLY)),
                layout =
                    mapOf(
                        NodeId(1) to Position(0.0, 0.0),
                        NodeId(2) to Position(0.0, 0.0),
                        NodeId(3) to Position(0.0, 0.0),
                        NodeId(4) to Position(0.0, 0.0)),
            )
        assertEquals(
            listOf(Problem(NodeId(2), "o alias 'u' ja nomeia o node #1 desta arvore")),
            sourceClashes(clashing))
        apply(clashing, SetNode(NodeId(4), FilterNode(Comparison("u.age", CompareOp.GT, 30))))
        assertEquals(
            emptyList(),
            sourceClashes(apply(clashing, SetNode(NodeId(2), TableNode(TableId(1), "v")))))
    }

    @Test
    fun `node ids key JSON objects by their number`() {
        val session = demo().session
        val encoded = wire.encodeToJsonElement(Session.serializer(), session).jsonObject
        assertEquals(setOf("1", "2", "3", "4"), encoded.getValue("nodes").jsonObject.keys)
    }

    @Test
    fun `undo inverts every command kind`() {
        val commands =
            demoCommands() +
                listOf(
                    Move(NodeId(4), Position(500.0, 60.0)),
                    SetNode(NodeId(4), FilterNode(Comparison("o.total", CompareOp.GTE, 250))),
                    Disconnect(Edge(NodeId(3), NodeId(4), Port.ONLY)),
                    RemoveNode(NodeId(4)),
                    Batch(
                        listOf(
                            AddNode(NodeId(5), DistinctNode(), Position(320.0, 60.0)),
                            Connect(Edge(NodeId(3), NodeId(5), Port.ONLY)))),
                )
        var editorState = EditorState()
        for (command in commands) {
            val before = editorState.session
            editorState = edit(editorState, command)
            assertEquals(before, undo(editorState).session, "undo failed for $command")
        }
    }

    @Test
    fun `undo all the way back reaches the empty session, redo replays forward`() {
        var editorState = demo()
        val full = editorState.session
        repeat(editorState.undoStack.size, { _ -> editorState = undo(editorState) })
        assertEquals(Session(), editorState.session)
        repeat(editorState.redoStack.size, { _ -> editorState = redo(editorState) })
        assertEquals(full, editorState.session)
    }

    @Test
    fun `removing a node restores its edges and position on undo`() {
        val removed = edit(demo(), RemoveNode(NodeId(3)))
        assertTrue(removed.session.edges.isEmpty())
        assertEquals(demo().session, undo(removed).session)
    }

    @Test
    fun `moving a selection is one undo step`() {
        val start = demo()
        val moved =
            edit(
                start,
                Batch(
                    listOf(
                        Move(NodeId(3), Position(180.0, 80.0)),
                        Move(NodeId(4), Position(340.0, 80.0)))))
        assertEquals(start.undoStack.size + 1, moved.undoStack.size)
        assertEquals(start.session, undo(moved).session)
        assertEquals(moved.session, redo(undo(moved)).session)
    }

    @Test
    fun `history keeps at most UNDO_LIMIT steps`() {
        var editorState = EditorState()
        for (i in 1..UNDO_LIMIT + 5) {
            editorState = edit(editorState, AddTable(TableId(i), users().copy(name = "t$i")))
        }
        assertEquals(UNDO_LIMIT, editorState.undoStack.size)
        assertEquals(UNDO_LIMIT + 5, editorState.session.tables.size)
    }

    @Test
    fun `bad edits are rejected`() {
        val session = demo().session
        assertFailsWith<IllegalArgumentException>(
            block = { apply(session, Connect(Edge(NodeId(4), NodeId(1), Port.ONLY))) })
        assertFailsWith<IllegalArgumentException>(
            block = { apply(session, Connect(Edge(NodeId(2), NodeId(4), Port.LEFT))) })
        assertFailsWith<IllegalArgumentException>(
            block = { apply(session, Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT))) })
        assertFailsWith<IllegalArgumentException>(
            block = { apply(session, RemoveTable(TableId(1))) })
        assertFailsWith<IllegalArgumentException>(
            block = { apply(session, SetNode(NodeId(4), DistinctNode())) })
        assertFailsWith<IllegalArgumentException>(
            block = {
                apply(session, AddNode(NodeId(9), TableNode(TableId(9), "x"), Position(0.0, 0.0)))
            })
        assertFailsWith<IllegalArgumentException>(
            block = { apply(session, AddTable(TableId(9), users().copy(name = " "))) })
    }

    @Test
    fun `nodes keep their own invariants, even when decoded`() {
        assertFailsWith<IllegalArgumentException>(block = { ProjectNode(emptyList()) })
        assertFailsWith<IllegalArgumentException>(block = { LimitNode(0) })
        assertFailsWith<IllegalArgumentException>(block = { JoinTerm("id", "o.user_id") })
        assertFailsWith<IllegalArgumentException>(
            block = { SortNode(listOf(SortKey("a", true), SortKey("b", false))) })
        assertFailsWith<IllegalArgumentException>(block = { Ref(" ") })
        assertFailsWith<IllegalArgumentException>(
            block = { JoinNode(algorithm = JoinAlgorithm.HASH, on = emptyList()) })
        assertFailsWith<IllegalArgumentException>(
            block = { JoinNode(algorithm = JoinAlgorithm.MERGE, on = emptyList()) })
        assertEquals(
            emptyList(), JoinNode(algorithm = JoinAlgorithm.NESTED_LOOP, on = emptyList()).on)
        assertFailsWith<IllegalArgumentException>(block = { ReferenceNode(emptyList()) })
        assertFailsWith<IllegalArgumentException>(block = { ReferenceNode(listOf("dept")) })
        assertFailsWith<IllegalArgumentException>(
            block = {
                wire.decodeFromString(Node.serializer(), """{"@type": "limit", "count": -1}""")
            })
    }

    @Test
    fun `roots are the unconsumed nodes and missing inputs are reported per port`() {
        val session = edit(demo(), Disconnect(Edge(NodeId(2), NodeId(3), Port.RIGHT))).session
        assertEquals(listOf(NodeId(2), NodeId(4)), roots(session))
        assertEquals(listOf(Problem(NodeId(3), "falta a entrada RIGHT")), missingInputs(session))
    }
}
