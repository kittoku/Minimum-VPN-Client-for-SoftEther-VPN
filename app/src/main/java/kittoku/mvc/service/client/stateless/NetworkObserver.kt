package kittoku.mvc.service.client.stateless

import android.net.*
import androidx.preference.PreferenceManager
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.setStringPrefValue
import kittoku.mvc.service.client.ClientBridge


internal class NetworkObserver(bridge: ClientBridge) {
    private val manager = bridge.service.getSystemService(ConnectivityManager::class.java)
    private val callback: ConnectivityManager.NetworkCallback
    private val prefs = PreferenceManager.getDefaultSharedPreferences(bridge.service)

    init {
        wipeStatus()

        val request = NetworkRequest.Builder().let {
            it.addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            it.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            it.build()
        }

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                makeSummary(linkProperties).also {
                    setStringPrefValue(it, MvcPreference.HOME_STATUS, prefs)
                }
            }
        }

        manager.registerNetworkCallback(request, callback)
    }

    private fun makeSummary(properties: LinkProperties): String {
        val summary = mutableListOf<String>()

        summary.add("[Assigned IP Address]")
        properties.linkAddresses.forEach {
            summary.add(it.address.hostAddress)
        }
        summary.add("")

        summary.add("[Route]")
        properties.routes.forEach {
            summary.add(it.toString())
        }

        return summary.reduce { acc, s ->
            acc + "\n" + s
        }
    }

    private fun wipeStatus() {
        setStringPrefValue("", MvcPreference.HOME_STATUS, prefs)
    }

    internal fun close() {
        manager.unregisterNetworkCallback(callback)
        wipeStatus()
    }
}
