package kittoku.mvc.unit.dhcp

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.toIntAsUByte
import java.nio.ByteBuffer


internal abstract class DhcpByteOption : DhcpOption() {
    internal var value: Byte = 0

    override val lengthWithoutHeader = Byte.SIZE_BYTES

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.put(value)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.get().toIntAsUByte() == lengthWithoutHeader)
        value = buffer.get()
    }
}

internal class DhcpOptionMessageType : DhcpByteOption() {
    override val tag = DHCP_OPTION_MESSAGE_TYPE
}
