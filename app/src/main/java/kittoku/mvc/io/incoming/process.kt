package kittoku.mvc.io.incoming

import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.indexOf
import kittoku.mvc.extension.move
import kittoku.mvc.extension.probeInt
import kittoku.mvc.unit.ETHERNET_HEADER_SIZE
import kittoku.mvc.unit.HttpMessage
import kittoku.mvc.unit.KeepAlivePacket
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import kotlin.math.min


private const val HTTP_SUFFIX = "\r\n\r\n"
private val HTTP_SUFFIX_BYTES = HTTP_SUFFIX.toByteArray(Charsets.UTF_8)

internal suspend fun IncomingManager.processDataPacket(frameSize: Int, buffer: ByteBuffer) {
    bridge.ipTerminal?.also {
        val start = buffer.position() + ETHERNET_HEADER_SIZE
        val packetSize = frameSize - ETHERNET_HEADER_SIZE

        it.writePacket(start, packetSize, buffer.array())
    }

    buffer.move(frameSize)
}

internal suspend fun IncomingManager.processKeepAlivePacket(buffer: ByteBuffer) {
    bridge.tcpTerminal!!.ensureBytes(Int.SIZE_BYTES * 2, buffer)
    val frameSize = buffer.probeInt(Int.SIZE_BYTES)
    bridge.tcpTerminal!!.ensureBytes(frameSize + Int.SIZE_BYTES * 2, buffer)

    val packet = KeepAlivePacket().also {
        it.read(buffer)
    }

    bridge.udpAccelerationConfig?.also {
        packet.extractNATTInfo()

        if (packet.nattPort != 0) {
            it.serverNATTPort = packet.nattPort
        }

        packet.nattAddress?.also { address ->
            it.serverNATTAddress = address
        }
    }
}

internal suspend fun IncomingManager.relayHttpMessage(buffer: ByteBuffer) {
    var header = ""

    // find header
    while (true) {
        yield()
        bridge.tcpTerminal!!.ensureBytes(HTTP_SUFFIX_BYTES.size, buffer)

        val startSuffix = buffer.indexOf(HTTP_SUFFIX_BYTES)
        if (startSuffix != null) {
            header += String(buffer.array(), buffer.position(), startSuffix - buffer.position(), Charsets.UTF_8)
            buffer.position(startSuffix + HTTP_SUFFIX_BYTES.size)
            break
        }

        header += String(buffer.array(), buffer.position(), buffer.remaining() - 3, Charsets.UTF_8)
        buffer.position(buffer.limit() - 3)
        // `-3` for HTTP_SUFFIX_BYTES being fragmented
    }


    val message = HttpMessage()
    header.split("\r\n").forEachIndexed { i, line ->
        if (i == 0){
            message.startLine = line
        } else {
            val delimiterIndex = line.indexOf(": ")
            assertAlways(delimiterIndex > 0)

            message.fields[line.take(delimiterIndex)] = line.substring(delimiterIndex + 2)
        }
    }


    message.fields["Content-Length"]?.also {
        val body = ByteArray(it.toInt())
        var writeStart = 0

        while (true) {
            val writeSize = min(body.size - writeStart, buffer.remaining())
            buffer.get(body, writeStart, writeSize)
            writeStart += writeSize

            if (writeSize == body.size) {
                break
            } else {
                bridge.tcpTerminal!!.ensureBytes(1, buffer)
            }
        }

        message.body = body
    }

    softEtherMailbox?.send(message)
}