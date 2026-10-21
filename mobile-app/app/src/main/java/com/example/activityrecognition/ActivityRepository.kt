package com.example.activityrecognition

/**
 * Source of activity data. Implemented by [MockRepository] and [ApiRepository],
 * so the UI doesn't care where the data comes from.
 * Callbacks are always delivered on the main thread.
 */
interface ActivityRepository {
    fun getLatest(onSuccess: (ActivityRecord) -> Unit, onError: (String) -> Unit)
    fun getHistory(limit: Int, onSuccess: (List<ActivityRecord>) -> Unit, onError: (String) -> Unit)

    /** Cancel any pending requests. */
    fun cancel()

    companion object {
        /** Sent to onError when the backend has no activity recorded yet (HTTP 404). */
        const val NO_DATA = "No activity recorded yet. Waiting for the device…"
    }
}
