package dbest.features.canvas

import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.graph.TableNode
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Batch
import dbest.features.canvas.history.Command
import dbest.features.canvas.history.json
import dbest.features.sessions.Sessions
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.stringColumn
import dbest.kernel.http.router
import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.parsedJson
import dbest.kernel.util.existsInCollection
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasRoutesTest {

    private fun app(): HttpHandler {
        val handler = router(Sessions())
        val created = handler(Request(Method.POST, "/sessions"))
        val sid = parsedJson(created.bodyString()).jsonObject.getValue("sid").jsonPrimitive.content
        return { request ->
            handler(request.uri(request.uri.copy(path = "/sessions/$sid" + request.uri.path)))
        }
    }

    private fun users() = MemorySpec(
        "users",
        listOf(intColumn("id", primaryKey = true), stringColumn("name"), intColumn("age")),
        listOf(
            mapOf("id" to 1, "name" to "Ana", "age" to 22),
            mapOf("id" to 2, "name" to "Bruno", "age" to 17),
            mapOf("id" to 3, "name" to "Carla", "age" to 34),
        ),
    )

    private fun HttpHandler.command(command: Command): Response =
        this(Request(Method.POST, "/commands").body(jsonText(json(command))))

    private fun Response.field(name: String) = parsedJson(bodyString()).jsonObject.getValue(name)
    private fun Response.revision() = field("revision").jsonPrimitive.int
    private fun Response.depth() = field("depth").jsonPrimitive.int

    @Test
    fun `a mutation returns only the new revision, never the session`() {
        val app = app()
        val response = app.command(AddTable(TableId(0), users()))

        assertEquals(Status.OK, response.status)
        assertEquals(1, response.revision())
        assertFalse(existsInCollection("session", parsedJson(response.bodyString()).jsonObject))
    }

    @Test
    fun `GET session carries a caption per node, and never the node itself`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), TableNode(TableId(0), "u"), Position(0.0, 0.0)))

        val body = parsedJson(app(Request(Method.GET, "/session")).bodyString()).jsonObject
        val caption = body.getValue("captions").jsonObject.getValue("0").jsonObject
        assertEquals("IndexScan", caption.getValue("engineClass").jsonPrimitive.content)
        assertEquals("users as u", caption.getValue("expression").jsonPrimitive.content)

        val node = body.getValue("session").jsonObject.getValue("nodes").jsonObject.getValue("0").jsonObject
        assertFalse(existsInCollection("engineClass", node))
        assertFalse(existsInCollection("expression", node))
    }

    @Test
    fun `revisions increment and GET session reflects applied commands`() {
        val app = app()
        assertEquals(1, app.command(AddTable(TableId(0), users())).revision())
        assertEquals(2, app.command(AddNode(NodeId(0), TableNode(TableId(0), "u"), Position(0.0, 0.0))).revision())

        val session = app(Request(Method.GET, "/session"))
        val body = parsedJson(session.bodyString()).jsonObject
        assertEquals(2, body.getValue("revision").jsonPrimitive.int)
        assertTrue(body.getValue("canUndo").jsonPrimitive.content.toBoolean())
        val sessionObj = body.getValue("session").jsonObject
        assertTrue(existsInCollection("tables", sessionObj) && existsInCollection("nodes", sessionObj))
    }

    @Test
    fun `an invalid command is a 400 carrying the model message and does not change revision`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        val bad = app.command(AddNode(NodeId(0), TableNode(TableId(99), "u"), Position(0.0, 0.0)))

        assertEquals(Status.BAD_REQUEST, bad.status)
        assertTrue("nao existe" in bad.field("error").jsonPrimitive.content)
        assertEquals(1, app(Request(Method.GET, "/session")).field("revision").jsonPrimitive.int)
    }

    @Test
    fun `undo then redo restores state`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), TableNode(TableId(0), "u"), Position(0.0, 0.0)))
        val afterAdd = parsedJson(app(Request(Method.GET, "/session")).bodyString()).jsonObject.getValue("session")

        val undo = app(Request(Method.POST, "/undo"))
        assertEquals(3, undo.revision())
        assertTrue(undo.field("canRedo").jsonPrimitive.content.toBoolean())
        val afterUndo = parsedJson(app(Request(Method.GET, "/session")).bodyString()).jsonObject.getValue("session")
        val nodesAfterUndo = afterUndo.jsonObject["nodes"]
        assertTrue(nodesAfterUndo == null || "0" !in nodesAfterUndo.jsonObject)

        val redo = app(Request(Method.POST, "/redo"))
        assertEquals(4, redo.revision())
        val afterRedo = parsedJson(app(Request(Method.GET, "/session")).bodyString()).jsonObject.getValue("session")
        assertEquals(afterAdd, afterRedo)
    }

    @Test
    fun `depth tracks the undo stack while revision only ever climbs`() {
        val app = app()
        val afterTable = app.command(AddTable(TableId(0), users()))
        assertEquals(1 to 1, afterTable.revision() to afterTable.depth())
        val afterNode = app.command(AddNode(NodeId(0), TableNode(TableId(0), "u"), Position(0.0, 0.0)))
        assertEquals(2 to 2, afterNode.revision() to afterNode.depth())

        val undo = app(Request(Method.POST, "/undo"))
        assertEquals(3, undo.revision())
        assertEquals(1, undo.depth())

        val redo = app(Request(Method.POST, "/redo"))
        assertEquals(4, redo.revision())
        assertEquals(2, redo.depth())

        assertEquals(2, app(Request(Method.GET, "/session")).depth())
    }

    @Test
    fun `a batch is one undo step and is atomic when rejected`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))

        val added = app.command(Batch(listOf(
            AddNode(NodeId(0), TableNode(TableId(0), "u"), Position(0.0, 0.0)),
            AddNode(NodeId(1), TableNode(TableId(0), "v"), Position(120.0, 0.0)),
        )))
        assertEquals(2, added.depth())

        val undone = app(Request(Method.POST, "/undo"))
        assertEquals(1, undone.depth())
        val afterUndo = parsedJson(app(Request(Method.GET, "/session")).bodyString()).jsonObject
        assertFalse(existsInCollection("nodes", afterUndo.getValue("session").jsonObject))

        val failed = app.command(Batch(listOf(
            AddNode(NodeId(2), TableNode(TableId(0), "w"), Position(240.0, 0.0)),
            AddNode(NodeId(3), TableNode(TableId(99), "missing"), Position(360.0, 0.0)),
        )))
        assertEquals(Status.BAD_REQUEST, failed.status)

        val afterFailure = parsedJson(app(Request(Method.GET, "/session")).bodyString()).jsonObject
        assertEquals(1, afterFailure.getValue("depth").jsonPrimitive.int)
        assertFalse(existsInCollection("nodes", afterFailure.getValue("session").jsonObject))
    }
}
