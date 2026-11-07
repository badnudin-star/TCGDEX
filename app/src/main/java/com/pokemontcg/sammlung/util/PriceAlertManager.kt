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
    const val DAILY_REPORT_CHANNEL_ID = "daily_price_report_channel"
    private const val NOTIFICATION_ID = 2001
    private const val DAILY_REPORT_NOTIFICATION_ID = 2002

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

    fun createDailyReportChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DAILY_REPORT_CHANNEL_ID,
                context.getString(R.string.daily_report_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.daily_report_channel_description)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Posts the nightly summary answering "did any card value change?". Unlike the threshold
     * alert this is also sent when nothing moved, because that is the answer the user asked for.
     * Returns true only when a notification was really posted.
     */
    fun sendDailyPriceReport(
        context: Context,
        report: DailyPriceReport,
        cardNames: Map<String, String>,
        enabled: Boolean,
        lastSentTimestamp: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!report.hasCards || !report.hasReportableContent) return false
        if (!shouldSendDailyPriceReport(
                enabled = enabled,
                lastSentTimestamp = lastSentTimestamp,
                notificationsAllowed = notificationsAllowed(context),
                now = now
            )
        ) {
            return false
        }

        createDailyReportChannel(context)

        val changedCount = report.changedCardIds.size
        val title = if (changedCount == 0) {
            context.getString(R.string.daily_report_title_unchanged)
        } else {
            context.resources.getQuantityString(
                R.plurals.daily_report_title_changed,
                changedCount,
                changedCount
            )
        }

        val valueText = PriceFormatter.formatPriceShort(context, report.totalValue)
        val changeText = report.valueChangePercent?.takeIf { report.hasValueChange }?.let { percent ->
            context.getString(
                R.string.daily_report_value_change,
                if (percent > 0) "+" else "",
                percent
            )
        } ?: context.getString(R.string.daily_report_value_unchanged)
        val lines = mutableListOf(context.getString(R.string.daily_report_value_line, valueText, changeText))

        if (changedCount > 0) {
            val entries = report.changedCardIds.take(3).map { cardId ->
                val percent = report.changePercentByCardId[cardId] ?: 0.0
                context.getString(
                    R.string.price_alert_entry,
                    cardNames[cardId] ?: cardId,
                    if (percent > 0) "+" else "",
                    percent
                )
            }
            val visible = entries.joinToString(", ")
            lines += if (changedCount > entries.size) {
                context.getString(R.string.price_alert_more, visible, changedCount - entries.size)
            } else {
                visible
            }
        }

        val text = lines.joinToString("\n")
        val contentIntent = buildContentIntent(context) ?: return false

        val builder = NotificationCompat.Builder(context, DAILY_REPORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return try {
            manager.notify(DAILY_REPORT_NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildContentIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun checkAndNotifyPriceChange(
        context: Context,
        history: List<PriceHistoryEntity>,
        cardNames: Map<String, String>,
        settings: PriceAlertSettings,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!canSendPriceAlert(settings, now, notificationsAllowed(context))) return false

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

        val contentIntent = buildContentIntent(context) ?: return false

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
