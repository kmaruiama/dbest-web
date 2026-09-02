package dbest.features.canvas

import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.json
import dbest.features.canvas.query.OpenTables
import dbest.features.canvas.query.execute
import dbest.features.canvas.query.exists
import dbest.features.canvas.query.json
import dbest.features.canvas.query.plan
import dbest.features.canvas.query.problems
import dbest.features.canvas.query.roots
import dbest.features.canvas.query.schema
import dbest.features.sessions.Sessions
import dbest.features.sessions.Workspace
import dbest.features.sessions.workspaceOf
import dbest.kernel.adapter.compactRowJson
import dbest.kernel.adapter.json
import dbest.kernel.http.NotFoundException
import dbest.kernel.http.jsonResponse
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.util.asInputStream
import dbest.kernel.util.mapCollection
import kotlinx.serialization.json.JsonArray
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

fun nodeRoutes(sessions: Sessions): RoutingHttpHandler = routes(
    "/sessions/{sid}/roots" bind GET to ({ request ->
        val session = currentSession(workspaceOf(sessions, request))
        jsonResponse(Status.OK, JsonArray(mapCollection(roots(session), { nodeId -> json(nodeId) })))
    }),
    "/sessions/{sid}/problems" bind GET to ({ request ->
        val workspace = workspaceOf(sessions, request)
        val session = currentSession(workspace)
        val found = sessions.engine.runWaiting({ problems(session, workspace.tables) })
        jsonResponse(Status.OK, JsonArray(mapCollection(found, { problem -> json(problem) })))
    }),
    "/sessions/{sid}/nodes/{id}/rows" bind GET to ({ request ->
        val workspace = workspaceOf(sessions, request)
        rowsResponse(sessions, workspace, request)
    }),
    "/sessions/{sid}/nodes/{id}/schema" bind GET to ({ request ->
        val workspace = workspaceOf(sessions, request)
        val session = currentSession(workspace)
        val id = nodeId(session, request)
        val columns = sessions.engine.runWaiting({ schema(session, id, workspace.tables) })
        jsonResponse(Status.OK, JsonArray(mapCollection(columns, { column -> json(column) })))
    }),
    "/sessions/{sid}/nodes/{id}/exists" bind GET to ({ request ->
        val workspace = workspaceOf(sessions, request)
        val session = currentSession(workspace)
        val id = nodeId(session, request)
        val found = sessions.engine.runWaiting({ exists(session, id, workspace.tables) })
        jsonResponse(Status.OK, obj("exists" to json(found)))
    }),
)

fun nodeId(session: Session, request: Request): NodeId {
    val raw = request.path("id") ?: throw NotFoundException("id de node ausente")
    val value = raw.toIntOrNull() ?: throw IllegalArgumentException("id de node invalido: '$raw'")
    val id = NodeId(value)
    if (id !in session.nodes) {
        throw NotFoundException("O node #$value nao existe")
    }
    return id
}

fun intParam(name: String, raw: String): Int =
    raw.toIntOrNull() ?: throw IllegalArgumentException("o parametro '$name' precisa ser um inteiro, recebi '$raw'")

private fun currentSession(workspace: Workspace): Session = workspace.canvas.get().history.session

private fun rowsResponse(sessions: Sessions, workspace: Workspace, request: Request): Response {
    val session = currentSession(workspace)
    val id = nodeId(session, request)
    val offset = request.query("offset")
    val limit = request.query("limit")
    return if (offset != null && limit != null) {
        sessions.engine.run({
            pagedRowsResponse(session, id, workspace.tables, intParam("offset", offset), intParam("limit", limit))
        })
    } else {
        streamingRowsResponse(sessions, session, id, workspace.tables)
    }
}

private fun pagedRowsResponse(session: Session, id: NodeId, tables: OpenTables, offset: Int, limit: Int): Response {
    val schemaColumns = schema(session, id, tables)
    val start = System.nanoTime()
    val rows = execute(session, id, tables, offset, limit)
    val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
    val body = JsonArray(mapCollection(rows, { row -> compactRowJson(row, schemaColumns) }))
    return jsonResponse(Status.OK, obj("rows" to body, "elapsedMs" to json(elapsedMs)))
}

private fun streamingRowsResponse(sessions: Sessions, session: Session, id: NodeId, tables: OpenTables): Response {
    val stream = sessions.engine.stream({ sink, cancelled ->
        for (line in ndjsonLines(plan(session, id, tables))) {
            if (cancelled()) {
                break
            }
            sink(line)
        }
    })
    return Response(Status.OK)
        .header("Content-Type", "application/x-ndjson; charset=utf-8")
        .body(stream.lines().asInputStream(onClose = stream::close))
}
