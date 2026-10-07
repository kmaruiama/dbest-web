package dbest

import java.nio.file.Files
import java.nio.file.Path
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response

const val TOKEN = "test-token"

fun column(
    name: String,
    type: ColumnType,
    primaryKey: Boolean = false,
    nullable: Boolean = false
): Column = Column(name, type, primaryKey, nullable)

fun users(): MemorySpec =
    MemorySpec(
        "users",
        listOf(
            column("id", ColumnType.INT, primaryKey = true),
            column("name", ColumnType.STRING),
            column("age", ColumnType.INT)),
        listOf(
            mapOf("id" to 1, "name" to "Ana", "age" to 22),
            mapOf("id" to 2, "name" to "Bruno", "age" to 17),
            mapOf("id" to 3, "name" to "Carla", "age" to 34),
        ),
    )

fun orders(): MemorySpec =
    MemorySpec(
        "orders",
        listOf(
            column("id", ColumnType.INT, primaryKey = true),
            column("user_id", ColumnType.INT),
            column("total", ColumnType.INT)),
        listOf(
            mapOf("id" to 10, "user_id" to 1, "total" to 250),
            mapOf("id" to 11, "user_id" to 1, "total" to 50),
            mapOf("id" to 12, "user_id" to 3, "total" to 900),
        ),
    )

fun demoCommands(): List<Command> =
    listOf(
        AddTable(TableId(1), users()),
        AddTable(TableId(2), orders()),
        AddNode(NodeId(1), TableNode(TableId(1), "u"), Position(0.0, 0.0)),
        AddNode(NodeId(2), TableNode(TableId(2), "o"), Position(0.0, 120.0)),
        AddNode(
            NodeId(3), JoinNode(on = listOf(JoinTerm("u.id", "o.user_id"))), Position(160.0, 60.0)),
        AddNode(
            NodeId(4), FilterNode(Comparison("o.total", CompareOp.GT, 100)), Position(320.0, 60.0)),
        Connect(Edge(NodeId(1), NodeId(3), Port.LEFT)),
        Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)),
        Connect(Edge(NodeId(3), NodeId(4), Port.ONLY)),
    )

fun demo(): EditorState =
    demoCommands().fold(EditorState(), { editorState, command -> edit(editorState, command) })

class Harness : AutoCloseable {
    private val previousHome: String = System.getProperty("user.home")
    val home: Path = Files.createTempDirectory("dbest-home")
    val dir: Path = home.resolve("sessions")
    val sessions: Sessions
    val app: (Request) -> Response

    init {
        System.setProperty("user.home", home.toString())
        sessions = Sessions()
        setSessionsDir(sessions, dir)
        app = router(sessions, TOKEN)
    }

    fun send(method: Method, path: String, body: String? = null): Response {
        val request = Request(method, path).header("X-DBest-Token", TOKEN)
        return app(if (body == null) request else request.body(body))
    }

    fun create(name: String = "s"): String {
        val created = send(Method.POST, "/sessions", "{\"name\":\"$name\"}")
        return wire.decodeFromString(SessionMeta.serializer(), created.bodyString()).sid
    }

    fun command(sid: String, command: Command): Response =
        send(
            Method.POST,
            "/sessions/$sid/commands",
            wire.encodeToString(Command.serializer(), command))

    override fun close() {
        sessions.engine.close()
        System.setProperty("user.home", previousHome)
        home.toFile().deleteRecursively()
    }
}
