package kittoku.mvc.service.client.dhcp

import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.MvcException
import kittoku.mvc.extension.copy
import kittoku.mvc.extension.isSame
import kittoku.mvc.extension.read
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlMessage
import kittoku.mvc.unit.dhcp.*
import kittoku.mvc.unit.ethernet.ETHERNET_BROADCAST_ADDRESS
import kittoku.mvc.unit.ethernet.ETHER_TYPE_IPv4
import kittoku.mvc.unit.ethernet.EthernetFrame
import kittoku.mvc.unit.ip.IP_PROTOCOL_UDP
import kittoku.mvc.unit.ip.IPv4Packet
import kittoku.mvc.unit.ip.IPv4_BROADCAST_ADDRESS
import kittoku.mvc.unit.ip.IPv4_UNKNOWN_ADDRESS
import kittoku.mvc.unit.udp.UDPDatagram
import kittoku.mvc.unit.udp.UDP_PORT_DHCP_CLIENT
import kittoku.mvc.unit.udp.UDP_PORT_DHCP_SEVER
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull


internal class DhcpClient(private val bridge: ClientBridge) {
    internal fun launchJobInitial() {
        bridge.scope.launch {
            while (isActive) {
                val offer = startDiscoverOfferSequence(DHCP_RESEND_MESSAGE_TIMEOUT) ?: continue
                val ack = startRequestAckSequence(offer, DHCP_RESEND_MESSAGE_TIMEOUT) ?: continue

                if (!registerDhcpInformation(ack)) {
                    throw MvcException(ErrorCode.DHCP_INVALID_CONFIGURATION_ASSIGNED)
                }

                break
            }

            bridge.controlMailbox.send(ControlMessage.DHCP_NEGOTIATION_FINISHED)
        }
    }

    private fun prepareBasicOptionsParameters(): ByteArray {
        val parameters = mutableListOf<Byte>()

        parameters.add(DHCP_OPTION_SUBNET_MASK)
        parameters.add(DHCP_OPTION_ROUTER_ADDRESS)
        parameters.add(DHCP_OPTION_DNS_SERVER_ADDRESS)

        return parameters.toByteArray()
    }

    private fun extractMessageToMe(frame: EthernetFrame): DhcpMessage? {
        val isBroadcastFrame = frame.dstMac.isSame(ETHERNET_BROADCAST_ADDRESS)
        val isToMeFrame = frame.dstMac.isSame(bridge.clientMacAddress)
        if (!(isBroadcastFrame || isToMeFrame)) {
            return null
        }

        val packet = frame.payloadIPv4Packet!!
        val isBroadcastPacket = packet.dstAddress.isSame(IPv4_BROADCAST_ADDRESS)
        val isToMePacket = packet.dstAddress.isSame(bridge.assignedIpAddress)
        if (!(isBroadcastPacket || isToMePacket)) {
            return null
        }

        val datagram = packet.payloadUDPDatagram!!
        if (datagram.dstPort != UDP_PORT_DHCP_CLIENT || datagram.srcPort != UDP_PORT_DHCP_SEVER) {
            return null
        }

        val message = datagram.payloadDhcpMessage!!
        if (!message.clientMacAddress.isSame(bridge.clientMacAddress)) {
            return null
        }

        return datagram.payloadDhcpMessage!!
    }

    private suspend fun sendAsBroadcast(message: DhcpMessage) {
        val datagram = UDPDatagram().also {
            it.dstPort = UDP_PORT_DHCP_SEVER
            it.srcPort = UDP_PORT_DHCP_CLIENT
            it.payloadDhcpMessage = message
        }

        val packet = IPv4Packet().also {
            it.protocol = IP_PROTOCOL_UDP
            it.identification = bridge.random.nextInt().toShort()
            it.dstAddress.read(IPv4_BROADCAST_ADDRESS)
            it.srcAddress.read(IPv4_UNKNOWN_ADDRESS)
            it.payloadUDPDatagram = datagram

            datagram.importIPv4Header(it)
        }

        val frame = EthernetFrame().also {
            it.etherType = ETHER_TYPE_IPv4
            it.dstMac.read(ETHERNET_BROADCAST_ADDRESS)
            it.srcMac.read(bridge.clientMacAddress)
            it.payloadIPv4Packet = packet
        }

        bridge.controlChannel.send(frame)
    }

    private suspend fun expectOfferMessage(transactionId: Int): DhcpMessage? {
        while (true) {
            val received = bridge.dhcpChannel.receive()
            val reply = extractMessageToMe(received) ?: continue
            if (reply.transactionId != transactionId) continue

            return if (reply.options.messageType == DHCP_MESSAGE_TYPE_OFFER) {
                reply
            } else null
        }
    }

    private suspend fun expectAckMessage(transactionId: Int): DhcpMessage? {
        while (true) {
            val reply = extractMessageToMe(bridge.dhcpChannel.receive()) ?: continue
            if (reply.transactionId != transactionId) continue

            return if (reply.options.messageType == DHCP_MESSAGE_TYPE_ACK) {
                reply
            } else null
        }
    }

    private suspend fun startDiscoverOfferSequence(timeout: Long): DhcpMessage? {
         return withTimeoutOrNull(timeout) {
             val transactionId = bridge.random.nextInt()

             val options = OptionPack().also {
                 it.messageType = DHCP_MESSAGE_TYPE_DISCOVER
                 it.optionParameterList = DhcpOptionParameterList().also { option ->
                     option.value = prepareBasicOptionsParameters()
                 }
             }

             val message = DhcpMessage().also {
                 it.opcode = DHCP_OPCODE_BOOT_REQUEST
                 it.transactionId = transactionId
                 it.clientMacAddress.read(bridge.clientMacAddress)
                 it.options = options
             }

             sendAsBroadcast(message)
             expectOfferMessage(transactionId)
        }
    }

    private suspend fun startRequestAckSequence(offer: DhcpMessage, timeout: Long): DhcpMessage? {
        return withTimeoutOrNull(timeout) {
            val options = OptionPack().also {
                it.messageType = DHCP_MESSAGE_TYPE_REQUEST
                it.optionRequestedAddress = DhcpOptionRequestedAddress().also { option ->
                    option.address.read(offer.yourIpAddress)
                }
                it.optionDhcpServerAddress = offer.options.optionDhcpServerAddress
                it.optionParameterList = DhcpOptionParameterList().also { option ->
                    option.value = prepareBasicOptionsParameters()
                }
            }

            val message = DhcpMessage().also {
                it.opcode = DHCP_OPCODE_BOOT_REQUEST
                it.transactionId = offer.transactionId
                it.clientMacAddress.read(bridge.clientMacAddress)
                it.options = options
            }

            sendAsBroadcast(message)
            expectAckMessage(offer.transactionId)
        }
    }

    private fun registerDhcpInformation(ack: DhcpMessage): Boolean {
        if (ack.yourIpAddress.isSame(IPv4_UNKNOWN_ADDRESS)) return false
        bridge.assignedIpAddress.read(ack.yourIpAddress)

        val subnetMask = ack.options.optionSubnetMask?.address ?: return false
        if (subnetMask.isSame(IPv4_UNKNOWN_ADDRESS)) return false
        bridge.subnetMask.read(subnetMask)

        val defaultGatewayAddress = ack.options.optionRouterAddress?.address ?: return false
        if (defaultGatewayAddress.isSame(IPv4_UNKNOWN_ADDRESS)) return false
        bridge.defaultGatewayIpAddress.read(defaultGatewayAddress)

        val dhcpServerAddress = ack.options.optionDhcpServerAddress?.address ?: return false
        if (dhcpServerAddress.isSame(IPv4_UNKNOWN_ADDRESS)) return false
        bridge.dhcpServerIpAddress.read(dhcpServerAddress)

        ack.options.optionDnsServerAddress?.also {
            if (it.address.isSame(IPv4_UNKNOWN_ADDRESS)) return false
            bridge.dnsServerIpAddress = it.address.copy()
        }

        ack.options.optionLeaseTime?.also {
            if (it.length < 0) return false
            bridge.leaseTime = it.length.toLong() * 1_000 // as millisecond
        }

        return true
    }
}
