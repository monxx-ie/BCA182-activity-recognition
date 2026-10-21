package com.example.activityrecognition

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Turns the backend's JSON into [ActivityRecord]s.
 *
 * Confirmed backend format:
 *  - /api/activity/latest  -> {"timestamp", "activity", "accelerometer": {x,y,z}, "gyroscope": {x,y,z}}
 *  - /api/activity/history -> [{"id", "timestamp", "activity"}, ...]  (no sensor values)
 *
 * Also tolerates flat fields (ax, ay, ...) and a few wrapper shapes, just in case.
 */
object RecordParser {

    private val WRAPPER_ARRAY_KEYS = listOf("data", "history", "records", "results", "activities")

    @Throws(JSONException::class)
    fun parseRecord(body: String): ActivityRecord {
        val root = JSONTokener(body).nextValue()
        val obj = when (root) {
            is JSONObject -> root.optJSONObject("data") ?: root
            is JSONArray -> root.getJSONObject(0)
            else -> throw JSONException("Unexpected JSON")
        }
        return fromJson(obj)
    }

    @Throws(JSONException::class)
    fun parseList(body: String): List<ActivityRecord> {
        val root = JSONTokener(body).nextValue()
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> WRAPPER_ARRAY_KEYS.firstNotNullOfOrNull { root.optJSONArray(it) }
                ?: throw JSONException("No list found in response")
            else -> throw JSONException("Unexpected JSON")
        }
        return List(array.length()) { fromJson(array.getJSONObject(it)) }
    }

    private fun fromJson(obj: JSONObject): ActivityRecord {
        val accel = obj.optJSONObject("accelerometer")
        val gyro = obj.optJSONObject("gyroscope")

        val activity = listOf("activity", "prediction", "label")
            .firstOrNull { obj.has(it) && !obj.isNull(it) }
            ?.let { obj.optString(it) }
            ?.trim()
            ?.lowercase()
            ?: "unknown"

        val timestamp = if (obj.has("timestamp") && !obj.isNull("timestamp")) {
            obj.optString("timestamp")
        } else null

        val id = if (obj.has("id") && !obj.isNull("id")) obj.optInt("id") else null

        return ActivityRecord(
            activity = activity,
            ax = accel?.number("x") ?: obj.number("ax"),
            ay = accel?.number("y") ?: obj.number("ay"),
            az = accel?.number("z") ?: obj.number("az"),
            gx = gyro?.number("x") ?: obj.number("gx"),
            gy = gyro?.number("y") ?: obj.number("gy"),
            gz = gyro?.number("z") ?: obj.number("gz"),
            timestamp = timestamp,
            id = id
        )
    }

    /** Returns the number at [key], or null if missing / not a number. */
    private fun JSONObject.number(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key, Double.NaN).takeIf { !it.isNaN() } else null
}
