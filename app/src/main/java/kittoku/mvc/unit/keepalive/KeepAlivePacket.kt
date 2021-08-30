package kittoku.mvc.unit.keepalive

import kittoku.mvc.extension.nextBytes
import kittoku.mvc.unit.DataUnit
import java.nio.ByteBuffer
import java.security.SecureRandom


internal class KeepAlivePacket : DataUnit {
    internal var value: ByteArray? = null

    override val length: Int
        get() = value?.size ?: 0

    internal fun importRandomBytes(random: SecureRandom) {
        value = random.nextBytes(random.nextInt(KEEP_ALIVE_MAX_SIZE + 1))
    }

    override fun read(buffer: ByteBuffer) {
        throw NotImplementedError()
    }

    override fun write(buffer: ByteBuffer) {
        buffer.putInt(KEEP_ALIVE_FRAME_NUM)
        buffer.putInt(length)
        value?.also { buffer.put(it) }
    }
}
