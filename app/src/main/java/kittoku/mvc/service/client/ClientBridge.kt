package kittoku.mvc.service.client

import android.content.SharedPreferences
import android.net.Uri
import android.net.VpnService
import kittoku.mvc.extension.read
import kittoku.mvc.extension.toHexByteArray
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.getBooleanPrefValue
import kittoku.mvc.preference.accessor.getIntPrefValue
import kittoku.mvc.preference.accessor.getSetPrefValue
import kittoku.mvc.preference.accessor.getStringPrefValue
import kittoku.mvc.unit.DataUnit
import kittoku.mvc.unit.ethernet.ETHERNET_MAC_ADDRESS_SIZE
import kittoku.mvc.unit.ethernet.EthernetFrame
import kittoku.mvc.unit.http.HttpMessage
import kittoku.mvc.unit.ip.IPv4_ADDRESS_SIZE
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import java.security.SecureRandom


internal enum class ControlMessage {
    SOFTETHER_NEGOTIATION_FINISHED,
    KEEP_ALIVE_INIT_FINISHED,
    DHCP_NEGOTIATION_FINISHED,
    ARP_NEGOTIATION_FINISHED,
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
        clientMacAddress.read(getStringPrefValue(MvcPreference.MAC_ADDRESS, prefs).toHexByteArray())

        sslVersion = getStringPrefValue(MvcPreference.SSL_VERSION, prefs)
        doSelectCipherSuites = getBooleanPrefValue(MvcPreference.SSL_DO_SELECT_SUITES, prefs)
        selectedCipherSuites.clear()
        if (doSelectCipherSuites) {
            getSetPrefValue(MvcPreference.SSL_SUITES, prefs).forEach { selectedCipherSuites.add(it) }
        }

        isLogEnabled = getBooleanPrefValue(MvcPreference.LOG_DO_SAVE_LOG, prefs)
        logDirectory = getStringPrefValue(MvcPreference.LOG_DIRECTORY, prefs).let {
            if (it.isEmpty()) {
                null
            } else {
                Uri.parse(it)
            }
        }
    }
}
