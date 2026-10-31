package com.pokemontcg.sammlung.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
    private const val NOTIFICATION_ID = 2001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.price_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.price_alert_channel_description)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotifyPriceChange(
        context: Context,
        history: List<PriceHistoryEntity>,
        cardNames: Map<String, String>,
        settings: PriceAlertSettings,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (!canSendPriceAlert(settings, now, notificationsAllowed)) return false

        val triggeredAlerts = findPriceChanges(
            history = history,
            thresholdPercent = settings.thresholdPercent,
            windowDays = settings.windowDays,
            now = now
        )
        if (triggeredAlerts.isEmpty()) return false

        createNotificationChannel(context)

        val entries = triggeredAlerts.map { change ->
            val name = cardNames[change.cardId] ?: change.cardId
            val sign = if (change.changePercent > 0) "+" else ""
            context.getString(
                R.string.price_alert_entry,
                name,
                sign,
                change.changePercent
            )
        }
        val visibleEntries = entries.take(3).joinToString(", ")
        val text = if (entries.size > 3) {
            context.getString(R.string.price_alert_more, visibleEntries, entries.size - 3)
        } else {
            visibleEntries
        }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return false
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.resources.getQuantityString(
            R.plurals.price_alert_title,
            entries.size,
            entries.size
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return try {
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
