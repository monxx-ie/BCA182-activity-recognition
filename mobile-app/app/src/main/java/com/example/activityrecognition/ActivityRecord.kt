package com.example.activityrecognition

/**
 * One recognized activity.
 * Sensor values are only present on the "latest" response; history records don't include them.
 * [timestamp] is kept as the raw string from the backend (may be null).
 */
data class ActivityRecord(
    val activity: String,
    val ax: Double? = null,
    val ay: Double? = null,
    val az: Double? = null,
    val gx: Double? = null,
    val gy: Double? = null,
    val gz: Double? = null,
    val timestamp: String? = null,
    val id: Int? = null
) {
    val hasSensors: Boolean
        get() = listOf(ax, ay, az, gx, gy, gz).all { it != null }
}
