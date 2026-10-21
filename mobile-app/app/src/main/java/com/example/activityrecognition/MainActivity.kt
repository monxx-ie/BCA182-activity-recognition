package com.example.activityrecognition

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.activityrecognition.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ActivityRepository

    private val adapter = HistoryAdapter { showDetails(it) }
    private val handler = Handler(Looper.getMainLooper())

    private var latestRecord: ActivityRecord? = null
    private var shownActivity: String? = null
    private var walkingFraction = 0.5f
    private var pulse: ObjectAnimator? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, AppConfig.REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = if (AppConfig.USE_MOCK_DATA) MockRepository() else ApiRepository(this)

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { refresh() }
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface_solid)
        )
        binding.swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent_blue))
        binding.cardLatest.setOnClickListener { latestRecord?.let { showDetails(it) } }
        playEntrance()

        setConnection(
            if (AppConfig.USE_MOCK_DATA) "● Mock data" else "● Connecting…",
            R.color.text_secondary
        )
    }

    override fun onStart() {
        super.onStart()
        handler.post(refreshRunnable) // auto-refresh while the screen is visible
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(refreshRunnable)
        repository.cancel()
        stopPulse()
    }

    /** The latest card fades and slides in when the screen first opens. */
    private fun playEntrance() {
        binding.cardLatest.alpha = 0f
        binding.cardLatest.translationY = 32f * resources.displayMetrics.density
        binding.cardLatest.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(550)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    /** Gently pulses the "Live" pill so it feels alive. */
    private fun startPulse() {
        if (pulse?.isRunning == true) return
        pulse = ObjectAnimator.ofFloat(binding.tvConnection, View.ALPHA, 1f, 0.45f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        binding.tvConnection.alpha = 1f
    }

    private fun refresh() {
        repository.getLatest(
            onSuccess = { record ->
                latestRecord = record
                showLatest(record)
                showOnline()
            },
            onError = { showError(it) }
        )
        repository.getHistory(
            limit = AppConfig.HISTORY_LIMIT,
            onSuccess = { records ->
                adapter.submitList(records)
                updateSummary(records)
                binding.swipeRefresh.isRefreshing = false
            },
            onError = {
                binding.swipeRefresh.isRefreshing = false
                showError(it)
            }
        )
    }

    /** Shows how many walking vs running records are in the recent history, as an animated bar. */
    private fun updateSummary(records: List<ActivityRecord>) {
        val walking = records.count { it.activity == "walking" }
        val running = records.count { it.activity == "running" }
        val total = walking + running
        if (total == 0) {
            binding.cardSummary.visibility = View.GONE
            return
        }
        binding.cardSummary.visibility = View.VISIBLE
        binding.tvWalkingCount.text = "🚶 Walking: $walking"
        binding.tvRunningCount.text = "Running: $running 🏃"

        val target = walking.toFloat() / total
        ValueAnimator.ofFloat(walkingFraction, target).apply {
            duration = 400
            addUpdateListener {
                val f = it.animatedValue as Float
                setWeight(binding.viewWalking, f)
                setWeight(binding.viewRunning, 1f - f)
            }
            start()
        }
        walkingFraction = target
    }

    private fun setWeight(view: View, weight: Float) {
        (view.layoutParams as LinearLayout.LayoutParams).weight = weight
        view.requestLayout()
    }

    private fun showLatest(record: ActivityRecord) {
        binding.tvEmoji.text = ActivityStyle.emoji(record.activity)
        binding.tvActivity.text = ActivityStyle.label(record.activity)
        binding.tvActivity.setTextColor(ActivityStyle.accent(this, record.activity))
        binding.tvTimestamp.text = TimeFormat.pretty(record.timestamp)

        if (record.activity != shownActivity) {
            animateChange(record.activity, firstTime = shownActivity == null)
            shownActivity = record.activity
        }
    }

    /** Fades the card color and pops the emoji when the activity changes. */
    private fun animateChange(activity: String, firstTime: Boolean) {
        val target = ActivityStyle.soft(this, activity)
        if (firstTime) {
            binding.cardLatest.setCardBackgroundColor(target)
            return
        }
        val from = binding.cardLatest.cardBackgroundColor.defaultColor
        ValueAnimator.ofArgb(from, target).apply {
            duration = 400
            addUpdateListener { binding.cardLatest.setCardBackgroundColor(it.animatedValue as Int) }
            start()
        }
        binding.tvEmoji.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).withEndAction {
            binding.tvEmoji.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    private fun showOnline() {
        binding.tvError.visibility = View.GONE
        if (!AppConfig.USE_MOCK_DATA) {
            setConnection("● Live", R.color.status_live)
            startPulse()
        }
    }

    private fun showError(message: String) {
        binding.swipeRefresh.isRefreshing = false
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        stopPulse()
        if (message == ActivityRepository.NO_DATA) {
            // Server is reachable, there's just nothing recorded yet.
            binding.tvError.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            setConnection("● Connected, no data yet", R.color.status_live)
        } else {
            binding.tvError.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
            setConnection("● Offline", R.color.status_offline)
        }
    }

    private fun setConnection(text: String, colorRes: Int) {
        binding.tvConnection.text = text
        binding.tvConnection.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showDetails(record: ActivityRecord) {
        fun f(v: Double?) = if (v == null) "—" else String.format(java.util.Locale.US, "%.3f", v)
        val message = buildString {
            append("Time: ${TimeFormat.pretty(record.timestamp)}")
            if (record.hasSensors) {
                append("\n\nAccelerometer")
                append("\nax: ${f(record.ax)}\nay: ${f(record.ay)}\naz: ${f(record.az)}")
                append("\n\nGyroscope")
                append("\ngx: ${f(record.gx)}\ngy: ${f(record.gy)}\ngz: ${f(record.gz)}")
            } else {
                append("\n\nSensor values are only available for the latest reading.")
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("${ActivityStyle.emoji(record.activity)} ${ActivityStyle.label(record.activity)}")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
}
