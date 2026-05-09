package com.yrolland.loom

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

        adapter = AppAdapter { entry ->
            viewModel.recordLaunchAndGetIntent(entry.packageName)?.let { startActivity(it) }
        }
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
        if (!needsRefresh) return
        needsRefresh = false
        applyAccent()
        greeting.alpha = 0f
        greeting.animate().alpha(1f).setDuration(350).start()
        hideSearch()
        recycler.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall_down)
        viewModel.refresh()
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
            override fun onLongPress(e: MotionEvent) = setDetailsVisible(true)

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
                if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    setDetailsVisible(false)
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = Unit
            override fun onRequestDisallowInterceptTouchEvent(b: Boolean) = Unit
        })
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
}
