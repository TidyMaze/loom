package com.example.ailauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AppAdapter(private val onClick: (AppEntry) -> Unit) :
    ListAdapter<AppEntry, AppAdapter.ViewHolder>(DIFF) {

    private val timeFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())
    private var maxScore = 1f

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val label: TextView = view.findViewById(R.id.tv_label)
        val stats: TextView = view.findViewById(R.id.tv_stats)
        val scoreText: TextView = view.findViewById(R.id.tv_score)
        val scoreBar: View = view.findViewById(R.id.score_bar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun submitList(list: List<AppEntry>?) {
        maxScore = list?.filter { it.launchCount > 0 }?.maxOfOrNull { it.score }?.coerceAtLeast(0.001f) ?: 1f
        super.submitList(list)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val pm = holder.itemView.context.packageManager

        holder.label.text = entry.label
        holder.stats.text = buildStats(entry)

        runCatching {
            holder.icon.setImageDrawable(pm.getApplicationIcon(entry.packageName))
        }.onFailure {
            holder.icon.setImageDrawable(pm.defaultActivityIcon)
        }

        if (entry.launchCount > 0) {
            val rel = (entry.score / maxScore).coerceIn(0.15f, 1f)
            holder.scoreBar.alpha = rel
            holder.scoreText.alpha = rel
            holder.scoreText.text = "%.2f".format(entry.score)
        } else {
            holder.scoreBar.alpha = 0f
            holder.scoreText.alpha = 0f
        }

        holder.itemView.setOnClickListener { onClick(entry) }
    }

    private fun buildStats(entry: AppEntry): String {
        if (entry.launchCount == 0) return "never launched"
        val lastStr = entry.lastLaunchedMillis?.let {
            timeFmt.format(Instant.ofEpochMilli(it))
        } ?: "?"
        return "${entry.launchCount}× · last $lastStr"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(a: AppEntry, b: AppEntry) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppEntry, b: AppEntry) = a == b
        }
    }
}
