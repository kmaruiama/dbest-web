package dbest.features.canvas

import dbest.kernel.adapter.Plan
import dbest.kernel.adapter.compile.compile
import dbest.kernel.adapter.gate
import dbest.kernel.adapter.rawJson
import dbest.kernel.json.compactJsonText
import ibd.query.Operation
import ibd.query.Tuple
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

internal fun ndjsonLines(plan: Plan): Sequence<ByteArray> = sequence({
    val operation = compile(plan)
    try {
        val tuples = gate({ operation.run() })
        val columns = gate({ tupleColumns(operation) })
        while (gate({ tuples.hasNext() })) {
            val tuple = gate({ tuples.next() })
            yield(encodeLine(tuple, columns))
        }
    } finally {
        closeQuietly(operation)
    }
})

private data class TupleColumn(val rowIndex: Int, val name: String)

private fun tupleColumns(operation: Operation): List<TupleColumn> = buildList({
    for ((rowIndex, source) in operation.exposedDataSources.withIndex()) {
        for (column in source.prototype.columns) {
            add(TupleColumn(rowIndex, column.name))
        }
    }
})

private fun encodeLine(tuple: Tuple, columns: List<TupleColumn>): ByteArray {
    val values = ArrayList<JsonElement>(columns.size)
    for (column in columns) {
        values.add(rawJson(tuple.rows[column.rowIndex].getValue(column.name)))
    }
    return (compactJsonText(JsonArray(values)) + "\n").toByteArray(Charsets.UTF_8)
}

private fun closeQuietly(operation: Operation) {
    try {
        operation.close()
    } catch (_: Exception) {
    }
}
