package kittoku.mvc.service.teminal

import kittoku.mvc.extension.match
import kittoku.mvc.service.client.UDP_PORT_ECHO
import kittoku.mvc.unit.ethernet.ETHERNET_BROADCAST_ADDRESS
import kittoku.mvc.unit.ip.IP_PROTOCOL_UDP
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

internal fun isEchoFrame(buffer: ByteBuffer): Boolean {
    if (buffer.remaining() < 24) { // 24 = d(IPHeaderStart, UDPPortStop)
        return false
    }

    val protocolIndex = buffer.position() + 9
    if (buffer.get(protocolIndex) != IP_PROTOCOL_UDP) {
        return false
    }

    val srcPortIndex = protocolIndex + 11
    if (buffer.getShort(srcPortIndex) != UDP_PORT_ECHO) {
        return false
    }

    val dstPortIndex = srcPortIndex + 2
    if (buffer.getShort(dstPortIndex) != UDP_PORT_ECHO) {
        return false
    }

    return true
}
