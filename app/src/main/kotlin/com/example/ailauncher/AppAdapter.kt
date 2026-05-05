package com.example.ailauncher

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class AppAdapter(private val onClick: (AppEntry) -> Unit) :
    ListAdapter<AppEntry, AppAdapter.ViewHolder>(DIFF) {

    private val iconCache = HashMap<String, Drawable>()
    var maxScore = 1f
        private set

    var showScores = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val label: TextView = view.findViewById(R.id.tv_label)
        val stats: TextView = view.findViewById(R.id.tv_stats)
        val progressFill: View = view.findViewById(R.id.v_progress_fill)
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
        
        // Dynamic Weight based on Score (Slick Hybrid)
        val relScore = if (maxScore > 0) (entry.score / maxScore).coerceIn(0f, 1f) else 0f
        holder.label.typeface = when {
            relScore > 0.85f -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            relScore > 0.6f  -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            relScore > 0.3f  -> Typeface.create("sans-serif", Typeface.NORMAL)
            else             -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        holder.label.textSize = if (relScore > 0.85f) 20f else 18f
        holder.label.alpha = 0.35f + (relScore * 0.65f)

        val statsText = buildStats(entry)
        holder.stats.text = statsText
        holder.stats.alpha = if (showScores) 1f else 0.75f
        holder.stats.setTextColor(
            if (statsText == "now") 0xFF1DB954.toInt() else 0xFF888888.toInt()
        )

        holder.icon.setImageDrawable(
            iconCache.getOrPut(entry.packageName) {
                runCatching { pm.getApplicationIcon(entry.packageName) }
                    .getOrDefault(pm.defaultActivityIcon)
            }
        )
        holder.icon.alpha = 0.35f + (relScore * 0.65f)

        // Card tint based on recency heat (mutate to avoid shared-state mutation)
        val cardColor = if (entry.launchCount > 0 && relScore > 0.9f) 0x1A1DB954.toInt()
                        else 0x0FFFFFFF.toInt()
        val bg = holder.itemView.background.mutate() as? GradientDrawable
        bg?.setColor(cardColor)

        if (entry.launchCount > 0) {
            val ratio = if (entry.dailyAvg > 0f) entry.todayCount / entry.dailyAvg else 1f
            val heatColor = when {
                ratio >= 1.2f -> 0xFF1DB954.toInt()
                ratio >= 0.7f -> 0xFFF5A623.toInt()
                else          -> 0xFFE53935.toInt()
            }
            val r = 14f * holder.progressFill.resources.displayMetrics.density
            val fillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // left corners rounded, right corners square
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                setColor(heatColor)
            }
            holder.progressFill.background = fillBg
            holder.progressFill.alpha = 0.28f
            holder.itemView.post {
                val params = holder.progressFill.layoutParams
                params.width = (holder.itemView.width * relScore * 0.6f).toInt()
                holder.progressFill.layoutParams = params
            }
            holder.progressFill.visibility = View.VISIBLE
        } else {
            holder.progressFill.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { v ->
            v.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(80)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onClick(entry)
                }.start()
        }
    }

    fun buildStatsPublic(entry: AppEntry) = buildStats(entry)

    private fun buildStats(entry: AppEntry): String {
        if (entry.launchCount == 0) return "never"
        val lastMillis = entry.lastLaunchedMillis ?: return ""
        val diffMs = System.currentTimeMillis() - lastMillis
        
        return when {
            diffMs < 60_000 -> "now"
            diffMs < 3600_000 -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)}m"
            diffMs < 86400_000 -> "${TimeUnit.MILLISECONDS.toHours(diffMs)}h"
            else -> "${TimeUnit.MILLISECONDS.toDays(diffMs)}d"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(a: AppEntry, b: AppEntry) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppEntry, b: AppEntry) = a == b
        }
    }
}
