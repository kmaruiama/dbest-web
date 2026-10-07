package dbest

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

enum class ExportFormat(val extension: String, val contentType: String) {
    CSV("csv", "text/csv; charset=utf-8"),
    SQL("sql", "application/sql; charset=utf-8"),
}

fun exportRoutes(sessions: Sessions): RoutingHttpHandler {
    val node = "/sessions/{sid}/nodes/{id}"
    return routes(
        "$node/export" bind GET to ({ request -> exportResponse(sessions, request) }),
    )
}

private fun exportResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val session = workspace.editorState.session
    val id = nodeId(session, request)
    val query = planForNodeById(session, id)
    val format = exportFormatOf(request.query("format") ?: "csv")
    val table = request.query("table") ?: "export"
    val body =
        withJoinHint(
            session,
            id,
            {
                sessions.engine.run({
                    val cursor = Cursor(query, workspace.engineState.tables)
                    try {
                        val rows = cursor.take(Int.MAX_VALUE)
                        exportRows(format, table, cursor.schema, rows)
                    } finally {
                        cursor.close()
                    }
                })
            })
    return Response(Status.OK)
        .header("Content-Type", format.contentType)
        .header(
            "Content-Disposition",
            "attachment; filename=\"${fileSafe(table)}.${format.extension}\"")
        .body(body)
}

fun exportFormatOf(raw: String): ExportFormat =
    when (raw.lowercase()) {
        "csv" -> ExportFormat.CSV
        "sql" -> ExportFormat.SQL
        else ->
            throw IllegalArgumentException(
                "formato de exportacao invalido: '$raw' (use csv ou sql)")
    }

fun exportRows(
    format: ExportFormat,
    table: String,
    schema: List<SchemaColumn>,
    rows: List<List<Any?>>
): String =
    when (format) {
        ExportFormat.CSV -> csvExport(schema, rows)
        ExportFormat.SQL -> sqlExport(table, schema, rows)
    }

private const val CRLF: String = "\r\n"

private fun csvExport(schema: List<SchemaColumn>, rows: List<List<Any?>>): String {
    val out = StringBuilder()
    out.append(headerNames(schema).joinToString(",", transform = { header -> csvField(header) }))
        .append(CRLF)
    for (row in rows) {
        out.append(
                row.joinToString(
                    ",",
                    transform = { value -> if (value == null) "" else csvField(value.toString()) }))
            .append(CRLF)
    }
    return out.toString()
}

private fun headerNames(schema: List<SchemaColumn>): List<String> {
    val names = schema.map({ column -> column.name })
    val qualify = names.toSet().size < names.size
    return schema.map({ column -> if (qualify) "${column.source}.${column.name}" else column.name })
}

private fun csvField(value: String): String {
    val needsQuote =
        value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
    return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

private fun sqlExport(table: String, schema: List<SchemaColumn>, rows: List<List<Any?>>): String {
    val headers = headerNames(schema)
    val out = StringBuilder()
    out.append("CREATE TABLE IF NOT EXISTS ").append(sqlIdentifier(table)).append(" (\n")
    out.append(
        schema.indices.joinToString(
            ",\n",
            transform = { i -> "    ${sqlIdentifier(headers[i])} ${sqlType(schema[i].type)}" }))
    val primaryKeys =
        schema.indices.filter({ i -> schema[i].primaryKey }).map({ i -> sqlIdentifier(headers[i]) })
    if (primaryKeys.isNotEmpty()) {
        out.append(",\n    PRIMARY KEY (").append(primaryKeys.joinToString(", ")).append(')')
    }
    out.append("\n);\n\n")
    val columns = headers.joinToString(", ", transform = { header -> sqlIdentifier(header) })
    for (row in rows) {
        val values = row.joinToString(", ", transform = { value -> sqlLiteral(value) })
        out.append("INSERT INTO ")
            .append(sqlIdentifier(table))
            .append(" (")
            .append(columns)
            .append(") VALUES (")
            .append(values)
            .append(");\n")
    }
    return out.toString()
}

private fun sqlIdentifier(name: String): String = "`" + name.replace("`", "``") + "`"

private fun sqlType(type: ColumnType): String =
    when (type) {
        ColumnType.INT -> "INT"
        ColumnType.LONG -> "BIGINT"
        ColumnType.FLOAT -> "FLOAT"
        ColumnType.DOUBLE -> "DOUBLE"
        ColumnType.BOOLEAN -> "BOOLEAN"
        ColumnType.STRING -> "TEXT"
    }

private fun sqlLiteral(value: Any?): String =
    when (value) {
        null -> "NULL"
        is Boolean -> if (value) "TRUE" else "FALSE"
        is Number -> value.toString()
        else -> "'" + value.toString().replace("\\", "\\\\").replace("'", "\\'") + "'"
    }
