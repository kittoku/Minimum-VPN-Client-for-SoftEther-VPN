package kittoku.mvc.service.teminal.ip

import android.os.ParcelFileDescriptor
import kittoku.mvc.extension.move
import kittoku.mvc.extension.toInetAddress
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.unit.ethernet.ETHERNET_HEADER_SIZE
import kittoku.mvc.unit.ethernet.ETHER_TYPE_IPv4
import kittoku.mvc.unit.ip.IPv4_VERSION_AND_HEADER_LENGTH
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
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

    private val mutex = Mutex()

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
            val bufferSize = IP_MTU + ETHERNET_HEADER_SIZE
            val alpha = ByteBuffer.allocate(bufferSize)
            val beta = ByteBuffer.allocate(bufferSize)

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
        while (true) {
            yield()

            buffer.clear()
            buffer.put(bridge.defaultGatewayMacAddress)
            buffer.put(bridge.clientMacAddress)
            buffer.putShort(ETHER_TYPE_IPv4)
            val readLength = inputStream.read(buffer.array(), buffer.position(), IP_MTU)
            buffer.move(readLength)
            buffer.flip()

            if (buffer.get(ETHERNET_HEADER_SIZE) == IPv4_VERSION_AND_HEADER_LENGTH) break // send only IPv4 packet
        }

        retrieveChannel.send(buffer)
    }

    internal suspend fun waitOutgoingPacket() = retrieveChannel.receive()

    internal fun pollOutgoingPacket() = retrieveChannel.poll()

    internal suspend fun feedIncomingPacket(buffer: ByteBuffer) {
        mutex.withLock {
            outputStream.write(buffer.array(), buffer.position(), buffer.remaining())
            outputStream.flush()

            buffer.position(buffer.limit())
        }
    }

    internal fun close() {
        if (::fd.isInitialized) fd.close()
    }
}
