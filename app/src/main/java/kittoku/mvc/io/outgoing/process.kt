package kittoku.mvc.io.outgoing

import kittoku.mvc.unit.KeepAlivePacket
import java.nio.ByteBuffer


internal suspend fun OutgoingManager.sendTCPKeepAlive() {
    val packet = KeepAlivePacket().also {
        it.nattAddress = bridge.udpAccelerationConfig?.clientNATTAddress
        it.nattPort = bridge.udpAccelerationConfig?.clientNATTPort ?: 0
        it.preparePacket(bridge.random)
    }

    val buffer = ByteBuffer.allocate(packet.length)
    buffer.clear()
    packet.write(buffer)
    buffer.flip()
    bridge.tcpTerminal!!.sendStream(buffer)
}

internal fun OutgoingManager.addOutGoingPacket(buffer: ByteBuffer) {
    mainBuffer.putInt(buffer.remaining())
    mainBuffer.put(buffer)
}

internal suspend fun OutgoingManager.sendOutgoingPacket(frameNum: Int) {
    mainBuffer.putInt(0, frameNum)
    mainBuffer.flip()
    bridge.tcpTerminal!!.sendStream(mainBuffer)
}