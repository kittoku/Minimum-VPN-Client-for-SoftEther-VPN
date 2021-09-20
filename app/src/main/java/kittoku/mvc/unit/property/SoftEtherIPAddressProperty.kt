package kittoku.mvc.unit.property

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.read
import kittoku.mvc.unit.ip.IPv4_ADDRESS_SIZE
import java.nio.ByteBuffer


internal open class SoftEtherIPAddressProperty : SoftEtherProperty() {
    override val valueType = SEP_INT_TYPE
    override val valueNum = 1
    override val length: Int
        get() = headerLength + Int.SIZE_BYTES

    internal val value = ByteArray(IPv4_ADDRESS_SIZE)

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)
        buffer.put(value.reversedArray())
    }

    override fun read(buffer: ByteBuffer) {
        assertAlways(buffer.int == valueType)
        assertAlways(buffer.int == valueNum)

        val reversed = ByteArray(IPv4_ADDRESS_SIZE)
        buffer.get(reversed)
        value.read(reversed.reversedArray())
    }
}

internal class SepUDPClientIP : SoftEtherIPAddressProperty() {
    override val key = SEP_UDP_CLIENT_IP
}

internal class SepUDPServerIP : SoftEtherIPAddressProperty() {
    override val key = SEP_UDP_SERVER_IP
}
