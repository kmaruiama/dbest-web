package dbest

import ibd.query.Operation
import ibd.query.Tuple
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.min

class EngineBusyException(message: String) : RuntimeException(message)

private const val ADMIT_GRACE_MS = 1_000L

class Engine : AutoCloseable {
    private val worker: ExecutorService =
        Executors.newSingleThreadExecutor({ runnable ->
            val thread = Thread(runnable, "dbest-engine")
            thread.isDaemon = true
            thread
        })
    private val admit = Semaphore(1, true)

    fun <T> run(job: () -> T): T = admitted(false, job)

    private fun <T> admitted(wait: Boolean, job: () -> T): T {
        reserve(wait)
        try {
            return call(job)
        } finally {
            admit.release()
        }
    }

    fun <T> runWaitingIndefinitely(job: () -> T): T = admitted(true, job)

    fun stream(open: () -> Cursor, chunk: (Cursor) -> ByteArray): InputStream {
        reserve(false)
        val cursor =
            try {
                call(open)
            } catch (failure: Throwable) {
                admit.release()
                throw failure
            }
        return RowStream(
            { call({ chunk(cursor) }) },
            {
                call({ cursor.close() })
                admit.release()
            })
    }

    override fun close() {
        worker.shutdownNow()
    }

    private fun reserve(wait: Boolean) {
        if (admit.tryAcquire(ADMIT_GRACE_MS, TimeUnit.MILLISECONDS)) {
            return
        }
        if (!wait) {
            throw EngineBusyException("ja existe uma query rodando; espere ela terminar")
        }
        admit.acquire()
    }

    private fun <T> call(job: () -> T): T =
        try {
            worker.submit(Callable({ job() })).get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
}

private class RowStream(private val refill: () -> ByteArray, private val finish: () -> Unit) :
    InputStream() {
    private var chunk = ByteArray(0)
    private var position = 0
    private var done = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        while (position == chunk.size) {
            if (done) {
                return -1
            }
            chunk =
                try {
                    refill()
                } catch (failure: Exception) {
                    close()
                    throw failure
                }
            position = 0
            if (chunk.isEmpty()) {
                close()
            }
        }
        val count = min(length, chunk.size - position)
        chunk.copyInto(target, offset, position, position + count)
        position += count
        return count
    }

    override fun close() {
        if (!done) {
            done = true
            finish()
        }
    }
}

class Cursor(query: Query, tables: OpenTables) {
    private val operation: Operation = tryCompile(query, tables)
    private val tuples: Iterator<Tuple>
    private var closed = false
    val schema: List<SchemaColumn>
    var consumed: Int = 0
        private set

    init {
        try {
            tuples = gate({ operation.run() })
            schema = gate({ columnsOf(operation) })
        } catch (failure: RuntimeException) {
            closeQuietly(operation)
            throw failure
        }
    }

    fun hasNext(): Boolean = !closed && gate({ tuples.hasNext() })

    fun next(): List<Any?> {
        val tuple = gate({ tuples.next() })
        consumed += 1
        val sources = operation.exposedDataSources
        val row = ArrayList<Any?>(schema.size)
        for (index in sources.indices) {
            val present = index < tuple.rows.size
            for (column in sources[index].prototype.columns) {
                val value = if (present) tuple.rows[index].getValue(column.name) else null
                row.add(value)
            }
        }
        return row
    }

    fun skipTo(position: Int) {
        while (consumed < position && hasNext()) {
            next()
        }
    }

    fun take(limit: Int): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        while (rows.size < limit && hasNext()) {
            rows.add(next())
        }
        return rows
    }

    fun close() {
        if (!closed) {
            closed = true
            closeQuietly(operation)
        }
    }
}

private fun closeQuietly(operation: Operation) {
    try {
        operation.close()
    } catch (ignored: Exception) {}
}

const val CURSOR_CAPACITY = 8

class EngineState {
    val tables = OpenTables()
    val cursors = LinkedHashMap<Query, Cursor>(CURSOR_CAPACITY, 0.75f, true)

    fun page(query: Query, offset: Int, limit: Int): List<List<Any?>> {
        val cursor = cursorFor(query, offset)
        cursor.skipTo(offset)
        val rows = cursor.take(limit)
        if (!cursor.hasNext()) {
            cursors.remove(query)
            cursor.close()
        }
        return rows
    }

    // tenta buscar um cursor que já existe para continuar mostrando rows na página, se nao
    // conseguir,
    // cria um novo se nao existir
    private fun cursorFor(query: Query, offset: Int): Cursor {
        val cached = cursors.get(query)
        if (cached != null && cached.consumed <= offset) {
            return cached
        }
        cursors.remove(query)
        cached?.close()
        val fresh = Cursor(query, tables)
        cursors.put(query, fresh)
        trim(cursors, CURSOR_CAPACITY, { evicted -> evicted.close() })
        return fresh
    }

    fun close() {
        for (cursor in cursors.values) {
            cursor.close()
        }
        cursors.clear()
        closeTables(tables)
    }
}

private fun <K, V> trim(map: LinkedHashMap<K, V>, capacity: Int, dropped: (V) -> Unit) {
    val entries = map.entries.iterator()
    while (map.size > capacity && entries.hasNext()) {
        dropped(entries.next().value)
        entries.remove()
    }
}

private const val SCHEMA_CAPACITY = 256

private val schemas = LinkedHashMap<Query, List<SchemaColumn>>(64, 0.75f, true)

fun schema(query: Query, tables: OpenTables): List<SchemaColumn> {
    val known = schemas.get(query)
    if (known != null) {
        return known
    }
    val operation = tryCompile(query, tables)
    try {
        val columns =
            gate({
                operation.prepareAllDataSources()
                columnsOf(operation)
            })
        schemas.put(query, columns)
        trim(schemas, SCHEMA_CAPACITY, { _ -> })
        return columns
    } finally {
        closeQuietly(operation)
    }
}

fun closeTables(tables: OpenTables) {
    for (table in tables.values) {
        gate({ table.close() })
    }
    tables.clear()
}
