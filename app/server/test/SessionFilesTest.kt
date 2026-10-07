package dbest

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionFilesTest {

    private fun session(name: String): Session = load(Path.of("test/sessions/$name.dbest")).session

    private fun results(session: Session): Map<Int, String> {
        val opened = OpenTables()
        try {
            val lines = LinkedHashMap<Int, String>()
            for (root in roots(session)) {
                val line =
                    try {
                        val cursor = Cursor(planForNodeById(session, root), opened)
                        val rows = ArrayList<String>()
                        while (cursor.hasNext()) {
                            rows.add(cursor.next().joinToString(","))
                        }
                        cursor.close()
                        cursor.schema.joinToString(
                            ",", transform = { column -> "${column.source}.${column.name}" }) +
                            " | " +
                            rows.joinToString(" ; ")
                    } catch (failure: EngineException) {
                        "error: ${failure.message}"
                    }
                lines.put(root.value, line)
            }
            return lines
        } finally {
            closeTables(opened)
        }
    }

    private fun problemsOf(session: Session): List<Problem> {
        val opened = OpenTables()
        try {
            return problems(session, opened)
        } finally {
            closeTables(opened)
        }
    }

    private fun counted(session: Session, id: Int, limit: Int?): Map<String, Long> {
        val measure = measure(planForNodeById(session, NodeId(id)), limit)
        return measure.counters.associate({ metric -> Pair(metric.name, metric.value) }) +
            ("tuples" to measure.tuples.toLong())
    }

    @Test
    fun `named unions, appends, aggregations and row numbers qualify their rows with their alias, and the others keep their left input's`() {
        val aliases = session("aliases")
        assertEquals(emptyList(), problemsOf(aliases))
        assertEquals(
            mapOf(
                6 to
                    "pessoas.id,pessoas.name,pessoas.dept,pessoas.tags | 1,ana,10,x;y ; 3,cid,10,w",
                12 to "todos.id,todos.city | 10,sp ; 20,rj ; 30,bh ; 10,sp ; 20,rj ; 30,bh",
                15 to "porDept.dept,porDept.COUNT_id | 20,1 ; 40,1",
                19 to "e6.id,e6.name,e6.dept,e6.tags | 1,ana,10,x;y ; 3,cid,10,w",
                21 to "linha.n,d3.id,d3.city | 1,10,sp ; 2,20,rj ; 3,30,bh",
                29 to "grupo.id,grupo.name,grupo.dept,grupo.tags | 1,ana,10,x;y ; 3,cid,10,w",
                30 to "e3.id,e3.name,e3.dept,e3.tags | 1,ana,10,x;y ; 3,cid,10,w",
            ),
            results(aliases),
        )
    }

    @Test
    fun `correlated plans answer as their SQL would`() {
        val correlation = session("correlation")
        assertEquals(emptyList(), problemsOf(correlation))
        assertEquals(
            mapOf(
                4 to "d.id,d.city | 10,sp ; 20,rj",
                8 to "d.id,d.city | 30,bh",
                14 to "func.id,func.name,func.dept,func.tags | 2,bob,20,z ; 3,cid,10,w",
                28 to
                    "func.id,func.name,func.dept,func.tags,atual.func_dept | 1,ana,10,x;y,10 ; 3,cid,10,w,10",
                21 to
                    "func.id,func.name,func.dept,func.tags,d.id,d.city | 1,ana,10,x;y,10,sp ; 2,bob,20,z,20,rj ; 3,cid,10,w,10,sp ; 4,dan,40,q,null,null",
                27 to "d.id,d.city | 10,sp ; 20,rj",
            ),
            results(correlation),
        )
    }

    @Test
    fun `a hash and a nested loop plan of one join cost the engine differently, and a sort counts what it sorts`() {
        val plans = session("comparator")
        val hash = counted(plans, 3, null)
        val nested = counted(plans, 6, null)
        assertEquals(Pair(3L, 3L), Pair(hash["tuples"], nested["tuples"]))
        assertEquals(
            Pair(0L, 12L),
            Pair(hash["comparisons"], nested["comparisons"]),
            "the nested loop compares each of 4 rows with each of 3")
        assertTrue(
            (hash["memoryBytes"] ?: 0) > 0 && nested["memoryBytes"] == 0L,
            "only the hash join builds a table")
        assertEquals(4L, counted(plans, 8, null)["sortedTuples"])
        assertEquals(
            3L,
            counted(plans, 6, 1)["comparisons"],
            "the first tuple needs only the first left row")
    }

    @Test
    fun `a version 2 file loads with its term-less hash join asking for a term and its old union named after its left input`() {
        val legacy = session("legacy-v2")
        val placeholder = JoinTerm("$PLACEHOLDER.$PLACEHOLDER", "$PLACEHOLDER.$PLACEHOLDER")
        assertEquals(
            JoinNode(JoinType.INNER, JoinAlgorithm.HASH, listOf(placeholder)),
            legacy.nodes.getValue(NodeId(3)))
        assertEquals(listOf(NodeId(3)), problemsOf(legacy).map({ problem -> problem.node }))
        val results = results(legacy)
        assertEquals(
            "e.id,e.name,e.dept,e.tags | 1,ana,10,x;y ; 3,cid,10,w ; 4,dan,40,q", results.get(8))
        assertEquals(
            "e.id,e.name,e.dept,e.tags,d.id,d.city | 1,ana,10,x;y,10,sp ; 2,bob,20,z,20,rj ; 3,cid,10,w,10,sp",
            results.get(11))
    }
}
