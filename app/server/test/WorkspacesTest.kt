package dbest

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class WorkspacesTest {

    private fun view(response: Response): SessionView =
        wire.decodeFromString(SessionView.serializer(), response.bodyString())

    private fun listed(harness: Harness): List<SessionMeta> =
        wire.decodeFromString(
            ListSerializer(SessionMeta.serializer()),
            harness.send(Method.GET, "/sessions").bodyString())

    @Test
    fun `creating a session writes its file, and every edit is written through`() {
        Harness()
            .use({ harness ->
                val sid = harness.create("demo")
                val file = harness.dir.resolve("demo.dbest")
                assertEquals(EditorState(), load(file))

                val response = harness.command(sid, AddTable(TableId(1), users()))
                assertEquals(Status.OK, response.status)
                assertEquals(1, view(response).undoDepth)
                assertEquals(view(response).session, load(file).session)

                harness.send(Method.POST, "/sessions/$sid/undo")
                assertEquals(Session(), load(file).session)
                harness.send(Method.POST, "/sessions/$sid/redo")
                assertEquals(1, load(file).session.tables.size)
            })
    }

    @Test
    fun `a rejected command changes neither the session nor the file`() {
        Harness()
            .use({ harness ->
                val sid = harness.create("demo")
                harness.command(sid, AddTable(TableId(1), users()))
                val before = Files.readString(harness.dir.resolve("demo.dbest"))

                val batch =
                    Batch(
                        listOf(
                            AddNode(NodeId(1), TableNode(TableId(1), "u"), Position(0.0, 0.0)),
                            AddNode(
                                NodeId(2), TableNode(TableId(99), "missing"), Position(0.0, 0.0)),
                        ))
                val bad = harness.command(sid, batch)
                assertEquals(Status.BAD_REQUEST, bad.status)
                assertTrue("nao existe" in bad.bodyString())
                assertEquals(before, Files.readString(harness.dir.resolve("demo.dbest")))
                assertEquals(1, view(harness.send(Method.GET, "/sessions/$sid/session")).undoDepth)
            })
    }

    @Test
    fun `an edit that cannot be saved is not applied`() {
        Harness()
            .use({ harness ->
                val sid = harness.create("demo")
                Files.setPosixFilePermissions(
                    harness.dir, PosixFilePermissions.fromString("r-xr-xr-x"))
                try {
                    assertEquals(
                        Status.INTERNAL_SERVER_ERROR,
                        harness.command(sid, AddTable(TableId(1), users())).status)
                } finally {
                    Files.setPosixFilePermissions(
                        harness.dir, PosixFilePermissions.fromString("rwxr-xr-x"))
                }
                assertEquals(0, view(harness.send(Method.GET, "/sessions/$sid/session")).undoDepth)
            })
    }

    @Test
    fun `a name already taken by a file is refused instead of overwriting it`() {
        Harness()
            .use({ harness ->
                val sid = harness.create("demo")
                harness.command(sid, AddTable(TableId(1), users()))
                assertEquals(
                    Status.CONFLICT,
                    harness.send(Method.POST, "/sessions", "{\"name\":\"demo\"}").status)
                assertEquals(1, load(harness.dir.resolve("demo.dbest")).session.tables.size)
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.POST, "/sessions", "{\"name\":\" \"}").status)
            })
    }

    @Test
    fun `opening a file that is already open returns its tab`() {
        Harness()
            .use({ harness ->
                val sid = harness.create("demo")
                val path = harness.dir.resolve("demo.dbest").toString()
                val opened = harness.send(Method.POST, "/sessions/open", "{\"path\":\"$path\"}")
                assertEquals(
                    sid, wire.decodeFromString(SessionMeta.serializer(), opened.bodyString()).sid)
                assertEquals(1, listed(harness).size)
            })
    }

    @Test
    fun `the open tabs come back, in order, after a restart and a closed tab does not`() {
        Harness()
            .use({ harness ->
                val first = harness.create("first")
                harness.create("second")
                val third = harness.create("third")
                harness.command(first, AddTable(TableId(1), users()))
                harness.send(Method.POST, "/sessions/$third/close")

                val restarted = Sessions()
                try {
                    restoreOpen(restarted)
                    val names =
                        restarted.workspaces.values.map({ workspace -> stem(workspace.file) })
                    assertEquals(listOf("first", "second"), names)
                    assertEquals(
                        1, restarted.workspaces.values.first().editorState.session.tables.size)
                } finally {
                    restarted.engine.close()
                }
            })
    }

    @Test
    fun `a file that no longer loads is dropped from the reopen list`() {
        Harness()
            .use({ harness ->
                harness.create("gone")
                Files.delete(harness.dir.resolve("gone.dbest"))
                val restarted = Sessions()
                try {
                    restoreOpen(restarted)
                    assertTrue(restarted.workspaces.isEmpty())
                    assertTrue(restarted.config.open.isEmpty())
                } finally {
                    restarted.engine.close()
                }
            })
    }

    @Test
    fun `rename moves the file and refuses to overwrite another one`() {
        Harness()
            .use({ harness ->
                val sid = harness.create("original")
                harness.create("taken")
                val renamed =
                    harness.send(Method.POST, "/sessions/$sid/rename", "{\"name\":\"renomeada\"}")
                assertEquals(
                    "renomeada",
                    wire.decodeFromString(SessionMeta.serializer(), renamed.bodyString()).name)
                assertTrue(Files.exists(harness.dir.resolve("renomeada.dbest")))
                assertFalse(Files.exists(harness.dir.resolve("original.dbest")))
                assertEquals(
                    Status.CONFLICT,
                    harness
                        .send(Method.POST, "/sessions/$sid/rename", "{\"name\":\"taken\"}")
                        .status)
                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.POST, "/sessions/$sid/rename", "{\"name\":\" \"}").status)
            })
    }

    @Test
    fun `files lists the saved sessions and config reports the folder`() {
        Harness()
            .use({ harness ->
                harness.create("smoke")
                val files =
                    wire.decodeFromString(
                        ListSerializer(FileEntry.serializer()),
                        harness.send(Method.GET, "/files").bodyString())
                assertEquals(listOf("smoke"), files.map({ file -> file.name }))
                val config =
                    wire.decodeFromString(
                        Config.serializer(), harness.send(Method.GET, "/config").bodyString())
                assertEquals(harness.dir.toString(), config.sessionsDir)
            })
    }

    @Test
    fun `closing a session drops it and an unknown session is a 404`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                assertEquals(Status.OK, harness.send(Method.POST, "/sessions/$sid/close").status)
                assertTrue(listed(harness).isEmpty())
                assertEquals(
                    Status.NOT_FOUND, harness.send(Method.GET, "/sessions/$sid/session").status)
            })
    }

    @Test
    fun `the view carries a caption per node`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                harness.command(sid, AddTable(TableId(1), users()))
                val added =
                    view(
                        harness.command(
                            sid,
                            AddNode(NodeId(1), TableNode(TableId(1), "u"), Position(0.0, 0.0))))
                assertEquals(Caption("IndexScan", "users", "u"), added.captions.get(NodeId(1)))
                assertEquals(2, added.undoDepth)
                assertEquals(0, added.redoDepth)
            })
    }

    @Test
    fun `no sessions folder means no new sessions`() {
        val home = Files.createTempDirectory("dbest-empty-home")
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", home.toString())
        val sessions = Sessions()
        try {
            val response =
                router(sessions, TOKEN)(
                    Request(Method.POST, "/sessions")
                        .header("X-DBest-Token", TOKEN)
                        .body("{\"name\":\"x\"}"))
            assertEquals(Status.BAD_REQUEST, response.status)
            assertEquals(null, sessions.config.sessionsDir)
        } finally {
            sessions.engine.close()
            System.setProperty("user.home", previous)
            Path.of(home.toString()).toFile().deleteRecursively()
        }
    }

    private fun aliases(harness: Harness): String {
        val copy = harness.dir.resolve("aliases.dbest")
        Files.copy(Path.of("test/sessions/aliases.dbest"), copy)
        val opened = harness.send(Method.POST, "/sessions/open", "{\"path\": \"$copy\"}")
        return wire.decodeFromString(SessionMeta.serializer(), opened.bodyString()).sid
    }

    private fun rename(harness: Harness, sid: String, id: Int, alias: String): Response =
        harness.send(Method.POST, "/sessions/$sid/nodes/$id/alias", "{\"alias\": \"$alias\"}")

    private fun condition(view: SessionView, id: Int): Condition =
        (view.session.nodes.getValue(NodeId(id)) as FilterNode).condition

    @Test
    fun `renaming a source renames the columns its readers take from it, downstream and below a join it provides`() {
        Harness()
            .use({ harness ->
                val sid = aliases(harness)
                val named = view(rename(harness, sid, 5, "gente"))
                assertEquals("gente", (named.session.nodes.getValue(NodeId(5)) as SetOpNode).alias)
                assertEquals(Comparison("gente.dept", CompareOp.EQ, 10), condition(named, 6))
                assertEquals(
                    Comparison("e.dept", CompareOp.EQ, 10),
                    condition(named, 3),
                    "upstream reads the table, not the union")

                val provided = view(rename(harness, sid, 26, "time"))
                assertEquals(
                    Comparison("dd.id", CompareOp.EQ, Ref("time.dept")), condition(provided, 28))
                val rows =
                    wire.decodeFromString(
                        RowsPage.serializer(),
                        harness
                            .send(Method.GET, "/sessions/$sid/nodes/29/rows?offset=0&limit=10")
                            .bodyString())
                assertEquals(listOf("ana", "cid"), rows.rows.map({ row -> row[1].content }))
                assertEquals(
                    emptyList(),
                    wire.decodeFromString(
                        ListSerializer(Problem.serializer()),
                        harness.send(Method.GET, "/sessions/$sid/problems").bodyString()))
            })
    }

    @Test
    fun `naming a blank union renames the name it inherited, and one undo takes the rename and its readers back`() {
        Harness()
            .use({ harness ->
                val sid = aliases(harness)
                val named = view(rename(harness, sid, 9, "turma"))
                assertEquals(Comparison("turma.dept", CompareOp.EQ, 10), condition(named, 30))
                assertEquals(TableNode(TableId(1), "e3"), named.session.nodes.getValue(NodeId(7)))

                val undone = view(harness.send(Method.POST, "/sessions/$sid/undo"))
                assertEquals("", (undone.session.nodes.getValue(NodeId(9)) as SetOpNode).alias)
                assertEquals(Comparison("e3.dept", CompareOp.EQ, 10), condition(undone, 30))
            })
    }

    @Test
    fun `a rename that would clash, hold a dot, or name what names no source is refused and changes nothing`() {
        Harness()
            .use({ harness ->
                val sid = aliases(harness)
                val before = Files.readString(harness.dir.resolve("aliases.dbest"))
                val clash = rename(harness, sid, 5, "e")
                assertEquals(Status.BAD_REQUEST, clash.status)
                assertTrue("ja nomeia o node #1" in clash.bodyString(), clash.bodyString())
                assertEquals(Status.BAD_REQUEST, rename(harness, sid, 5, "a.b").status)
                assertEquals(Status.BAD_REQUEST, rename(harness, sid, 5, " ").status)
                assertEquals(Status.BAD_REQUEST, rename(harness, sid, 6, "x").status)
                assertEquals(Status.NOT_FOUND, rename(harness, sid, 99, "x").status)
                assertEquals(before, Files.readString(harness.dir.resolve("aliases.dbest")))
                assertEquals(
                    Status.OK,
                    rename(harness, sid, 12, "e").status,
                    "another tree may use the name")
            })
    }

    @Test
    fun `renaming a source a Reference reads renames the columns the Reference names after it, for their readers too`() {
        Harness()
            .use({ harness ->
                val copy = harness.dir.resolve("correlation.dbest")
                Files.copy(Path.of("test/sessions/correlation.dbest"), copy)
                val opened = harness.send(Method.POST, "/sessions/open", "{\"path\": \"$copy\"}")
                val sid = wire.decodeFromString(SessionMeta.serializer(), opened.bodyString()).sid
                val renamed = view(rename(harness, sid, 15, "f"))
                assertEquals(
                    ReferenceNode(listOf("f.dept"), "atual"),
                    renamed.session.nodes.getValue(NodeId(16)))
                assertEquals(Comparison("atual.f_dept", CompareOp.EQ, 10), condition(renamed, 28))
                val rows =
                    wire.decodeFromString(
                        RowsPage.serializer(),
                        harness
                            .send(Method.GET, "/sessions/$sid/nodes/28/rows?offset=0&limit=10")
                            .bodyString())
                assertEquals(listOf("ana", "cid"), rows.rows.map({ row -> row[1].content }))
            })
    }
}
