package dbest.features.canvas

import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.graph.TableNode
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Command
import dbest.features.canvas.history.json
import dbest.features.sessions.Sessions
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.stringColumn
import dbest.kernel.http.router
import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.parsedJson
import dbest.kernel.util.filterCollection
import dbest.kernel.util.isBlank
import dbest.kernel.util.mapCollection
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeRoutesTest {

    private fun app(sessions: Sessions = Sessions()): HttpHandler {
        val handler = router(sessions)
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

    @Test
    fun `derived reads expose schema and rows from the engine`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), TableNode(TableId(0), "u"), Position(0.0, 0.0)))

        val schema = app(Request(Method.GET, "/nodes/0/schema"))
        assertEquals(Status.OK, schema.status)
        val columns = mapCollection(parsedJson(schema.bodyString()).jsonArray, { element -> element.jsonObject.getValue("name").jsonPrimitive.content })
        assertEquals(listOf("id", "name", "age"), columns)

        val paged = app(Request(Method.GET, "/nodes/0/rows?offset=0&limit=200"))
        val pagedBody = parsedJson(paged.bodyString()).jsonObject
        assertEquals(3, pagedBody.getValue("rows").jsonArray.size)
        assertTrue(pagedBody.getValue("elapsedMs").jsonPrimitive.double >= 0.0)
        assertEquals(3, pagedBody.getValue("rows").jsonArray[0].jsonArray.size)

        val all = app(Request(Method.GET, "/nodes/0/rows"))
        assertEquals(Status.OK, all.status)
        assertEquals("application/x-ndjson; charset=utf-8", all.header("Content-Type"))
        val lines = filterCollection(all.bodyString().split("\n"), { line -> !isBlank(line) })
        assertEquals(3, lines.size)
        assertEquals(listOf("1", "Ana", "22"), mapCollection(parsedJson(lines[0]).jsonArray, { element -> element.jsonPrimitive.content }))
    }

    private fun manyRows(count: Int) = MemorySpec(
        "nums",
        listOf(intColumn("id", primaryKey = true)),
        mapCollection((0 until count).toList(), { id -> mapOf("id" to id) }),
    )

    @Test
    fun `closing an unpaged response early frees the engine from any thread`() {
        val sessions = Sessions()
        val app = app(sessions)
        app.command(AddTable(TableId(0), manyRows(5000)))
        app.command(AddNode(NodeId(0), TableNode(TableId(0), "n"), Position(0.0, 0.0)))

        val response = app(Request(Method.GET, "/nodes/0/rows"))
        assertEquals(Status.CONFLICT, app(Request(Method.GET, "/nodes/0/rows?offset=0&limit=1")).status)

        val closer = Thread({ response.close() })
        closer.start()
        closer.join()

        assertEquals(Status.OK, app(Request(Method.GET, "/nodes/0/rows?offset=0&limit=1")).status)
    }

    @Test
    fun `a read on an unknown node is a 404`() {
        val app = app()
        val response = app(Request(Method.GET, "/nodes/7/schema"))
        assertEquals(Status.NOT_FOUND, response.status)
    }
}
