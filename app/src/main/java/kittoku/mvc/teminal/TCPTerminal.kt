package kittoku.mvc.teminal

import kittoku.mvc.SharedBridge
import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.capacityAfterPayload
import kittoku.mvc.unit.EthernetFrame
import kittoku.mvc.unit.HttpMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory


internal const val TCP_SOFTETHER_HEADER_SIZE = 8 + 4 // for first frame; 8 == SoftEther header, 4 == Frame Header
private const val TCP_CONTROL_UNIT_WAIT_TIMEOUT = 10
private const val TCP_DATA_UNIT_WAIT_TIMEOUT = 1_000
private const val TCP_KEEP_ALIVE_TIMEOUT = 20_000
internal const val TCP_KEEP_ALIVE_MIN_INTERVAL = TCP_KEEP_ALIVE_TIMEOUT / 5
internal const val TCP_KEEP_ALIVE_INTERVAL_DIFF = TCP_KEEP_ALIVE_TIMEOUT / 2 - TCP_KEEP_ALIVE_MIN_INTERVAL

internal class TCPTerminal(private val bridge: SharedBridge) {
    private val socket: SSLSocket

    private val outgoingBuffer = ByteBuffer.allocate(16384)
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
        bridge.socket = socket
    }

    internal fun setTimeoutForData() {
        socket.soTimeout = TCP_DATA_UNIT_WAIT_TIMEOUT
    }


    internal suspend fun sendStream(buffer: ByteBuffer) {
        mutex.withLock {
            buffer.array().sliceArray(buffer.position() until buffer.limit())

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

            assertAlways(readLength >= 0)

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

    internal suspend fun ensureBytes(minBytes: Int, buffer: ByteBuffer) {
        while (true) {
            yield()

            if (buffer.remaining() >= minBytes) {
                return
            } else {
                extendStream(buffer)
            }
        }
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

    internal suspend fun sendHttpMessage(message: HttpMessage) {
        outgoingBuffer.clear()
        message.write(outgoingBuffer)
        outgoingBuffer.flip()
        sendStream(outgoingBuffer)
    }

    internal fun close() {
        socket.close()
    }
}