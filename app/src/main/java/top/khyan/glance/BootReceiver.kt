package top.khyan.glance

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import top.khyan.glance.notifications.GlimpseStore
import top.khyan.glance.notifications.LiveUpdateNotificationManager

class BootReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            restoreLiveUpdates(context)
        }
    }
}

/** Re-posts all persisted Glimpses as live update notifications. */
@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
fun restoreLiveUpdates(context: Context) {
    val store = GlimpseStore(context)
    val manager = LiveUpdateNotificationManager(context)
    store.getAll().forEach { glimpse ->
        manager.createLiveUpdate(
            notificationId = glimpse.id,
            content = glimpse.toLiveUpdateContent(),
        )
    }
}
