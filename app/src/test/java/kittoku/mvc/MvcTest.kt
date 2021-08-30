package kittoku.mvc

import kittoku.mvc.extension.isSame
import kittoku.mvc.extension.read
import kittoku.mvc.extension.toHexByteArray
import kittoku.mvc.extension.toHexString
import kittoku.mvc.hash.hashSha0
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlClient
import kittoku.mvc.unit.ip.IPv4Packet
import kittoku.mvc.unit.udp.UDPDatagram
import kotlinx.coroutines.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible


class MvcTest {
    private fun createTestBridge(scope: CoroutineScope): ClientBridge {
        val handler = CoroutineExceptionHandler { _, exception ->
            throw exception
        }

        return ClientBridge(scope, handler).also {
            it.isTest = true

            it.serverPort = System.getenv("TEST_PORT")?.toInt() ?: 443
            it.serverHostname = System.getenv("TEST_HOST") ?: ""
            it.clientUsername = System.getenv("TEST_USERNAME") ?: ""
            it.clientPassword = System.getenv("TEST_PASSWORD") ?: ""

            val macAddress = ByteArray(6)
            macAddress[0] = 0x5E
            macAddress[1] = 11
            macAddress[2] = 23
            macAddress[3] = 58
            macAddress[4] = 13
            macAddress[5] = 21
            it.clientMacAddress.read(macAddress)
        }
    }

    @Test
    fun testControlClient() {
        runBlocking {
            val bridge = createTestBridge(CoroutineScope(Dispatchers.IO + SupervisorJob()))
            val client = ControlClient(bridge)

            client.run()
            delay(10_000)
        }
    }

    @Test
    fun testHashSha0() {
        val actual0 = hashSha0("abc".toByteArray(Charsets.US_ASCII))
        val expected0 = "0164B8A914CD2A5E74C4F7FF082C4D97F1EDF880".toHexByteArray()

        val actual1 = hashSha0("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".toByteArray(Charsets.US_ASCII))
        val expected1 = "D2516EE1ACFA5BAF33DFC1C471E438449EF134C8".toHexByteArray()

        assert(actual0.isSame(expected0))
        assert(actual1.isSame(expected1))
    }

    private fun writeReadUDPDatagram(datagram: UDPDatagram) {
        val buffer = ByteBuffer.allocate(datagram.length)

        try {
            datagram.write(buffer)
            buffer.flip()
            datagram.read(buffer)
        } catch (e: Exception) {
            println("SRC: ${datagram.srcPort}")
            println("DST: ${datagram.dstPort}")
            println("PAYLOAD: ${datagram.payloadUnknown?.toHexString() ?: ""}")
            println("HEADER: ${accessPseudoIPHeader(datagram).toHexString()}")

            throw e
        }
    }

    private fun accessPseudoIPHeader(datagram: UDPDatagram): ByteArray {
        val property = UDPDatagram::class.memberProperties.find { it.name == "pseudoIPHeader" }

        @Suppress("UNCHECKED_CAST")
        property as KProperty1<UDPDatagram, ByteArray>

        return property.let {
            it.isAccessible = true
            it.get(datagram)
        }
    }

    private fun modifyPseudoIPHeader(value: ByteArray, datagram: UDPDatagram) {
        val property = UDPDatagram::class.memberProperties.find { it.name == "pseudoIPHeader" }

        @Suppress("UNCHECKED_CAST")
        property as KMutableProperty1<UDPDatagram, ByteArray>

        property.also {
            it.isAccessible = true
            it.set(datagram, value)
        }
    }

    @Test
    fun testUDPDatagram() {
        UDPDatagram().also {
            it.srcPort = -8
            it.dstPort = 0
            it.payloadUnknown = ByteArray(0)
            modifyPseudoIPHeader(ByteArray(0), it)

            writeReadUDPDatagram(it)
        }

        UDPDatagram().also {
            it.srcPort = -549654713
            it.dstPort = -1991713114
            it.payloadUnknown = ByteArray(1).also { array -> array[0] = -100 }
            modifyPseudoIPHeader(ByteArray(0), it)

            writeReadUDPDatagram(it)
        }

        UDPDatagram().also {
            it.srcPort = 65214
            it.dstPort = 33409
            it.payloadUnknown = listOf(
                "35BBF96E035649A57EB582554A292B9A1932553E70930421BF5D207A6DB421717",
                "CE65F0A76CDD62C926CA9950EAD9FA9925B84066D7E7724CF39F922BA2593855B",
                "7FDFD2B434"
            ).reduce { acc, s -> acc + s }.toHexByteArray()
            modifyPseudoIPHeader("29DB7C1FB0CF03F513C4B712".toHexByteArray(), it)

            writeReadUDPDatagram(it)
        }
    }

    private fun writeReadIPv4Packet(packet: IPv4Packet) {
        val buffer = ByteBuffer.allocate(packet.length)

        try {
            packet.write(buffer)
            buffer.flip()
            packet.read(buffer)
        } catch (e: Exception) {
            println("SRC: ${packet.srcAddress.toHexString()}")
            println("DST: ${packet.dstAddress.toHexString()}")
            println("PAYLOAD: ${packet.payloadUnknown?.toHexString() ?: ""}")

            throw e
        }
    }

    @Test
    fun testIPPacket() {
        IPv4Packet().also {
            it.srcAddress[3] = -20
            it.payloadUnknown = ByteArray(0)

            writeReadIPv4Packet(it)
        }
    }
}
