package dbest.kernel.util

import java.io.InputStream
import java.util.Objects
import kotlin.math.min

fun Sequence<ByteArray>.asInputStream(
    bufferBytes: Int = 64 * 1024,
    onClose: () -> Unit = {},
): InputStream = ChunkedSequenceInputStream(iterator(), bufferBytes, onClose)

private class ChunkedSequenceInputStream(
    private val chunks: Iterator<ByteArray>,
    bufferBytes: Int,
    private val onClose: () -> Unit,
) : InputStream() {

    private val buffer = ByteArray(bufferBytes)
    private val single = ByteArray(1)
    private var position = 0
    private var limit = 0
    private var pending = ByteArray(0)
    private var pendingPosition = 0
    private var done = false
    private var notified = false

    override fun read(): Int = if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        Objects.checkFromIndexSize(offset, length, target.size)
        if (length == 0) {
            return 0
        }
        if (position == limit && !fill()) {
            return -1
        }
        val count = min(length, limit - position)
        buffer.copyInto(target, offset, position, position + count)
        position += count
        return count
    }

    override fun available(): Int = limit - position

    override fun close() {
        position = limit
        pending = ByteArray(0)
        pendingPosition = 0
        finish()
    }

    private fun fill(): Boolean {
        if (done) {
            return false
        }
        position = 0
        limit = 0
        try {
            while (limit < buffer.size) {
                if (pendingPosition == pending.size) {
                    if (!chunks.hasNext()) {
                        finish()
                        break
                    }
                    pending = chunks.next()
                    pendingPosition = 0
                }
                val count = min(buffer.size - limit, pending.size - pendingPosition)
                pending.copyInto(buffer, limit, pendingPosition, pendingPosition + count)
                limit += count
                pendingPosition += count
            }
        } catch (failure: Throwable) {
            finish()
            throw failure
        }
        return limit > 0
    }

    private fun finish() {
        done = true
        if (!notified) {
            notified = true
            onClose()
        }
    }
}
