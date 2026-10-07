package dbest

import dbest.JoinAlgorithm.HASH
import dbest.JoinAlgorithm.MERGE
import dbest.JoinAlgorithm.NESTED_LOOP
import dbest.JoinType.FULL
import dbest.JoinType.INNER
import dbest.JoinType.LEFT
import dbest.JoinType.LEFT_ANTI
import dbest.JoinType.LEFT_SEMI
import dbest.JoinType.RIGHT
import dbest.JoinType.RIGHT_ANTI
import dbest.JoinType.RIGHT_SEMI
import engine.exceptions.DataBaseException
import ibd.query.ColumnDescriptor
import ibd.query.Operation
import ibd.query.binaryop.conditional.Exists
import ibd.query.binaryop.conditional.LogicalAnd
import ibd.query.binaryop.conditional.LogicalOr
import ibd.query.binaryop.conditional.LogicalXor
import ibd.query.binaryop.join.CrossJoin
import ibd.query.binaryop.join.HashInnerJoin
import ibd.query.binaryop.join.JoinPredicate
import ibd.query.binaryop.join.MergeJoin
import ibd.query.binaryop.join.NestedLoopJoin
import ibd.query.binaryop.join.anti.HashLeftAntiJoin
import ibd.query.binaryop.join.anti.HashRightAntiJoin
import ibd.query.binaryop.join.anti.MergeLeftAntiJoin
import ibd.query.binaryop.join.anti.MergeRightAntiJoin
import ibd.query.binaryop.join.anti.NestedLoopLeftAntiJoin
import ibd.query.binaryop.join.outer.HashFullOuterJoin
import ibd.query.binaryop.join.outer.HashLeftJoin
import ibd.query.binaryop.join.outer.HashRightJoin
import ibd.query.binaryop.join.outer.MergeFullOuterJoin
import ibd.query.binaryop.join.outer.MergeLeftOuterJoin
import ibd.query.binaryop.join.outer.MergeRightOuterJoin
import ibd.query.binaryop.join.outer.NestedLoopLeftJoin
import ibd.query.binaryop.join.outer.NestedLoopRightJoin
import ibd.query.binaryop.join.semi.HashLeftSemiJoin
import ibd.query.binaryop.join.semi.HashRightSemiJoin
import ibd.query.binaryop.join.semi.MergeLeftSemiJoin
import ibd.query.binaryop.join.semi.MergeRightSemiJoin
import ibd.query.binaryop.join.semi.NestedLoopSemiJoin
import ibd.query.binaryop.set.Append
import ibd.query.binaryop.set.Difference
import ibd.query.binaryop.set.HashDifference
import ibd.query.binaryop.set.HashIntersection
import ibd.query.binaryop.set.HashUnion
import ibd.query.binaryop.set.Intersection
import ibd.query.binaryop.set.Union
import ibd.query.lookup.ColumnElement
import ibd.query.lookup.CompositeLookupFilter
import ibd.query.lookup.Element
import ibd.query.lookup.LiteralElement
import ibd.query.lookup.LookupFilter
import ibd.query.lookup.SingleColumnLookupFilter
import ibd.query.sourceop.IndexScan
import ibd.query.unaryop.AutoIncrement
import ibd.query.unaryop.DuplicateRemoval
import ibd.query.unaryop.Explode
import ibd.query.unaryop.HashDuplicateRemoval
import ibd.query.unaryop.HashIndex
import ibd.query.unaryop.Limit
import ibd.query.unaryop.Materialization
import ibd.query.unaryop.Memoize
import ibd.query.unaryop.Projection
import ibd.query.unaryop.Reference
import ibd.query.unaryop.RemoveColumns
import ibd.query.unaryop.Scan
import ibd.query.unaryop.SourceRename
import ibd.query.unaryop.SourceRename1
import ibd.query.unaryop.aggregation.Aggregation
import ibd.query.unaryop.aggregation.AggregationType
import ibd.query.unaryop.aggregation.AllAggregation
import ibd.query.unaryop.aggregation.HashAggregation
import ibd.query.unaryop.filter.Condition as EngineCondition
import ibd.query.unaryop.filter.Filter
import ibd.query.unaryop.sort.Sort
import ibd.table.ComparisonTypes
import java.lang.reflect.InvocationTargetException
import kotlinx.serialization.Serializable

sealed class EngineException(message: String, cause: Throwable?) : RuntimeException(message, cause)

class PlanError(message: String, cause: Throwable? = null) : EngineException(message, cause)

class StorageError(message: String, cause: Throwable? = null) : EngineException(message, cause)

class EngineFailure(message: String, cause: Throwable? = null) : EngineException(message, cause)

data class Query(val node: Node, val inputs: List<Query>, val table: TableSpec?)

@Serializable
data class SchemaColumn(
    val source: String,
    val name: String,
    val type: ColumnType,
    val primaryKey: Boolean
)

fun planForNodeById(session: Session, id: NodeId): Query {
    val node = session.nodes.get(id) ?: throw PlanError("O node #${id.value} nao existe")
    val table =
        if (node is TableNode) {
            session.tables.get(node.table)
                ?: throw PlanError("A tabela ${node.table.value} nao existe")
        } else {
            null
        }
    val inputs =
        inputPorts(node)
            .map({ port ->
                val upstream = upstreamNode(session, id, port)
                planForNodeById(session, upstream)
            })
    return Query(node, inputs, table)
}

private fun upstreamNode(session: Session, id: NodeId, port: Port): NodeId =
    inputOf(session, id, port)
        ?: throw PlanError("O node #${id.value} esta sem sua entrada ${port.name}")

fun tryCompile(query: Query, tables: OpenTables): Operation = gate({ compile(query, tables) })

fun <T> gate(block: () -> T): T =
    try {
        block()
    } catch (failure: Exception) {
        throw engineError(failure)
    }

private val JOINS: Map<Pair<JoinType, JoinAlgorithm>, Class<out Operation>> =
    mapOf(
        Pair(INNER, NESTED_LOOP) to NestedLoopJoin::class.java,
        Pair(INNER, HASH) to HashInnerJoin::class.java,
        Pair(INNER, MERGE) to MergeJoin::class.java,
        Pair(LEFT, NESTED_LOOP) to NestedLoopLeftJoin::class.java,
        Pair(LEFT, HASH) to HashLeftJoin::class.java,
        Pair(LEFT, MERGE) to MergeLeftOuterJoin::class.java,
        Pair(RIGHT, NESTED_LOOP) to NestedLoopRightJoin::class.java,
        Pair(RIGHT, HASH) to HashRightJoin::class.java,
        Pair(RIGHT, MERGE) to MergeRightOuterJoin::class.java,
        Pair(FULL, HASH) to HashFullOuterJoin::class.java,
        Pair(FULL, MERGE) to MergeFullOuterJoin::class.java,
        Pair(LEFT_SEMI, NESTED_LOOP) to NestedLoopSemiJoin::class.java,
        Pair(LEFT_SEMI, HASH) to HashLeftSemiJoin::class.java,
        Pair(LEFT_SEMI, MERGE) to MergeLeftSemiJoin::class.java,
        Pair(RIGHT_SEMI, HASH) to HashRightSemiJoin::class.java,
        Pair(RIGHT_SEMI, MERGE) to MergeRightSemiJoin::class.java,
        Pair(LEFT_ANTI, NESTED_LOOP) to NestedLoopLeftAntiJoin::class.java,
        Pair(LEFT_ANTI, HASH) to HashLeftAntiJoin::class.java,
        Pair(LEFT_ANTI, MERGE) to MergeLeftAntiJoin::class.java,
        Pair(RIGHT_ANTI, HASH) to HashRightAntiJoin::class.java,
        Pair(RIGHT_ANTI, MERGE) to MergeRightAntiJoin::class.java,
    )

private fun engineError(failure: Throwable): EngineException {
    if (failure is EngineException) {
        return failure
    }
    if (failure is InvocationTargetException) {
        return engineError(failure.targetException)
    }
    if (failure is DataBaseException) {
        return StorageError(failure.message ?: "erro de storage", failure)
    }
    val message = failure.message.orEmpty()
    return if ("not found" in message) PlanError(message, failure)
    else EngineFailure(message.ifEmpty({ failure.javaClass.simpleName }), failure)
}

private fun compile(query: Query, tables: OpenTables): Operation {
    val node = query.node
    val inputs = query.inputs.map({ input -> compile(input, tables) })
    return when (node) {
        is TableNode -> {
            val spec = query.table as TableSpec
            IndexScan(node.alias, tables.getOrPut(spec, { openTable(spec) }))
        }
        is ConditionNode -> named(EngineCondition(lookupFilter(node.condition)), node.alias)
        is ReferenceNode -> named(Reference(node.columns.toTypedArray()), node.alias)
        is FilterNode -> Filter(inputs[0], lookupFilter(node.condition))
        is ProjectNode -> Projection(inputs[0], node.columns.toTypedArray())
        is RemoveColumnsNode -> RemoveColumns(inputs[0], node.alias, node.columns)
        is SortNode ->
            Sort(
                inputs[0],
                node.keys.map({ key -> key.column }).toTypedArray(),
                node.keys[0].ascending)
        is LimitNode -> Limit(inputs[0], node.count, node.offset)
        is AliasNode -> SourceRename(inputs[0], node.from, node.to)
        is CollapseNode -> SourceRename1(inputs[0], node.alias)
        is ExplodeNode -> Explode(inputs[0], node.column, node.delimiter)
        is RowNumberNode -> AutoIncrement(inputs[0], node.alias, node.column, node.start)
        is AggNode -> aggregate(node, inputs[0])
        is ExistsNode -> Exists(inputs[0], inputs[1], node.bilateral)
        is JoinNode -> make(classOf(node), inputs + joinPredicate(node.on))
        is SetOpNode -> setOperation(node, inputs)
        is LogicalOpNode -> named(make(classOf(node), inputs), node.alias)
        is DistinctNode,
        is MaterializeNode,
        is MemoizeNode,
        is HashIndexNode,
        is ScanNode,
        is CrossNode -> make(classOf(node), inputs)
    }
}

private fun named(operation: Operation, alias: String): Operation {
    operation.dataSourceAlias = alias
    return operation
}

private fun lookupFilter(condition: Condition): LookupFilter =
    when (condition) {
        is Comparison ->
            SingleColumnLookupFilter(
                column(condition.left), comparisonCode(condition.op), operand(condition.right))
        is IsNull ->
            SingleColumnLookupFilter(
                column(condition.column), ComparisonTypes.IS_NULL, LiteralElement(null))
        is IsNotNull ->
            SingleColumnLookupFilter(
                column(condition.column), ComparisonTypes.IS_NOT_NULL, LiteralElement(null))
        is And -> composite(CompositeLookupFilter.AND, condition.conditions)
        is Or -> composite(CompositeLookupFilter.OR, condition.conditions)
    }

private fun column(ref: String): Element {
    val source = sourceOf(ref)
    return ColumnElement(
        if (source == null) ColumnDescriptor(ref) else ColumnDescriptor(source, nameOf(ref)))
}

private fun comparisonCode(op: CompareOp): Int =
    when (op) {
        CompareOp.EQ -> ComparisonTypes.EQUAL
        CompareOp.NEQ -> ComparisonTypes.DIFF
        CompareOp.GT -> ComparisonTypes.GREATER_THAN
        CompareOp.GTE -> ComparisonTypes.GREATER_EQUAL_THAN
        CompareOp.LT -> ComparisonTypes.LOWER_THAN
        CompareOp.LTE -> ComparisonTypes.LOWER_EQUAL_THAN
    }

private fun operand(value: Any): Element =
    if (value is Ref) column(value.column) else LiteralElement(value as Comparable<*>)

private fun composite(connector: Int, conditions: List<Condition>): LookupFilter {
    val filter = CompositeLookupFilter(connector)
    for (condition in conditions) {
        filter.addFilter(lookupFilter(condition))
    }
    return filter
}

private fun aggregate(node: AggNode, input: Operation): Operation {
    val types =
        node.aggregates.map({ aggregate ->
            AggregationType(aggregate.column, aggregationCode(aggregate.function))
        })
    val by = node.by
    return when {
        by == null -> AllAggregation(input, node.alias, types)
        node.hashed -> HashAggregation(input, node.alias, sourceOf(by), nameOf(by), types, true)
        else -> Aggregation(input, node.alias, sourceOf(by), nameOf(by), types, true)
    }
}

private fun aggregationCode(function: AggFunction): Int =
    when (function) {
        AggFunction.MAX -> AggregationType.MAX
        AggFunction.MIN -> AggregationType.MIN
        AggFunction.COUNT -> AggregationType.COUNT
        AggFunction.AVG -> AggregationType.AVG
        AggFunction.SUM -> AggregationType.SUM
        AggFunction.FIRST -> AggregationType.FIRST
        AggFunction.LAST -> AggregationType.LAST
        AggFunction.COUNT_ALL -> AggregationType.COUNT_ALL
        AggFunction.COUNT_NULL -> AggregationType.COUNT_NULL
    }

private fun classOf(node: Node): Class<out Operation> =
    engineClass(node) ?: throw PlanError("join nao implementado")

fun engineClass(node: Node): Class<out Operation>? =
    when (node) {
        is TableNode -> IndexScan::class.java
        is ConditionNode -> EngineCondition::class.java
        is ReferenceNode -> Reference::class.java
        is FilterNode -> Filter::class.java
        is ProjectNode -> Projection::class.java
        is RemoveColumnsNode -> RemoveColumns::class.java
        is SortNode -> Sort::class.java
        is DistinctNode ->
            if (node.hashed) HashDuplicateRemoval::class.java else DuplicateRemoval::class.java
        is LimitNode -> Limit::class.java
        is AliasNode -> SourceRename::class.java
        is CollapseNode -> SourceRename1::class.java
        is ExplodeNode -> Explode::class.java
        is RowNumberNode -> AutoIncrement::class.java
        is AggNode ->
            when {
                node.by == null -> AllAggregation::class.java
                node.hashed -> HashAggregation::class.java
                else -> Aggregation::class.java
            }
        is MaterializeNode -> Materialization::class.java
        is MemoizeNode -> Memoize::class.java
        is HashIndexNode -> HashIndex::class.java
        is ScanNode -> Scan::class.java
        is JoinNode -> JOINS.get(Pair(node.type, node.algorithm))
        is CrossNode -> CrossJoin::class.java
        is SetOpNode ->
            when (node.kind) {
                SetKind.UNION -> if (node.hashed) HashUnion::class.java else Union::class.java
                SetKind.INTERSECT ->
                    if (node.hashed) HashIntersection::class.java else Intersection::class.java
                SetKind.EXCEPT ->
                    if (node.hashed) HashDifference::class.java else Difference::class.java
                SetKind.APPEND -> Append::class.java
            }
        is LogicalOpNode ->
            when (node.kind) {
                LogicalKind.AND -> LogicalAnd::class.java
                LogicalKind.OR -> LogicalOr::class.java
                LogicalKind.XOR -> LogicalXor::class.java
            }
        is ExistsNode -> Exists::class.java
    }

private fun make(type: Class<out Operation>, arguments: List<Any>): Operation {
    val parameters =
        arguments.map({ argument ->
            if (argument is Operation) Operation::class.java else argument.javaClass
        })
    return type.getConstructor(*parameters.toTypedArray()).newInstance(*arguments.toTypedArray())
}

private fun joinPredicate(on: List<JoinTerm>): JoinPredicate {
    val predicate = JoinPredicate()
    for (term in on) {
        predicate.addTerm(
            sourceOf(term.left), nameOf(term.left), sourceOf(term.right), nameOf(term.right))
    }
    return predicate
}

private fun setOperation(node: SetOpNode, inputs: List<Operation>): Operation {
    val type = classOf(node)
    inputs[0].prepareAllDataSources()
    inputs[1].prepareAllDataSources()
    val left = columnsOf(inputs[0])
    val right = columnsOf(inputs[1])
    if (left.size != right.size) {
        throw PlanError(
            "os dois lados de ${type.simpleName} precisam ter o mesmo numero de colunas " +
                "(esquerda tem ${left.size}, direita tem ${right.size})",
        )
    }
    val inherited =
        left.map({ column -> column.source }).distinct().singleOrNull() ?: type.simpleName
    return named(make(type, inputs), node.alias.ifBlank({ inherited }))
}
