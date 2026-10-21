package com.example.activityrecognition

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.TimeoutError
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

/** Talks to the Flask REST API using Volley. */
class ApiRepository(context: Context) : ActivityRepository {

    private val queue = Volley.newRequestQueue(context.applicationContext)

    override fun getLatest(onSuccess: (ActivityRecord) -> Unit, onError: (String) -> Unit) {
        get(
            url = "${AppConfig.BASE_URL}/api/activity/latest",
            parse = RecordParser::parseRecord,
            onSuccess = onSuccess,
            onError = onError,
            notFoundMessage = ActivityRepository.NO_DATA // backend returns 404 when empty
        )
    }

    override fun getHistory(
        limit: Int,
        onSuccess: (List<ActivityRecord>) -> Unit,
        onError: (String) -> Unit
    ) {
        get(
            url = "${AppConfig.BASE_URL}/api/activity/history?limit=$limit",
            parse = RecordParser::parseList,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    override fun cancel() {
        queue.cancelAll(TAG)
    }

    private fun <T> get(
        url: String,
        parse: (String) -> T,
        onSuccess: (T) -> Unit,
        onError: (String) -> Unit,
        notFoundMessage: String? = null
    ) {
        val request = StringRequest(
            Request.Method.GET,
            url,
            { body ->
                try {
                    onSuccess(parse(body))
                } catch (e: Exception) {
                    onError("Unexpected response format from the server.")
                }
            },
            { error ->
                if (notFoundMessage != null && error.networkResponse?.statusCode == 404) {
                    onError(notFoundMessage)
                } else {
                    onError(describe(error))
                }
            }
        )
        request.tag = TAG
        request.retryPolicy = DefaultRetryPolicy(5000, 0, 1f)
        queue.add(request)
    }

    private fun describe(error: VolleyError): String = when {
        error is TimeoutError ->
            "Request timed out. Make sure the phone and laptop are on the same Wi-Fi."
        error is NoConnectionError ->
            "Can't reach the server. Check the IP in AppConfig and that the backend is running."
        error.networkResponse != null ->
            "Server error (${error.networkResponse.statusCode})."
        else -> "Network error."
    }

    private companion object {
        const val TAG = "activity-api"
    }
}
