package dbest

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun renaming(session: Session, id: NodeId, alias: String, old: String?): Command {
    val node = session.nodes.getValue(id)
    require(aliasOf(node) != null, { "O node #${id.value} nao da nome a uma fonte" })
    require(alias.isNotBlank(), { "O alias nao pode ser vazio" })
    val renamed = LinkedHashMap<NodeId, Node>()
    renamed.put(id, withAlias(node, alias))
    if (old != null && old != alias) {
        renameReaders(session, id, old, alias, renamed)
    }
    val commands =
        renamed.entries
            .filter({ entry -> entry.value != session.nodes.getValue(entry.key) })
            .map({ entry -> SetNode(entry.key, entry.value) })
    return Batch(commands)
}

private fun withAlias(node: Node, alias: String): Node {
    val fields = wire.encodeToJsonElement(Node.serializer(), node).jsonObject
    val renamed = JsonObject(fields + ("alias" to JsonPrimitive(alias)))
    return wire.decodeFromJsonElement(Node.serializer(), renamed)
}

private fun renameReaders(
    session: Session,
    id: NodeId,
    old: String,
    alias: String,
    renamed: MutableMap<NodeId, Node>
) {
    val bySource = { ref: String -> if (sourceOf(ref) == old) "$alias.${nameOf(ref)}" else ref }
    for (reader in readers(session, id)) {
        val current = renamed.get(reader) ?: session.nodes.getValue(reader)
        renamed.put(reader, renameColumns(current, bySource))
        val reference = session.nodes.getValue(reader)
        if (reference is ReferenceNode) {
            renameReferenceReaders(session, reader, reference, old, alias, renamed)
        }
    }
}

private fun renameColumns(node: Node, rename: (String) -> String): Node {
    val fields = wire.encodeToJsonElement(Node.serializer(), node).jsonObject
    val specs = fieldsFor(kindOf(node))
    val renamed = renamedFields(specs, fields, rename)
    return wire.decodeFromJsonElement(Node.serializer(), renamed)
}

private fun renamedFields(
    specs: List<FieldSpec>,
    fields: JsonObject,
    rename: (String) -> String
): JsonObject {
    val renamed = LinkedHashMap<String, JsonElement>()
    for ((at, value) in fields) {
        val spec = specs.firstOrNull({ field -> field.at == at })
        renamed.put(at, if (spec == null) value else renamedValue(spec, value, rename))
    }
    return JsonObject(renamed)
}

private fun renamedValue(
    spec: FieldSpec,
    value: JsonElement,
    rename: (String) -> String
): JsonElement =
    when {
        value is JsonNull -> value
        spec.widget == Widget.COLUMN -> JsonPrimitive(rename(value.jsonPrimitive.content))
        spec.widget == Widget.LIST && spec.item == Widget.COLUMN ->
            JsonArray(
                value.jsonArray.map({ item -> JsonPrimitive(rename(item.jsonPrimitive.content)) }))
        spec.widget == Widget.ROWS ->
            JsonArray(
                value.jsonArray.map({ row -> renamedFields(spec.of, row.jsonObject, rename) }))
        spec.widget == Widget.CONDITION -> {
            val condition = wire.decodeFromJsonElement(Condition.serializer(), value)
            wire.encodeToJsonElement(Condition.serializer(), renamedCondition(condition, rename))
        }
        else -> value
    }

private fun renamedCondition(condition: Condition, rename: (String) -> String): Condition =
    when (condition) {
        is Comparison -> {
            val right = condition.right
            Comparison(
                rename(condition.left),
                condition.op,
                if (right is Ref) Ref(rename(right.column)) else right)
        }
        is IsNull -> IsNull(rename(condition.column))
        is IsNotNull -> IsNotNull(rename(condition.column))
        is And -> And(condition.conditions.map({ inner -> renamedCondition(inner, rename) }))
        is Or -> Or(condition.conditions.map({ inner -> renamedCondition(inner, rename) }))
    }

private fun renameReferenceReaders(
    session: Session,
    id: NodeId,
    reference: ReferenceNode,
    old: String,
    alias: String,
    renamed: MutableMap<NodeId, Node>
) {
    val outputs =
        reference.columns
            .filter({ column -> sourceOf(column) == old })
            .associate({ column ->
                val name = nameOf(column)
                val before = "${reference.alias}.${old}_$name"
                val after = "${reference.alias}.${alias}_$name"
                Pair(before, after)
            })
    for (reader in readers(session, id)) {
        val current = renamed.get(reader) ?: session.nodes.getValue(reader)
        val rewritten = renameColumns(current, { ref -> outputs.get(ref) ?: ref })
        renamed.put(reader, rewritten)
    }
}
