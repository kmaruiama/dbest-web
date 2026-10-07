package dbest

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

const val SESSION_VERSION = 3

class Workspace(val id: String, @Volatile var file: Path, @Volatile var editorState: EditorState) {
    val engineState = EngineState()
}

@Serializable
data class Config(val sessionsDir: String? = null, val open: List<String> = emptyList())

class Sessions(val engine: Engine = Engine()) {
    val workspaces = LinkedHashMap<String, Workspace>()

    @Volatile var config: Config = readConfig()
}

private fun readConfig(): Config {
    val file = configFile()
    if (!Files.exists(file)) {
        return Config()
    }
    return try {
        wire.decodeFromString(Config.serializer(), Files.readString(file))
    } catch (failure: SerializationException) {
        logError("configuracao ilegivel em $file, usando a padrao: ${failure.message}", null)
        Config()
    }
}

private fun configFile(): Path = Path.of(System.getProperty("user.home"), ".dbest", "config.json")

@Serializable data class SessionMeta(val sid: String, val name: String, val file: String)

@Serializable
data class SessionView(
    val session: Session,
    val captions: Map<NodeId, Caption>,
    val undoDepth: Int,
    val redoDepth: Int
)

@Serializable
data class SessionFile(val version: Int, @SerialName("history") val editorState: EditorState)

@Serializable data class FileEntry(val name: String, val path: String)

@Serializable private data class NameBody(val name: String)

@Serializable private data class PathBody(val path: String)

@Serializable private data class AliasBody(val alias: String)

fun workspaceRoutes(sessions: Sessions): RoutingHttpHandler {
    val session = "/sessions/{sid}"
    return routes(
        "/config" bind GET to ({ configResponse(sessions) }),
        "/config/sessions-dir" bind POST to ({ directoryResponse(sessions) }),
        "/files" bind GET to ({ filesResponse(sessions) }),
        "/sessions" bind GET to ({ sessionsResponse(sessions) }),
        "/sessions" bind POST to ({ request -> createResponse(sessions, request) }),
        "/sessions/open" bind POST to ({ request -> openResponse(sessions, request) }),
        "$session/close" bind POST to ({ request -> closeResponse(sessions, request) }),
        "$session/rename" bind POST to ({ request -> renameResponse(sessions, request) }),
        "$session/session" bind GET to ({ request -> sessionResponse(sessions, request) }),
        "$session/commands" bind POST to ({ request -> commandsResponse(sessions, request) }),
        "$session/nodes/{id}/alias" bind POST to ({ request -> aliasResponse(sessions, request) }),
        "$session/undo" bind POST to ({ request -> undoResponse(sessions, request) }),
        "$session/redo" bind POST to ({ request -> redoResponse(sessions, request) }),
    )
}

private fun configResponse(sessions: Sessions): Response =
    respond(Config.serializer(), sessions.config)

private fun directoryResponse(sessions: Sessions): Response {
    val picked = pickDirectory()
    if (picked != null) {
        setSessionsDir(sessions, picked)
    }
    return configResponse(sessions)
}

fun setSessionsDir(sessions: Sessions, dir: Path) {
    val absolute = dir.toAbsolutePath()
    Files.createDirectories(absolute)
    updateConfig(sessions, { config -> config.copy(sessionsDir = absolute.toString()) })
}

private fun updateConfig(sessions: Sessions, change: (Config) -> Config) {
    synchronized(
        sessions,
        {
            val next = change(sessions.config)
            writeAtomically(configFile(), files.encodeToString(Config.serializer(), next))
            sessions.config = next
        })
}

private fun writeAtomically(target: Path, text: String) {
    val absolute = target.toAbsolutePath()
    Files.createDirectories(absolute.parent)
    val temporary = absolute.resolveSibling(absolute.fileName.toString() + ".tmp")
    Files.writeString(temporary, text)
    try {
        Files.move(
            temporary,
            absolute,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE)
    } catch (unsupported: AtomicMoveNotSupportedException) {
        Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun filesResponse(sessions: Sessions): Response {
    val entries = sessionFiles(sessions)
    return respond(ListSerializer(FileEntry.serializer()), entries)
}

private fun sessionFiles(sessions: Sessions): List<FileEntry> {
    val dir = sessionsDir(sessions)
    val found = ArrayList<Path>()
    if (Files.isDirectory(dir)) {
        Files.newDirectoryStream(dir, "*.dbest")
            .use({ stream -> stream.forEach({ path -> found.add(path) }) })
    }
    found.sort()
    return found.map({ path -> FileEntry(stem(path), path.toString()) })
}

private fun sessionsDir(sessions: Sessions): Path {
    val dir =
        sessions.config.sessionsDir
            ?: throw IllegalArgumentException("a pasta das sessoes ainda nao foi configurada")
    return Path.of(dir)
}

fun stem(path: Path): String {
    val name = path.fileName.toString()
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(0, dot) else name
}

private fun sessionsResponse(sessions: Sessions): Response {
    val open = listed(sessions)
    return respond(ListSerializer(SessionMeta.serializer()), open)
}

private fun listed(sessions: Sessions): List<SessionMeta> =
    synchronized(sessions, { sessions.workspaces.values.map({ workspace -> meta(workspace) }) })

private fun meta(workspace: Workspace): SessionMeta =
    SessionMeta(workspace.id, stem(workspace.file), workspace.file.toString())

private fun createResponse(sessions: Sessions, request: Request): Response {
    val body = decode(request, NameBody.serializer())
    val workspace = createSession(sessions, body.name)
    return respond(SessionMeta.serializer(), meta(workspace))
}

private fun createSession(sessions: Sessions, name: String): Workspace {
    val fileName = sessionFileName(name)
    val file = sessionsDir(sessions).resolve(fileName)
    save(file, EditorState(), true)
    return register(sessions, file, EditorState())
}

private fun sessionFileName(name: String): String {
    require(name.isNotBlank(), { "o nome da sessao nao pode ser vazio" })
    return fileSafe(name) + ".dbest"
}

fun fileSafe(name: String): String {
    val out = StringBuilder()
    for (c in name) {
        out.append(if (c.isLetterOrDigit() || c == '.' || c == '_' || c == '-') c else '_')
    }
    return if (out.isEmpty()) "export" else out.toString()
}

fun save(file: Path, editorState: EditorState, create: Boolean) {
    val text =
        files.encodeToString(SessionFile.serializer(), SessionFile(SESSION_VERSION, editorState))
    if (create) {
        Files.writeString(file, text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    } else {
        writeAtomically(file, text)
    }
}

private fun register(sessions: Sessions, file: Path, editorState: EditorState): Workspace {
    val workspace =
        Workspace(UUID.randomUUID().toString(), file.toAbsolutePath().normalize(), editorState)
    synchronized(sessions, { sessions.workspaces.put(workspace.id, workspace) })
    remember(sessions)
    return workspace
}

private fun remember(sessions: Sessions) {
    updateConfig(
        sessions,
        { config ->
            config.copy(
                open = sessions.workspaces.values.map({ workspace -> workspace.file.toString() }))
        })
}

private fun openResponse(sessions: Sessions, request: Request): Response {
    val body = decode(request, PathBody.serializer())
    val workspace = openSession(sessions, Path.of(body.path))
    return respond(SessionMeta.serializer(), meta(workspace))
}

private fun openSession(sessions: Sessions, path: Path): Workspace {
    val file = path.toAbsolutePath().normalize()
    return synchronized(
        sessions,
        {
            sessions.workspaces.values.firstOrNull({ workspace -> workspace.file == file })
                ?: register(sessions, file, load(file))
        })
}

fun load(file: Path): EditorState {
    val text = Files.readString(file)
    val fields = wire.parseToJsonElement(text).jsonObject
    val version =
        fields.get("version")?.jsonPrimitive?.intOrNull
            ?: throw SerializationException("campo 'version' ausente")
    require(
        version in 1..SESSION_VERSION,
        {
            "A versao $version do arquivo de sessao nao eh suportada (esperado 1 a $SESSION_VERSION)"
        })
    val migrated = migrate(version, fields)
    val sessionFile = wire.decodeFromJsonElement(SessionFile.serializer(), migrated)
    return sessionFile.editorState
}

private fun closeResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    closeSession(sessions, workspace)
    return sessionsResponse(sessions)
}

fun workspaceOf(sessions: Sessions, request: Request): Workspace {
    val sid = request.path("sid") ?: throw NotFoundException("id de sessao ausente")
    return synchronized(sessions, { sessions.workspaces.get(sid) })
        ?: throw NotFoundException("a sessao '$sid' nao existe")
}

private fun closeSession(sessions: Sessions, workspace: Workspace) {
    synchronized(sessions, { sessions.workspaces.remove(workspace.id) })
    sessions.engine.runWaitingIndefinitely({ workspace.engineState.close() })
    remember(sessions)
}

private fun renameResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val body = decode(request, NameBody.serializer())
    renameSession(sessions, workspace, body.name)
    return respond(SessionMeta.serializer(), meta(workspace))
}

private fun renameSession(sessions: Sessions, workspace: Workspace, name: String) {
    val fileName = sessionFileName(name)
    synchronized(
        workspace,
        {
            val target = workspace.file.resolveSibling(fileName)
            if (target != workspace.file) {
                Files.move(workspace.file, target)
                workspace.file = target
            }
        })
    remember(sessions)
}

private fun sessionResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    return viewResponse(workspace)
}

private fun viewResponse(workspace: Workspace): Response {
    val editorState = workspace.editorState
    val view =
        SessionView(
            editorState.session,
            captions(editorState.session),
            editorState.undoStack.size,
            editorState.redoStack.size)
    return respond(SessionView.serializer(), view)
}

private fun commandsResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val command = decode(request, Command.serializer())
    change(workspace, { editorState -> edit(editorState, command) })
    return viewResponse(workspace)
}

private fun change(workspace: Workspace, transition: (EditorState) -> EditorState) {
    synchronized(
        workspace,
        {
            val next = transition(workspace.editorState)
            if (next != workspace.editorState) {
                save(workspace.file, next, false)
                workspace.editorState = next
            }
        })
}

private fun aliasResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val session = workspace.editorState.session
    val id = nodeId(session, request)
    val alias = decode(request, AliasBody.serializer()).alias
    val old =
        sessions.engine.runWaitingIndefinitely({
            visibleName(session, id, workspace.engineState.tables)
        })
    change(
        workspace,
        { editorState ->
            val command = renaming(editorState.session, id, alias, old)
            edit(editorState, command)
        })
    return viewResponse(workspace)
}

private fun undoResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    change(workspace, { editorState -> undo(editorState) })
    return viewResponse(workspace)
}

private fun redoResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    change(workspace, { editorState -> redo(editorState) })
    return viewResponse(workspace)
}

fun restoreOpen(sessions: Sessions) {
    for (path in sessions.config.open) {
        try {
            openSession(sessions, Path.of(path))
        } catch (failure: Exception) {
            logError("nao foi possivel reabrir $path: ${failure.message}", null)
        }
    }
    remember(sessions)
}
