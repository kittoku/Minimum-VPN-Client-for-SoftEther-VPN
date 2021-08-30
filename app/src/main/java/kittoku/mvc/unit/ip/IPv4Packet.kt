package kittoku.mvc.unit.ip

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.addOnesComplement
import kittoku.mvc.extension.move
import kittoku.mvc.extension.toIntAsUShort
import kittoku.mvc.unit.DataUnit
import kittoku.mvc.unit.udp.UDPDatagram
import java.nio.ByteBuffer


internal class IPv4Packet : DataUnit { // not to be fragmented
    internal var identification: Short = 0
    internal var protocol: Byte = 0

    internal val srcAddress = ByteArray(4)
    internal val dstAddress = ByteArray(4)

    internal var payloadUDPDatagram: UDPDatagram? = null
    internal var payloadUnknown: ByteArray? = null

    private val headerLength = 5 * Int.SIZE_BYTES

    private val validPayloadLength: Int
        get() {
            return when {
                payloadUDPDatagram != null -> payloadUDPDatagram!!.length
                payloadUnknown != null -> payloadUnknown!!.size
                else -> 0
            }
        }

    override val length: Int
        get() = headerLength + validPayloadLength

    override fun write(buffer: ByteBuffer) {
        val startIp = buffer.position()

        buffer.put(IPv4_VERSION_AND_HEADER_LENGTH)
        buffer.put(0) // tos
        buffer.putShort(length.toShort())
        buffer.putShort(identification)
        buffer.putShort(0) // frags and fragment offset
        buffer.put(IPv4_DEFAULT_TTL)
        buffer.put(protocol)

        val startChecksum = buffer.position()
        buffer.putShort(0)

        buffer.put(srcAddress)
        buffer.put(dstAddress)

        val stopIp = buffer.position()

        buffer.position(startChecksum)
        buffer.putShort(calcChecksum(startIp, buffer))
        buffer.position(stopIp)

        when {
            payloadUDPDatagram != null -> payloadUDPDatagram!!.write(buffer)
            payloadUnknown != null -> buffer.put(payloadUnknown!!)
        }
    }

    override fun read(buffer: ByteBuffer) {
        val startIp = buffer.position()

        assert(buffer.get() == IPv4_VERSION_AND_HEADER_LENGTH)
        buffer.move(Byte.SIZE_BYTES) // ignore tos

        val payloadLength = buffer.short - headerLength
        assertAlways(payloadLength >= 0)

        identification = buffer.short

        assertAlways(buffer.short.toInt() and 0b101_111111111 == 0) // don't allow fragment

        buffer.move(Byte.SIZE_BYTES) // ignore ttl
        protocol = buffer.get()

        buffer.move(Short.SIZE_BYTES) // skip given checksum
        buffer.get(srcAddress)
        buffer.get(dstAddress)

        assertAlways(calcChecksum(startIp, buffer) == IPv4_CORRECT_CHECKSUM)

        if (payloadLength > 0) {
            when (protocol) {
                IP_PROTOCOL_UDP -> payloadUDPDatagram = UDPDatagram().also { it.read(buffer) }
                else -> payloadUnknown = ByteArray(payloadLength).also { buffer.get(it) }
            }
        }
    }

    private fun calcChecksum(start: Int, buffer: ByteBuffer): Short {
        var sum = 0

        (start until (start + headerLength) step 2).forEach {
            sum = sum.addOnesComplement(buffer.getShort(it).toIntAsUShort())
        }

        val invSum = sum.inv()

        return if (invSum and 0x0000FFFF == 0) {
            -1 // differentiated from no-checksum-type UDP
        } else {
            invSum.toShort()
        }
    }














}
