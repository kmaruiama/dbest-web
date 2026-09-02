package dbest.features.canvas.graph

import dbest.kernel.adapter.Plan
import dbest.kernel.adapter.SortKey
import dbest.kernel.adapter.agg
import dbest.kernel.adapter.alias
import dbest.kernel.adapter.collapse
import dbest.kernel.adapter.explode
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.join
import dbest.kernel.adapter.limit
import dbest.kernel.adapter.memoryTable
import dbest.kernel.adapter.project
import dbest.kernel.adapter.removeColumns
import dbest.kernel.adapter.rowNumber
import dbest.kernel.adapter.sort
import dbest.kernel.adapter.table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class NodeInvariantParityTest {

    private fun source(): Plan =
        table(memoryTable("parity", intColumn("id", primaryKey = true)), "t")

    private data class Case(val name: String, val node: () -> Node, val plan: () -> Plan)

    private val cases = listOf(
        Case("table alias", { TableNode(TableId(1), " ") }, { table(memoryTable("parity2", intColumn("id", primaryKey = true)), " ") }),
        Case("limit bounds", { LimitNode(0, 0) }, { limit(source(), 0, 0) }),
        Case("project columns", { ProjectNode(emptyList()) }, { project(source()) }),
        Case("sort keys", { SortNode(emptyList()) }, { sort(source()) }),
        Case("sort direction", { SortNode(listOf(SortKey("a", true), SortKey("b", false))) }, { sort(source(), SortKey("a", true), SortKey("b", false)) }),
        Case("alias pair", { AliasNode("", "") }, { alias(source(), "", "") }),
        Case("collapse alias", { CollapseNode(" ") }, { collapse(source(), " ") }),
        Case("explode column", { ExplodeNode(" ", ",") }, { explode(source(), " ", ",") }),
        Case("rowNumber alias", { RowNumberNode(" ", "c") }, { rowNumber(source(), " ", "c") }),
        Case("aggregate list", { AggNode("a", null, emptyList()) }, { agg(source(), "a") }),
        Case("removeColumns list", { RemoveColumnsNode(emptyList(), "r") }, { removeColumns(source(), alias = "r") }),
        Case("join terms", { JoinNode(emptyList()) }, { join(source(), source()) }),
    )

    @Test
    fun `node and plan reject the same states with the same message`() {
        for (case in cases) {
            val nodeError = assertFailsWith<IllegalArgumentException>(case.name, { case.node() })
            val planError = assertFailsWith<IllegalArgumentException>(case.name, { case.plan() })
            assertNotNull(nodeError.message, case.name)
            assertEquals(nodeError.message, planError.message, case.name)
        }
    }
}
