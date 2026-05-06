package com.example.ailauncher

import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class AppAdapter(private val onClick: (AppEntry) -> Unit) :
    ListAdapter<AppEntry, AppAdapter.ViewHolder>(DIFF) {

    private val iconCache = HashMap<String, Drawable>()
    var showScores = false
    var accent: Accent = accentForNow()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val label: TextView = view.findViewById(R.id.tv_label)
        val stats: TextView = view.findViewById(R.id.tv_stats)
        val progressFill: View = view.findViewById(R.id.v_progress_fill)
        val row: android.widget.LinearLayout = view.findViewById(R.id.ll_row)
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        view.clipToOutline = true
        return ViewHolder(view)
    }

    override fun submitList(list: List<AppEntry>?) = submitList(list, null)

    override fun submitList(list: List<AppEntry>?, commitCallback: Runnable?) {
        super.submitList(list, commitCallback)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val pm = holder.itemView.context.packageManager

        holder.label.text = entry.label

        // Use pre-computed rank so filtered lists (search) keep original sizes
        val relScore = entry.rank
        holder.label.typeface = when {
            relScore > 0.85f -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            relScore > 0.6f  -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            relScore > 0.3f  -> Typeface.create("sans-serif", Typeface.NORMAL)
            else             -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        holder.label.textSize = when {
            relScore > 0.85f -> 20f
            relScore > 0.5f  -> 18f
            else             -> 16f
        }
        holder.label.alpha = 0.35f + (relScore * 0.65f)

        val dp = holder.itemView.resources.displayMetrics.density

        val statsText = buildStats(entry)
        holder.stats.text = statsText
        holder.stats.alpha = if (showScores) 1f else 0.75f
        holder.stats.setTextColor(
            if (statsText == "now") 0xFFFFFFFF.toInt() else 0xFF888888.toInt()
        )

        holder.icon.setImageDrawable(
            iconCache.getOrPut(entry.packageName) {
                runCatching { pm.getApplicationIcon(entry.packageName) }
                    .getOrDefault(pm.defaultActivityIcon)
            }
        )
        holder.icon.alpha = 0.35f + (relScore * 0.65f)

        val bg = holder.itemView.background.mutate() as? GradientDrawable
        bg?.setColor(0xFF0E0E0E.toInt()) // near-black card, lifts off pure black bg

        if (entry.launchCount > 0) {
            val fillScale = entry.rank.coerceIn(0.02f, 1f)
            val a = accent
            val dp = holder.progressFill.resources.displayMetrics.density
            val r = 14f * dp
            val radii = floatArrayOf(r, r, r, r, r, r, r, r)
            val bar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = radii
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(a.barStart, a.barEnd)
            }
            val halo = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = radii
                gradientType = GradientDrawable.RADIAL_GRADIENT
                setGradientCenter(0.07f, 0.5f)
                gradientRadius = 80f * dp
                colors = intArrayOf(a.haloCore, a.haloMid, a.haloEdge)
            }
            val clip = ClipDrawable(LayerDrawable(arrayOf(bar, halo)), Gravity.START, ClipDrawable.HORIZONTAL)
            holder.progressFill.background = clip
            clip.level = (fillScale * 10000).toInt()
            holder.progressFill.alpha = 1f
            holder.progressFill.scaleX = 1f
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
        val time = when {
            diffMs < 60_000 -> "now"
            diffMs < 3600_000 -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)}m"
            diffMs < 86400_000 -> "${TimeUnit.MILLISECONDS.toHours(diffMs)}h"
            else -> "${TimeUnit.MILLISECONDS.toDays(diffMs)}d"
        }
        val arrow = when {
            entry.dailyAvg > 0f && entry.todayCount >= entry.dailyAvg * 1.5f -> " ↑"
            entry.dailyAvg > 0f && entry.todayCount < entry.dailyAvg * 0.4f  -> " ↓"
            else -> ""
        }
        return time + arrow
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(a: AppEntry, b: AppEntry) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppEntry, b: AppEntry) = a == b
        }
    }
}
