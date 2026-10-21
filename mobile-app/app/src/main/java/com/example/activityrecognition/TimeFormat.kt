package com.example.activityrecognition

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Makes backend timestamps readable. Falls back to the raw text if it can't parse it. */
object TimeFormat {

    fun pretty(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val date = parse(raw.trim()) ?: return raw
        return SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(date)
    }

    private fun parse(raw: String): Date? = try {
        if (raw.contains(',')) {
            // e.g. "Fri, 16 Oct 2026 06:00:00 GMT" (Flask's default datetime format)
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(raw)
        } else {
            // e.g. "2026-10-16T14:30:00.123456" or "2026-10-16 14:30:00"
            // The backend sends UTC timestamps without a zone marker, so always treat them as UTC.
            val isUtc = true
            val cleaned = raw.replace('T', ' ')
                .replace(Regex("\\.\\d+"), "")
                .removeSuffix("Z")
                .removeSuffix("+00:00")
                .trim()
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            if (isUtc) format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(cleaned)
        }
    } catch (e: Exception) {
        null
    }
}
