package dbest.features.sessions

import dbest.features.config.requireDir
import dbest.kernel.dialogs.pickSaveFile
import dbest.kernel.http.NotFoundException
import dbest.kernel.http.jsonResponse
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.parsedJson
import dbest.kernel.json.string
import dbest.kernel.util.existsInCollection
import dbest.kernel.util.fileSafe
import dbest.kernel.util.isBlank
import dbest.kernel.util.mapCollection
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

fun sessionRoutes(sessions: Sessions): RoutingHttpHandler = routes(
    "/files" bind GET to ({ filesResponse() }),
    "/sessions" bind GET to ({ sessionsResponse(sessions) }),
    "/sessions" bind POST to ({ newSessionResponse(sessions) }),
    "/sessions/open" bind POST to ({ request -> openSessionResponse(sessions, request) }),
    "/sessions/{sid}/close" bind POST to ({ request -> closeSessionResponse(sessions, request) }),
    "/sessions/{sid}/save" bind POST to ({ request -> saveSessionResponse(sessions, request) }),
    "/sessions/{sid}/rename" bind POST to ({ request -> renameSessionResponse(sessions, request) }),
)

fun workspaceOf(sessions: Sessions, request: Request): Workspace {
    val sid = request.path("sid") ?: throw NotFoundException("id de sessao ausente")
    return getSession(sessions, sid) ?: throw NotFoundException("a sessao '$sid' nao existe")
}

private fun workspaceJson(workspace: Workspace): JsonElement {
    val file = workspace.file
    return obj(
        "sid" to json(workspace.id),
        "name" to json(workspace.name),
        "dirty" to json(workspace.dirty),
        "file" to if (file == null) null else json(file.toString()),
    )
}

private fun sessionsResponse(sessions: Sessions): Response =
    jsonResponse(Status.OK, JsonArray(mapCollection(listSessions(sessions), { workspace -> workspaceJson(workspace) })))

private fun newSessionResponse(sessions: Sessions): Response =
    jsonResponse(Status.OK, workspaceJson(createSession(sessions)))

private fun closeSessionResponse(sessions: Sessions, request: Request): Response {
    val sid = request.path("sid") ?: throw NotFoundException("id de sessao ausente")
    closeSession(sessions, sid)
    return jsonResponse(Status.OK, obj("closed" to json(sid)))
}

private fun filesResponse(): Response {
    val dir = requireDir()
    val files = mutableListOf<Path>()
    if (Files.isDirectory(dir)) {
        Files.newDirectoryStream(dir, "*.dbest").use({ stream -> stream.forEach({ path -> files.add(path) }) })
    }
    files.sort()
    val body = JsonArray(mapCollection(files, { path -> obj("name" to json(sessionName(path)), "path" to json(path.toString())) }))
    return jsonResponse(Status.OK, body)
}

private fun openSessionResponse(sessions: Sessions, request: Request): Response {
    val path = Path.of(objOf(parsedJson(request.bodyString())).string("path"))
    val workspace = createSession(sessions, load(path.toString()), path, sessionName(path))
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

private fun saveSessionResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val body = request.bodyString()
    val name = if (isBlank(body)) {
        null
    } else {
        val parsed = objOf(parsedJson(body))
        if (existsInCollection("name", parsed)) parsed.string("name") else null
    }
    val target = saveTarget(workspace, name)
    save(workspace.canvas.get().history, target.toString())
    workspace.file = target
    workspace.name = sessionName(target)
    workspace.dirty = false
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

private fun renameSessionResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val name = objOf(parsedJson(request.bodyString())).string("name")
    if (isBlank(name)) throw IllegalArgumentException("o nome da sessao nao pode ser vazio")
    val current = workspace.file
    if (current != null) {
        val target = requireDir().resolve(fileSafe(name) + ".dbest")
        if (target != current) {
            Files.move(current, target)
            workspace.file = target
        }
    }
    workspace.name = name
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

private fun saveTarget(workspace: Workspace, name: String?): Path {
    if (name != null) {
        return requireDir().resolve(fileSafe(name) + ".dbest")
    }
    val existing = workspace.file
    if (existing != null) return existing
    return pickSaveFile(workspace.name.ifBlank({ "sessao" }) + ".dbest")
        ?: throw IllegalArgumentException("salvar cancelado")
}

private fun sessionName(path: Path): String = path.fileName.toString().removeSuffix(".dbest")
