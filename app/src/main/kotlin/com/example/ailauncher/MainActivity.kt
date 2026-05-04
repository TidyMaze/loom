package com.example.ailauncher

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()
    private lateinit var adapter: AppAdapter
    private lateinit var recycler: RecyclerView
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

        findViewById<TextView>(R.id.tv_greeting).text = greeting()

        adapter = AppAdapter { entry ->
            viewModel.recordLaunchAndGetIntent(entry.packageName)?.let { startActivity(it) }
        }

        recycler = findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = this@MainActivity.adapter
        }

        attachScoreGesture()

        val search = findViewById<EditText>(R.id.et_search)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                adapter.submitList(
                    if (query.isEmpty()) fullList
                    else fullList.filter { it.label.contains(query, ignoreCase = true) }
                )
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
        findViewById<TextView>(R.id.tv_greeting).text = greeting()
        viewModel.refresh()
    }

    private fun attachScoreGesture() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) = setScoresVisible(true)
        })

        recycler.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    setScoresVisible(false)
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = Unit
            override fun onRequestDisallowInterceptTouchEvent(b: Boolean) = Unit
        })
    }

    private fun setScoresVisible(visible: Boolean) {
        if (adapter.showScores == visible) return
        adapter.showScores = visible
        val lm = recycler.layoutManager as LinearLayoutManager
        for (i in lm.findFirstVisibleItemPosition()..lm.findLastVisibleItemPosition()) {
            val vh = recycler.findViewHolderForAdapterPosition(i) as? AppAdapter.ViewHolder ?: continue
            val entry = adapter.currentList.getOrNull(i) ?: continue
            if (entry.launchCount > 0) {
                vh.scoreText.animate().alpha(if (visible) 1f else 0f).setDuration(180).start()
            }
        }
    }

    private fun greeting(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        in 18..21 -> "Good evening."
        else -> "Good night."
    }
}
