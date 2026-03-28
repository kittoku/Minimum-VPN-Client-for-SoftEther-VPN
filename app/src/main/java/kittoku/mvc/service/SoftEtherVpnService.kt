package kittoku.mvc.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import kittoku.mvc.R
import kittoku.mvc.service.client.ClientBridge
import kittoku.mvc.service.client.ControlClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


internal const val ACTION_VPN_CONNECT = "kittoku.mvc.connect"
internal const val ACTION_VPN_DISCONNECT = "kittoku.mvc.disconnect"

internal const val NOTIFICATION_ERROR_CHANNEL = "ERROR"
internal const val NOTIFICATION_RECONNECT_CHANNEL = "RECONNECT"
internal const val NOTIFICATION_DISCONNECT_CHANNEL = "DISCONNECT"
internal const val NOTIFICATION_CERTIFICATE_CHANNEL = "CERTIFICATE"

internal const val NOTIFICATION_ERROR_ID = 1
internal const val NOTIFICATION_RECONNECT_ID = 2
internal const val NOTIFICATION_DISCONNECT_ID = 3
internal const val NOTIFICATION_CERTIFICATE_ID = 4


internal class SoftEtherVpnService : VpnService() {
    private lateinit var notificationManager: NotificationManagerCompat
    private var client: ControlClient? = null

    override fun onCreate() {
        notificationManager = NotificationManagerCompat.from(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (ACTION_VPN_CONNECT == intent?.action ?: false) {
            client?.kill(null)
            client = ControlClient(createBridge()).also {
                beForegrounded()
                it.run()
            }

            Service.START_STICKY
        } else {
            client?.kill(null)
            client = null

            Service.START_NOT_STICKY
        }
    }

    private fun createBridge(): ClientBridge {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val handler = CoroutineExceptionHandler { _, throwable ->
            client?.kill(throwable)
            client = null
        }

        val bridge = ClientBridge(scope, handler)

        bridge.service = this
        bridge.prepareParameters(PreferenceManager.getDefaultSharedPreferences(this))

        return bridge
    }

    private fun beForegrounded() {
        arrayOf(
            NOTIFICATION_ERROR_CHANNEL,
            NOTIFICATION_RECONNECT_CHANNEL,
            NOTIFICATION_DISCONNECT_CHANNEL,
            NOTIFICATION_CERTIFICATE_CHANNEL,
        ).map {
            NotificationChannel(it, it, NotificationManager.IMPORTANCE_DEFAULT)
        }.also {
            notificationManager.createNotificationChannels(it)
        }

        val pendingIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, SoftEtherVpnService::class.java).setAction(ACTION_VPN_DISCONNECT),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_DISCONNECT_CHANNEL).also {
            it.priority = NotificationCompat.PRIORITY_DEFAULT
            it.setOngoing(true)
            it.setAutoCancel(true)
            it.setSmallIcon(R.drawable.ic_baseline_vpn_lock_24)
            it.addAction(R.drawable.ic_baseline_close_24, "DISCONNECT", pendingIntent)
        }

        startForeground(NOTIFICATION_DISCONNECT_ID, builder.build())
    }
    internal fun notifyMessage(message: String, id: Int, channel: String) {
        NotificationCompat.Builder(this, channel).also {
            it.setSmallIcon(R.drawable.ic_baseline_vpn_lock_24)
            it.setContentText(message)
            it.priority = NotificationCompat.PRIORITY_DEFAULT
            it.setAutoCancel(true)

            tryNotify(it.build(), id)
        }
    }

    internal fun notifyError(message: String) {
        notifyMessage(message,
            NOTIFICATION_ERROR_ID,
            NOTIFICATION_ERROR_CHANNEL
        )
    }

    internal fun tryNotify(notification: Notification, id: Int) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(id, notification)
        }
    }

    internal fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    internal fun close() {
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        client?.kill(null)
        client = null
    }
}
