package kittoku.mvc.unit.dhcp

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.toIntAsUByte
import java.nio.ByteBuffer


internal abstract class DhcpIntOption : DhcpOption() {
    internal var value: Int = 0

    override val lengthWithoutHeader = Int.SIZE_BYTES

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.putInt(value)
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.get().toIntAsUByte() == lengthWithoutHeader)
        value = buffer.int
    }
}

internal class DhcpOptionLeaseTime : DhcpIntOption() {
    override val tag = DHCP_OPTION_LEASE_TIME
}
