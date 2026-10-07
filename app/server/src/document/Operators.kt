package dbest

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Widget {
    @SerialName("text") TEXT,
    @SerialName("int") INT,
    @SerialName("flag") FLAG,
    @SerialName("column") COLUMN,
    @SerialName("pick") PICK,
    @SerialName("condition") CONDITION,
    @SerialName("list") LIST,
    @SerialName("rows") ROWS,
}

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class Input(val widget: Widget)

const val PLACEHOLDER: String = "?"

fun sourceOf(ref: String): String? {
    val dot = ref.indexOf('.')
    return if (dot < 0) null else ref.substring(0, dot)
}

fun nameOf(ref: String): String {
    val dot = ref.indexOf('.')
    return if (dot < 0) ref else ref.substring(dot + 1)
}

private fun requireQualified(ref: String) {
    val source = sourceOf(ref)
    require(
        source != null && source.isNotBlank() && nameOf(ref).isNotBlank(),
        { "'$ref' precisa ser qualificada com sua fonte, ex: \"o.$ref\"" })
}

data class Ref(val column: String) {
    init {
        require(column.isNotBlank(), { "A coluna de referencia nao pode ser vazia" })
    }
}

enum class CompareOp {
    EQ,
    NEQ,
    GT,
    GTE,
    LT,
    LTE
}

@Serializable sealed interface Condition

@Serializable
@SerialName("cmp")
data class Comparison(
    val left: String,
    val op: CompareOp,
    @Serializable(with = LiteralSerializer::class) val right: Any,
) : Condition {
    init {
        require(left.isNotBlank(), { "O nome da coluna nao pode ser vazio" })
    }
}

@Serializable @SerialName("isNull") data class IsNull(val column: String) : Condition

@Serializable @SerialName("isNotNull") data class IsNotNull(val column: String) : Condition

@Serializable
@SerialName("and")
data class And(val conditions: List<Condition>) : Condition {
    init {
        require(conditions.size >= 2, { "and() precisa de pelo menos duas condicoes" })
    }
}

@Serializable
@SerialName("or")
data class Or(val conditions: List<Condition>) : Condition {
    init {
        require(conditions.size >= 2, { "or() precisa de pelo menos duas condicoes" })
    }
}

@Serializable data class SortKey(@Input(Widget.COLUMN) val column: String, val ascending: Boolean)

enum class AggFunction {
    MAX,
    MIN,
    COUNT,
    AVG,
    SUM,
    FIRST,
    LAST,
    COUNT_ALL,
    COUNT_NULL
}

@Serializable data class Agg(val function: AggFunction, @Input(Widget.COLUMN) val column: String)

@Serializable
data class JoinTerm(
    @Input(Widget.COLUMN) val left: String,
    @Input(Widget.COLUMN) val right: String
) {
    init {
        requireQualified(left)
        requireQualified(right)
    }
}

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL,
    LEFT_SEMI,
    RIGHT_SEMI,
    LEFT_ANTI,
    RIGHT_ANTI
}

enum class JoinAlgorithm {
    NESTED_LOOP,
    HASH,
    MERGE
}

enum class SetKind {
    UNION,
    INTERSECT,
    EXCEPT,
    APPEND
}

enum class LogicalKind {
    AND,
    OR,
    XOR
}

@Serializable @JvmInline value class TableId(val value: Int)

@Serializable sealed interface Node

sealed interface SourceNode : Node

sealed interface UnaryNode : Node

sealed interface BinaryNode : Node

@Serializable
@SerialName("table")
data class TableNode(val table: TableId, val alias: String) : SourceNode {
    init {
        require(alias.isNotBlank(), { "O alias nao pode ser uma string vazia" })
    }
}

@Serializable
@SerialName("condition")
data class ConditionNode(val condition: Condition, val alias: String = "condition") : SourceNode {
    init {
        require(alias.isNotBlank(), { "Condition precisa de um alias para sua saida" })
    }
}

@Serializable
@SerialName("reference")
data class ReferenceNode(
    @Input(Widget.COLUMN) val columns: List<String>,
    val alias: String = "ref"
) : SourceNode {
    init {
        require(columns.isNotEmpty(), { "Reference precisa de pelo menos uma coluna" })
        for (column in columns) {
            requireQualified(column)
        }
        require(alias.isNotBlank(), { "Reference precisa de um alias para sua saida" })
    }
}

@Serializable @SerialName("filter") data class FilterNode(val condition: Condition) : UnaryNode

@Serializable
@SerialName("project")
data class ProjectNode(@Input(Widget.COLUMN) val columns: List<String>) : UnaryNode {
    init {
        require(
            columns.isNotEmpty(), { "Eh necessario selecionar no minimo uma coluna para projecao" })
    }
}

@Serializable
@SerialName("sort")
data class SortNode(val keys: List<SortKey>) : UnaryNode {
    init {
        require(keys.isNotEmpty(), { "Sort precisa de pelo menos um parametro" })
        val ascending = keys[0].ascending
        require(
            keys.all({ key -> key.ascending == ascending }),
            { "O mecanismo atual aceita uma unica direcao para todas as colunas de ordenacao" })
    }
}

@Serializable
@SerialName("distinct")
data class DistinctNode(val hashed: Boolean = true) : UnaryNode

@Serializable
@SerialName("limit")
data class LimitNode(val count: Int, val offset: Int = 0) : UnaryNode {
    init {
        require(count > 0, { "Limit precisa ser positivo" })
        require(offset >= 0, { "Offset nao pode ser negativo" })
    }
}

@Serializable
@SerialName("alias")
data class AliasNode(@Input(Widget.COLUMN) val from: String, val to: String) : UnaryNode {
    init {
        require(
            from.isNotBlank() && to.isNotBlank(),
            { "Alias precisa do alias atual e do novo alias da fonte" })
    }
}

@Serializable
@SerialName("collapse")
data class CollapseNode(val alias: String) : UnaryNode {
    init {
        require(alias.isNotBlank(), { "Collapse precisa de um alias para a fonte unificada" })
    }
}

@Serializable
@SerialName("explode")
data class ExplodeNode(@Input(Widget.COLUMN) val column: String, val delimiter: String = ",") :
    UnaryNode {
    init {
        require(column.isNotBlank(), { "Explode precisa de uma coluna" })
        require(delimiter.isNotEmpty(), { "Explode precisa de um delimitador" })
    }
}

@Serializable
@SerialName("rowNumber")
data class RowNumberNode(val alias: String, val column: String, val start: Int = 1) : UnaryNode {
    init {
        require(alias.isNotBlank(), { "RowNumber precisa de um alias de fonte para sua saida" })
        require(column.isNotBlank(), { "RowNumber precisa do nome de uma coluna" })
    }
}

@Serializable
@SerialName("agg")
data class AggNode(
    val alias: String,
    @Input(Widget.COLUMN) val by: String? = null,
    val aggregates: List<Agg>,
    val hashed: Boolean = true,
) : UnaryNode {
    init {
        require(alias.isNotBlank(), { "Agregacao precisa de um alias de fonte para sua saida" })
        require(aggregates.isNotEmpty(), { "Agregacao precisa de pelo menos um agregado" })
        if (by != null) {
            requireQualified(by)
        }
    }
}

@Serializable
@SerialName("removeColumns")
data class RemoveColumnsNode(
    @Input(Widget.COLUMN) val columns: List<String>,
    val alias: String = "Projection"
) : UnaryNode {
    init {
        require(columns.isNotEmpty(), { "RemoveColumns precisa de pelo menos uma coluna" })
        require(alias.isNotBlank(), { "RemoveColumns precisa de um alias para sua saida" })
    }
}

@Serializable @SerialName("materialize") data object MaterializeNode : UnaryNode

@Serializable @SerialName("memoize") data object MemoizeNode : UnaryNode

@Serializable @SerialName("hashIndex") data object HashIndexNode : UnaryNode

@Serializable @SerialName("scan") data object ScanNode : UnaryNode

@Serializable
@SerialName("join")
data class JoinNode(
    val type: JoinType = JoinType.INNER,
    val algorithm: JoinAlgorithm = JoinAlgorithm.NESTED_LOOP,
    val on: List<JoinTerm>,
) : BinaryNode {
    init {
        require(
            on.isNotEmpty() || algorithm == JoinAlgorithm.NESTED_LOOP,
            { "Join precisa de pelo menos um termo de igualdade" })
    }
}

@Serializable @SerialName("cross") data object CrossNode : BinaryNode

@Serializable
@SerialName("setOp")
data class SetOpNode(val kind: SetKind, val hashed: Boolean = true, val alias: String = "") :
    BinaryNode

@Serializable
@SerialName("logicalOp")
data class LogicalOpNode(val kind: LogicalKind, val alias: String = "condition") : BinaryNode {
    init {
        require(alias.isNotBlank(), { "O operador logico precisa de um alias para sua saida" })
    }
}

@Serializable
@SerialName("exists")
data class ExistsNode(val bilateral: Boolean = false) : BinaryNode

fun aliasOf(node: Node): String? =
    when (node) {
        is TableNode -> node.alias
        is ConditionNode -> node.alias
        is ReferenceNode -> node.alias
        is CollapseNode -> node.alias
        is RowNumberNode -> node.alias
        is AggNode -> node.alias
        is RemoveColumnsNode -> node.alias
        is LogicalOpNode -> node.alias
        is SetOpNode ->
            if (node.kind == SetKind.UNION || node.kind == SetKind.APPEND) node.alias else null
        is FilterNode,
        is ProjectNode,
        is SortNode,
        is DistinctNode,
        is LimitNode,
        is AliasNode,
        is ExplodeNode,
        is MaterializeNode,
        is MemoizeNode,
        is HashIndexNode,
        is ScanNode,
        is JoinNode,
        is CrossNode,
        is ExistsNode -> null
    }
