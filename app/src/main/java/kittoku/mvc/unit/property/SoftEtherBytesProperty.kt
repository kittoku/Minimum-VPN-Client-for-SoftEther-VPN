package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import java.nio.ByteBuffer


internal open class SoftEtherBytesProperty : SoftEtherProperty() {
    override val valueType = SEP_BYTES_TYPE
    override val valueNum = 1

    private val valueLength: Int
        get() = value?.size ?: 0

    override val length: Int
        get() = headerLength + Int.SIZE_BYTES + valueLength

    internal var value: ByteArray? = null

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.putInt(valueLength)
        value?.also { buffer.put(it) }
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.int == valueType)
        assertAlways(buffer.int == valueNum)

        val size = buffer.int
        assertAlways(size >= 0)

        value = if(size > 0) {
            ByteArray(size).also { buffer.get(it) }
        } else null
    }
}

internal class SepPenCore : SoftEtherBytesProperty() {
    override val key = SEP_PEN_CORE
}

internal class SepUDPClientKeyV2 : SoftEtherBytesProperty() {
    override val key = SEP_UDP_CLIENT_KEY_V2
}

internal class SepUDPServerKeyV2 : SoftEtherBytesProperty() {
    override val key = SEP_UDP_SERVER_KEY_V2
}
