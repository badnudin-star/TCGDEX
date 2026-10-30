package com.pokemontcg.sammlung.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.local.user.PriceHistoryEntity

data class PriceAlertSettings(
    val enabled: Boolean = false,
    val thresholdPercent: Double = 10.0,
    val windowDays: Int = 1,
    val lastAlertTimestamp: Long = 0L
)

object PriceAlertManager {

    const val CHANNEL_ID = "price_alerts_channel"
    private const val CHANNEL_NAME = "Preisalarme"
    private const val NOTIFICATION_ID = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen über wesentliche Preisänderungen gesammelter Karten"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotifyPriceChanges(
        context: Context,
        history: List<PriceHistoryEntity>,
        cardNames: Map<String, String>,
        settings: PriceAlertSettings,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!settings.enabled) return false
        if (now - settings.lastAlertTimestamp < 24L * 60L * 60L * 1000L) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val triggeredAlerts = findPriceChanges(
            history = history,
            thresholdPercent = settings.thresholdPercent,
            windowDays = settings.windowDays,
            now = now
        ).map { change ->
            val name = cardNames[change.cardId] ?: change.cardId
            val sign = if (change.changePercent > 0) "+" else ""
            "$name $sign%.1f%%".format(change.changePercent)
        }

        if (triggeredAlerts.isEmpty()) return false

        createNotificationChannel(context)

        val title = "Preisalarm: ${triggeredAlerts.size} Karte(n) verändert"
        val text = triggeredAlerts.take(3).joinToString(", ") + if (triggeredAlerts.size > 3) " und ${triggeredAlerts.size - 3} weitere" else ""

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())

        return true
    }
}
