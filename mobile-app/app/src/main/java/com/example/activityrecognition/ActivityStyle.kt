package com.example.activityrecognition

import android.content.Context
import androidx.core.content.ContextCompat

/** Emoji, label and colors for each activity. Add new activities here later. */
object ActivityStyle {

    fun emoji(activity: String): String = when (activity) {
        "walking" -> "🚶"
        "running" -> "🏃"
        else -> "❔"
    }

    fun label(activity: String): String =
        if (activity.isBlank()) "Unknown" else activity.replaceFirstChar { it.uppercase() }

    fun accent(context: Context, activity: String): Int = ContextCompat.getColor(
        context,
        when (activity) {
            "walking" -> R.color.walking_accent
            "running" -> R.color.running_accent
            else -> R.color.unknown_accent
        }
    )

    fun soft(context: Context, activity: String): Int = ContextCompat.getColor(
        context,
        when (activity) {
            "walking" -> R.color.walking_soft
            "running" -> R.color.running_soft
            else -> R.color.unknown_soft
        }
    )
}
