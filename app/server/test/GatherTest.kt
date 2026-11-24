package dbest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GatherTest {

    private val dir: Path = Files.createTempDirectory("dbest-gather")
    private val nums: BTreeSpec = numbers(dir, 3_000)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private fun scan(parallel: Boolean, alias: String = "n"): Query =
        Query(TableNode(TableId(1), alias, parallel), emptyList(), nums)

    private fun over(node: Node, vararg inputs: Query): Query = Query(node, inputs.toList(), null)

    private fun gather(threads: Int, input: Query): Query = over(GatherNode(threads), input)

    private fun rows(query: Query): List<List<Any?>> {
        val tables = OpenTables()
        val cursor = Cursor(query, tables)
        try {
            return cursor.take(Int.MAX_VALUE)
        } finally {
            cursor.close()
            closeTables(tables)
        }
    }

    private fun sameRows(serial: Query, parallel: Query, ordered: Boolean = false) {
        val expected = rows(serial)
        val actual = rows(parallel)
        if (ordered) {
            assertEquals(expected, actual)
        } else {
            assertEquals(expected.size, actual.size)
            assertEquals(
                expected.groupingBy({ it }).eachCount(), actual.groupingBy({ it }).eachCount())
        }
    }

    @Test
    fun `a gathered scan returns the serial rows in key order, for any thread count`() {
        for (threads in listOf(1, 2, 3, 4, 7, 8, 64)) {
            sameRows(scan(false), gather(threads, scan(true)), ordered = true)
        }
    }

    @Test
    fun `a parallel table outside a gather reads the whole table`() {
        sameRows(scan(false), scan(true), ordered = true)
    }

    @Test
    fun `operators under a gather run once per part`() {
        val filter = { input: Query -> over(FilterNode(Comparison("n.v", CompareOp.EQ, 3)), input) }
        val project = { input: Query -> over(ProjectNode(listOf("n.id")), input) }
        val limit = { input: Query -> over(LimitNode(5), input) }
        val sort = { input: Query -> over(SortNode(listOf(SortKey("n.v", false))), input) }
        for (wrap in listOf(filter, project)) {
            for (threads in listOf(2, 5)) {
                sameRows(wrap(scan(false)), gather(threads, wrap(scan(true))))
            }
        }
        assertEquals(
            20, rows(gather(4, limit(scan(true)))).size, "a limit under a gather is per part")
        assertEquals(3_000, rows(gather(4, sort(scan(true)))).size)
        val distinct = over(DistinctNode(true), over(ProjectNode(listOf("n.v")), scan(true)))
        assertEquals(14, rows(gather(2, distinct)).size, "a distinct under a gather is per part")
        assertEquals(
            3_000, rows(over(SortNode(listOf(SortKey("n.v", true))), gather(4, scan(true)))).size)
    }

    @Test
    fun `a parallel table can be joined with other tables, parallel or not`() {
        val join = { left: Query, right: Query, algorithm: JoinAlgorithm ->
            over(JoinNode(JoinType.INNER, algorithm, listOf(JoinTerm("n.v", "u.id"))), left, right)
        }
        val keyless = users().copy(columns = users().columns.map({ it.copy(primaryKey = false) }))
        val users = Query(TableNode(TableId(2), "u"), emptyList(), keyless)
        val other = Query(TableNode(TableId(1), "m"), emptyList(), nums)
        for (algorithm in JoinAlgorithm.entries.filter({ it != JoinAlgorithm.MERGE })) {
            sameRows(
                join(scan(false), users, algorithm), gather(4, join(scan(true), users, algorithm)))
        }
        val self = over(JoinNode(on = listOf(JoinTerm("n.id", "m.id"))), scan(true), other)
        assertEquals(3_000, rows(gather(3, self)).size)
        val byId = JoinNode(on = listOf(JoinTerm("n.id", "m.id")))
        sameRows(
            over(byId, scan(false), scan(false, "m")),
            gather(2, over(byId, scan(true), scan(true, "m"))))
    }

    @Test
    fun `a grouped aggregation under a gather groups each part, and over it groups them all`() {
        val count = listOf(Agg(AggFunction.COUNT, "n.id"))
        val byV = { input: Query -> over(AggNode("g", "n.v", count), input) }
        assertEquals(7, rows(byV(gather(4, scan(true)))).size)
        assertTrue(rows(gather(4, byV(scan(true)))).size >= 7)
    }

    @Test
    fun `gather plans that cannot run say why`() {
        fun failure(query: Query): String =
            assertFailsWith<PlanError>(block = { rows(query) }).message.orEmpty()
        assertTrue("uma tabela paralela" in failure(gather(2, scan(false))))
        assertTrue("outro Gather" in failure(gather(2, gather(2, scan(true)))))
        val memory = Query(TableNode(TableId(2), "u", true), emptyList(), users())
        assertTrue("B-tree" in failure(gather(2, memory)))
        assertTrue("B-tree" in failure(memory))
        for (threads in listOf(0, -1, 65)) {
            assertFailsWith<IllegalArgumentException>(block = { GatherNode(threads) })
            assertFailsWith<IllegalArgumentException>(
                block = {
                    wire.decodeFromString(
                        Node.serializer(), """{"@type":"gather","threads":$threads}""")
                })
        }
    }

    @Test
    fun `two gathers side by side each run their own threads`() {
        val both =
            over(SetOpNode(SetKind.APPEND), gather(3, scan(true)), gather(2, scan(true, "m")))
        assertEquals(6_000, rows(both).size)
    }

    @Test
    fun `pages continue a running gather, and closing it early stops its threads`() {
        val state = EngineState()
        try {
            val query = gather(4, scan(true))
            val first = state.page(query, 0, 10)
            val second = state.page(query, 10, 10)
            assertEquals((1..20).toList(), (first + second).map({ row -> row[0] }))
        } finally {
            state.close()
        }
        val before =
            Thread.getAllStackTraces().keys.count({ it.name.startsWith("gather-") && it.isAlive })
        repeat(20, { rows(over(LimitNode(1), gather(8, scan(true)))) })
        val after =
            Thread.getAllStackTraces().keys.count({ it.name.startsWith("gather-") && it.isAlive })
        assertTrue(after <= before, "gather threads leaked: $before before, $after after")
    }

    @Test
    fun `the comparator measures a gather against its serial plan`() {
        val serial = measure(scan(false), null)
        val parallel = measure(gather(4, scan(true)), null)
        assertEquals(serial.tuples, parallel.tuples)
        val read = { measured: Measure ->
            measured.counters.first({ it.name == "recordsRead" }).value
        }
        assertEquals(read(serial), read(parallel))
    }

    @Test
    fun `a session file leaves parallel out unless it is set`() {
        val plain = files.encodeToString(Node.serializer(), TableNode(TableId(1), "n"))
        assertTrue("parallel" !in plain, plain)
        val parallel = TableNode(TableId(1), "n", true)
        assertEquals(
            parallel,
            files.decodeFromString(
                Node.serializer(), files.encodeToString(Node.serializer(), parallel)))
    }
}
