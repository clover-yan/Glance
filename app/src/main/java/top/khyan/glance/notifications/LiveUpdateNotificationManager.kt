package top.khyan.glance.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Wrapper for Android 16 Live Updates (promoted ongoing notifications).
 *
 * On Android 16+, this requests promotion via EXTRA_REQUEST_PROMOTED_ONGOING.
 * On lower API levels, it posts a normal ongoing progress notification.
 */
class LiveUpdateNotificationManager(
    private val context: Context,
    private val channelId: String = DEFAULT_CHANNEL_ID,
) {

    private val appContext = context.applicationContext

    fun createLiveUpdate(
        notificationId: Int,
        content: LiveUpdateContent,
        tag: String? = null,
    ): Boolean {
        return postLiveUpdate(notificationId = notificationId, content = content, tag = tag)
    }

    fun cancelLiveUpdate(notificationId: Int, tag: String? = null) {
        if (tag == null) {
            NotificationManagerCompat.from(appContext).cancel(notificationId)
        } else {
            NotificationManagerCompat.from(appContext).cancel(tag, notificationId)
        }
    }

    /**
     * Whether app-level promoted notifications are allowed by current system/user settings.
     */
    fun canPostPromotedLiveUpdates(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return false
        }
        val manager = appContext.getSystemService(NotificationManager::class.java)
        return manager.canPostPromotedNotifications()
    }

    /**
     * Intent to open app-specific promoted notification settings on Android 16+.
     */
    fun buildPromotedSettingsIntent(): Intent {
        val promotedIntent = Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && canResolve(promotedIntent)) {
            return promotedIntent
        }
        return buildAppNotificationSettingsIntent()
    }

    private fun postLiveUpdate(
        notificationId: Int,
        content: LiveUpdateContent,
        tag: String?,
    ): Boolean {
        if (!canPostNotifications()) {
            return false
        }

        ensureChannel()

        val contentWithDeleteIntent = if (content.deleteIntent == null) {
            content.copy(deleteIntent = buildDeleteIntent(notificationId))
        } else {
            content
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            buildPlatformLiveUpdateNotification(contentWithDeleteIntent)
        } else {
            buildCompatLiveUpdateNotification(contentWithDeleteIntent)
        }
        if (tag == null) {
            NotificationManagerCompat.from(appContext).notify(notificationId, notification)
        } else {
            NotificationManagerCompat.from(appContext).notify(tag, notificationId, notification)
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildPlatformLiveUpdateNotification(content: LiveUpdateContent): Notification {
        val extras = Bundle().apply {
            putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }

        val builder = Notification.Builder(appContext, channelId)
            .setSmallIcon(content.smallIconRes)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(content.whenTimeMillis != null)
            .setExtras(extras)

        content.contentIntent?.let { builder.setContentIntent(it) }
        content.deleteIntent?.let { builder.setDeleteIntent(it) }

        content.whenTimeMillis?.let { builder.setWhen(it) }
        if (content.useChronometer) {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(content.chronometerCountDown)
        }

        if (content.progress != null && content.progress >= 0) {
            val max = content.maxProgress.coerceAtLeast(1)
            val value = content.progress.coerceIn(0, max)
            val style = Notification.ProgressStyle().apply {
                progress = value
                progressSegments = listOf(Notification.ProgressStyle.Segment(max))
            }
            builder.setStyle(style)
        }

        return builder.build()
    }

    private fun buildCompatLiveUpdateNotification(content: LiveUpdateContent): Notification {
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(content.smallIconRes)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(content.whenTimeMillis != null)

        content.contentIntent?.let { builder.setContentIntent(it) }
        content.deleteIntent?.let { builder.setDeleteIntent(it) }

        content.whenTimeMillis?.let { builder.setWhen(it) }
        if (content.useChronometer) {
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(content.chronometerCountDown)
        }

        if (content.progress != null && content.progress >= 0) {
            val max = content.maxProgress.coerceAtLeast(1)
            val value = content.progress.coerceIn(0, max)
            builder.setProgress(max, value, content.indeterminate)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    private fun canPostNotifications(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        if (!notificationsEnabled) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return false
            }
        }

        return true
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) != null) {
            return
        }

        val channel = NotificationChannel(
            channelId,
            appContext.getString(top.khyan.glance.R.string.live_update_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = appContext.getString(top.khyan.glance.R.string.live_update_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildAppNotificationSettingsIntent(): Intent {
        val appSettingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", appContext.packageName, null)
            }
        }
        return appSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun canResolve(intent: Intent): Boolean {
        return intent.resolveActivity(appContext.packageManager) != null
    }

    private fun buildDeleteIntent(notificationId: Int): PendingIntent {
        val intent = Intent(appContext, LiveUpdateDeletedReceiver::class.java).apply {
            action = LiveUpdateDeletedReceiver.ACTION_LIVE_UPDATE_DISMISSED
            putExtra(LiveUpdateDeletedReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val DEFAULT_CHANNEL_ID: String = "live_updates"
        private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS: String =
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
    }
}

data class LiveUpdateContent(
    val title: String,
    val text: String,
    val smallIconRes: Int = android.R.drawable.ic_popup_reminder,
    val progress: Int? = null,
    val maxProgress: Int = 100,
    val indeterminate: Boolean = false,
    val whenTimeMillis: Long? = null,
    val useChronometer: Boolean = false,
    val chronometerCountDown: Boolean = false,
    val contentIntent: android.app.PendingIntent? = null,
    val deleteIntent: android.app.PendingIntent? = null,
)

