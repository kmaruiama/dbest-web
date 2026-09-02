package dbest.features.sessions

import dbest.features.canvas.CanvasState
import dbest.features.canvas.history.History
import dbest.features.canvas.query.OpenTables
import dbest.features.canvas.query.closeTables
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

data class Workspace(
    val id: String,
    val canvas: AtomicReference<CanvasState>,
    val tables: OpenTables,
    var file: Path? = null,
    var name: String = "",
    var dirty: Boolean = false,
)

data class Sessions(
    val workspaces: ConcurrentHashMap<String, Workspace> = ConcurrentHashMap(),
    val engine: Engine = Engine(),
)

fun createSession(sessions: Sessions, history: History = History(), file: Path? = null, name: String = ""): Workspace {
    val id = UUID.randomUUID().toString()
    val workspace = Workspace(id, AtomicReference(CanvasState(history, 0)), OpenTables(), file, name)
    sessions.workspaces[id] = workspace
    return workspace
}

fun getSession(sessions: Sessions, id: String): Workspace? = sessions.workspaces[id]

fun listSessions(sessions: Sessions): List<Workspace> = sessions.workspaces.values.toList()

fun closeSession(sessions: Sessions, id: String): Unit {
    val removed = sessions.workspaces.remove(id)
    if (removed != null) {
        closeTables(removed.tables)
    }
}

fun closeAllSessions(sessions: Sessions): Unit {
    for (workspace in sessions.workspaces.values) {
        closeTables(workspace.tables)
    }
    sessions.workspaces.clear()
}
