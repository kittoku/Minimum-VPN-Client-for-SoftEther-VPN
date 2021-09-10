package kittoku.mvc.service.client

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kittoku.mvc.R
import kittoku.mvc.debug.ErrorCode
import kittoku.mvc.debug.MvcException
import kittoku.mvc.debug.assertAlways
import kittoku.mvc.extension.*
import kittoku.mvc.preference.MvcPreference
import kittoku.mvc.preference.accessor.setBooleanPrefValue
import kittoku.mvc.service.CHANNEL_ID
import kittoku.mvc.service.client.*
import kittoku.mvc.service.client.arp.ARPClient
import kittoku.mvc.service.client.dhcp.DhcpClient
import kittoku.mvc.service.client.softether.SoftEtherClient
import kittoku.mvc.service.client.stateless.LogWriter
import kittoku.mvc.service.client.stateless.NetworkObserver
import kittoku.mvc.service.teminal.ip.IPTerminal
import kittoku.mvc.service.teminal.tcp.TCPTerminal
import kittoku.mvc.unit.ethernet.*
import kittoku.mvc.unit.http.HttpMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


internal class ControlClient(private val bridge: ClientBridge) {
    private lateinit var tcpTerminal: TCPTerminal
    private lateinit var ipTerminal: IPTerminal
    private lateinit var networkObserver: NetworkObserver
    private var logWriter: LogWriter? = null

    private lateinit var softEtherClient: SoftEtherClient
    private lateinit var dhcpClient: DhcpClient
    private lateinit var arpClient: ARPClient

    private val mailbox = bridge.controlMailbox

    private var isClosing = false
    private val mutex = Mutex()

    private lateinit var jobIncoming: Job
    private lateinit var jobOutgoing: Job
    private lateinit var jobControl: Job

    internal fun run() {
        if (bridge.isLogEnabled && bridge.logDirectory != null) {
            logWriter = LogWriter(bridge)
        }

        launchJobIncoming()
    }

    private fun launchJobIncoming() {
        jobIncoming = bridge.scope.launch(bridge.handler) {
            logWriter?.report("Connecting has been attempted")

            tcpTerminal = TCPTerminal(bridge).also { it.createSocket() }
            tcpTerminal.setTimeoutForControl()
            ipTerminal = IPTerminal(bridge)


            // SoftEther negotiation
            softEtherClient = SoftEtherClient(bridge).also { it.launchJobNegotiation() }

            launchJobControl()

            withTimeoutOrNull(SOFTETHER_NEGOTIATION_TIMEOUT) {
                repeat(2) {
                    relaySoftEtherMessage()
                }

                assertAlways(mailbox.receive() == ControlMessage.SOFTETHER_NEGOTIATION_FINISHED)
                bridge.softEtherChannel.clear()
            } ?: throw MvcException(ErrorCode.SOFTETHER_NEGOTIATION_TIMEOUT, null)


            // start to keep alive tcp
            tcpTerminal.launchJobKeepAlive()


            // DHCP negotiation
            dhcpClient = DhcpClient(bridge).also { it.launchJobInitial() }

            withTimeoutOrNull(DHCP_NEGOTIATION_TIMEOUT) {
                while (isActive) {
                    relayDhcpMessage()

                    if (mailbox.poll() == ControlMessage.DHCP_NEGOTIATION_FINISHED) {
                        bridge.dhcpChannel.clear()
                        break
                    }
                }
            } ?: throw MvcException(ErrorCode.DHCP_NEGOTIATION_TIMEOUT, null)


            // ARP negotiation
            arpClient = ARPClient(bridge).also { it.launchJobInitial() }

            withTimeoutOrNull(ARP_NEGOTIATION_TIMEOUT) {
                while (isActive) {
                    relayAprPacket()

                    if (mailbox.poll() == ControlMessage.ARP_NEGOTIATION_FINISHED) {
                        bridge.arpChannel.clear()
                        break
                    }
                }
            } ?: throw MvcException(ErrorCode.ARP_NEGOTIATION_TIMEOUT, null)


            // if this is test, we need to get out because VpnService.Builder is not given
            if (bridge.isTest) {
                return@launch
            }


            // start observing network
            networkObserver = NetworkObserver(bridge)


            // Establish VPN connection
            tcpTerminal.setTimeoutForData()
            tcpTerminal.protectSocket()
            ipTerminal.initializeBuilder()
            ipTerminal.launchJobRetrieve()
            launchJobOutgoing()

            logWriter?.report("VPN connection has been established")

            while (isActive) {
                if (mailbox.poll() == ControlMessage.SECURE_NAT_ECHO_REQUEST) {
                    arpClient.launchReplyBeacon()
                }

                relayIPPacket()
            }
        }
    }

    private fun launchJobOutgoing() {
        jobOutgoing = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                ipTerminal.waitOutgoingPacket().also {
                    tcpTerminal.loadOutgoingPacket(it)
                }

                while (isActive) {
                    val polled = ipTerminal.pollOutgoingPacket() ?: break

                    val isAddable = tcpTerminal.addOutGoingPacket(polled)
                    if (!isAddable) break
                }

                tcpTerminal.sendOutgoingPacket()
            }
        }
    }

    private fun launchJobControl() {
        jobControl = bridge.scope.launch(bridge.handler) {
            while (isActive) {
                when (val received = bridge.controlChannel.receive()) {
                    is HttpMessage -> {
                        tcpTerminal.sendHttpMessage(received)
                    }

                    is EthernetFrame -> {
                        tcpTerminal.sendFrame(received)
                    }

                    else -> throw NotImplementedError()
                }
            }
        }
    }

    private suspend fun relaySoftEtherMessage() {
        val response = tcpTerminal.receiveHttpMessage()
        bridge.softEtherChannel.send(response)
    }

    private suspend fun relayDhcpMessage() {
        tcpTerminal.consumeFrame {
            if (it.payloadIPv4Packet?.payloadUDPDatagram?.payloadDhcpMessage != null) {
                bridge.dhcpChannel.send(it)
            }
        }
    }

    private suspend fun relayAprPacket() {
        tcpTerminal.consumeFrame {
            if (it.payloadARPPacket != null) {
                bridge.arpChannel.send(it)
            }
        }
    }

    private suspend fun relayIPPacket() {
        tcpTerminal.consumeIPPacketBuffer {
            ipTerminal.feedIncomingPacket(it)
        }
    }

    private fun notify(message: String) {
        val builder = NotificationCompat.Builder(bridge.service, CHANNEL_ID).also {
            it.setSmallIcon(R.drawable.ic_baseline_vpn_lock_24)
            it.priority = NotificationCompat.PRIORITY_DEFAULT
            it.setAutoCancel(true)
            it.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        NotificationManagerCompat.from(bridge.service).also {
            it.notify(0, builder.build())
        }
    }

    internal fun kill(throwable: Throwable?) {
        bridge.scope.launch {
            mutex.withLock {
                if (!isClosing) {
                    if (throwable != null) {
                        // report exception first
                        var message = "Disconnected because of "

                        message += if (throwable is MvcException) {
                            throwable.message
                        } else {
                            "UNKNOWN EXCEPTION/ERROR"
                        }

                        notify(message)
                        logWriter?.reportThrowable(throwable)
                    }

                    isClosing = true

                    if (::tcpTerminal.isInitialized) tcpTerminal.close()
                    if (::ipTerminal.isInitialized) ipTerminal.close()
                    if (::networkObserver.isInitialized) networkObserver.close()

                    if (::jobControl.isInitialized) jobControl.cancel()
                    if (::jobIncoming.isInitialized) jobIncoming.cancel()
                    if (::jobOutgoing.isInitialized) jobOutgoing.cancel()

                    PreferenceManager.getDefaultSharedPreferences(bridge.service).also {
                        setBooleanPrefValue(false, MvcPreference.HOME_CONNECTOR, it)
                    }

                    if (throwable == null) {
                        // after confirming everything was OK
                        logWriter?.report("The connection has been closed")
                    }

                    logWriter?.close()

                    bridge.service.stopForeground(true)
                    bridge.service.stopSelf()
                }
            }
        }
    }
}
