package dbest.features.sessions

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EngineBusyException(message: String) : RuntimeException(message)

private const val ENGINE_THREAD_NAME = "dbest-engine"
private const val ADMIT_GRACE_MS = 1_000L
private const val CHUNK_POLL_MS = 100L
private const val STREAM_QUEUE_CAPACITY = 64
private const val BUSY_MESSAGE = "ja existe uma query rodando; espere ela terminar"

private sealed interface Chunk {
    class Data(val bytes: ByteArray) : Chunk

    class Failure(val cause: Throwable) : Chunk

    data object End : Chunk
}

class Engine : AutoCloseable {

    private val worker: ExecutorService = Executors.newSingleThreadExecutor({ runnable ->
        val thread = Thread(runnable, ENGINE_THREAD_NAME)
        thread.isDaemon = true
        thread
    })
    private val admit = Semaphore(1, true)

    fun <T> run(job: () -> T): T {
        reserve(reject = true)
        try {
            return call(job)
        } finally {
            admit.release()
        }
    }

    fun <T> runWaiting(job: () -> T): T {
        reserve(reject = false)
        try {
            return call(job)
        } finally {
            admit.release()
        }
    }

    fun stream(produce: (sink: (ByteArray) -> Unit, cancelled: () -> Boolean) -> Unit): RowStream {
        reserve(reject = true)
        return RowStream(worker, admit, produce)
    }

    override fun close() {
        worker.shutdownNow()
    }

    private fun reserve(reject: Boolean) {
        if (admit.tryAcquire(ADMIT_GRACE_MS, TimeUnit.MILLISECONDS)) {
            return
        }
        if (reject) {
            throw EngineBusyException(BUSY_MESSAGE)
        }
        admit.acquire()
    }

    private fun <T> call(job: () -> T): T =
        try {
            worker.submit(Callable({ job() })).get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
}

class RowStream internal constructor(
    worker: ExecutorService,
    private val admit: Semaphore,
    produce: (sink: (ByteArray) -> Unit, cancelled: () -> Boolean) -> Unit,
) {

    private val queue = LinkedBlockingQueue<Chunk>(STREAM_QUEUE_CAPACITY)
    private val cancelled = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private var primed: Chunk? = null

    init {
        worker.execute({
            try {
                produce({ line -> feed(Chunk.Data(line)) }, cancelled::get)
                offer(Chunk.End)
            } catch (_: CancellationException) {
            } catch (t: Throwable) {
                offer(Chunk.Failure(t))
            } finally {
                release()
            }
        })
        val first = queue.take()
        primed = first
        if (first is Chunk.Failure) {
            release()
            throw first.cause
        }
    }

    fun lines(): Sequence<ByteArray> = sequence({
        val start = primed
        primed = null
        when (start) {
            is Chunk.Data -> yield(start.bytes)
            is Chunk.Failure -> throw start.cause
            Chunk.End -> return@sequence
            null -> {}
        }
        while (true) {
            when (val chunk = queue.poll(CHUNK_POLL_MS, TimeUnit.MILLISECONDS)) {
                is Chunk.Data -> yield(chunk.bytes)
                is Chunk.Failure -> throw chunk.cause
                Chunk.End -> return@sequence
                null -> if (cancelled.get()) return@sequence
            }
        }
    })

    fun close() {
        cancelled.set(true)
        queue.clear()
    }

    private fun feed(chunk: Chunk) {
        if (!offer(chunk)) {
            throw CancellationException()
        }
    }

    private fun offer(chunk: Chunk): Boolean {
        while (!cancelled.get()) {
            if (queue.offer(chunk, CHUNK_POLL_MS, TimeUnit.MILLISECONDS)) {
                return true
            }
        }
        return false
    }

    private fun release() {
        if (released.compareAndSet(false, true)) {
            admit.release()
        }
    }
}
