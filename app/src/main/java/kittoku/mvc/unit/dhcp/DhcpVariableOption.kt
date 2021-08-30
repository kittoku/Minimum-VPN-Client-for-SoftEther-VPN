package kittoku.mvc.unit.dhcp

import kittoku.mvc.extension.toIntAsUByte
import java.nio.ByteBuffer


internal abstract class DhcpVariableOption : DhcpOption() {
    internal var value: ByteArray? = null

    override val lengthWithoutHeader: Int
        get() = value?.size ?: 0

    override fun write(buffer: ByteBuffer) {
        writeHeader(buffer)

        value?.also {
            buffer.put(it)
        }
    }

    override fun read(buffer: ByteBuffer) {
        val valueLength = buffer.get().toIntAsUByte()

        value = if (valueLength > 0) ByteArray(valueLength).also {
            buffer.get(it)
        } else null
    }
}

internal class DhcpOptionParameterList : DhcpVariableOption() {
    override val tag = DHCP_OPTION_PARAMETER_LIST
}
