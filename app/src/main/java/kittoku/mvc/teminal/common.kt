package kittoku.mvc.teminal

import kittoku.mvc.extension.match
import kittoku.mvc.extension.probeByte
import kittoku.mvc.extension.probeShort
import kittoku.mvc.unit.ETHERNET_BROADCAST_ADDRESS
import kittoku.mvc.unit.ETHERNET_HEADER_SIZE
import kittoku.mvc.unit.ETHERNET_MAC_ADDRESS_SIZE
import kittoku.mvc.unit.ETHER_TYPE_IPv4
import kittoku.mvc.unit.EthernetFrame
import kittoku.mvc.unit.IP_PROTOCOL_UDP
import kittoku.mvc.unit.IPv4_HEADER_SIZE
import kittoku.mvc.unit.UDP_PORT_DHCP_CLIENT
import kittoku.mvc.unit.UDP_PORT_ECHO
import java.nio.ByteBuffer


internal fun isToMeFrame(buffer: ByteBuffer, myMacAddress: ByteArray): Boolean {
    if (buffer.array().match(myMacAddress, buffer.position())) {
        return true
    }

    if (buffer.array().match(ETHERNET_BROADCAST_ADDRESS, buffer.position())) {
        return true
    }

    return false
}

internal fun isDataPacket(buffer: ByteBuffer): Boolean {
    if (buffer.probeShort(ETHERNET_MAC_ADDRESS_SIZE * 2) != ETHER_TYPE_IPv4) {
        return false
    }

    if (buffer.probeByte(ETHERNET_HEADER_SIZE + 9) != IP_PROTOCOL_UDP) {
        return true
    }

    if (buffer.probeShort(ETHERNET_HEADER_SIZE + IPv4_HEADER_SIZE + Short.SIZE_BYTES) == UDP_PORT_DHCP_CLIENT) {
        return false // DHCP message is handled by this app, not native stack
    }

    return true
}

internal fun isEchoFrame(frame: EthernetFrame): Boolean {
    val datagram = frame.payloadIPv4Packet?.payloadUDPDatagram ?: return false

    return datagram.srcPort == UDP_PORT_ECHO && datagram.dstPort == UDP_PORT_ECHO
}
