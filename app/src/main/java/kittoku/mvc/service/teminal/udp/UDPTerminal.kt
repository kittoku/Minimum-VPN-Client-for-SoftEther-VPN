package kittoku.mvc.service.teminal.udp

import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.MvcException
import kittoku.mvc.extension.*
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlMessage
import kittoku.mvc.service.teminal.isEchoFrame
import kittoku.mvc.service.teminal.isToMeFrame
import kittoku.mvc.service.teminal.tcp.DATA_UNIT_WAIT_TIMEOUT
import kittoku.mvc.unit.ethernet.ETHERNET_MAC_ADDRESS_SIZE
import kittoku.mvc.unit.ethernet.ETHER_TYPE_IPv4
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.*
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import kotlin.math.max
import kotlin.math.min


internal enum class UDPStatus {
    CLOSED,
    OPEN,
}

internal class UDPTerminal(private val bridge: ClientBridge) {
    private val socket: DatagramSocket
    private val config = bridge.udpAccelerationConfig!!

    private val clientCipher = Cipher.getInstance(UDP_CIPHER_ALGORITHM)
    private val serverCipher = Cipher.getInstance(UDP_CIPHER_ALGORITHM)

    private var lastReceivedTick: Long = 0
    private var lastReceivedClientTick: Long = 0
    private var lastReceivedServerTick: Long = 0

    private lateinit var jobKeepAlive: Job
    private lateinit var jobInquireNATT: Job

    private val socketMutex = Mutex()
    private val packetMutex = Mutex()

    private var _status = UDPStatus.CLOSED
    internal val status: UDPStatus
        get() = _status

    private val incomingPacket = DatagramPacket(ByteArray(UDP_BUFFER_SIZE), UDP_BUFFER_SIZE)
    private val outgoingPacket = DatagramPacket(ByteArray(UDP_BUFFER_SIZE), UDP_BUFFER_SIZE)
    private val nonceHolder = ByteArray(CHACHA20_POLY1305_NONCE_SIZE)
    private val encryptBuffer = ByteBuffer.allocate(UDP_BUFFER_SIZE)
    private val decryptBuffer = ByteBuffer.allocate(UDP_BUFFER_SIZE)

    private val regexIP = Regex(UDP_NATT_IP_REGEX)
    private val regexPort = Regex(UDP_NATT_PORT_REGEX)

    init {
        val address = kotlin.run {
            NetworkInterface.getNetworkInterfaces().iterator().forEach { nic ->
                nic.inetAddresses.iterator().forEach {
                    if (it is Inet4Address && !it.isAnyLocalAddress && !it.isLinkLocalAddress && !it.isLoopbackAddress) {
                        return@run it
                    }
                }
            }

            throw MvcException(ErrorCode.UDP_NO_AVAILABLE_IP_ADDRESS, null)
        }

        socket = DatagramSocket(0, address)

        config.clientReportedAddress = address
        config.clientReportedPort = socket.localPort

        socket.soTimeout = DATA_UNIT_WAIT_TIMEOUT

        bridge.service.protect(socket)
    }

    internal fun launchJobKeepAlive() {
        jobKeepAlive = bridge.scope.launch(bridge.handler) {
            val buffer = ByteBuffer.allocate(0)

            while (isActive) {
                sendData(buffer)

                (UDP_KEEP_ALIVE_MIN_INTERVAL + bridge.random.nextInt(UDP_KEEP_ALIVE_INTERVAL_DIFF)).toLong().also {
                    delay(it)
                }
            }
        }
    }

    internal fun launchJobInquireNATT() {
        jobInquireNATT = bridge.scope.launch(bridge.handler) {
            val packet = DatagramPacket(
                "B".toByteArray(Charsets.US_ASCII),
                1, config.nattAddress, UDP_NATT_PORT
            )

            while (isActive) {
                sendPacket(packet)

                val interval = if (status == UDPStatus.OPEN) {
                    UDP_NATT_INTERVAL_MIN + bridge.random.nextInt(UDP_NATT_INTERVAL_DIFF)
                } else {
                    UDP_NATT_INTERVAL_INITIAL
                }

                delay(interval.toLong())
            }
        }
    }

    private fun expectPacket(): Boolean {
        try {
            socket.receive(incomingPacket)
            return true
        } catch (_: SocketTimeoutException) { }

        return false
    }

    private suspend fun sendPacket(packet: DatagramPacket) {
        socketMutex.withLock {
            socket.send(packet)
        }
    }

    internal suspend fun sendData(buffer: ByteBuffer) {
        packetMutex.withLock {
            val dataSize = buffer.remaining()

            encryptBuffer.clear()
            encryptBuffer.putInt(config.serverCookie)
            encryptBuffer.putLong(System.currentTimeMillis())
            encryptBuffer.putLong(lastReceivedServerTick)
            encryptBuffer.putShort(dataSize.toShort())
            encryptBuffer.padZeroByte(1) // flag
            encryptBuffer.put(buffer)

            val diff = UDP_MAX_PAYLOAD_SIZE - dataSize
            if (diff >= 0) {
                val randomSize = bridge.random.nextInt(min(diff + 1, UDP_MAX_PADDING_SIZE))
                encryptBuffer.padZeroByte(randomSize)
            } else {
                throw NotImplementedError()
            }

            bridge.random.nextBytes(nonceHolder)
            clientCipher.init(Cipher.ENCRYPT_MODE, config.clientKey, IvParameterSpec(nonceHolder))

            val packetBuffer = ByteBuffer.wrap(outgoingPacket.data)
            packetBuffer.put(nonceHolder)

            clientCipher.doFinal(
                encryptBuffer.array(),
                0,
                encryptBuffer.position(),
                packetBuffer.array(),
                CHACHA20_POLY1305_NONCE_SIZE
            ).also {
                outgoingPacket.length = CHACHA20_POLY1305_NONCE_SIZE + it
            }

            if (status == UDPStatus.OPEN) {
                // fast lane
                outgoingPacket.address = config.serverCurrentAddress!!
                outgoingPacket.port = config.serverCurrentPort
                sendPacket(outgoingPacket)
            } else {
                config.validServerAddresses.forEach { address ->
                    outgoingPacket.address = address

                    config.validServerPorts.forEach { port ->
                        outgoingPacket.port = port
                        sendPacket(outgoingPacket)
                    }
                }
            }
        }
    }

    private fun processNATTInformation() {
        val text = incomingPacket.let {
            it.data.sliceArray(0 until it.length).toStringOrNull(Charsets.US_ASCII)
        } ?: return

        val resultIP = regexIP.find(text)?.value ?: return
        val resultPort = regexPort.find(text)?.value ?: return

        config.clientNATTAddress = Inet4Address.getByName(resultIP.substring(3)) as Inet4Address
        config.clientNATTPort = resultPort.substring(5).toInt()
    }

    internal suspend fun receivePacket(): ByteBuffer {
        while (true) {
            yield()

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReceivedTick > UDP_KEEP_ALIVE_TIMEOUT) {
                _status = UDPStatus.CLOSED
            }

            if (!expectPacket()) {
                continue
            }

            if (incomingPacket.address == config.nattAddress) {
                processNATTInformation()
                continue
            }

            if (incomingPacket.length < UDP_SOFTETHER_HEADER_SIZE) {
                continue
            }

            serverCipher.init(
                Cipher.DECRYPT_MODE,
                config.serverKey,
                IvParameterSpec(incomingPacket.data, 0, CHACHA20_POLY1305_NONCE_SIZE)
            )

            serverCipher.doFinal(
                incomingPacket.data,
                CHACHA20_POLY1305_NONCE_SIZE,
                incomingPacket.length - CHACHA20_POLY1305_NONCE_SIZE,
                decryptBuffer.array()
            ).also {
                decryptBuffer.position(0)
                decryptBuffer.limit(it)
            }

            if (decryptBuffer.int != config.clientCookie) {
                continue
            }

            val serverTick = decryptBuffer.long
            if (serverTick >= lastReceivedServerTick) {
                lastReceivedServerTick = serverTick

                config.serverCurrentAddress = incomingPacket.address as Inet4Address
                config.serverCurrentPort = incomingPacket.port
            } else {
                if (lastReceivedServerTick - serverTick >= UDP_PACKET_AVAILABLE_TIME) {
                    continue
                }
            }

            val clientTick = decryptBuffer.long
            lastReceivedClientTick = max(clientTick, lastReceivedClientTick)
            if (lastReceivedClientTick + UDP_PACKET_AVAILABLE_TIME >= currentTime) {
                lastReceivedTick = currentTime
            }

            _status = if (currentTime - lastReceivedTick > UDP_KEEP_ALIVE_TIMEOUT) {
                UDPStatus.CLOSED
            } else {
                UDPStatus.OPEN
            }

            val realDataSize = decryptBuffer.short.toIntAsUShort()

            decryptBuffer.move(1) // ignore flag

            if (realDataSize <= 0) {
                continue
            }

            decryptBuffer.limit(decryptBuffer.position() + realDataSize)


            // parse frame
            if (isToMeFrame(decryptBuffer, bridge.clientMacAddress)) {
                decryptBuffer.move(ETHERNET_MAC_ADDRESS_SIZE)
            } else continue

            decryptBuffer.move(ETHERNET_MAC_ADDRESS_SIZE) // ignore sender's MAC address

            if (decryptBuffer.short != ETHER_TYPE_IPv4) continue

            if (isEchoFrame(decryptBuffer)) {
                // send ARP Replay anyway
                bridge.controlMailbox.send(ControlMessage.SECURE_NAT_ECHO_REQUEST)
            }

            return decryptBuffer
        }
    }
}
