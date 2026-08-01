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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalTime

class MainActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()
    private lateinit var adapter: AppAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var greeting: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var btnOpenChrome: View
    private lateinit var btnOpenGemini: View
    private var fullList: List<AppEntry> = emptyList()
    private var searchShownAt = 0L
    private var needsRefresh = false
    private var launcherResumeMs = System.currentTimeMillis()

    private fun filteredList(query: String) =
        if (query.isEmpty()) fullList.filter { it.launchCount > 0 }
        else fullList.filter { FuzzyMatcher.matches(query, it.label) }

    private fun updateListAndEmptyState(query: String, onSubmitted: (() -> Unit)? = null) {
        val list = filteredList(query)
        adapter.submitList(list) {
            onSubmitted?.invoke()
        }
        layoutEmptyState.visibility = if (SearchFallback.shouldShowEmptyState(query, list.isNotEmpty())) View.VISIBLE else View.GONE
    }

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
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        btnOpenChrome = findViewById(R.id.btn_open_chrome)
        btnOpenGemini = findViewById(R.id.btn_open_gemini)

        btnOpenChrome.setOnClickListener {
            val query = search.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                val url = SearchFallback.buildChromeUrl(query)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    setPackage("com.android.chrome")
                }
                runCatching { startActivity(intent) }.onFailure {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
        }

        btnOpenGemini.setOnClickListener {
            val query = search.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                val intent = SearchFallback.createGeminiIntent(this, query)
                runCatching { startActivity(intent) }
            }
        }

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
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, 4)
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
                updateListAndEmptyState(s?.toString()?.trim().orEmpty())
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
            updateListAndEmptyState(search.text?.toString()?.trim().orEmpty()) {
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
        viewModel.updateLastLaunchDwell()
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
        layoutEmptyState.visibility = View.GONE
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
        view.findViewById<TextView>(R.id.sheet_reset).setOnClickListener {
            dialog.dismiss()
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("Reset app stats?")
                .setMessage("This will clear usage history for ${entry.label}.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.resetApp(entry.packageName)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        view.findViewById<TextView>(R.id.sheet_info).setOnClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${entry.packageName}"))
            startActivity(intent)
            dialog.dismiss()
        }
        dialog.show()
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
