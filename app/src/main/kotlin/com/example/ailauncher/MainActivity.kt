package com.example.ailauncher

import android.graphics.Color
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
    private var wasPaused = false

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
        greeting.text = greetingText()

        search = findViewById(R.id.et_search)

        adapter = AppAdapter { entry ->
            viewModel.recordLaunchAndGetIntent(entry.packageName)?.let { startActivity(it) }
        }

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

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                adapter.submitList(
                    if (query.isEmpty()) fullList
                    else fullList.filter { it.label.contains(query, ignoreCase = true) }
                )
                if (query.isEmpty()) hideSearch()
            }
        })

        wasPaused = true // treat first launch as coming from outside

        viewModel.apps.observe(this) { apps ->
            fullList = apps
            val query = search.text?.toString()?.trim().orEmpty()
            adapter.submitList(
                if (query.isEmpty()) apps
                else apps.filter { it.label.contains(query, ignoreCase = true) }
            ) { recycler.scrollToPosition(0) }
        }
    }

    override fun onPause() {
        super.onPause()
        wasPaused = true
    }

    override fun onResume() {
        super.onResume()
        if (!wasPaused) return
        wasPaused = false
        greeting.text = greetingText()
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
                if (distanceY < -10f && lm.findFirstCompletelyVisibleItemPosition() == 0) showSearch()
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
                    vh.stats.text = "%.2f".format(entry.score)
                    vh.stats.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    vh.stats.text = adapter.buildStatsPublic(entry)
                    vh.stats.setTextColor(
                        if (vh.stats.text == "now") 0xFF1DB954.toInt() else 0xFF888888.toInt()
                    )
                    vh.stats.animate().alpha(0.6f).setDuration(180).start()
                }
            }.start()
        }
    }

    private fun greetingText(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        in 18..21 -> "Good evening."
        else -> "Good night."
    }
}
