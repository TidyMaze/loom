package com.yrolland.loom

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
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
    var accent: Accent? = null
    var iconSizeDp: Int = 44
    var itemHeightDp: Int = 76
    /** Grid the items live in; read at bind time to size icons to the cell. Set by [spanSizeLookup]. */
    private var grid: GridLayoutManager? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val label: TextView = view.findViewById(R.id.tv_label)
        val stats: TextView = view.findViewById(R.id.tv_stats)
        val rank: View = view.findViewById(R.id.v_rank)
        val rankFill: View = view.findViewById(R.id.v_rank_fill)
        val rankRest: View = view.findViewById(R.id.v_rank_rest)
        /** The entry currently bound to this holder; read by the accessibility action. */
        var entry: AppEntry? = null
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).packageName.hashCode().toLong()

    override fun getItemViewType(position: Int): Int = TileLayout.viewTypeFor(getItem(position).rank)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = when (viewType) {
            TileLayout.TYPE_HERO -> R.layout.item_app_hero
            TileLayout.TYPE_ROW -> R.layout.item_app_row
            else -> R.layout.item_app_tile
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        // The focus ring is the view's foreground state; the scale is the only extra cue.
        view.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) TileLayout.FOCUS_SCALE else 1f
            v.scaleX = s
            v.scaleY = s
        }
        val holder = ViewHolder(view)
        // Named action for TalkBack and switch access: same callback as the long press (opens the item sheet).
        ViewCompat.addAccessibilityAction(view, view.context.getString(R.string.tile_action_more)) { _, _ ->
            holder.entry?.let(onLongClickItem)
            true
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val type = getItemViewType(position)
        val ctx = holder.itemView.context
        val pm = ctx.packageManager
        val dp = holder.itemView.resources.displayMetrics.density
        val a = accent ?: accentForNow(ctx)
        val rank = entry.rank.coerceIn(0f, 1f)
        val detailed = type != TileLayout.TYPE_TILE

        holder.entry = entry
        holder.itemView.minimumHeight = (TileLayout.heightDp(type, itemHeightDp) * dp).toInt()
        // The icon never draws wider than its cell (5-6 columns, large icon size).
        val iconDp = TileLayout.fitIconDp(type, iconSizeDp, itemWidthDp(type, dp))
        val iconPx = (iconDp * dp).toInt()
        holder.icon.layoutParams = holder.icon.layoutParams.apply {
            width = iconPx
            height = iconPx
        }
        // Icons are always fully opaque (contrast); rank is shown by the bar, the icon size and the label weight.

        holder.label.text = entry.label
        // Tiles show no stats, so the spoken description carries rank, frequency and trend.
        holder.itemView.contentDescription = describe(ctx, entry)

        holder.stats.visibility = if (detailed) View.VISIBLE else View.GONE
        holder.stats.text = buildStats(ctx, entry)
        holder.stats.setTextColor(if (isNow(entry)) a.now else a.textSecondary)

        holder.rank.visibility = if (detailed) View.VISIBLE else View.GONE
        if (detailed) setRankWeights(holder, rank)

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

        holder.itemView.setOnClickListener { v ->
            HapticHelper.vibrateClick(v)
            v.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(80)
                .withEndAction {
                    val s = if (v.isFocused) TileLayout.FOCUS_SCALE else 1f
                    v.animate().scaleX(s).scaleY(s).setDuration(100).start()
                    onClick(entry)
                }.start()
        }
        holder.itemView.setOnLongClickListener { v ->
            HapticHelper.vibrateLongPress(v)
            onLongClickItem(entry)
            true
        }
    }

    /** Grid span of every item for the given column count (used by the span lookup and D-pad). */
    fun spanSizes(columns: Int): IntArray =
        IntArray(itemCount) { TileLayout.spanFor(this.getItemViewType(it), columns) }

    /** Also remembers [layoutManager] as the grid whose width sizes the icons (call once, from the screen setup). */
    fun spanSizeLookup(layoutManager: GridLayoutManager): GridLayoutManager.SpanSizeLookup {
        grid = layoutManager
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                TileLayout.spanFor(this@AppAdapter.getItemViewType(position), layoutManager.spanCount)
        }
    }

    /** Width in dp of an item of [type] in the current grid, or 0 when the grid has no width yet. */
    private fun itemWidthDp(type: Int, density: Float): Int {
        val lm = grid ?: return 0
        val gridWidthDp = ((lm.width - lm.paddingLeft - lm.paddingRight) / density).toInt()
        if (gridWidthDp <= 0) return 0
        return TileLayout.itemWidthDp(type, lm.spanCount, gridWidthDp)
    }

    private fun setRankWeights(holder: ViewHolder, rank: Float) {
        (holder.rankFill.layoutParams as LinearLayout.LayoutParams).let {
            it.weight = rank
            holder.rankFill.layoutParams = it
        }
        (holder.rankRest.layoutParams as LinearLayout.LayoutParams).let {
            it.weight = 1f - rank
            holder.rankRest.layoutParams = it
        }
    }

    private fun isNow(entry: AppEntry): Boolean =
        entry.launchCount > 0 &&
            entry.lastLaunchedMillis?.let { System.currentTimeMillis() - it < 60_000 } == true

    /** -1 = below usual, 0 = normal or no baseline, +1 = above usual today. */
    private fun trend(entry: AppEntry): Int = when {
        entry.dailyAvg > 0f && entry.todayCount >= entry.dailyAvg * 1.5f -> 1
        entry.dailyAvg > 0f && entry.todayCount < entry.dailyAvg * 0.4f -> -1
        else -> 0
    }

    fun buildStats(context: Context, entry: AppEntry): String {
        if (entry.launchCount == 0) return context.getString(R.string.stats_never)
        val lastMillis = entry.lastLaunchedMillis ?: return ""
        val diffMs = System.currentTimeMillis() - lastMillis
        val time = when {
            diffMs < 60_000 -> context.getString(R.string.stats_now)
            diffMs < 3600_000 -> context.getString(R.string.stats_minutes, TimeUnit.MILLISECONDS.toMinutes(diffMs))
            diffMs < 86400_000 -> context.getString(R.string.stats_hours, TimeUnit.MILLISECONDS.toHours(diffMs))
            else -> context.getString(R.string.stats_days, TimeUnit.MILLISECONDS.toDays(diffMs))
        }
        val arrow = when (trend(entry)) {
            1 -> " ↑"
            -1 -> " ↓"
            else -> ""
        }
        return time + arrow
    }

    /** Spoken name, rank word, last use and trend, for TalkBack. */
    private fun describe(ctx: Context, entry: AppEntry): String {
        if (entry.launchCount == 0) return ctx.getString(R.string.tile_desc_never, entry.label)
        val last = entry.lastLaunchedMillis
        val timeText = last?.let { agoText(ctx, System.currentTimeMillis() - it) }
            ?: ctx.getString(R.string.tile_time_unknown)
        val freq = ctx.getString(
            when {
                entry.rank >= TileLayout.HERO_MIN_RANK -> R.string.tile_freq_top
                entry.rank >= TileLayout.ROW_MIN_RANK -> R.string.tile_freq_often
                else -> R.string.tile_freq_rare
            }
        )
        val launches = ctx.resources.getQuantityString(R.plurals.tile_launches, entry.launchCount, entry.launchCount)
        val base = ctx.getString(R.string.tile_desc_used, entry.label, timeText, launches, freq)
        return when (trend(entry)) {
            1 -> "$base, ${ctx.getString(R.string.tile_more_than_usual)}"
            -1 -> "$base, ${ctx.getString(R.string.tile_less_than_usual)}"
            else -> base
        }
    }

    private fun agoText(ctx: Context, diffMs: Long): String = when {
        diffMs < 60_000 -> ctx.getString(R.string.tile_time_now)
        diffMs < 3600_000 -> ctx.getString(R.string.tile_time_minutes, TimeUnit.MILLISECONDS.toMinutes(diffMs))
        diffMs < 86400_000 -> ctx.getString(R.string.tile_time_hours, TimeUnit.MILLISECONDS.toHours(diffMs))
        else -> ctx.getString(R.string.tile_time_days, TimeUnit.MILLISECONDS.toDays(diffMs))
    }

    companion object {
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
