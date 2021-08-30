package kittoku.mvc.service.client.keepalive

import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlMessage
import kittoku.mvc.unit.keepalive.KeepAlivePacket
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


internal class KeepAliveClient(private val bridge: ClientBridge) {
    private val minInterval = KEEP_ALIVE_TIMEOUT / 5
    private val diff = KEEP_ALIVE_TIMEOUT / 2 - minInterval

    internal fun launchJobKeepAlive() {
        bridge.scope.launch {
            sendKeepAlive()

            bridge.controlMailbox.send(ControlMessage.KEEP_ALIVE_INIT_FINISHED)

            var nextTime: Long = System.currentTimeMillis() + generateInterval()

            while (isActive) {
                val currentTime = System.currentTimeMillis()

                if (currentTime >= nextTime) {
                    sendKeepAlive()

                    nextTime = System.currentTimeMillis() + generateInterval()
                } else {
                    delay(nextTime - currentTime)
                }
            }
        }
    }

    private fun generateInterval(): Long {
        return (minInterval + bridge.random.nextInt(diff)).toLong()
    }

    private suspend fun sendKeepAlive() {
        val packet = KeepAlivePacket()
        packet.importRandomBytes(bridge.random)
        bridge.controlChannel.send(packet)
    }
}
