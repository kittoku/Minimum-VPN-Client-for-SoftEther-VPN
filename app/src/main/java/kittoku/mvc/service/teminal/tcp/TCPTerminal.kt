package kittoku.mvc.service.teminal.tcp

import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.MvcException
import kittoku.mvc.extension.capacityAfterPayload
import kittoku.mvc.service.client.ClientBridge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory


internal class TCPTerminal(private val bridge: ClientBridge) {
    internal lateinit var socket: SSLSocket
    private val mutex = Mutex()

    internal fun createSocket() {
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
    }

    internal fun setTimeoutForControl() {
        socket.soTimeout = CONTROL_UNIT_WAIT_TIMEOUT.toInt()
    }

    internal fun setTimeoutForData() {
        socket.soTimeout = DATA_UNIT_WAIT_TIMEOUT.toInt()
    }

    internal suspend fun sendStream(buffer: ByteBuffer) {
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
                throw MvcException(ErrorCode.TCP_SOCKET_CLOSED)
            }

            buffer.limit(buffer.limit() + readLength)
        } catch (_: SocketTimeoutException) { }
    }

    internal fun extendStream(buffer: ByteBuffer) {
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

    internal fun close() {
        if (::socket.isInitialized) socket.close()
    }
}
