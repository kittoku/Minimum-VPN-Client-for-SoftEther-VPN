package kittoku.mvc.service.client.softether

import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.assertAlways
import kittoku.mvc.debug.assertOrThrow
import kittoku.mvc.extension.nextBytes
import kittoku.mvc.extension.read
import kittoku.mvc.hash.hashSha0
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlMessage
import kittoku.mvc.service.teminal.udp.CHACHA20_POLY1305_KEY_SIZE
import kittoku.mvc.service.teminal.udp.UDP_CIPHER_ALGORITHM
import kittoku.mvc.unit.http.HttpMessage
import kittoku.mvc.unit.property.*
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.nio.ByteBuffer
import javax.crypto.spec.SecretKeySpec


internal class SoftEtherClient(private val bridge: ClientBridge) {
    private lateinit var challenge: SepRandom

    internal fun launchJobNegotiation() {
        bridge.scope.launch(bridge.handler) {
            uploadWatermark()
            uploadProperties()

            bridge.controlMailbox.send(ControlMessage.SOFTETHER_NEGOTIATION_FINISHED)
        }
    }

    private suspend fun checkSoftEtherServer() { // reserved for future use
        val request = HttpMessage().also {
            it.header = "GET / HTTP/1.1"
            it.fieldMap["X-VPN"] = "1"
            it.fieldMap["Host"] = bridge.serverHostname
            it.fieldMap["Keep-Alive"] = HTTP_KEEP_ALIVE
            it.fieldMap["Connection"] = "Keep-Alive"
            it.fieldMap["Accept-Language"] = "ja"
            it.fieldMap["User-Agent"] = DEFAULT_USER_AGENT
            it.fieldMap["Pragma"] = "no-cache"
            it.fieldMap["Cache-Control"] = "no-cache"
        }

        bridge.controlChannel.send(request)
        val response = bridge.softEtherChannel.receive()

        val bodyText = response.body?.toString(Charsets.US_ASCII) ?: ""

        assertOrThrow(ErrorCode.SOFTETHER_INVALID_PROTOCOL_SERVER) {
            assertAlways(bodyText.contains(HTTP_DETECT_TAG) || bodyText.startsWith(HTTP_DETECT_BODY))
        }
    }

    private suspend fun uploadWatermark() {
        val randomSize = bridge.random.nextInt(2000)
        val bodySize = WATERMARK.size + randomSize

        val request = HttpMessage().also {
            it.header = "POST /vpnsvc/connect.cgi HTTP/1.1"
            it.fieldMap["Host"] = bridge.serverHostname
            it.fieldMap["Content-Type"] = "image/jpeg"
            it.fieldMap["Content-Length"] = bodySize.toString()
            it.fieldMap["Connection"] = "Keep-Alive"
        }


        request.body = ByteBuffer.allocate(bodySize).let {
            it.put(WATERMARK)
            it.put(bridge.random.nextBytes(randomSize))
            it.array()
        }

        bridge.controlChannel.send(request)
        val response = bridge.softEtherChannel.receive()

        val pack = PropertyPack().also {
            val buffer = ByteBuffer.wrap(response.body!!)
            assertOrThrow(ErrorCode.SOFTETHER_INVALID_PROPERTY_PACK) {
                it.read(buffer)
            }
        }

        assertOrThrow(ErrorCode.SOFTETHER_INVALID_PROTOCOL_SERVER) {
            assertAlways(response.header == HTTP_200_HEADER)
            assertAlways(pack.sepError == null)
            assertAlways(pack.sepRandom != null)
            challenge = pack.sepRandom!!
        }
    }

    private fun calcSecurePassword(): ByteArray {
        val password = bridge.clientPassword.toByteArray(Charsets.US_ASCII)
        val uppercaseUsername = bridge.clientUsername.uppercase().toByteArray(Charsets.US_ASCII)

        val hashedPassword = hashSha0(password + uppercaseUsername)

        return hashSha0(hashedPassword + challenge.value)
    }

    private fun prepareProperties(): ByteArray {
        val properties = PropertyPack()

        properties.sepMethod = SepMethod().also { it.value = "login" }
        properties.sepAuthType = SepAuthType().also { it.value = 1 }
        properties.sepUsername = SepUsername().also { it.value = bridge.clientUsername }
        properties.sepProtocol = SepProtocol().also { it.value = 0 }
        properties.sepHubName = SepHubName().also { it.value = bridge.serverHubName }
        properties.sepUseEncrypt = SepUseEncrypt().also { it.value = 1 }
        properties.sepUseCompress = SepUseCompress().also { it.value = 0 }
        properties.sepMaxConnection = SepMaxConnection().also { it.value = 1 }
        properties.sepHalfConnection = SepHalfConnection().also { it.value = 0 }
        properties.sepSecurePassword = SepSecurePassword().also { it.value.read(calcSecurePassword()) }
        properties.sepClientProductName = SepClientProductName().also { it.value = "Minimum VPN Client for SoftEther VPN" }
        properties.sepPenCore = SepPenCore().also {
            val randomSize = bridge.random.nextInt(1000)
            it.value = bridge.random.nextBytes(randomSize)
        }

        bridge.udpAccelerationConfig?.also { config ->
            properties.sepUseUDPAcceleration = SepUseUDPAcceleration().also { it.value = true }
            properties.sepUDPVersion = SepUDPVersion().also { it.value = 2 }
            properties.sepUDPMaxVersion = SepUDPMaxVersion().also { it.value = 2 }
            properties.sepUDPClientIP = SepUDPClientIP().also { it.value.read(config.clientReportedAddress.address) }
            properties.sepUDPClientPort = SepUDPClientPort().also { it.value = config.clientReportedPort }
            properties.sepUDPSupportFastDisconnectDetect = SepUDPSupportFastDisconnectDetect().also { it.value = true }
            properties.sepUDPClientKeyV2 = SepUDPClientKeyV2().also {
                val key = bridge.random.nextBytes(UDP_ACCELERATION_V2_KEY_SIZE)
                it.value = key

                val array = ByteArray(CHACHA20_POLY1305_KEY_SIZE)
                array.read(key)
                config.clientKey = SecretKeySpec(array, UDP_CIPHER_ALGORITHM)
            }
        }

        val buffer = ByteBuffer.allocate(properties.length)
        properties.write(buffer)

        return buffer.array()
    }

    private suspend fun uploadProperties() {
        val request = HttpMessage().also {
            it.header = "POST /vpnsvc/vpn.cgi HTTP/1.1"
            it.body = prepareProperties()
            it.fieldMap["Host"] = bridge.serverHostname
            it.fieldMap["Content-Type"] = "application/octet-stream"
            it.fieldMap["Content-Length"] = it.body!!.size.toString()
            it.fieldMap["Connection"] = "Keep-Alive"
            it.fieldMap["Keep-Alive"] = HTTP_KEEP_ALIVE
        }

        bridge.controlChannel.send(request)
        val response = bridge.softEtherChannel.receive()

        val pack = PropertyPack().also {
            val buffer = ByteBuffer.wrap(response.body!!)
            assertOrThrow(ErrorCode.SOFTETHER_INVALID_PROPERTY_PACK) {
                it.read(buffer)
            }
        }

        assertOrThrow(ErrorCode.SOFTETHER_AUTHENTICATION_FAILED) {
            assertAlways(response.header == HTTP_200_HEADER)
            assertAlways(pack.sepError == null)
        }

        bridge.udpAccelerationConfig?.also { config ->
            assertOrThrow(ErrorCode.UDP_INVALID_CONFIGURATION_ASSIGNED) {
                // notify disabled denied
                assertAlways(pack.sepUDPVersion?.value == 2)
                assertAlways(pack.sepUDPUseEncryption?.value == true)
                assertAlways(pack.sepUDPEnableFastDisconnectDetect?.value == true)

                pack.sepUDPClientCookie?.also {
                    config.clientCookie = it.value
                } ?: throw AssertionError()

                pack.sepUDPServerIP?.also {
                    config.serverReportedAddress = Inet4Address.getByAddress(it.value) as Inet4Address
                } ?: throw AssertionError()

                pack.sepUDPServerPort?.also {
                    config.serverReportedPort = it.value
                } ?: throw AssertionError()

                pack.sepUDPServerCookie?.also {
                    config.serverCookie = it.value
                } ?: throw AssertionError()

                pack.sepUDPServerKeyV2?.value?.also {
                    val array = ByteArray(CHACHA20_POLY1305_KEY_SIZE)
                    array.read(it)
                    config.serverKey = SecretKeySpec(array, UDP_CIPHER_ALGORITHM)
                } ?: throw AssertionError()

            }
        }
    }
}
