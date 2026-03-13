package top.khyan.glance.notifications

import kotlinx.serialization.Serializable

/**
 * Describes a single live update activity.
 * progress == -1 means no progress bar.
 */
@Serializable
data class Glimpse(
    val id: Int,
    val title: String,
    val text: String,
    val progress: Int = -1,
) {
    /** Human-readable label used in the list UI. */
    fun toLabel(): String {
        return "#$id · $title${if (progress < 0) "" else " · $progress%"}"
    }

    fun toLiveUpdateContent() = LiveUpdateContent(
        title = title,
        text = text,
        progress = progress,
    )
}

