package kittoku.mvc.service.teminal.tcp

import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.MvcException
import kittoku.mvc.debug.assertAlways
import kittoku.mvc.debug.assertOrThrow
import kittoku.mvc.extension.capacityAfterPayload
import kittoku.mvc.extension.move
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlMessage
import kittoku.mvc.service.teminal.isEchoFrame
import kittoku.mvc.service.teminal.isToMeFrame
import kittoku.mvc.unit.ethernet.ETHERNET_MAC_ADDRESS_SIZE
import kittoku.mvc.unit.ethernet.ETHER_TYPE_IPv4
import kittoku.mvc.unit.ethernet.EthernetFrame
import kittoku.mvc.unit.http.HttpMessage
import kittoku.mvc.unit.keepalive.KEEP_ALIVE_FRAME_NUM
import kittoku.mvc.unit.keepalive.KeepAlivePacket
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.SocketTimeoutException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory


internal class TCPTerminal(private val bridge: ClientBridge) {
    private val socket: SSLSocket
    private lateinit var jobKeepAlive: Job

    private val incomingBuffer = ByteBuffer.allocate(16384).also {
        // discard payload
        it.position(0)
        it.limit(0)
    }

    private val outgoingBuffer = ByteBuffer.allocate(16384)
    private var outgoingFrameNum = 0
    private val minFrameBufferSize = bridge.internalEthernetMTU + Int.SIZE_BYTES // need size info

    private val mutex = Mutex()

    init {
        val socketFactory = SSLSocketFactory.getDefault()

        socket = socketFactory.createSocket(bridge.serverHostname, bridge.serverPort) as SSLSocket

        if (bridge.sslVersion != "DEFAULT") {
            socket.enabledProtocols = arrayOf(bridge.sslVersion)
        }

        if (bridge.doSelectCipherSuites) {
            socket.enabledCipherSuites = socket.supportedCipherSuites.filter {
                // the order of suites should be kept
                bridge.selectedCipherSuites.contains(it)
            }.toTypedArray()
        }

        socket.startHandshake()
        socket.soTimeout = TCP_CONTROL_UNIT_WAIT_TIMEOUT
        bridge.service.protect(socket)
    }

    internal fun setTimeoutForData() {
        socket.soTimeout = TCP_DATA_UNIT_WAIT_TIMEOUT
    }

    internal fun launchJobKeepAlive() {
        jobKeepAlive = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                sendKeepAlive()

                (TCP_KEEP_ALIVE_MIN_INTERVAL + bridge.random.nextInt(TCP_KEEP_ALIVE_INTERVAL_DIFF)).toLong().also {
                    delay(it)
                }
            }
        }
    }

    private suspend fun sendStream(buffer: ByteBuffer) {
        mutex.withLock {
            socket.outputStream.write(
                buffer.array(),
                buffer.position(),
                buffer.remaining()
            )

            socket.outputStream.flush()

            buffer.position(buffer.limit())
        }
    }

    private fun receiveStream(buffer: ByteBuffer) {
        try {
            val capacity = buffer.capacity() - buffer.limit()
            val readLength = socket.inputStream.read(buffer.array(), buffer.limit(), capacity)

            if (readLength < 0) {
                throw MvcException(ErrorCode.TCP_SOCKET_CLOSED, null)
            }

            buffer.limit(buffer.limit() + readLength)
        } catch (_: SocketTimeoutException) { }
    }

    private fun extendStream(buffer: ByteBuffer) {
        val payloadLengthBeforeExtended = buffer.remaining()

        if (buffer.capacityAfterPayload() < 1) { // need slide
            buffer.get(buffer.array(), 0, payloadLengthBeforeExtended) // slide

            // update startPayload
            buffer.position(0)

            // update stopPayload
            buffer.limit(payloadLengthBeforeExtended)
        }

        receiveStream(buffer)
    }

    internal suspend fun sendFrame(frame: EthernetFrame) {
        val buffer = ByteBuffer.allocate(2 * Int.SIZE_BYTES + frame.length)
        buffer.clear()
        buffer.putInt(1)
        buffer.putInt(frame.length)
        frame.write(buffer)
        buffer.flip()
        sendStream(buffer)
    }

    private suspend fun sendKeepAlive() {
        val packet = KeepAlivePacket().also {
            it.nattAddress = bridge.udpAccelerationConfig?.clientNATTAddress
            it.nattPort = bridge.udpAccelerationConfig?.clientNATTPort ?: 0
            it.preparePacket(bridge.random)
        }

        val buffer = ByteBuffer.allocate(packet.length)
        buffer.clear()
        packet.write(buffer)
        buffer.flip()
        sendStream(buffer)
    }

    internal suspend fun sendHttpMessage(message: HttpMessage) {
        if (::jobKeepAlive.isInitialized) {
            throw NotImplementedError()
        }

        outgoingBuffer.clear()
        message.write(outgoingBuffer)
        outgoingBuffer.flip()
        sendStream(outgoingBuffer)
    }

    internal suspend fun receiveHttpMessage(): HttpMessage {
        var startPack = incomingBuffer.position()

        while (true) {
            yield()

            try {
                val response = HttpMessage()
                response.read(incomingBuffer)
                return response
            } catch (e: Exception) {
                when (e) {
                    is BufferUnderflowException -> {
                        incomingBuffer.position(startPack)
                        extendStream(incomingBuffer)
                        startPack = incomingBuffer.position()
                        continue
                    }

                    is AssertionError -> {
                        throw MvcException(ErrorCode.SOFTETHER_INVALID_HTTP_MESSAGE, null)
                    }
                }
            }
        }
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
            bridge.udpAccelerationConfig?.also {
                incomingBuffer.reset()
                KeepAlivePacket().also { packet ->
                    packet.read(incomingBuffer)
                    packet.extractNATTInfo()

                    if (packet.nattPort != 0) {
                        it.serverNATTPort = packet.nattPort
                    }

                    packet.nattAddress?.also { address ->
                        it.serverNATTAddress = address
                    }
                }
            }

            0
        } else {
            incomingBuffer.reset()
            frameNum
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
                extendStream(incomingBuffer)
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

    private suspend fun consumeFramePack(body: suspend () -> Unit) {
        val actualFrameNum = ensureSomeFrame()
        val potentialFrameNum = incomingBuffer.int

        repeat(actualFrameNum) { body() }

        if (actualFrameNum < potentialFrameNum) {
            incomingBuffer.move(-Int.SIZE_BYTES)
            incomingBuffer.putInt(incomingBuffer.position(), potentialFrameNum - actualFrameNum)
        }
    }

    internal suspend fun consumeFrame(body: suspend (EthernetFrame) -> Unit) {
        consumeFramePack {
            expectValidFrame()?.also {
                body(it)
            }
        }
    }

    internal suspend fun consumeIPPacketBuffer(body: suspend (ByteBuffer) -> Unit) {
        consumeFramePack {
            // avoid instantiating EthernetFrame for performance
            val frameLength = incomingBuffer.int
            val startFrame = incomingBuffer.position()
            val stopFrame = startFrame + frameLength
            val currentLimit = incomingBuffer.limit()
            incomingBuffer.limit(stopFrame) // avoid frame reading beyond expected length


            if (isToMeFrame(incomingBuffer, bridge.clientMacAddress)) {
                incomingBuffer.move(ETHERNET_MAC_ADDRESS_SIZE)
            } else {
                incomingBuffer.position(stopFrame) // discard frame
                incomingBuffer.limit(currentLimit)
                return@consumeFramePack
            }

            incomingBuffer.move(ETHERNET_MAC_ADDRESS_SIZE) // ignore sender's MAC address

            if (incomingBuffer.short != ETHER_TYPE_IPv4) {
                incomingBuffer.position(stopFrame) // discard frame
                incomingBuffer.limit(currentLimit)
                return@consumeFramePack
            }

            if (isEchoFrame(incomingBuffer)) {
                // send ARP Replay anyway
                bridge.controlMailbox.send(ControlMessage.SECURE_NAT_ECHO_REQUEST)
            }

            body(incomingBuffer)

            incomingBuffer.limit(currentLimit)
        }
    }

    internal fun loadOutgoingPacket(buffer: ByteBuffer) {
        outgoingBuffer.clear()
        outgoingBuffer.move(Int.SIZE_BYTES)
        outgoingBuffer.putInt(buffer.remaining())
        outgoingBuffer.put(buffer)

        outgoingFrameNum = 1
    }

    internal fun addOutGoingPacket(buffer: ByteBuffer): Boolean {
        outgoingBuffer.putInt(buffer.remaining())
        outgoingBuffer.put(buffer)

        outgoingFrameNum += 1

        return outgoingBuffer.remaining() >= minFrameBufferSize
    }

    internal suspend fun sendOutgoingPacket() {
        outgoingBuffer.putInt(0, outgoingFrameNum)
        outgoingBuffer.flip()
        sendStream(outgoingBuffer)
    }

    internal fun close() {
        if (::jobKeepAlive.isInitialized) jobKeepAlive.cancel()
        socket.close()
    }
}
