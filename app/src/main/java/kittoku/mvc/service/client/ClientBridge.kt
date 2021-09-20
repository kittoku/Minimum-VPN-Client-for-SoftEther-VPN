package kittoku.mvc.service.client

import android.content.SharedPreferences
import android.net.Uri
import android.net.VpnService
import kittoku.mvc.extension.nextBytes
import kittoku.mvc.extension.read
import kittoku.mvc.extension.toHexByteArray
import kittoku.mvc.extension.toHexString
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.*
import kittoku.mvc.unit.DataUnit
import kittoku.mvc.unit.ethernet.ETHERNET_MAC_ADDRESS_SIZE
import kittoku.mvc.unit.ethernet.EthernetFrame
import kittoku.mvc.unit.http.HttpMessage
import kittoku.mvc.unit.ip.IPv4_ADDRESS_SIZE
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import java.net.Inet4Address
import java.security.SecureRandom
import javax.crypto.SecretKey


internal enum class ControlMessage {
    SOFTETHER_NEGOTIATION_FINISHED,
    DHCP_NEGOTIATION_FINISHED,
    ARP_NEGOTIATION_FINISHED,
    SECURE_NAT_ECHO_REQUEST,
}

internal class UDPAccelerationConfig(random: SecureRandom) {
    internal lateinit var clientReportedAddress: Inet4Address
    internal var clientNATTAddress: Inet4Address? = null
    internal var clientReportedPort = 0
    internal var clientNATTPort = 0
    internal var clientCookie = 0
    internal lateinit var clientKey: SecretKey

    internal var serverCurrentAddress: Inet4Address? = null
    internal lateinit var serverReportedAddress: Inet4Address
    internal var serverNATTAddress: Inet4Address? = null
    internal var serverCurrentPort = 0
    internal var serverReportedPort = 0
    internal var serverNATTPort = 0
    internal var serverCookie = 0
    internal lateinit var serverKey: SecretKey

    private val nattHostname: String
    internal lateinit var nattAddress: Inet4Address

    internal val validServerPorts: List<Int>
        get() = mutableListOf<Int>().also {
            if (serverCurrentPort != 0) {
                it.add(serverCurrentPort)
            }

            if (serverReportedPort != 0 && serverReportedPort != serverCurrentPort) {
                it.add(serverReportedPort)
            }

            if (serverNATTPort != 0 && serverNATTPort != serverCurrentPort && serverNATTPort != serverReportedPort) {
                it.add(serverNATTPort)
            }
        }

    internal val validServerAddresses: List<Inet4Address>
        get() = mutableListOf<Inet4Address>().also { list ->
            serverCurrentAddress?.also {
                list.add(it)
            }

            serverReportedAddress.also {
                if (it != serverCurrentAddress) {
                    list.add(it)
                }
            }

            serverNATTAddress?.also {
                if (it != serverCurrentAddress && it != serverReportedAddress) {
                    list.add(it)
                }
            }
        }

    init {
        val hex = random.nextBytes(1).toHexString().lowercase()
        nattHostname = "x${hex[0]}.x${hex[1]}.dev.servers.nat-traversal.softether-network.net"
    }

    internal fun initializeNATTAddress() {
        nattAddress = Inet4Address.getByName(nattHostname) as Inet4Address
    }
}

internal class ClientBridge(internal val scope: CoroutineScope, internal val handler: CoroutineExceptionHandler) {
    internal lateinit var service: VpnService // separate from constructor for test

    internal var isTest = false
    internal val controlMailbox = Channel<ControlMessage>(1)

    internal var serverPort: Int = 0
    internal var serverHostname: String = ""
    internal var serverHubName: String = ""
    internal var clientUsername: String = ""
    internal var clientPassword: String = ""
    internal val clientMacAddress = ByteArray(ETHERNET_MAC_ADDRESS_SIZE)

    internal var sslVersion = "DEFAULT"
    internal var doSelectCipherSuites = false
    internal val selectedCipherSuites = mutableListOf<String>()

    internal var udpAccelerationConfig: UDPAccelerationConfig? = null

    internal val assignedIpAddress = ByteArray(IPv4_ADDRESS_SIZE)
    internal val subnetMask = ByteArray(IPv4_ADDRESS_SIZE)
    internal val defaultGatewayIpAddress = ByteArray(IPv4_ADDRESS_SIZE)
    internal val defaultGatewayMacAddress = ByteArray(ETHERNET_MAC_ADDRESS_SIZE)
    internal val dhcpServerIpAddress  = ByteArray(IPv4_ADDRESS_SIZE)

    internal var dnsServerIpAddress: ByteArray? = null
    internal var leaseTime: Long? = null

    internal val controlChannel = Channel<DataUnit>(1)
    internal val softEtherChannel = Channel<HttpMessage>(1)
    internal val dhcpChannel = Channel<EthernetFrame>(1)
    internal val arpChannel = Channel<EthernetFrame>(1)

    internal var isLogEnabled = false
    internal var logDirectory: Uri? = null

    internal val random = SecureRandom()

    internal fun prepareParameters(prefs: SharedPreferences) {
        serverPort = getIntPrefValue(MvcPreference.SSL_PORT, prefs)
        serverHostname = getStringPrefValue(MvcPreference.HOME_HOSTNAME, prefs)
        serverHubName = getStringPrefValue(MvcPreference.HOME_HUB, prefs)
        clientUsername = getStringPrefValue(MvcPreference.HOME_USERNAME, prefs)
        clientPassword = getStringPrefValue(MvcPreference.HOME_PASSWORD, prefs)
        clientMacAddress.read(loadMac(prefs))

        sslVersion = getStringPrefValue(MvcPreference.SSL_VERSION, prefs)
        doSelectCipherSuites = getBooleanPrefValue(MvcPreference.SSL_DO_SELECT_SUITES, prefs)
        selectedCipherSuites.clear()
        if (doSelectCipherSuites) {
            getSetPrefValue(MvcPreference.SSL_SUITES, prefs).forEach { selectedCipherSuites.add(it) }
        }

        if (getBooleanPrefValue(MvcPreference.UDP_ENABLE_ACCELERATION, prefs)) {
            udpAccelerationConfig = UDPAccelerationConfig(random)
        }

        isLogEnabled = getBooleanPrefValue(MvcPreference.LOG_DO_SAVE_LOG, prefs)
        logDirectory = loadUri(MvcPreference.LOG_DIRECTORY, prefs)
    }

    private fun loadUri(key: MvcPreference, prefs: SharedPreferences): Uri? {
        val uriString = getStringPrefValue(key, prefs)

        return if (uriString.isEmpty()) {
            null
        } else {
            Uri.parse(uriString)
        }
    }

    private fun loadMac(prefs: SharedPreferences): ByteArray {
        var addressString = getStringPrefValue(MvcPreference.MAC_ADDRESS, prefs)

        if (addressString.isEmpty()) {
            addressString = "5E" + random.nextBytes(5).toHexString()
            setStringPrefValue(addressString, MvcPreference.MAC_ADDRESS, prefs)
        }

        return addressString.toHexByteArray()
    }
}
