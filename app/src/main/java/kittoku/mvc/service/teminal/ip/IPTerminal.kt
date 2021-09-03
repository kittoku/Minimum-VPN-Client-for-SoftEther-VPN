package kittoku.mvc.service.teminal.ip

import android.os.ParcelFileDescriptor
import kittoku.mvc.extension.toInetAddress
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.unit.ip.IPv4_VERSION_AND_HEADER_LENGTH
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer


internal class IPTerminal(private val bridge: ClientBridge) {
    private lateinit var fd: ParcelFileDescriptor
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream

    private lateinit var jobRetrieve: Job
    private val retrieveChannel = Channel<ByteBuffer>(0)

    internal fun initializeBuilder() {
        val builder = bridge.service.Builder()

        val prefixLength = bridge.subnetMask.let {
            val buffer = ByteBuffer.wrap(it)
            buffer.int.countOneBits()
        }

        builder.addAddress(bridge.assignedIpAddress.toInetAddress(), prefixLength)
        builder.addRoute("0.0.0.0", 0)

        bridge.dnsServerIpAddress?.also {
            builder.addDnsServer(it.toInetAddress())
        }

        builder.setBlocking(true)
        builder.setMtu(IP_MTU)

        fd = builder.establish()!!
        inputStream = FileInputStream(fd.fileDescriptor)
        outputStream = FileOutputStream(fd.fileDescriptor)
    }

    internal fun launchJobRetrieve() {
        jobRetrieve = bridge.scope.launch(bridge.handler) {
            val alpha = ByteBuffer.allocate(IP_MTU)
            val beta = ByteBuffer.allocate(IP_MTU)

            var isAlphaGo = true

            while (isActive) {
                if (isAlphaGo) {
                    retrievePacket(alpha)
                    isAlphaGo = false
                } else {
                    retrievePacket(beta)
                    isAlphaGo = true
                }
            }
        }
    }

    private suspend fun retrievePacket(buffer: ByteBuffer) {
        buffer.clear()
        val readLength = inputStream.read(buffer.array())
        buffer.limit(readLength)

        if (buffer.get(0) == IPv4_VERSION_AND_HEADER_LENGTH) { // send only IPv4 packet
            retrieveChannel.send(buffer)
        }
    }

    internal suspend fun waitOutgoingPacket() = retrieveChannel.receive()

    internal fun pollOutgoingPacket() = retrieveChannel.poll()

    internal fun feedIncomingPacket(buffer: ByteBuffer) {
        outputStream.write(buffer.array(), buffer.position(), buffer.remaining())
        outputStream.flush()

        buffer.position(buffer.limit())
    }

    internal fun close() {
        if (::fd.isInitialized) fd.close()
    }
}
