package dbest

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.jsonObject
import org.http4k.core.Method.GET
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

@Serializable
enum class OperatorCategory {
    @SerialName("Algebra") ALGEBRA,
    @SerialName("Aggregation") AGGREGATION,
    @SerialName("ETL") ETL,
    @SerialName("Index") INDEX,
    @SerialName("Joins") JOINS,
    @SerialName("SemiAntiJoins") SEMI_ANTI_JOINS,
    @SerialName("Sets") SETS,
    @SerialName("Boolean") BOOLEAN,
}

@Serializable
enum class Arity {
    @SerialName("source") SOURCE,
    @SerialName("unary") UNARY,
    @SerialName("binary") BINARY,
}

@Serializable
data class FieldSpec(
    val at: String,
    val widget: Widget,
    val options: List<String> = emptyList(),
    val item: Widget? = null,
    val of: List<FieldSpec> = emptyList(),
    val nullable: Boolean = false,
)

@Serializable
data class OperatorType(
    val arity: Arity,
    val editable: Boolean,
    val fields: List<FieldSpec> = emptyList(),
    val variants: List<String> = emptyList()
)

@Serializable
data class Chip(
    val key: String,
    val type: String,
    val symbol: String,
    val category: OperatorCategory,
    val template: Node
)

@Serializable
data class Catalog(
    val categories: List<OperatorCategory>,
    val types: Map<String, OperatorType>,
    val operators: List<Chip>,
    val placeholder: String = PLACEHOLDER,
)

@Serializable
data class Caption(val engineClass: String?, val expression: String, val alias: String?)

val CHIPS: List<Chip> =
    listOf(
        chip(
            "filter",
            "σ",
            OperatorCategory.ALGEBRA,
            FilterNode(Comparison(PLACEHOLDER, CompareOp.EQ, PLACEHOLDER))),
        chip("projection", "π", OperatorCategory.ALGEBRA, ProjectNode(listOf(PLACEHOLDER))),
        chip(
            "selectColumns", "S", OperatorCategory.ALGEBRA, RemoveColumnsNode(listOf(PLACEHOLDER))),
        chip("rename", "ρ", OperatorCategory.ALGEBRA, AliasNode(PLACEHOLDER, PLACEHOLDER)),
        chip("sort", "↕", OperatorCategory.ALGEBRA, SortNode(listOf(SortKey(PLACEHOLDER, true)))),
        chip("limit", "L", OperatorCategory.ALGEBRA, LimitNode(10)),
        chip("duplicateRemoval", "Δ", OperatorCategory.ALGEBRA, DistinctNode(false)),
        chip("hashDuplicateRemoval", "#Δ", OperatorCategory.ALGEBRA, DistinctNode(true)),
        chip(
            "aggregation",
            "∑",
            OperatorCategory.AGGREGATION,
            AggNode("agg", null, listOf(Agg(AggFunction.COUNT, PLACEHOLDER)))),
        chip("collapse", "{}", OperatorCategory.AGGREGATION, CollapseNode("collapse")),
        chip("explode", "E", OperatorCategory.ETL, ExplodeNode(PLACEHOLDER)),
        chip("autoInc", "A", OperatorCategory.ETL, RowNumberNode("row", PLACEHOLDER)),
        chip("hash", "#", OperatorCategory.INDEX, HashIndexNode),
        chip("memoize", "ℳ", OperatorCategory.INDEX, MemoizeNode),
        chip("materialization", "⧉", OperatorCategory.INDEX, MaterializeNode),
        chip("scan", "→", OperatorCategory.INDEX, ScanNode),
        chip(
            "reference",
            "R",
            OperatorCategory.INDEX,
            ReferenceNode(listOf("$PLACEHOLDER.$PLACEHOLDER"))),
        chip(
            "join", "|X|", OperatorCategory.JOINS, join(JoinType.INNER, JoinAlgorithm.NESTED_LOOP)),
        chip(
            "mergeJoin", "↕|X|", OperatorCategory.JOINS, join(JoinType.INNER, JoinAlgorithm.MERGE)),
        chip("hashJoin", "#|X|", OperatorCategory.JOINS, join(JoinType.INNER, JoinAlgorithm.HASH)),
        chip(
            "leftOuterJoin",
            "⟕",
            OperatorCategory.JOINS,
            join(JoinType.LEFT, JoinAlgorithm.NESTED_LOOP)),
        chip(
            "rightOuterJoin",
            "⟖",
            OperatorCategory.JOINS,
            join(JoinType.RIGHT, JoinAlgorithm.NESTED_LOOP)),
        chip(
            "mergeLeftOuterJoin",
            "↕⟕",
            OperatorCategory.JOINS,
            join(JoinType.LEFT, JoinAlgorithm.MERGE)),
        chip(
            "mergeRightOuterJoin",
            "↕⟖",
            OperatorCategory.JOINS,
            join(JoinType.RIGHT, JoinAlgorithm.MERGE)),
        chip(
            "mergeFullOuterJoin",
            "↕⟗",
            OperatorCategory.JOINS,
            join(JoinType.FULL, JoinAlgorithm.MERGE)),
        chip(
            "hashLeftOuterJoin",
            "#⟕",
            OperatorCategory.JOINS,
            join(JoinType.LEFT, JoinAlgorithm.HASH)),
        chip(
            "hashRightOuterJoin",
            "#⟖",
            OperatorCategory.JOINS,
            join(JoinType.RIGHT, JoinAlgorithm.HASH)),
        chip(
            "hashFullOuterJoin",
            "#⟗",
            OperatorCategory.JOINS,
            join(JoinType.FULL, JoinAlgorithm.HASH)),
        chip("cartesianProduct", "✕", OperatorCategory.JOINS, CrossNode),
        chip(
            "semiJoin",
            "⋉",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP)),
        chip(
            "mergeLeftSemiJoin",
            "↕⋉",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.LEFT_SEMI, JoinAlgorithm.MERGE)),
        chip(
            "mergeRightSemiJoin",
            "↕⋊",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.RIGHT_SEMI, JoinAlgorithm.MERGE)),
        chip(
            "hashLeftSemiJoin",
            "#⋉",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.LEFT_SEMI, JoinAlgorithm.HASH)),
        chip(
            "hashRightSemiJoin",
            "#⋊",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.RIGHT_SEMI, JoinAlgorithm.HASH)),
        chip(
            "antiJoin",
            "▷",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.LEFT_ANTI, JoinAlgorithm.NESTED_LOOP)),
        chip(
            "mergeLeftAntiJoin",
            "↕▷",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.LEFT_ANTI, JoinAlgorithm.MERGE)),
        chip(
            "mergeRightAntiJoin",
            "↕◁",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.RIGHT_ANTI, JoinAlgorithm.MERGE)),
        chip(
            "hashLeftAntiJoin",
            "#▷",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.LEFT_ANTI, JoinAlgorithm.HASH)),
        chip(
            "hashRightAntiJoin",
            "#◁",
            OperatorCategory.SEMI_ANTI_JOINS,
            join(JoinType.RIGHT_ANTI, JoinAlgorithm.HASH)),
        chip("append", "+", OperatorCategory.SETS, SetOpNode(SetKind.APPEND, false)),
        chip("union", "∪", OperatorCategory.SETS, SetOpNode(SetKind.UNION, false)),
        chip("hashUnion", "#∪", OperatorCategory.SETS, SetOpNode(SetKind.UNION, true)),
        chip("intersection", "∩", OperatorCategory.SETS, SetOpNode(SetKind.INTERSECT, false)),
        chip("hashIntersection", "#∩", OperatorCategory.SETS, SetOpNode(SetKind.INTERSECT, true)),
        chip("difference", "-", OperatorCategory.SETS, SetOpNode(SetKind.EXCEPT, false)),
        chip("hashDifference", "#-", OperatorCategory.SETS, SetOpNode(SetKind.EXCEPT, true)),
        chip("unilateralExistence", "∃⟗", OperatorCategory.SETS, ExistsNode(false)),
        chip("bilateralExistence", "∃⨝", OperatorCategory.SETS, ExistsNode(true)),
        chip("logicalAnd", "∧", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.AND)),
        chip("logicalOr", "∨", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.OR)),
        chip("logicalXor", "⊻", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.XOR)),
        chip(
            "condition",
            "?",
            OperatorCategory.BOOLEAN,
            ConditionNode(Comparison(PLACEHOLDER, CompareOp.EQ, PLACEHOLDER))),
    )

val CATALOG: Catalog = Catalog(OperatorCategory.entries, operatorTypes(), CHIPS)

fun catalogRoutes(): RoutingHttpHandler =
    routes(
        "/operators" bind GET to ({ respond(Catalog.serializer(), CATALOG) }),
    )

fun captions(session: Session): Map<NodeId, Caption> =
    session.nodes.mapValues({ entry ->
        Caption(
            engineClass(entry.value)?.simpleName,
            expression(session, entry.value),
            aliasOf(entry.value))
    })

fun expression(session: Session, node: Node): String =
    when (node) {
        is TableNode -> session.tables.get(node.table)?.name ?: ""
        is FilterNode -> conditionText(node.condition)
        is ConditionNode -> conditionText(node.condition)
        is ReferenceNode -> node.columns.joinToString(", ")
        is ProjectNode -> node.columns.joinToString(", ")
        is SortNode -> node.keys.map({ key -> sortKeyText(key) }).joinToString(", ")
        is LimitNode ->
            if (node.offset == 0) node.count.toString() else "${node.count}, ${node.offset}"
        is AliasNode -> "${node.from} → ${node.to}"
        is ExplodeNode -> node.column
        is RowNumberNode -> "${node.column} from ${node.start}"
        is AggNode -> aggregationText(node)
        is RemoveColumnsNode -> node.columns.joinToString(", ")
        is JoinNode -> node.on.map({ term -> joinTermText(term) }).joinToString(", ")
        is SetOpNode -> node.kind.name
        is LogicalOpNode -> node.kind.name
        is CollapseNode,
        is DistinctNode,
        is MaterializeNode,
        is MemoizeNode,
        is HashIndexNode,
        is ScanNode,
        is CrossNode,
        is ExistsNode -> ""
    }

private fun sortKeyText(key: SortKey): String {
    val direction = if (key.ascending) "ASC" else "DESC"
    return "${key.column} $direction"
}

private fun aggregationText(node: AggNode): String {
    val aggregates = node.aggregates.map({ aggregate -> aggregateText(aggregate) })
    val listed = aggregates.joinToString(", ")
    return if (node.by == null) listed else "${node.by}: $listed"
}

private fun aggregateText(aggregate: Agg): String {
    val function = aggregate.function.name.lowercase()
    return "$function(${aggregate.column})"
}

private fun joinTermText(term: JoinTerm): String = "${term.left} = ${term.right}"

private fun chip(key: String, symbol: String, category: OperatorCategory, template: Node): Chip =
    Chip(key, kindOf(template), symbol, category, template)

private fun join(type: JoinType, algorithm: JoinAlgorithm): Node =
    JoinNode(
        type, algorithm, listOf(JoinTerm("$PLACEHOLDER.$PLACEHOLDER", "$PLACEHOLDER.$PLACEHOLDER")))

private fun operatorTypes(): Map<String, OperatorType> {
    val types = LinkedHashMap<String, OperatorType>()
    for (chip in CHIPS) {
        if (chip.type in types) {
            continue
        }
        val fields = fieldsFor(chip.type).filter({ field -> field.at != "alias" })
        types.put(
            chip.type,
            OperatorType(
                arityOf(chip.template), fields.isNotEmpty(), fields, variantsOf(chip.type, fields)))
    }
    return types
}

private fun arityOf(node: Node): Arity =
    when (node) {
        is SourceNode -> Arity.SOURCE
        is UnaryNode -> Arity.UNARY
        is BinaryNode -> Arity.BINARY
    }

fun fieldsFor(kind: String): List<FieldSpec> {
    val descriptor = descriptorOf(kind)
    return fieldsOf(descriptor)
}

@OptIn(ExperimentalSerializationApi::class)
private fun descriptorOf(kind: String): SerialDescriptor {
    val subclasses = Node.serializer().descriptor.getElementDescriptor(1)
    return subclasses.getElementDescriptor(subclasses.getElementIndex(kind))
}

@OptIn(ExperimentalSerializationApi::class)
private fun fieldsOf(descriptor: SerialDescriptor): List<FieldSpec> =
    (0 until descriptor.elementsCount).map({ index -> fieldOf(descriptor, index) })

@OptIn(ExperimentalSerializationApi::class)
private fun fieldOf(parent: SerialDescriptor, index: Int): FieldSpec {
    val at = parent.getElementName(index)
    val element = parent.getElementDescriptor(index)
    val declared =
        parent.getElementAnnotations(index).filterIsInstance<Input>().firstOrNull()?.widget
    if (element.kind == StructureKind.LIST) {
        val item = element.getElementDescriptor(0)
        return if (item.kind is PrimitiveKind) {
            FieldSpec(at, Widget.LIST, item = declared ?: Widget.TEXT)
        } else {
            FieldSpec(at, Widget.ROWS, of = fieldsOf(item))
        }
    }
    val widget =
        declared
            ?: when (element.kind) {
                SerialKind.ENUM -> Widget.PICK
                PolymorphicKind.SEALED -> Widget.CONDITION
                PrimitiveKind.INT -> Widget.INT
                PrimitiveKind.BOOLEAN -> Widget.FLAG
                else -> Widget.TEXT
            }
    val options =
        when (element.kind) {
            SerialKind.ENUM -> element.elementNames.toList()
            PolymorphicKind.SEALED -> CompareOp.entries.map({ op -> op.name })
            else -> emptyList()
        }
    return FieldSpec(at, widget, options = options, nullable = element.isNullable)
}

private fun variantsOf(kind: String, fields: List<FieldSpec>): List<String> {
    val templates =
        CHIPS.filter({ chip -> chip.type == kind })
            .map({ chip -> wire.encodeToJsonElement(Node.serializer(), chip.template).jsonObject })
    return fields
        .map({ field -> field.at })
        .filter({ at -> templates.map({ template -> template.get(at) }).distinct().size > 1 })
}

private fun conditionText(condition: Condition): String =
    when (condition) {
        is Comparison ->
            "${condition.left} ${operatorText(condition.op)} ${literalText(condition.right)}"
        is IsNull -> "${condition.column} IS NULL"
        is IsNotNull -> "${condition.column} IS NOT NULL"
        is And ->
            condition.conditions.joinToString(" AND ", transform = { inner -> nestedText(inner) })
        is Or ->
            condition.conditions.joinToString(" OR ", transform = { inner -> nestedText(inner) })
    }

private fun nestedText(condition: Condition): String =
    if (condition is And || condition is Or) "(${conditionText(condition)})"
    else conditionText(condition)

private fun operatorText(op: CompareOp): String =
    when (op) {
        CompareOp.EQ -> "="
        CompareOp.NEQ -> "≠"
        CompareOp.GT -> ">"
        CompareOp.GTE -> "≥"
        CompareOp.LT -> "<"
        CompareOp.LTE -> "≤"
    }

private fun literalText(value: Any): String =
    when (value) {
        is Ref -> value.column
        is String -> "'$value'"
        else -> value.toString()
    }
