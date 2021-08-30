package kittoku.mvc.unit.udp

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.addOnesComplement
import kittoku.mvc.extension.toIntAsUByte
import kittoku.mvc.extension.toIntAsUShort
import kittoku.mvc.unit.DataUnit
import kittoku.mvc.unit.dhcp.DhcpMessage
import kittoku.mvc.unit.ip.IP_PROTOCOL_UDP
import kittoku.mvc.unit.ip.IPv4Packet
import java.nio.ByteBuffer


internal class UDPDatagram : DataUnit { // for only TCP Connection, not UDP acceleration
    internal var srcPort = 0
    internal var dstPort = 0

    internal var payloadDhcpMessage: DhcpMessage? = null
    internal var payloadUnknown: ByteArray? = null

    private var pseudoIPHeader: ByteArray? = null // don't validate checksum if null
    private val headerLength = 4 * Short.SIZE_BYTES

    private val validPayloadLength: Int
        get() {
            return when {
                payloadDhcpMessage != null -> payloadDhcpMessage!!.length
                payloadUnknown != null -> payloadUnknown!!.size
                else -> 0
            }
        }

    override val length: Int
        get() = headerLength + validPayloadLength

    override fun write(buffer: ByteBuffer) {
        val startUdp = buffer.position()

        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(length.toShort())

        val startChecksum = buffer.position()

        buffer.putShort(0)

        when {
            payloadDhcpMessage != null -> payloadDhcpMessage!!.write(buffer)
            payloadUnknown != null -> buffer.put(payloadUnknown!!)
        }

        val stopUdp = buffer.position()

        val checksum = pseudoIPHeader?.let {
            calcChecksum(it, startUdp, buffer)
        } ?: 0

        buffer.position(startChecksum)
        buffer.putShort(checksum)
        buffer.position(stopUdp)
    }

    override fun read(buffer: ByteBuffer) {
        val startUdp = buffer.position()

        srcPort = buffer.short.toIntAsUShort()
        dstPort = buffer.short.toIntAsUShort()

        val payloadLength = buffer.short - headerLength
        assertAlways(payloadLength >= 0)

        val givenChecksum = buffer.short // discard given checksum

         if (payloadLength > 0) {
             when {
                 srcPort == UDP_PORT_DHCP_SEVER && dstPort == UDP_PORT_DHCP_CLIENT -> {
                     payloadDhcpMessage = DhcpMessage().also { it.read(buffer) }
                 }

                 else -> payloadUnknown = ByteArray(payloadLength).also { buffer.get(it) }
             }
        }

        pseudoIPHeader?.also {
            if (givenChecksum.toIntAsUShort() != 0) {
                assertAlways(calcChecksum(it, startUdp, buffer) == UDP_CORRECT_CHECKSUM)
            }
        }
    }

    private fun calcChecksum(ipHeader: ByteArray, startUdp: Int, buffer: ByteBuffer): Short {
        val wrapped = ByteBuffer.wrap(ipHeader)

        var sum = 0

        while (wrapped.hasRemaining()) {
            sum = sum.addOnesComplement(wrapped.short.toIntAsUShort())
        }

        val udpLength = length
        if (udpLength % 2 == 0) {
            (startUdp until (startUdp + udpLength) step 2).forEach {
                sum = sum.addOnesComplement(buffer.getShort(it).toIntAsUShort())
            }
        } else {
            val lastByteIndex = startUdp + udpLength - 1
            (startUdp until lastByteIndex step 2).forEach {
                sum = sum.addOnesComplement(buffer.getShort(it).toIntAsUShort())
            }

            sum = sum.addOnesComplement(buffer.get(lastByteIndex).toIntAsUByte().shl(Byte.SIZE_BITS)) // as if 00 were padded
        }

        val invSum = sum.inv()

        return if (invSum and 0x0000FFFF == 0) {
            -1 // differentiated from no-checksum-type UDP
        } else {
            invSum.toShort()
        }
    }

    internal fun importIPv4Header(packet: IPv4Packet) { // invoke just before `write`
        val header = ByteBuffer.allocate(3 * Int.SIZE_BYTES)

        header.put(packet.srcAddress)
        header.put(packet.dstAddress)
        header.put(0)
        header.put(IP_PROTOCOL_UDP)
        header.putShort(length.toShort())
    }
}
