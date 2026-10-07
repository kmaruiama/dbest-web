package dbest

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import sources.xml.XMLRecognizer

@Serializable
data class CsvGuess(
    val separator: Char,
    val headerLine: Int,
    val columns: List<String>,
    val sampleRows: List<List<String?>>
)

@Serializable
data class XmlGuess(
    val rootElement: String,
    val recordElement: String,
    val columns: List<String>,
    val sampleRows: List<List<String?>>,
    val totalRecords: Int,
)

@Serializable sealed interface Picked

@Serializable @SerialName("cancelled") data object PickCancelled : Picked

@Serializable
@SerialName("csv")
data class PickedCsv(val path: String, val name: String, val guess: CsvGuess) : Picked

@Serializable
@SerialName("xml")
data class PickedXml(val path: String, val name: String, val guess: XmlGuess) : Picked

@Serializable
@SerialName("btree")
data class PickedBTree(val path: String, val name: String) : Picked

@Serializable
private data class CsvPreviewBody(
    val path: String,
    val headerLine: Int = 1,
    val separator: Char? = null
)

@Serializable
private data class XmlPreviewBody(
    val path: String,
    val rootElement: String? = null,
    val recordElement: String? = null
)

private val TABLE_EXTENSIONS = listOf("csv", "xml", "head", "dat")
private val SEPARATOR_CANDIDATES = listOf(',', ';', '\t', '|')
private const val SNIFF_SAMPLE_SIZE = 5

fun ingestRoutes(): RoutingHttpHandler =
    routes(
        "/pick-file" bind POST to ({ pickedResponse() }),
        "/csv-preview" bind POST to ({ request -> csvPreviewResponse(request) }),
        "/xml-preview" bind POST to ({ request -> xmlPreviewResponse(request) }),
    )

private fun pickedResponse(): Response = respond(Picked.serializer(), pickFile())

private fun csvPreviewResponse(request: Request): Response {
    val body = decode(request, CsvPreviewBody.serializer())
    val guess = sniffCsv(body.path, body.headerLine, body.separator)
    return respond(CsvGuess.serializer(), guess)
}

private fun xmlPreviewResponse(request: Request): Response {
    val body = decode(request, XmlPreviewBody.serializer())
    val guess = sniffXml(body.path, body.rootElement, body.recordElement)
    return respond(XmlGuess.serializer(), guess)
}

fun sniffCsv(path: String, headerLine: Int = 1, separatorOverride: Char? = null): CsvGuess {
    val candidates =
        if (separatorOverride != null) listOf(separatorOverride) else SEPARATOR_CANDIDATES
    val best =
        candidates
            .mapNotNull({ separator -> tryCsv(path, separator, headerLine) })
            .maxByOrNull({ attempt -> attempt.score })
    return best?.guess ?: CsvGuess(separatorOverride ?: ',', headerLine, emptyList(), emptyList())
}

fun sniffXml(path: String, rootElement: String? = null, recordElement: String? = null): XmlGuess {
    val root = rootElement?.trim()?.ifEmpty({ null })
    val record = recordElement?.trim()?.ifEmpty({ null })
    return try {
        requireSafeXml(path)
        val analysis =
            XMLRecognizer(path, root, record, XMLRecognizer.FlatteningStrategy.NESTED_COLUMNS)
                .analyzeStructure()
        val names = analysis.columns.map({ column -> column.name() })
        val sample = analysis.sampleData.map({ row -> names.map({ name -> row.get(name) }) })
        XmlGuess(analysis.rootElement, analysis.recordElement, names, sample, analysis.totalRecords)
    } catch (failure: Exception) {
        XmlGuess(root ?: "", record ?: "", emptyList(), emptyList(), 0)
    }
}

fun splitLine(line: String, separator: Char): List<String> {
    if (line.isEmpty()) {
        return emptyList()
    }
    val fields = ArrayList<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"') {
            field.append('"')
            i++
        } else if (c == '"') {
            quoted = !quoted
        } else if (c == separator && !quoted) {
            fields.add(field.toString().trim())
            field.clear()
        } else {
            field.append(c)
        }
        i++
    }
    fields.add(field.toString().trim())
    return fields
}

private fun pickFile(): Picked {
    val listed = TABLE_EXTENSIONS.joinToString(", ", transform = { extension -> ".$extension" })
    val picked = pickTableFile("Tabelas ($listed)", TABLE_EXTENSIONS) ?: return PickCancelled
    val path = picked.toAbsolutePath().toString()
    val name = stem(picked)
    return when (picked.fileName.toString().lowercase().substringAfterLast('.')) {
        "csv" -> PickedCsv(path, name, sniffCsv(path))
        "xml" -> PickedXml(path, name, sniffXml(path))
        "head",
        "dat" -> PickedBTree(path, name)
        else ->
            throw IllegalArgumentException(
                "tipo de arquivo nao suportado: '${picked.fileName}' (use $listed)")
    }
}

private data class CsvAttempt(val guess: CsvGuess, val score: Int)

private fun tryCsv(path: String, separator: Char, headerLine: Int): CsvAttempt? {
    try {
        val header =
            File(path)
                .bufferedReader()
                .use({ reader ->
                    var line: String? = null
                    repeat(headerLine, { _ -> line = reader.readLine() })
                    line
                })
        val rawNames = if (header == null) emptyList() else splitLine(header, separator)
        if (rawNames.size < 2) {
            return null
        }
        val names = uniqueNames(rawNames)
        val spec =
            CsvSpec(
                "sniff",
                path,
                names.map({ name -> Column(name, ColumnType.STRING, false, true) }),
                separator,
                headerLine = headerLine)
        val tables = OpenTables()
        val cursor = Cursor(Query(TableNode(TableId(0), "s"), emptyList(), spec), tables)
        try {
            val rows = cursor.take(SNIFF_SAMPLE_SIZE)
            val sample = rows.map({ row -> row.map({ value -> value as? String }) })
            val score = names.size * 100 + rows.count({ row -> row.size == names.size })
            val guess = CsvGuess(separator, headerLine, names, sample)
            return CsvAttempt(guess, score)
        } finally {
            cursor.close()
            closeTables(tables)
        }
    } catch (failure: Exception) {
        return null
    }
}

private fun uniqueNames(names: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return names.map({ raw ->
        val base = raw.ifBlank({ "column" })
        val count = seen.getOrDefault(base, 0)
        seen.put(base, count + 1)
        if (count == 0) base else "${base}_$count"
    })
}
