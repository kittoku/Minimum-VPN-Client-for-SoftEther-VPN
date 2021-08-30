package kittoku.mvc.unit.dhcp

import kittoku.mvc.extension.toIntAsUByte
import kittoku.mvc.unit.DataUnit
import java.nio.ByteBuffer


internal open class DhcpOption : DataUnit { // as unknown option
    internal open val tag: Byte = 0

    protected open val lengthWithoutHeader = 0

    override val length: Int
        get() = lengthWithoutHeader + 2

    protected fun writeHeader(buffer: ByteBuffer) {
        buffer.put(tag)
        buffer.put(lengthWithoutHeader.toByte()) // subtract header length
    }

    override fun write(buffer: ByteBuffer) {
        throw NotImplementedError()
    }

    override fun read(buffer: ByteBuffer) {
        val valueLength = buffer.get().toIntAsUByte()

        buffer.get(ByteArray(valueLength))
    }
}
