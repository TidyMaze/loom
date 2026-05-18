package com.yrolland.loom

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()
    private lateinit var adapter: AppAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var greeting: TextView
    private var fullList: List<AppEntry> = emptyList()
    private var searchShownAt = 0L
    private var needsRefresh = false
    private var launcherResumeMs = System.currentTimeMillis()

    private fun filteredList(query: String) =
        if (query.isEmpty()) fullList.filter { it.launchCount > 0 }
        else fullList.filter { it.label.contains(query, ignoreCase = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.BLACK
            navigationBarColor = Color.BLACK
            decorView.setBackgroundColor(Color.BLACK)
        }

        setContentView(R.layout.activity_main)

        greeting = findViewById(R.id.tv_greeting)

        search = findViewById(R.id.et_search)

        adapter = AppAdapter(
            scope = lifecycleScope,
            onClick = { entry ->
                val ctx = LaunchContext.capture(this, launcherResumeMs, entry.packageName)
                viewModel.recordLaunchAndGetIntent(entry.packageName, ctx)?.let { startActivity(it) }
            },
            onLongClickItem = { entry -> showItemSheet(entry) }
        )
        applyAccent()

        recycler = findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = this@MainActivity.adapter
            (itemAnimator as? DefaultItemAnimator)?.apply {
                addDuration = 120; removeDuration = 80; moveDuration = 150; changeDuration = 100
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (search.visibility == View.VISIBLE) hideSearch()
            }
        })

        attachGestures()
        attachSwipeToReset()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                adapter.submitList(filteredList(s?.toString()?.trim().orEmpty()))
            }
        })

        // Request runtime perms for the feature-collection pipeline. Each is optional —
        // if denied, the corresponding capture function silently returns null.
        val perms = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CALENDAR)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.READ_CALENDAR)
            if (android.os.Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (android.os.Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_PERMS)
        }

        // Subscribe to activity recognition updates (callback-driven, ~60s cadence).
        ActivityState.startUpdates(this)

        needsRefresh = true // treat first launch as coming from outside

        viewModel.apps.observe(this) { apps ->
            fullList = apps
            adapter.submitList(filteredList(search.text?.toString()?.trim().orEmpty())) {
                recycler.scrollToPosition(0)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        needsRefresh = true
    }

    override fun onResume() {
        super.onResume()
        launcherResumeMs = System.currentTimeMillis()
        if (!needsRefresh) return
        needsRefresh = false
        applyAccent()
        greeting.alpha = 0f
        greeting.animate().alpha(1f).setDuration(350).start()
        hideSearch()
        recycler.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall_down)
        viewModel.refresh(LaunchContext.capture(this, launcherResumeMs))
    }

    private fun showSearch() {
        if (search.visibility == View.VISIBLE) return
        searchShownAt = System.currentTimeMillis()
        search.visibility = View.VISIBLE
        search.alpha = 0f
        search.translationY = -24f
        search.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    private fun hideSearch() {
        if (search.visibility == View.GONE) return
        search.animate().alpha(0f).translationY(-24f).setDuration(150).withEndAction {
            search.visibility = View.GONE
            search.text?.clear()
        }.start()
        search.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun attachGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val lm = recycler.layoutManager as LinearLayoutManager
                if (distanceY < -10f && lm.findFirstCompletelyVisibleItemPosition() <= 0) showSearch()
                if (distanceY > 10f && search.visibility == View.VISIBLE
                    && System.currentTimeMillis() - searchShownAt > 400) hideSearch()
                return false
            }
        })

        recycler.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = Unit
            override fun onRequestDisallowInterceptTouchEvent(b: Boolean) = Unit
        })

        // Tap greeting → open settings (long-press intercepted by system gesture zone)
        greeting.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showItemSheet(entry: AppEntry) {
        val ctx = this
        val view = layoutInflater.inflate(R.layout.sheet_item, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog)
            .setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.sheet_title).text = entry.label
        view.findViewById<TextView>(R.id.sheet_hide).setOnClickListener {
            viewModel.setHidden(entry.packageName, true)
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.sheet_info).setOnClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${entry.packageName}"))
            startActivity(intent)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun setDetailsVisible(visible: Boolean) {
        if (adapter.showScores == visible) return
        adapter.showScores = visible
        val lm = recycler.layoutManager as LinearLayoutManager
        for (i in lm.findFirstVisibleItemPosition()..lm.findLastVisibleItemPosition()) {
            val vh = recycler.findViewHolderForAdapterPosition(i) as? AppAdapter.ViewHolder ?: continue
            val entry = adapter.currentList.getOrNull(i) ?: continue
            vh.stats.animate().alpha(1f).setDuration(180).withEndAction {
                if (visible) {
                    vh.stats.text = if (entry.score > 0f) "%.1f%%".format(entry.score * 100f) else "—"
                    vh.stats.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    vh.stats.text = adapter.buildStats(entry)
                    vh.stats.setTextColor(AppAdapter.statsColor(vh.stats.text.toString()))
                    vh.stats.animate().alpha(0.6f).setDuration(180).start()
                }
            }.start()
        }
    }

    private fun attachSwipeToReset() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * resources.displayMetrics.density
            textAlign = Paint.Align.RIGHT
        }
        val swipeThreshold = 0.7f
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = swipeThreshold
            override fun getSwipeEscapeVelocity(defaultValue: Float) = defaultValue * 4f
            override fun getSwipeVelocityThreshold(defaultValue: Float) = defaultValue * 4f
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val entry = adapter.currentList[vh.adapterPosition]
                viewModel.resetApp(entry.packageName)
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
                val v = vh.itemView
                val r = 14 * v.resources.displayMetrics.density
                val rect = RectF(v.left.toFloat(), v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
                val triggered = dX < -v.width * swipeThreshold
                paint.color = if (triggered) 0xFFE53935.toInt() else 0xFF8B0000.toInt()
                c.drawRoundRect(rect, r, r, paint)
                // Center "Reset" in the revealed area (right of the sliding row)
                val revealedLeft = v.right + dX
                val revealedCenter = (revealedLeft + v.right) / 2f
                textPaint.textAlign = Paint.Align.CENTER
                c.drawText("Reset", revealedCenter, rect.centerY() + textPaint.textSize * 0.35f, textPaint)
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        })
        helper.attachToRecyclerView(recycler)
    }

    private fun applyAccent() {
        val a = accentForNow()
        greeting.text = greetingText()
        greeting.setTextColor(a.greeting)
        adapter.accent = a
        adapter.notifyDataSetChanged()
    }

    private fun greetingText(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        in 18..21 -> "Good evening."
        else -> "Good night."
    }

    companion object {
        private const val REQ_PERMS = 1001
    }
}
