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

        viewModel.apps.observe(this) { apps ->
            fullList = apps
            val query = search.text?.toString()?.trim().orEmpty()
            adapter.submitList(
                if (query.isEmpty()) apps
                else apps.filter { it.label.contains(query, ignoreCase = true) }
            ) { recycler.scrollToPosition(0) }
        }
    }

    override fun onResume() {
        super.onResume()
        greeting.text = greetingText()
        greeting.alpha = 0f
        greeting.animate().alpha(1f).setDuration(350).start()
        hideSearch()
        recycler.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall_down)
        viewModel.refresh()
    }

    private fun showSearch() {
        if (search.visibility == View.VISIBLE) return
        search.visibility = View.VISIBLE
        search.alpha = 0f
        search.translationY = -24f
        search.animate().alpha(1f).translationY(0f).setDuration(180).start()
        search.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
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
            val target = if (visible) 1f else 0f
            vh.stats.animate().alpha(target).setDuration(180).start()
            if (entry.launchCount > 0) vh.scoreText.animate().alpha(target).setDuration(180).start()
        }
    }

    private fun greetingText(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        in 18..21 -> "Good evening."
        else -> "Good night."
    }
}
