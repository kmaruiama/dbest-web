package dbest

import ibd.table.btree.BTreeTable
import ibd.table.prototype.BasicDataRow
import ibd.table.prototype.Prototype
import ibd.table.prototype.column.IntegerColumn
import ibd.table.prototype.metadata.Metadata
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import org.http4k.core.Method
import org.http4k.core.Status

class ComparatorTest {

    private fun users(alias: String): Query =
        Query(TableNode(TableId(1), alias), emptyList(), users())

    private val adults =
        Query(FilterNode(Comparison("u.age", CompareOp.GT, 18)), listOf(users("u")), null)

    private val sorted = Query(SortNode(listOf(SortKey("v.name", true))), listOf(users("v")), null)

    private fun counted(measure: Measure): Map<String, Long> =
        measure.counters.associate({ metric -> Pair(metric.name, metric.value) }) +
            ("tuples" to measure.tuples.toLong())

    @Test
    fun `each side is counted from zero, so neither the order nor a rerun changes its numbers`() {
        val adultsFirst = counted(measure(adults, null))
        val sortedSecond = counted(measure(sorted, null))
        val sortedFirst = counted(measure(sorted, null))
        val adultsSecond = counted(measure(adults, null))
        assertEquals(adultsFirst, adultsSecond)
        assertEquals(sortedFirst, sortedSecond)
        assertEquals(2L, adultsFirst["tuples"])
        assertEquals(3L, sortedFirst["tuples"])
        assertEquals(0L, adultsFirst["sortedTuples"])
        assertEquals(3L, sortedFirst["sortedTuples"])
    }

    @Test
    fun `a limit stops a side early and says whether tuples remain`() {
        val one = measure(sorted, 1)
        assertEquals(Pair(1, false), Pair(one.tuples, one.exhausted))
        val three = measure(sorted, 3)
        assertEquals(Pair(3, true), Pair(three.tuples, three.exhausted))
        val all = measure(sorted, null)
        assertEquals(Pair(3, true), Pair(all.tuples, all.exhausted))
    }

    private fun btree(dir: Path, count: Int): BTreeSpec {
        val prototype = Prototype()
        prototype.addColumn(IntegerColumn("id", 4, Metadata.PRIMARY_KEY))
        prototype.addColumn(IntegerColumn("v", 4, Metadata.NONE))
        val table = BTreeTable(null, dir.toString(), "nums.dat", 1_000)
        table.create(prototype, 4096)
        for (id in 1..count) {
            val row = BasicDataRow()
            row.setInt("id", id)
            row.setInt("v", id % 7)
            table.addRecord(row, true)
        }
        table.flushDB()
        table.close()
        return BTreeSpec("nums", dir.resolve("nums.dat").toString())
    }

    @Test
    fun `a side reads its B-tree from a cold cache, so measuring it again loads the same blocks`() {
        val dir = Files.createTempDirectory("dbest-btree")
        try {
            val scan = Query(TableNode(TableId(1), "n"), emptyList(), btree(dir, 3_000))
            val first = counted(measure(scan, null))
            assertEquals(3_000L, first["tuples"])
            assertTrue((first["blocksLoaded"] ?: 0) > 0, "$first")
            assertEquals(
                first,
                counted(measure(scan, null)),
                "a cache kept warm between runs would load fewer blocks")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an unfinished or correlated node is not measured, and says why`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                for (command in demoCommands()) {
                    harness.command(sid, command)
                }
                harness.command(
                    sid,
                    AddNode(
                        NodeId(5),
                        FilterNode(Comparison("o.total", CompareOp.GT, 1)),
                        Position(0.0, 0.0)))
                harness.command(
                    sid, AddNode(NodeId(6), TableNode(TableId(1), "u"), Position(0.0, 0.0)))
                harness.command(
                    sid, AddNode(NodeId(7), TableNode(TableId(2), "o"), Position(0.0, 0.0)))
                harness.command(
                    sid,
                    AddNode(
                        NodeId(8),
                        FilterNode(Comparison("o.user_id", CompareOp.EQ, Ref("u.id"))),
                        Position(0.0, 0.0)))
                harness.command(
                    sid,
                    AddNode(
                        NodeId(9),
                        JoinNode(JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP, emptyList()),
                        Position(0.0, 0.0)))
                harness.command(sid, Connect(Edge(NodeId(7), NodeId(8), Port.ONLY)))
                harness.command(sid, Connect(Edge(NodeId(6), NodeId(9), Port.LEFT)))
                harness.command(sid, Connect(Edge(NodeId(8), NodeId(9), Port.RIGHT)))

                val unfinished = harness.send(Method.GET, "/sessions/$sid/nodes/4/compare/5")
                assertEquals(Status.UNPROCESSABLE_ENTITY, unfinished.status)
                assertTrue("sem sua entrada" in unfinished.bodyString(), unfinished.bodyString())
                val correlated = harness.send(Method.GET, "/sessions/$sid/nodes/8/compare/4")
                assertEquals(Status.UNPROCESSABLE_ENTITY, correlated.status)
                assertTrue("vindas do join #9" in correlated.bodyString(), correlated.bodyString())
                assertEquals(
                    Status.OK,
                    harness.send(Method.GET, "/sessions/$sid/nodes/9/compare/4").status,
                    "the join itself runs")
                for (limit in listOf("abc", "-3", "1.5", "")) {
                    assertEquals(
                        Status.BAD_REQUEST,
                        harness
                            .send(Method.GET, "/sessions/$sid/nodes/9/compare/4?limit=$limit")
                            .status,
                        "limit=$limit")
                }
            })
    }

    @Test
    fun `only nodes of different trees can be compared`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                for (command in demoCommands()) {
                    harness.command(sid, command)
                }
                harness.command(
                    sid, AddNode(NodeId(5), TableNode(TableId(1), "x"), Position(0.0, 0.0)))
                val apart = harness.send(Method.GET, "/sessions/$sid/nodes/4/compare/5?limit=10")
                assertEquals(Status.OK, apart.status)
                val measures =
                    wire.decodeFromString(ListSerializer(Measure.serializer()), apart.bodyString())
                assertEquals(
                    listOf(2, 3),
                    measures.map({ measure -> measure.tuples }),
                    "answered in request order")

                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.GET, "/sessions/$sid/nodes/3/compare/4").status)
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.GET, "/sessions/$sid/nodes/4/compare/4").status)
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.GET, "/sessions/$sid/nodes/4/compare/5?limit=0").status)
                assertEquals(
                    Status.NOT_FOUND,
                    harness.send(Method.GET, "/sessions/$sid/nodes/4/compare/9").status)

                harness.command(
                    sid,
                    AddNode(
                        NodeId(6),
                        FilterNode(Comparison("x.age", CompareOp.GT, 18)),
                        Position(0.0, 0.0)))
                harness.command(
                    sid,
                    AddNode(
                        NodeId(7),
                        FilterNode(Comparison("x.age", CompareOp.LT, 18)),
                        Position(0.0, 0.0)))
                harness.command(sid, Connect(Edge(NodeId(5), NodeId(6), Port.ONLY)))
                harness.command(sid, Connect(Edge(NodeId(5), NodeId(7), Port.ONLY)))
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.GET, "/sessions/$sid/nodes/6/compare/7").status)
                assertEquals(
                    Status.OK, harness.send(Method.GET, "/sessions/$sid/nodes/4/compare/7").status)
            })
    }
}
