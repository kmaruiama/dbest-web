package dbest.features.ingest

import dbest.kernel.adapter.requireSafeXml
import dbest.kernel.util.mapCollection
import dbest.kernel.util.orDefault
import sources.xml.XMLRecognizer

data class XmlGuess(
    val rootElement: String,
    val recordElement: String,
    val columns: List<String>,
    val sampleRows: List<List<String?>>,
    val totalRecords: Int,
)

fun sniffXml(path: String, rootElement: String? = null, recordElement: String? = null): XmlGuess {
    val rootOverride = rootElement?.trim()?.ifEmpty({ null })
    val recordOverride = recordElement?.trim()?.ifEmpty({ null })
    return try {
        requireSafeXml(path)
        val recognizer = XMLRecognizer(path, rootOverride, recordOverride, XMLRecognizer.FlatteningStrategy.NESTED_COLUMNS)
        val analysis = recognizer.analyzeStructure()
        val columnNames = mapCollection(analysis.columns, { column -> column.name() })
        val sampleRows = mapCollection(analysis.sampleData, { row -> mapCollection(columnNames, { name -> row[name] }) })
        XmlGuess(analysis.rootElement, analysis.recordElement, columnNames, sampleRows, analysis.totalRecords)
    } catch (e: Exception) {
        XmlGuess(orDefault(rootOverride, ""), orDefault(recordOverride, ""), emptyList(), emptyList(), 0)
    }
}
