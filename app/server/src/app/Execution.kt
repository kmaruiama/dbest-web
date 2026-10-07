package dbest

import java.io.ByteArrayOutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

@Serializable data class RowsPage(val rows: List<List<JsonPrimitive>>, val elapsedMs: Double)

@Serializable data class Columns(val inputs: Map<Port, List<String>>, val outer: List<String>)

fun nodeRoutes(sessions: Sessions): RoutingHttpHandler {
    val node = "/sessions/{sid}/nodes/{id}"
    return routes(
        "/sessions/{sid}/problems" bind GET to ({ request -> problemsResponse(sessions, request) }),
        "$node/schema" bind GET to ({ request -> schemaResponse(sessions, request) }),
        "$node/columns" bind GET to ({ request -> columnsResponse(sessions, request) }),
        "$node/rows" bind GET to ({ request -> rowsResponse(sessions, request) }),
    )
}

private fun problemsResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val session = workspace.editorState.session
    val found =
        sessions.engine.runWaitingIndefinitely({ problems(session, workspace.engineState.tables) })
    return respond(ListSerializer(Problem.serializer()), found)
}

fun problems(session: Session, tables: OpenTables): List<Problem> {
    val semantic = ArrayList<Problem>()
    for (root in roots(session)) {
        if (!complete(session, root)) {
            continue
        }
        val failure = validate(session, root, tables)
        if (failure != null) {
            semantic.add(Problem(root, failure))
        }
    }
    return missingInputs(session) + sourceClashes(session) + semantic
}

private fun validate(session: Session, root: NodeId, tables: OpenTables): String? =
    try {
        Cursor(planForNodeById(session, root), tables).close()
        null
    } catch (failure: RuntimeException) {
        failure.message ?: "plano invalido"
    }

private fun schemaResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val session = workspace.editorState.session
    val id = nodeId(session, request)
    val query = planForNodeById(session, id)
    val columns =
        sessions.engine.runWaitingIndefinitely({ schema(query, workspace.engineState.tables) })
    return respond(ListSerializer(SchemaColumn.serializer()), columns)
}

private fun columnsResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val session = workspace.editorState.session
    val id = nodeId(session, request)
    val offered =
        sessions.engine.runWaitingIndefinitely({
            columns(session, id, workspace.engineState.tables)
        })
    return respond(Columns.serializer(), offered)
}

fun columns(session: Session, id: NodeId, tables: OpenTables): Columns {
    val inputs = LinkedHashMap<Port, List<String>>()
    for (port in inputPorts(session.nodes.getValue(id))) {
        val upstream = inputOf(session, id, port) ?: continue
        val query = planForNodeById(session, upstream)
        val columns = schema(query, tables)
        inputs.put(port, names(columns))
    }
    return Columns(inputs, names(outerColumns(session, id, tables)))
}

private fun names(columns: List<SchemaColumn>): List<String> =
    columns.map({ column -> "${column.source}.${column.name}" }).distinct()

fun outerColumns(session: Session, id: NodeId, tables: OpenTables): List<SchemaColumn> {
    val columns = LinkedHashSet<SchemaColumn>()
    for (join in receivingJoins(session, id)) {
        val sides = lookupSides(session.nodes.getValue(join)) ?: continue
        val provider = inputOf(session, join, sides.provider)
        if (provider != null && complete(session, provider)) {
            val query = planForNodeById(session, provider)
            columns.addAll(schema(query, tables))
        }
    }
    return columns.toList()
}

private fun rowsResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val session = workspace.editorState.session
    val nodeId = nodeId(session, request)
    val planForNode = planForNodeById(session, nodeId)

    // mesma coisa do SQL, LIMIT 100 OFFSET 200
    val rawOffset = request.query("offset")
    val rawLimit = request.query("limit")
    if ((rawOffset == null) != (rawLimit == null)) {
        throw IllegalArgumentException(
            "offset e limit vem juntos: mande os dois para uma pagina, ou nenhum para o resultado inteiro")
    }

    // streamar todos, usando >> no cliente
    if (rawOffset == null || rawLimit == null) {
        val body =
            withJoinHint(
                session,
                nodeId,
                {
                    sessions.engine.stream(
                        { Cursor(planForNode, workspace.engineState.tables) },
                        { cursor -> ndjson(cursor) })
                })
        return Response(Status.OK)
            .header("Content-Type", "application/x-ndjson; charset=utf-8")
            .body(body)
    }

    val offset = intParam("offset", rawOffset)
    val limit = intParam("limit", rawLimit)
    val start = System.nanoTime()
    val rows =
        withJoinHint(
            session,
            nodeId,
            { sessions.engine.run({ workspace.engineState.page(planForNode, offset, limit) }) })
    val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
    return respond(
        RowsPage.serializer(),
        RowsPage(rows.map({ row -> row.map({ value -> cell(value) }) }), elapsedMs))
}

fun nodeId(session: Session, request: Request, name: String = "id"): NodeId {
    val raw = request.path(name) ?: throw NotFoundException("id de node ausente")
    val id = NodeId(intParam(name, raw))
    if (id !in session.nodes) {
        throw NotFoundException("O node #${id.value} nao existe")
    }
    return id
}

fun intParam(name: String, raw: String): Int =
    raw.toIntOrNull()
        ?: throw IllegalArgumentException(
            "o parametro '$name' precisa ser um inteiro, recebi '$raw'")

private const val STREAM_CHUNK_BYTES = 64 * 1024
private const val STREAM_CHUNK_MS = 100L

fun <T> withJoinHint(session: Session, id: NodeId, run: () -> T): T =
    try {
        run()
    } catch (failure: PlanError) {
        val join = receivingJoins(session, id).firstOrNull() ?: throw failure
        throw PlanError(
            "o node #${id.value} le colunas de fora dele (vindas do join #${join.value}); " +
                "rode o join para ve-lo em contexto: ${failure.message}",
            failure,
        )
    }

private fun ndjson(cursor: Cursor): ByteArray {
    val out = ByteArrayOutputStream()
    val deadline = System.nanoTime() + STREAM_CHUNK_MS * 1_000_000
    try {
        while (chunkHasRoom(out, deadline) && cursor.hasNext()) {
            val line = JsonArray(cursor.next().map({ value -> cell(value) }))
            out.write(ndjsonLine(JsonArray.serializer(), line))
        }
    } catch (failure: EngineException) {
        val line = JsonObject(mapOf("error" to JsonPrimitive(failure.message ?: "falha na engine")))
        out.write(ndjsonLine(JsonObject.serializer(), line))
        cursor.close()
    }
    return out.toByteArray()
}

private fun chunkHasRoom(out: ByteArrayOutputStream, deadline: Long): Boolean {
    val full = out.size() >= STREAM_CHUNK_BYTES
    val late = out.size() > 0 && System.nanoTime() >= deadline
    return !full && !late
}

private fun <T> ndjsonLine(serializer: KSerializer<T>, line: T): ByteArray {
    val text = wire.encodeToString(serializer, line) + "\n"
    return text.toByteArray(Charsets.UTF_8)
}

fun cell(value: Any?): JsonPrimitive =
    when (value) {
        null -> JsonNull
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else ->
            throw PlanError(
                "${value.javaClass.simpleName} nao pode ser serializado como valor de tupla")
    }

fun visibleName(session: Session, id: NodeId, tables: OpenTables): String? {
    val declared = sourceName(session.nodes.getValue(id))
    if (declared != null || !complete(session, id)) {
        return declared
    }
    return try {
        schema(planForNodeById(session, id), tables)
            .map({ column -> column.source })
            .distinct()
            .singleOrNull()
    } catch (failure: EngineException) {
        null
    }
}
