package dbest

import engine.info.Parameters
import ibd.query.QueryStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

@Serializable data class Metric(val name: String, val value: Long)

@Serializable
data class Measure(
    val tuples: Int,
    val exhausted: Boolean,
    val elapsedMs: Double,
    val counters: List<Metric>
)

fun comparatorRoutes(sessions: Sessions): RoutingHttpHandler {
    val node = "/sessions/{sid}/nodes/{id}"
    return routes(
        "$node/compare/{other}" bind GET to ({ request -> compareResponse(sessions, request) }),
    )
}

private fun compareResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val session = workspace.editorState.session
    val a = nodeId(session, request, "id")
    val b = nodeId(session, request, "other")
    require(
        b !in walk(session, a, Direction.EITHER),
        {
            "os nodes #${a.value} e #${b.value} estao na mesma arvore; compare nodes de arvores diferentes"
        })
    val rawLimit = request.query("limit")
    val limit = if (rawLimit == null) null else intParam("limit", rawLimit)
    require(limit == null || limit > 0, { "o limite de tuplas precisa ser positivo" })
    val first = planForNodeById(session, a)
    val second = planForNodeById(session, b)
    val measures =
        sessions.engine.run({
            listOf(
                withJoinHint(session, a, { measure(first, limit) }),
                withJoinHint(session, b, { measure(second, limit) }))
        })
    return respond(ListSerializer(Measure.serializer()), measures)
}

fun measure(query: Query, limit: Int?): Measure {
    val tables = OpenTables()
    try {
        openTablesBeforeCounting(query, tables)
        return measureOpened(query, tables, limit)
    } finally {
        closeTables(tables)
    }
}

private fun openTablesBeforeCounting(query: Query, tables: OpenTables) {
    val spec = query.table
    if (spec != null) {
        tables.getOrPut(spec, { openTable(spec) })
    }
    for (input in query.inputs) {
        openTablesBeforeCounting(input, tables)
    }
}

private fun measureOpened(query: Query, tables: OpenTables, limit: Int?): Measure {
    zeroCounters()
    val start = System.nanoTime()
    val cursor = Cursor(query, tables)
    try {
        cursor.skipTo(limit ?: Int.MAX_VALUE)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val counted = counters()
        return Measure(cursor.consumed, !cursor.hasNext(), elapsedMs, counted)
    } finally {
        cursor.close()
    }
}

private fun zeroCounters() {
    QueryStats.clear()
    Parameters.RECORDS_READ.reset()
    Parameters.BLOCKS_ACCESSED.reset()
    Parameters.BLOCKS_LOADED.reset()
}

private fun counters(): List<Metric> =
    listOf(
        Metric("nextCalls", QueryStats.NEXT_CALLS.sum()),
        Metric("comparisons", QueryStats.COMPARE_FILTER.sum()),
        Metric("pkSearches", QueryStats.PK_SEARCH.sum()),
        Metric("sortedTuples", QueryStats.SORT_TUPLES.sum()),
        Metric("memoryBytes", QueryStats.MEMORY_USED.sum()),
        Metric("recordsRead", Parameters.RECORDS_READ.sum()),
        Metric("blocksAccessed", Parameters.BLOCKS_ACCESSED.sum()),
        Metric("blocksLoaded", Parameters.BLOCKS_LOADED.sum()),
    )
