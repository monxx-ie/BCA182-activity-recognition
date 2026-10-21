package com.example.activityrecognition

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.activityrecognition.databinding.ItemActivityBinding

class HistoryAdapter(
    private val onClick: (ActivityRecord) -> Unit
) : ListAdapter<ActivityRecord, HistoryAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemActivityBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        with(holder.binding) {
            val accent = ActivityStyle.accent(root.context, record.activity)
            tvEmoji.text = ActivityStyle.emoji(record.activity)
            tvEmoji.backgroundTintList =
                ColorStateList.valueOf(ActivityStyle.soft(root.context, record.activity))
            viewAccent.setBackgroundColor(accent)
            tvActivity.text = ActivityStyle.label(record.activity)
            tvActivity.setTextColor(accent)
            tvTime.text = TimeFormat.pretty(record.timestamp)
            root.setOnClickListener { onClick(record) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ActivityRecord>() {
        override fun areItemsTheSame(old: ActivityRecord, new: ActivityRecord) =
            if (old.id != null) old.id == new.id else old == new
        override fun areContentsTheSame(old: ActivityRecord, new: ActivityRecord) = old == new
    }
}
