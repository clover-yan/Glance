package top.khyan.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import top.khyan.glance.notifications.GlimpseStore
import top.khyan.glance.notifications.LiveUpdateNotificationManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            restoreLiveUpdates(context)
        }
    }
}

/** Re-posts all persisted Glimpses as live update notifications. */
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
