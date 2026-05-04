package com.example.ailauncher

import android.graphics.Color
import android.graphics.drawable.Drawable
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
import kotlin.math.pow
import kotlin.math.roundToInt

class AppAdapter(private val onClick: (AppEntry) -> Unit) :
    ListAdapter<AppEntry, AppAdapter.ViewHolder>(DIFF) {

    private val timeFmt = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())
    private val iconCache = HashMap<String, Drawable>()
    var maxScore = 1f
        private set

    var showScores = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val label: TextView = view.findViewById(R.id.tv_label)
        val stats: TextView = view.findViewById(R.id.tv_stats)
        val scoreText: TextView = view.findViewById(R.id.tv_score)
        val scoreBar: View = view.findViewById(R.id.score_bar)
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).packageName.hashCode().toLong()

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
        holder.stats.alpha = if (showScores) 1f else 0f

        holder.icon.setImageDrawable(
            iconCache.getOrPut(entry.packageName) {
                runCatching { pm.getApplicationIcon(entry.packageName) }
                    .getOrDefault(pm.defaultActivityIcon)
            }
        )

        if (entry.launchCount > 0) {
            val rel = (entry.score / maxScore).coerceIn(0f, 1f).pow(0.5f)
            holder.scoreBar.setBackgroundColor(scoreColor(rel))
            holder.scoreBar.alpha = 1f
            holder.scoreText.text = "%.2f".format(entry.score)
            holder.scoreText.alpha = if (showScores) 1f else 0f
        } else {
            holder.scoreBar.alpha = 0f
            holder.scoreText.alpha = 0f
        }

        holder.itemView.setOnClickListener { v ->
            v.animate()
                .scaleX(0.93f).scaleY(0.93f)
                .setDuration(80)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onClick(entry)
                }.start()
        }
        holder.itemView.setOnLongClickListener { true }
    }

    private fun scoreColor(rel: Float): Int {
        return when {
            rel >= 0.5f -> lerpColor(0xFFF5A623.toInt(), 0xFF1DB954.toInt(), (rel - 0.5f) * 2f)
            else -> lerpColor(0xFFE53935.toInt(), 0xFFF5A623.toInt(), rel * 2f)
        }
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * t).roundToInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * t).roundToInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).roundToInt()
        return Color.rgb(r, g, b)
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
