package top.khyan.glance.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles user-dismissed live update notifications and removes them from persistent storage.
 */
class LiveUpdateDeletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LIVE_UPDATE_DISMISSED) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId <= 0) return

        GlimpseStore(context).delete(notificationId)

        // Notify in-app UI to refresh immediately when it is visible.
        context.sendBroadcast(Intent(ACTION_GLIMPSE_STORE_CHANGED))
    }

    companion object {
        const val ACTION_LIVE_UPDATE_DISMISSED =
            "top.khyan.glance.action.LIVE_UPDATE_DISMISSED"
        const val ACTION_GLIMPSE_STORE_CHANGED =
            "top.khyan.glance.action.GLIMPSE_STORE_CHANGED"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
