package dbest.features.canvas.graph

import dbest.kernel.adapter.Agg
import dbest.kernel.adapter.Condition
import dbest.kernel.adapter.JoinAlgorithm
import dbest.kernel.adapter.JoinTerm
import dbest.kernel.adapter.JoinType
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.QualifiedCol
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.SortKey
import dbest.kernel.adapter.requireAggregate
import dbest.kernel.adapter.requireAliasPair
import dbest.kernel.adapter.requireCollapseAlias
import dbest.kernel.adapter.requireExplode
import dbest.kernel.adapter.requireJoinTerms
import dbest.kernel.adapter.requireLimitBounds
import dbest.kernel.adapter.requireProjectColumns
import dbest.kernel.adapter.requireRemoveColumns
import dbest.kernel.adapter.requireRowNumber
import dbest.kernel.adapter.requireSortKeys
import dbest.kernel.adapter.requireSourceAlias

sealed interface Node

sealed interface SourceNode : Node

sealed interface UnaryNode : Node

sealed interface BinaryNode : Node

fun inputPorts(node: Node): Set<Port> = when (node) {
    is SourceNode -> emptySet()
    is UnaryNode -> setOf(Port.ONLY)
    is BinaryNode -> setOf(Port.LEFT, Port.RIGHT)
}

fun operatorKind(node: Node): String = when (node) {
    is TableNode -> "table"
    is FilterNode -> "filter"
    is ProjectNode -> "project"
    is SortNode -> "sort"
    is DistinctNode -> "distinct"
    is LimitNode -> "limit"
    is AliasNode -> "alias"
    is CollapseNode -> "collapse"
    is ExplodeNode -> "explode"
    is RowNumberNode -> "rowNumber"
    is AggNode -> "agg"
    is RemoveColumnsNode -> "removeColumns"
    is MaterializeNode -> "materialize"
    is MemoizeNode -> "memoize"
    is HashIndexNode -> "hashIndex"
    is ScanNode -> "scan"
    is JoinNode -> "join"
    is CrossNode -> "cross"
    is SetOpNode -> "setOp"
    is LogicalOpNode -> "logicalOp"
    is ExistsNode -> "exists"
}

data class TableNode(val table: TableId, val alias: String) : SourceNode {
    init {
        requireSourceAlias(alias)
    }
}

data class FilterNode(val condition: Condition) : UnaryNode

data class ProjectNode(val columns: List<String>) : UnaryNode {
    init {
        requireProjectColumns(columns)
    }
}

data class SortNode(val keys: List<SortKey>) : UnaryNode {
    init {
        requireSortKeys(keys)
    }
}

data class DistinctNode(val hashed: Boolean = true) : UnaryNode

data class LimitNode(val count: Int, val offset: Int = 0) : UnaryNode {
    init {
        requireLimitBounds(count, offset)
    }
}

data class AliasNode(val from: String, val to: String) : UnaryNode {
    init {
        requireAliasPair(from, to)
    }
}

data class CollapseNode(val alias: String) : UnaryNode {
    init {
        requireCollapseAlias(alias)
    }
}

data class ExplodeNode(val column: String, val delimiter: String = ",") : UnaryNode {
    init {
        requireExplode(column, delimiter)
    }
}

data class RowNumberNode(val alias: String, val column: String, val start: Int = 1) : UnaryNode {
    init {
        requireRowNumber(alias, column)
    }
}

data class AggNode(val alias: String, val by: QualifiedCol?, val aggregates: List<Agg>, val hashed: Boolean = true) : UnaryNode {
    init {
        requireAggregate(alias, aggregates)
    }
}

data class RemoveColumnsNode(val columns: List<String>, val alias: String = "Projection") : UnaryNode {
    init {
        requireRemoveColumns(columns, alias)
    }
}

data object MaterializeNode : UnaryNode

data object MemoizeNode : UnaryNode

data object HashIndexNode : UnaryNode

data object ScanNode : UnaryNode

data class JoinNode(val on: List<JoinTerm>, val type: JoinType = JoinType.INNER, val algorithm: JoinAlgorithm = JoinAlgorithm.NESTED_LOOP) : BinaryNode {
    init {
        requireJoinTerms(on)
    }
}

data object CrossNode : BinaryNode

data class SetOpNode(val kind: SetKind, val hashed: Boolean = true) : BinaryNode

data class LogicalOpNode(val kind: LogicalKind) : BinaryNode

data class ExistsNode(val bilateral: Boolean = false) : BinaryNode
