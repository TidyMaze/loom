package com.yrolland.loom

import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AppAdapter(
    private val scope: CoroutineScope,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClickItem: (AppEntry) -> Unit = {}
) : ListAdapter<AppEntry, AppAdapter.ViewHolder>(DIFF) {

    private val iconCache = LruCache<String, Drawable>(150)
    var showScores = false
    var accent: Accent = accentForNow()
    var iconSizeDp: Int = 44
    var itemHeightDp: Int = 76

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
        view.clipToOutline = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val pm = holder.itemView.context.packageManager
        val dp = holder.itemView.resources.displayMetrics.density

        holder.itemView.layoutParams = (holder.itemView.layoutParams ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (itemHeightDp * dp).toInt())).apply {
            height = (itemHeightDp * dp).toInt()
        }
        holder.icon.layoutParams = (holder.icon.layoutParams ?: ViewGroup.LayoutParams((iconSizeDp * dp).toInt(), (iconSizeDp * dp).toInt())).apply {
            width = (iconSizeDp * dp).toInt()
            height = (iconSizeDp * dp).toInt()
        }

        holder.label.text = entry.label

        // Use pre-computed rank so filtered lists (search) keep original sizes
        val relScore = entry.rank
        val prominence = 0.35f + (relScore * 0.65f)
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
        holder.label.alpha = prominence

        val statsText = buildStats(entry)
        holder.stats.text = statsText
        holder.stats.alpha = if (showScores) 1f else 0.75f
        holder.stats.setTextColor(statsColor(statsText))

        // Icon: cache hit = sync set; miss = placeholder + async load
        holder.icon.tag = entry.packageName
        val cached = iconCache[entry.packageName]
        if (cached != null) {
            holder.icon.setImageDrawable(cached)
        } else {
            holder.icon.setImageDrawable(pm.defaultActivityIcon)
            scope.launch {
                val icon = withContext(Dispatchers.Default) {
                    runCatching { pm.getApplicationIcon(entry.packageName) }
                        .getOrDefault(pm.defaultActivityIcon)
                }
                iconCache.put(entry.packageName, icon)
                if (holder.icon.tag == entry.packageName) {
                    holder.icon.setImageDrawable(icon)
                }
            }
        }
        holder.icon.alpha = prominence

        val bg = holder.itemView.background.mutate() as? GradientDrawable
        bg?.setColor(0xFF0E0E0E.toInt())

        if (entry.launchCount > 0) {
            val fillScale = entry.rank.coerceIn(0.02f, 1f)
            val r = 14f * dp
            val radii = floatArrayOf(r, r, r, r, r, r, r, r)
            val bar = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = radii
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(accent.barStart, accent.barEnd)
            }
            val halo = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = radii
                gradientType = GradientDrawable.RADIAL_GRADIENT
                setGradientCenter(0.07f, 0.5f)
                gradientRadius = 80f * dp
                colors = intArrayOf(accent.haloCore, accent.haloMid, accent.haloEdge)
            }
            val clip = ClipDrawable(LayerDrawable(arrayOf(bar, halo)), Gravity.START, ClipDrawable.HORIZONTAL)
            holder.progressFill.background = clip
            clip.level = (fillScale * 10000).toInt()
            holder.progressFill.alpha = 1f
            holder.progressFill.visibility = View.VISIBLE
        } else {
            holder.progressFill.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { v ->
            HapticHelper.vibrateClick(v)
            v.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(80)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onClick(entry)
                }.start()
        }
        holder.itemView.setOnLongClickListener { v ->
            HapticHelper.vibrateLongPress(v)
            onLongClickItem(entry)
            true
        }
    }

    fun buildStats(entry: AppEntry): String {
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
        fun statsColor(text: String): Int =
            if (text == "now") 0xFFFFFFFF.toInt() else 0xFF888888.toInt()

        private val DIFF = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(a: AppEntry, b: AppEntry) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppEntry, b: AppEntry) = a == b
        }
    }
}

object HapticHelper {
    fun vibrateClick(view: View) {
        view.isHapticFeedbackEnabled = true
        val ok = view.performHapticFeedback(
            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (!ok) {
            val vibrator = view.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(25L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25L)
                }
            }
        }
    }

    fun vibrateLongPress(view: View) {
        view.isHapticFeedbackEnabled = true
        val ok = view.performHapticFeedback(
            android.view.HapticFeedbackConstants.LONG_PRESS,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        if (!ok) {
            val vibrator = view.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(55L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(55L)
                }
            }
        }
    }
}
