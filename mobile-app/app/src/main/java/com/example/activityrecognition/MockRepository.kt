package com.example.activityrecognition

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Fake data for developing the UI before the backend/firmware is ready.
 * Mirrors the real API: "latest" has sensor values, "history" does not.
 * Switches between walking and running every 10 seconds so you can see the UI change.
 */
class MockRepository : ActivityRepository {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.US)

    override fun getLatest(onSuccess: (ActivityRecord) -> Unit, onError: (String) -> Unit) {
        onSuccess(makeRecord(System.currentTimeMillis(), withSensors = true))
    }

    override fun getHistory(
        limit: Int,
        onSuccess: (List<ActivityRecord>) -> Unit,
        onError: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        onSuccess(List(limit) { i ->
            makeRecord(now - i * 2000L, withSensors = false, id = limit - i)
        })
    }

    override fun cancel() = Unit

    private fun makeRecord(timeMillis: Long, withSensors: Boolean, id: Int? = null): ActivityRecord {
        val running = (timeMillis / 10_000L) % 2L == 1L
        val scale = if (running) 3.0 else 1.0
        val timestamp = timeFormat.format(Date(timeMillis))
        val activity = if (running) "running" else "walking"
        if (!withSensors) return ActivityRecord(activity = activity, timestamp = timestamp, id = id)

        return ActivityRecord(
            activity = activity,
            ax = rnd(-0.5, 0.5) * scale,
            ay = rnd(-0.8, 0.0) * scale,
            az = rnd(0.6, 1.2) * scale,
            gx = rnd(-0.3, 0.3) * scale,
            gy = rnd(-0.3, 0.3) * scale,
            gz = rnd(-0.3, 0.6) * scale,
            timestamp = timestamp,
            id = id
        )
    }

    private fun rnd(from: Double, until: Double) = Random.nextDouble(from, until)
}
