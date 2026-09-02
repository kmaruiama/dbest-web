package dbest.kernel.adapter

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SafeXmlTest {

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
