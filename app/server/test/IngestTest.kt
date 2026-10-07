package dbest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IngestTest {

    private val dir: Path = Files.createTempDirectory("dbest-ingest")

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun file(name: String, content: String): String {
        val file = dir.resolve(name)
        Files.writeString(file, content)
        return file.toString()
    }

    @Test
    fun `splitLine keeps a plain header intact`() {
        assertEquals(listOf("id", "name", "note"), splitLine("id,name,note", ','))
    }

    @Test
    fun `splitLine does not split on a separator inside quotes`() {
        assertEquals(listOf("1", "Smith, John", "hello"), splitLine("1,\"Smith, John\",hello", ','))
    }

    @Test
    fun `splitLine unwraps a plain quoted field`() {
        assertEquals(listOf("2", "Plain", "world"), splitLine("2,\"Plain\",world", ','))
    }

    @Test
    fun `splitLine resolves an escaped double-quote inside a quoted field`() {
        assertEquals(
            listOf("3", "she said \"hi\"", "ok"), splitLine("3,\"she said \"\"hi\"\"\",ok", ','))
    }

    @Test
    fun `splitLine handles the empty line`() {
        assertEquals(emptyList(), splitLine("", ','))
    }

    @Test
    fun `splitLine respects quoting with a non-comma separator`() {
        assertEquals(listOf("1", "a,b", "c"), splitLine("1;\"a,b\";c", ';'))
    }

    @Test
    fun `sniffCsv keeps a quoted field with an embedded separator as one column`() {
        val path = file("people.csv", "id,name,note\n1,\"Smith, John\",hello\n2,Plain,world\n")
        val guess = sniffCsv(path)
        assertEquals(',', guess.separator)
        assertEquals(listOf("id", "name", "note"), guess.columns)
        assertEquals(listOf("1", "Smith, John", "hello"), guess.sampleRows[0])
        assertEquals(listOf("2", "Plain", "world"), guess.sampleRows[1])
    }

    @Test
    fun `sniffCsv picks the semicolon separator when it is the real delimiter`() {
        val path = file("euro.csv", "id;name;score\n1;Ana;10\n2;Bruno;20\n")
        val guess = sniffCsv(path)
        assertEquals(';', guess.separator)
        assertEquals(listOf("id", "name", "score"), guess.columns)
        assertEquals(listOf("1", "Ana", "10"), guess.sampleRows[0])
    }

    @Test
    fun `sniffCsv respects an explicit separator override instead of guessing`() {
        val path = file("piped.csv", "id|name|score\n1|Ana|10\n")
        val guess = sniffCsv(path, separatorOverride = '|')
        assertEquals('|', guess.separator)
        assertEquals(listOf("id", "name", "score"), guess.columns)
    }

    @Test
    fun `sniffCsv respects a headerLine override for files with leading notes`() {
        val path = file("noted.csv", "# exported 2026-08-22\nid,name\n1,Ana\n2,Bruno\n")
        val guess = sniffCsv(path, headerLine = 2)
        assertEquals(2, guess.headerLine)
        assertEquals(listOf("id", "name"), guess.columns)
        assertEquals(listOf("1", "Ana"), guess.sampleRows[0])
    }

    @Test
    fun `sniffCsv on a missing file returns an empty guess instead of throwing`() {
        val guess = sniffCsv(dir.resolve("does-not-exist.csv").toString())
        assertEquals(emptyList(), guess.columns)
        assertEquals(emptyList(), guess.sampleRows)
    }

    @Test
    fun `sniffXml auto-detects root and record elements`() {
        val path =
            file(
                "employees.xml",
                """
                <employees>
                    <employee><name>Ana</name><age>22</age></employee>
                    <employee><name>Bruno</name><age>17</age></employee>
                    <employee><name>Carla</name><age>34</age></employee>
                </employees>
                """
                    .trimIndent(),
            )
        val guess = sniffXml(path)
        assertEquals("employees", guess.rootElement)
        assertEquals("employee", guess.recordElement)
        assertEquals(listOf("name", "age"), guess.columns)
        assertEquals(3, guess.totalRecords)
        assertEquals(listOf("Ana", "22"), guess.sampleRows[0])
    }

    @Test
    fun `sniffXml respects an explicit recordElement override`() {
        val path =
            file(
                "org.xml",
                """
                <org>
                    <department><name>TI</name></department>
                    <department><name>RH</name></department>
                    <employee><name>Ana</name></employee>
                    <employee><name>Bruno</name></employee>
                    <employee><name>Carla</name></employee>
                </org>
                """
                    .trimIndent(),
            )
        val guess = sniffXml(path, recordElement = "employee")
        assertEquals("employee", guess.recordElement)
        assertEquals(3, guess.totalRecords)
        assertEquals(listOf("Ana", "Bruno", "Carla"), guess.sampleRows.map({ row -> row[0] }))
    }

    @Test
    fun `sniffXml flattens nested elements to dot-notation columns`() {
        val path =
            file(
                "people.xml",
                """
                <people>
                    <person><name>Ana</name><address><city>SP</city><zip>01000</zip></address></person>
                    <person><name>Bruno</name><address><city>RJ</city><zip>20000</zip></address></person>
                </people>
                """
                    .trimIndent(),
            )
        val guess = sniffXml(path)
        assertTrue("address.city" in guess.columns)
        assertTrue("address.zip" in guess.columns)
        val cityIndex = guess.columns.indexOf("address.city")
        assertEquals("SP", guess.sampleRows[0][cityIndex])
    }

    @Test
    fun `sniffXml exposes attributes as @-prefixed columns`() {
        val path =
            file(
                "tagged.xml",
                """
                <items>
                    <item id="1"><label>First</label></item>
                    <item id="2"><label>Second</label></item>
                </items>
                """
                    .trimIndent(),
            )
        val guess = sniffXml(path)
        assertTrue("@id" in guess.columns)
        val idIndex = guess.columns.indexOf("@id")
        assertEquals("1", guess.sampleRows[0][idIndex])
    }

    @Test
    fun `sniffXml on a missing file returns an empty guess instead of throwing`() {
        val guess = sniffXml(dir.resolve("does-not-exist.xml").toString())
        assertEquals(emptyList(), guess.columns)
        assertEquals(emptyList(), guess.sampleRows)
        assertEquals(0, guess.totalRecords)
    }

    @Test
    fun `sniffXml on malformed XML returns an empty guess instead of throwing`() {
        val path = file("broken.xml", "<not><valid</xml>")
        val guess = sniffXml(path)
        assertEquals(emptyList(), guess.columns)
    }

    @Test
    fun `XML documents with a doctype are rejected before the engine parses them`() {
        val file = Files.createTempFile("unsafe", ".xml")
        Files.writeString(
            file,
            """<?xml version="1.0"?><!DOCTYPE data [<!ENTITY secret SYSTEM "file:///etc/passwd">]><data>&secret;</data>""",
        )
        try {
            assertFailsWith<IllegalArgumentException>(block = { requireSafeXml(file.toString()) })
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
