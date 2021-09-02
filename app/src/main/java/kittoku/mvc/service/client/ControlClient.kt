package kittoku.mvc.service.client

import android.util.Log
import androidx.preference.PreferenceManager
import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.MvcException
import kittoku.mvc.debug.assertAlways
import kittoku.mvc.debug.assertOrThrow
import kittoku.mvc.extension.*
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.setBooleanPrefValue
import kittoku.mvc.service.client.*
import kittoku.mvc.service.client.arp.ARPClient
import kittoku.mvc.service.client.dhcp.DhcpClient
import kittoku.mvc.service.client.keepalive.KeepAliveClient
import kittoku.mvc.service.client.softether.SoftEtherClient
import kittoku.mvc.service.teminal.ip.IPTerminal
import kittoku.mvc.service.teminal.tcp.TCPTerminal
import kittoku.mvc.unit.ethernet.*
import kittoku.mvc.unit.http.HttpMessage
import kittoku.mvc.unit.ip.IP_PROTOCOL_UDP
import kittoku.mvc.unit.keepalive.KEEP_ALIVE_FRAME_NUM
import kittoku.mvc.unit.keepalive.KeepAlivePacket
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer


internal class ControlClient(private val bridge: ClientBridge) {
    private lateinit var tcpTerminal: TCPTerminal
    private lateinit var ipTerminal: IPTerminal
    private lateinit var observer: NetworkObserver

    private lateinit var softEtherClient: SoftEtherClient
    private lateinit var keepAliveClient: KeepAliveClient
    private lateinit var dhcpClient: DhcpClient
    private lateinit var arpClient: ARPClient

    private val incomingBuffer = ByteBuffer.allocate(16384)
    private val outgoingBuffer = ByteBuffer.allocate(16384)

    private val mailbox = bridge.controlMailbox

    private val macAddressHolder = ByteArray(ETHERNET_MAC_ADDRESS_SIZE)

    private var isClosing = false
    private val mutex = Mutex()

    private lateinit var jobIncoming: Job
    private lateinit var jobOutgoing: Job
    private lateinit var jobControl: Job

    internal fun run() {
        launchJobIncoming()
    }

    private fun launchJobIncoming() {
        jobIncoming = bridge.scope.launch(bridge.handler) {
            tcpTerminal = TCPTerminal(bridge).also { it.createSocket() }
            tcpTerminal.setTimeoutForControl()
            ipTerminal = IPTerminal(bridge)

            discardPayload(incomingBuffer)


            // SoftEther negotiation
            softEtherClient = SoftEtherClient(bridge).also { it.launchJobNegotiation() }

            launchJobControl()

            withTimeoutOrNull(SOFTETHER_NEGOTIATION_TIMEOUT) {
                repeat(3) {
                    relaySoftEtherMessage()
                }

                assertAlways(mailbox.receive() == ControlMessage.SOFTETHER_NEGOTIATION_FINISHED)
                bridge.softEtherChannel.clear()
            } ?: throw MvcException(ErrorCode.SOFTETHER_NEGOTIATION_TIMEOUT)


            // send first keep alive
            keepAliveClient = KeepAliveClient(bridge).also { it.launchJobKeepAlive() }

            withTimeoutOrNull(KEEP_ALIVE_INIT_TIMEOUT) {
                assertAlways(mailbox.receive() == ControlMessage.KEEP_ALIVE_INIT_FINISHED)
            } ?: throw MvcException(ErrorCode.KEEP_ALIVE_INIT_FAILED)


            // DHCP negotiation
            dhcpClient = DhcpClient(bridge).also { it.launchJobInitial() }

            withTimeoutOrNull(DHCP_NEGOTIATION_TIMEOUT) {
                while (isActive) {
                    relayDhcpMessage()

                    if (mailbox.poll() == ControlMessage.DHCP_NEGOTIATION_FINISHED) {
                        bridge.dhcpChannel.clear()
                        break
                    }
                }
            } ?: throw MvcException(ErrorCode.DHCP_NEGOTIATION_TIMEOUT)


            // ARP negotiation
            arpClient = ARPClient(bridge).also { it.launchJobInitial() }

            withTimeoutOrNull(ARP_NEGOTIATION_TIMEOUT) {
                while (isActive) {
                    relayAprPacket()

                    if (mailbox.poll() == ControlMessage.ARP_NEGOTIATION_FINISHED) {
                        bridge.arpChannel.clear()
                        break
                    }
                }
            } ?: throw MvcException(ErrorCode.ARP_NEGOTIATION_TIMEOUT)


            // if this is test, we need to get out because VpnService.Builder is not given
            if (bridge.isTest) {
                return@launch
            }


            // start observing network
            observer = NetworkObserver(bridge)


            // Establish VPN connection
            tcpTerminal.setTimeoutForData()
            bridge.service.protect(tcpTerminal.socket)
            ipTerminal.initializeBuilder()
            ipTerminal.launchJobRetrieve()
            launchJobOutgoing()

            while (isActive) {
                relayIPPacket()
            }
        }
    }

    private fun launchJobOutgoing() {
        jobOutgoing = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                outgoingBuffer.clear()
                outgoingBuffer.move(Int.SIZE_BYTES)

                ipTerminal.waitOutgoingPacket().also {
                    addEthernetHeaderForIPPacket(it.limit())
                    outgoingBuffer.put(it)
                }

                var frameNum = 1

                while (isActive) {
                    if (outgoingBuffer.remaining() < ETHERNET_MTU) break

                    ipTerminal.pollOutgoingPacket()?.also {
                        addEthernetHeaderForIPPacket(it.limit())
                        outgoingBuffer.put(it)
                        frameNum += 1
                    } ?: break
                }

                outgoingBuffer.putInt(0, frameNum)
                outgoingBuffer.flip()
                tcpTerminal.sendStream(outgoingBuffer)
            }
        }
    }

    private fun launchJobControl() {
        jobControl = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                when (val received = bridge.controlChannel.receive()) {
                    is HttpMessage -> {
                        if (::keepAliveClient.isInitialized) {
                            throw NotImplementedError()
                        }

                        outgoingBuffer.clear()
                        received.write(outgoingBuffer)
                        outgoingBuffer.flip()
                        tcpTerminal.sendStream(outgoingBuffer)
                    }

                    is EthernetFrame -> {
                        val buffer = ByteBuffer.allocate(2 * Int.SIZE_BYTES + received.length)
                        buffer.clear()
                        buffer.putInt(1)
                        buffer.putInt(received.length)
                        received.write(buffer)
                        buffer.flip()
                        tcpTerminal.sendStream(buffer)
                    }

                    is KeepAlivePacket -> {
                        val buffer = ByteBuffer.allocate(2 * Int.SIZE_BYTES + received.length)
                        buffer.clear()
                        received.write(buffer)
                        buffer.flip()
                        tcpTerminal.sendStream(buffer)
                    }

                    else -> throw NotImplementedError()
                }
            }
        }
    }

    private fun discardPayload(buffer: ByteBuffer) {
        buffer.position(0)
        buffer.limit(0)
    }

    private fun addEthernetHeaderForIPPacket(packetLength: Int) {
        outgoingBuffer.putInt(ETHERNET_HEADER_SIZE + packetLength)
        outgoingBuffer.put(bridge.defaultGatewayMacAddress)
        outgoingBuffer.put(bridge.clientMacAddress)
        outgoingBuffer.putShort(ETHER_TYPE_IPv4)
    }

    private fun searchCompleteFrameNum(): Int {
        if (incomingBuffer.remaining() < Int.SIZE_BYTES) {
            return 0
        }

        incomingBuffer.mark()

        var isKeepAlive = false

        var frameNum = incomingBuffer.int
        if (frameNum == KEEP_ALIVE_FRAME_NUM) {
            frameNum = 1
            isKeepAlive = true
        }

        assertOrThrow(ErrorCode.SOFTETHER_INVALID_FRAME_PACK) {
            assertAlways(frameNum > 0)
        }

        var completeFrameNum = 0
        repeat(frameNum) {
            if (incomingBuffer.remaining() < Int.SIZE_BYTES) {
                incomingBuffer.reset()
                return completeFrameNum
            }

            val frameLength = incomingBuffer.int
            assertOrThrow(ErrorCode.SOFTETHER_INVALID_FRAME_PACK) {
                assertAlways(frameLength > 0)
            }

            if (incomingBuffer.remaining() < frameLength) {
                incomingBuffer.reset()
                return completeFrameNum
            }

            incomingBuffer.move(frameLength)

            completeFrameNum += 1
        }

        return if (isKeepAlive) {
            0
        } else {
            incomingBuffer.reset()
            return frameNum
        }
    }

    private suspend fun ensureSomeFrame(): Int {
        var frameNum: Int

        while (true) {
            frameNum = searchCompleteFrameNum()

            if (frameNum > 0) {
                break
            } else {
                yield()
                tcpTerminal.extendStream(incomingBuffer)
            }
        }

        return frameNum
    }

    private fun expectValidFrame(): EthernetFrame? {
        val frame =  EthernetFrame()
        val frameLength = incomingBuffer.int
        val startFrame = incomingBuffer.position()
        val stopFrame = startFrame + frameLength
        val currentLimit = incomingBuffer.limit()
        incomingBuffer.limit(stopFrame) // avoid frame reading beyond expected length
        incomingBuffer.move(-Int.SIZE_BYTES) // leave length info for EtherFrame.read

        try {
            frame.read(incomingBuffer)
        } catch (e: Exception) { // TODO: need notify receiving invalid frame
            incomingBuffer.position(stopFrame) // discard frame
            incomingBuffer.limit(currentLimit)
            return null
        }

        if (incomingBuffer.hasRemaining()) {
            incomingBuffer.position(stopFrame)
            // TODO: need notify receiving invalid frame
        }

        incomingBuffer.limit(currentLimit)
        return frame
    }

    private fun isToMeFrame(buffer: ByteBuffer): Boolean {
        // start with Ethernet frame header, consume
        buffer.get(macAddressHolder)

        if (macAddressHolder.isSame(bridge.clientMacAddress)) {
            return true
        }

        if (macAddressHolder.isSame(ETHERNET_BROADCAST_ADDRESS)) {
            return true
        }

        return false
    }

    private fun isEcho(buffer: ByteBuffer): Boolean {
        // start with IP Header, don't consume
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

    private fun tryFeedIPPacket() {
        // avoid instantiating EthernetFrame for performance
        val frameLength = incomingBuffer.int
        val startFrame = incomingBuffer.position()
        val stopFrame = startFrame + frameLength
        val currentLimit = incomingBuffer.limit()
        incomingBuffer.limit(stopFrame) // avoid frame reading beyond expected length


        if (!isToMeFrame(incomingBuffer)) {
            incomingBuffer.position(stopFrame) // discard frame
            incomingBuffer.limit(currentLimit)
            return
        }

        incomingBuffer.move(ETHERNET_MAC_ADDRESS_SIZE) // ignore sender's MAC address

        if (incomingBuffer.short != ETHER_TYPE_IPv4) {
            incomingBuffer.position(stopFrame) // discard frame
            incomingBuffer.limit(currentLimit)
            return
        }

        if (isEcho(incomingBuffer)) {
            // send ARP Replay anyway
            arpClient.launchReplyBeacon()
        }

        ipTerminal.feedIncomingPacket(incomingBuffer)
        incomingBuffer.limit(currentLimit)
    }

    private suspend fun relaySoftEtherMessage() {
        var startPack = incomingBuffer.position()

        while (jobIncoming.isActive) {
            try {
                val response = HttpMessage()
                response.read(incomingBuffer)
                bridge.softEtherChannel.send(response)
                break
            } catch (e: Exception) {
                when (e) {
                    is BufferUnderflowException -> {
                        incomingBuffer.position(startPack)
                        tcpTerminal.extendStream(incomingBuffer)
                        startPack = incomingBuffer.position()
                        continue
                    }
                    is AssertionError -> {
                        throw MvcException(ErrorCode.SOFTETHER_INVALID_HTTP_MESSAGE)
                    }
                }
            }
        }
    }

    private suspend fun relayFromFramePack(body: suspend () -> Unit) {
        val actualFrameNum = ensureSomeFrame()
        val potentialFrameNum = incomingBuffer.int

        repeat(actualFrameNum) { body() }

        if (actualFrameNum < potentialFrameNum) {
            incomingBuffer.move(-Int.SIZE_BYTES)
            incomingBuffer.putInt(incomingBuffer.position(), potentialFrameNum - actualFrameNum)
        }
    }

    private suspend fun relayDhcpMessage() {
        relayFromFramePack {
            val frame = expectValidFrame()

            if (frame?.payloadIPv4Packet?.payloadUDPDatagram?.payloadDhcpMessage != null) {
                bridge.dhcpChannel.send(frame)
            }
        }
    }

    private suspend fun relayAprPacket() {
        relayFromFramePack {
            val frame = expectValidFrame()

            if (frame?.payloadARPPacket != null) {
                bridge.arpChannel.send(frame)
            }
        }
    }

    private suspend fun relayIPPacket() {
        relayFromFramePack {
            tryFeedIPPacket()
        }
    }

    internal fun kill(throwable: Throwable?) {
        bridge.scope.launch {
            mutex.withLock {
                if (!isClosing) {
                    // TODO: need to inform the stacktrace to user
                    Log.d("MVC", "EXCEPTION: \n" + throwable?.stackTraceToString())

                    isClosing = true

                    if (::tcpTerminal.isInitialized) tcpTerminal.close()
                    if (::ipTerminal.isInitialized) ipTerminal.close()
                    if (::observer.isInitialized) observer.close()

                    if (::jobControl.isInitialized) jobControl.cancel()
                    if (::jobIncoming.isInitialized) jobIncoming.cancel()
                    if (::jobOutgoing.isInitialized) jobOutgoing.cancel()

                    PreferenceManager.getDefaultSharedPreferences(bridge.service.applicationContext).also {
                        setBooleanPrefValue(false, MvcPreference.HOME_CONNECTOR, it)
                    }

                    bridge.service.stopForeground(true)
                    bridge.service.stopSelf()
                }
            }
        }
    }
}
