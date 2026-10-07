package dbest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.http4k.core.Method
import org.http4k.core.Status

class ExportTest {

    private val schema =
        listOf(
            SchemaColumn("u", "id", ColumnType.INT, true),
            SchemaColumn("u", "name", ColumnType.STRING, false),
            SchemaColumn("u", "active", ColumnType.BOOLEAN, false),
        )

    private val rows = listOf(listOf<Any?>(1, "O'Brien", true), listOf<Any?>(2, "x, \"y\"", null))

    @Test
    fun `csv follows schema order, quotes per RFC 4180, and blanks null`() {
        assertEquals(
            "id,name,active\r\n1,O'Brien,true\r\n2,\"x, \"\"y\"\"\",\r\n",
            exportRows(ExportFormat.CSV, "users", schema, rows))
        val note = listOf(SchemaColumn("t", "note", ColumnType.STRING, false))
        assertEquals(
            "note\r\n\"line1\nline2\"\r\n",
            exportRows(ExportFormat.CSV, "t", note, listOf(listOf<Any?>("line1\nline2"))))
    }

    @Test
    fun `sql creates the table with MySQL types and its primary key, then inserts escaped literals`() {
        val sql = exportRows(ExportFormat.SQL, "users", schema, rows)
        assertTrue(
            "CREATE TABLE IF NOT EXISTS `users` (\n    `id` INT,\n    `name` TEXT,\n    `active` BOOLEAN,\n    PRIMARY KEY (`id`)\n);" in
                sql)
        assertTrue(
            "INSERT INTO `users` (`id`, `name`, `active`) VALUES (1, 'O\\'Brien', TRUE);" in sql)
        assertTrue("VALUES (2, 'x, \"y\"', NULL);" in sql)
        val path = listOf(SchemaColumn("t", "p", ColumnType.STRING, false))
        assertTrue(
            "VALUES ('a\\\\b\\'c');" in
                exportRows(ExportFormat.SQL, "t", path, listOf(listOf<Any?>("a\\b'c"))))
        val numeric =
            listOf(
                SchemaColumn("t", "a", ColumnType.LONG, false),
                SchemaColumn("t", "b", ColumnType.FLOAT, false),
                SchemaColumn("t", "c", ColumnType.DOUBLE, false))
        val types = exportRows(ExportFormat.SQL, "t", numeric, emptyList())
        assertTrue(
            "`a` BIGINT" in types &&
                "`b` FLOAT" in types &&
                "`c` DOUBLE" in types &&
                "INSERT" !in types)
    }

    @Test
    fun `colliding bare names qualify every column`() {
        val joined =
            listOf(
                SchemaColumn("u", "id", ColumnType.INT, true),
                SchemaColumn("o", "id", ColumnType.INT, false),
                SchemaColumn("o", "total", ColumnType.DOUBLE, false),
            )
        val row = listOf(listOf<Any?>(1, 9, 5.0))
        assertEquals(
            "u.id,o.id,o.total",
            exportRows(ExportFormat.CSV, "orders", joined, row).split("\r\n").first())
        val sql = exportRows(ExportFormat.SQL, "orders", joined, row)
        assertTrue("PRIMARY KEY (`u.id`)" in sql && "VALUES (1, 9, 5.0);" in sql)
    }

    @Test
    fun `export formats are named case-insensitively`() {
        assertEquals(ExportFormat.SQL, exportFormatOf("SQL"))
        assertFailsWith<IllegalArgumentException>(block = { exportFormatOf("xlsx") })
    }

    @Test
    fun `a node downloads as csv or sql, named by the table parameter`() {
        Harness()
            .use({ harness ->
                val sid = harness.create()
                harness.command(sid, AddTable(TableId(1), users()))
                harness.command(
                    sid, AddNode(NodeId(1), TableNode(TableId(1), "u"), Position(0.0, 0.0)))

                val csv = harness.send(Method.GET, "/sessions/$sid/nodes/1/export?format=csv")
                assertEquals(
                    "attachment; filename=\"export.csv\"", csv.header("Content-Disposition"))
                assertEquals(
                    listOf("id,name,age", "1,Ana,22", "2,Bruno,17", "3,Carla,34"),
                    csv.bodyString().trim().split("\r\n"))

                val sql =
                    harness.send(
                        Method.GET, "/sessions/$sid/nodes/1/export?format=sql&table=people")
                assertTrue(sql.header("Content-Type").orEmpty().startsWith("application/sql"))
                assertEquals(
                    "attachment; filename=\"people.sql\"", sql.header("Content-Disposition"))
                assertTrue("CREATE TABLE IF NOT EXISTS `people`" in sql.bodyString())

                assertEquals(
                    Status.BAD_REQUEST,
                    harness.send(Method.GET, "/sessions/$sid/nodes/1/export?format=pdf").status)
            })
    }
}
