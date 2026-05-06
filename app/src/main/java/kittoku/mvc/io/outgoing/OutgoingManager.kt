package kittoku.mvc.io.outgoing

import androidx.preference.PreferenceManager
import kittoku.mvc.SharedBridge
import kittoku.mvc.control.NetworkObserver
import kittoku.mvc.extension.move
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.setStringPrefValue
import kittoku.mvc.teminal.TCP_KEEP_ALIVE_INTERVAL_DIFF
import kittoku.mvc.teminal.TCP_KEEP_ALIVE_MIN_INTERVAL
import kittoku.mvc.teminal.TCP_SOFTETHER_HEADER_SIZE
import kittoku.mvc.teminal.UDPStatus
import kittoku.mvc.teminal.UDP_KEEP_ALIVE_INTERVAL_DIFF
import kittoku.mvc.teminal.UDP_KEEP_ALIVE_MIN_INTERVAL
import kittoku.mvc.teminal.UDP_NATT_INTERVAL_DIFF
import kittoku.mvc.teminal.UDP_NATT_INTERVAL_INITIAL
import kittoku.mvc.teminal.UDP_NATT_INTERVAL_MIN
import kittoku.mvc.teminal.UDP_NATT_PORT
import kittoku.mvc.unit.ETHERNET_HEADER_SIZE
import kittoku.mvc.unit.ETHERNET_MAX_MTU
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.nio.ByteBuffer
import kotlin.math.max


internal class OutgoingManager(internal val bridge: SharedBridge) {
    private val bufferSize = max(bridge.socket.session.applicationBufferSize, TCP_SOFTETHER_HEADER_SIZE + ETHERNET_MAX_MTU)
    internal val mainBuffer = ByteBuffer.allocate(bufferSize)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(bridge.service)
    private var observer: NetworkObserver? = null

    private var jobMain: Job? = null
    private var jobRetrieve: Job? = null
    private var jobTCPKeepAlive: Job? = null
    private var jobUDPKeepAlive: Job? = null
    private var jobUDPInquireNATT: Job? = null

    private val retrieveChannel = Channel<ByteBuffer>(0)

    internal fun launchJobMain() {
        jobMain = bridge.scope.launch(bridge.handler) {
            val minCapacity = TCP_SOFTETHER_HEADER_SIZE + ETHERNET_MAX_MTU

            var lastUDPStatus = UDPStatus.CLOSED

            while (isActive) {
                val firstPacket = retrieveChannel.receive()

                // send through UDP hole if possible
                if (bridge.udpTerminal != null) {
                    val currentUDPStatus = bridge.udpAccelerationConfig!!.status

                    if (currentUDPStatus != lastUDPStatus) {
                        setStringPrefValue(currentUDPStatus.name, MvcPreference.UDP_STATUS, prefs)
                        lastUDPStatus = currentUDPStatus
                    }

                    if (currentUDPStatus == UDPStatus.OPEN) {
                        bridge.udpTerminal!!.sendData(firstPacket)
                        continue
                    }
                }

                // finally TCP connection is needed
                mainBuffer.clear()
                mainBuffer.move(Int.SIZE_BYTES)
                addOutGoingPacket(firstPacket)
                var frameNum = 1

                while (mainBuffer.remaining() >= minCapacity) {
                    val polled = retrieveChannel.tryReceive().getOrNull() ?: break
                    addOutGoingPacket(polled)
                    frameNum += 1
                }

                sendOutgoingPacket(frameNum)
            }
        }
    }

    internal fun launchJobRetrieve() {
        jobRetrieve = bridge.scope.launch(bridge.handler) {
            val bufferSize = bridge.internalEthernetMTU + ETHERNET_HEADER_SIZE
            val alpha = ByteBuffer.allocate(bufferSize)
            val beta = ByteBuffer.allocate(bufferSize)

            var isAlphaGo = true

            while (isActive) {
                if (isAlphaGo) {
                    bridge.ipTerminal!!.retrievePacket(alpha)
                    retrieveChannel.send(alpha)
                    isAlphaGo = false
                } else {
                    bridge.ipTerminal!!.retrievePacket(beta)
                    retrieveChannel.send(beta)
                    isAlphaGo = true
                }
            }
        }
    }

    internal fun launchJobTCPKeepAlive() {
        jobTCPKeepAlive = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                sendTCPKeepAlive()

                (TCP_KEEP_ALIVE_MIN_INTERVAL + bridge.random.nextInt(TCP_KEEP_ALIVE_INTERVAL_DIFF)).toLong().also {
                    delay(it)
                }
            }
        }
    }

    internal fun launchJobUDPKeepAlive() {
        jobUDPKeepAlive = bridge.scope.launch(bridge.handler) {
            val buffer = ByteBuffer.allocate(0)

            while (isActive) {
                bridge.udpTerminal!!.sendData(buffer)

                (UDP_KEEP_ALIVE_MIN_INTERVAL + bridge.random.nextInt(UDP_KEEP_ALIVE_INTERVAL_DIFF)).toLong().also {
                    delay(it)
                }
            }
        }
    }

    internal fun launchJobInquireNATT() {
        jobUDPInquireNATT = bridge.scope.launch(bridge.handler) {
            val packet = DatagramPacket(
                "B".toByteArray(Charsets.US_ASCII),
                1, bridge.udpAccelerationConfig!!.nattAddress, UDP_NATT_PORT
            )

            while (isActive) {
                bridge.udpTerminal!!.sendPacket(packet)

                val interval = if (bridge.udpAccelerationConfig!!.status == UDPStatus.OPEN) {
                    UDP_NATT_INTERVAL_MIN + bridge.random.nextInt(UDP_NATT_INTERVAL_DIFF)
                } else {
                    UDP_NATT_INTERVAL_INITIAL
                }

                delay(interval.toLong())
            }
        }
    }

    internal fun cancel() {
        observer?.close()
        jobMain?.cancel()
        jobRetrieve?.cancel()
        jobTCPKeepAlive?.cancel()
        jobUDPKeepAlive?.cancel()
        jobUDPInquireNATT?.cancel()
    }
}