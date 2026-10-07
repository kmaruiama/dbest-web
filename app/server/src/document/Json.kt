package dbest

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

val wire: Json =
    Json(
        builderAction = {
            classDiscriminator = "@type"
            encodeDefaults = true
            ignoreUnknownKeys = true
        })

val files: Json = Json(from = wire, builderAction = { prettyPrint = true })

object LiteralSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("dbest.Literal")

    override fun serialize(encoder: Encoder, value: Any) {
        val tagged =
            when (value) {
                is Int -> tag("int", JsonPrimitive(value))
                is Long -> tag("long", JsonPrimitive(value))
                is Float -> tag("float", JsonPrimitive(value))
                is Double -> tag("double", JsonPrimitive(value))
                is Boolean -> tag("bool", JsonPrimitive(value))
                is String -> tag("str", JsonPrimitive(value))
                is Ref -> tag("ref", JsonPrimitive(value.column))
                else ->
                    throw SerializationException(
                        "${value.javaClass.simpleName} nao pode ser serializado como literal")
            }
        (encoder as JsonEncoder).encodeJsonElement(tagged)
    }

    override fun deserialize(decoder: Decoder): Any {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val entry = (element as? JsonObject)?.entries?.singleOrNull()
        val value = entry?.value as? JsonPrimitive
        if (entry == null || value == null) {
            throw SerializationException("literal malformado: $element")
        }
        val literal: Any? =
            when (entry.key) {
                "int" -> value.intOrNull
                "long" -> value.content.toLongOrNull()
                "float" -> value.content.toFloatOrNull()
                "double" -> value.doubleOrNull
                "bool" -> value.booleanOrNull
                "str" -> value.content
                "ref" -> Ref(value.content)
                else -> throw SerializationException("tag de literal desconhecida '${entry.key}'")
            }
        return literal ?: throw SerializationException("literal malformado: $element")
    }

    private fun tag(name: String, value: JsonElement): JsonElement =
        JsonObject(mapOf(name to value))
}

fun migrate(version: Int, element: JsonElement): JsonElement =
    when (element) {
        is JsonArray -> JsonArray(element.map({ child -> migrate(version, child) }))
        is JsonObject -> {
            val children = element.mapValues({ entry -> migrate(version, entry.value) })
            migrateObject(version, JsonObject(children))
        }
        else -> element
    }

private fun migrateObject(version: Int, fields: JsonObject): JsonObject {
    val type = (fields.get("@type") as? JsonPrimitive)?.content
    if (version == 1 && type == "scan" && "table" in fields) {
        return JsonObject(fields + ("@type" to JsonPrimitive("table")))
    }
    return when (type) {
        "cmp" -> JsonObject(fields + ("left" to refText(fields.getValue("left"))))
        "isNull",
        "isNotNull" -> JsonObject(fields + ("column" to refText(fields.getValue("column"))))
        "join" -> joinTerms(fields)
        "agg" ->
            if ("by" in fields) JsonObject(fields + ("by" to refText(fields.getValue("by"))))
            else fields
        else -> fields
    }
}

private fun joinTerms(fields: JsonObject): JsonObject {
    val terms = (fields.getValue("on") as JsonArray).map({ term -> joinTerm(term) })
    val algorithm =
        (fields.get("algorithm") as? JsonPrimitive)?.content ?: JoinAlgorithm.NESTED_LOOP.name
    if (terms.isNotEmpty() || algorithm == JoinAlgorithm.NESTED_LOOP.name) {
        return JsonObject(fields + ("on" to JsonArray(terms)))
    }
    val placeholder = JsonPrimitive("$PLACEHOLDER.$PLACEHOLDER")
    val term = JsonObject(mapOf("left" to placeholder, "right" to placeholder))
    val requiredTerms = JsonArray(listOf(term))
    return JsonObject(fields + ("on" to requiredTerms))
}

private fun joinTerm(term: JsonElement): JsonElement {
    val sides = term as JsonObject
    return JsonObject(
        sides +
            mapOf(
                "left" to refText(sides.getValue("left")),
                "right" to refText(sides.getValue("right"))))
}

private fun refText(ref: JsonElement): JsonElement {
    if (ref !is JsonObject) {
        return ref
    }
    val source = ref.get("source")
    val name = ref.get("name") ?: ref.getValue("column")
    val column = (name as JsonPrimitive).content
    return if (source == null || source is JsonNull) JsonPrimitive(column)
    else JsonPrimitive("${(source as JsonPrimitive).content}.$column")
}
