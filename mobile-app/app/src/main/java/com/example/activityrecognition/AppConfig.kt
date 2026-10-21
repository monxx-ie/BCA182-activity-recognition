package com.example.activityrecognition

/**
 * Central place for settings you may need to change.
 */
object AppConfig {
    /** Backend IP of the laptop. Change this if the laptop's IP changes. No trailing slash. */
    const val BASE_URL = "http://192.168.254.124:5000"

    /** true = fake data (no backend needed). Set to false to use the real REST API. */
    const val USE_MOCK_DATA = false

    /** How many history records to request. */
    const val HISTORY_LIMIT = 20

    /** How often the screen refreshes, in milliseconds. */
    const val REFRESH_INTERVAL_MS = 2000L
}
