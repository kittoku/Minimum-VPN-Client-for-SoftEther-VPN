package kittoku.mvc.service.client.arp

import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.MvcException
import kittoku.mvc.extension.isSame
import kittoku.mvc.extension.read
import kittoku.mvc.extension.toBroadcastAddress
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlMessage
import kittoku.mvc.service.client.dhcp.DHCP_RESEND_MESSAGE_TIMEOUT
import kittoku.mvc.unit.arp.ARPPacket
import kittoku.mvc.unit.arp.ARP_OPCODE_REPLY
import kittoku.mvc.unit.arp.ARP_OPCODE_REQUEST
import kittoku.mvc.unit.ethernet.ETHERNET_BROADCAST_ADDRESS
import kittoku.mvc.unit.ethernet.ETHERNET_UNKNOWN_ADDRESS
import kittoku.mvc.unit.ethernet.ETHER_TYPE_ARP
import kittoku.mvc.unit.ethernet.EthernetFrame
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull


internal class ARPClient(private val bridge: ClientBridge) {
    internal fun launchJobInitial() { // resolve default gateway MAC address
        bridge.scope.launch {
            while (isActive) {
                val reply = startResolveDefaultGatewaySequence(DHCP_RESEND_MESSAGE_TIMEOUT) ?: continue

                if (!registerArpInformation(reply)) {
                    throw MvcException(ErrorCode.ARP_INVALID_CONFIGURATION_ASSIGNED, null)
                }

                break
            }

            bridge.controlMailbox.send(ControlMessage.ARP_NEGOTIATION_FINISHED)
        }
    }

    internal fun launchReplyBeacon() {
        bridge.scope.launch {
            val packet = ARPPacket().also {
                it.opcode = ARP_OPCODE_REPLY
                it.senderIp.read(bridge.assignedIpAddress)
                it.senderMac.read(bridge.clientMacAddress)
                it.targetIp.read(bridge.assignedIpAddress.toBroadcastAddress(bridge.subnetMask))
                it.targetMac.read(ETHERNET_BROADCAST_ADDRESS)
            }

            sendAsBroadcast(packet)
        }
    }

    private fun extractMessageToMe(frame: EthernetFrame): ARPPacket? {
        val isBroadcastFrame = frame.dstMac.isSame(ETHERNET_BROADCAST_ADDRESS)
        val isToMeFrame = frame.dstMac.isSame(bridge.clientMacAddress)
        if (!(isBroadcastFrame || isToMeFrame)) {
            return null
        }

        return frame.payloadARPPacket!!
    }

    private suspend fun sendAsBroadcast(packet: ARPPacket) {
        val frame = EthernetFrame().also {
            it.etherType = ETHER_TYPE_ARP
            it.dstMac.read(ETHERNET_BROADCAST_ADDRESS)
            it.srcMac.read(bridge.clientMacAddress)
            it.payloadARPPacket = packet
        }

        bridge.controlChannel.send(frame)
    }

    private suspend fun expectReplyPacket(): ARPPacket? {
        while (true) {
            val reply = extractMessageToMe(bridge.arpChannel.receive()) ?: continue

            return if (reply.opcode == ARP_OPCODE_REPLY) {
                reply
            } else null
        }
    }

    private suspend fun startResolveDefaultGatewaySequence(timeout: Long): ARPPacket? {
        return withTimeoutOrNull(timeout) {
            val packet = ARPPacket().also {
                it.opcode = ARP_OPCODE_REQUEST
                it.senderIp.read(bridge.assignedIpAddress)
                it.senderMac.read(bridge.clientMacAddress)
                it.targetIp.read(bridge.defaultGatewayIpAddress)
            }

            sendAsBroadcast(packet)
            expectReplyPacket()
        }
    }

    private fun registerArpInformation(reply: ARPPacket): Boolean {
        if (!reply.targetIp.isSame(bridge.assignedIpAddress)) return false
        if (!reply.targetMac.isSame(bridge.clientMacAddress)) return false
        if (!reply.senderIp.isSame(bridge.defaultGatewayIpAddress)) return false
        if (reply.senderMac.isSame(ETHERNET_UNKNOWN_ADDRESS)) return false
        if (reply.senderMac.isSame(ETHERNET_BROADCAST_ADDRESS)) return false

        bridge.defaultGatewayMacAddress.read(reply.senderMac)

        return true
    }
}
