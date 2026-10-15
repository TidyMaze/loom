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
import android.view.KeyEvent
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
    private lateinit var gridStore: GridStore
    private lateinit var onboardingStore: OnboardingStore
    private var layoutOnboarding: View? = null
    private var currentOnboardingStep = 0
    private var fullList: List<AppEntry> = emptyList()
    private var searchShownAt = 0L
    private var needsRefresh = false
    private var launcherResumeMs = System.currentTimeMillis()

    private fun filteredList(query: String) =
        SearchFallback.filterApps(fullList, query)

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

        gridStore = GridStore(this)
        onboardingStore = OnboardingStore(this)
        greeting = findViewById(R.id.tv_greeting)
        search = findViewById(R.id.et_search)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        btnOpenChrome = findViewById(R.id.btn_open_chrome)
        btnOpenGemini = findViewById(R.id.btn_open_gemini)

        btnOpenChrome.setOnClickListener {
            val query = search.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                runCatching { startActivity(SearchFallback.createWebSearchIntent(query)) }
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
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, gridStore.getColumnCount())
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

        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
            ) {
                val query = search.text?.toString()?.trim().orEmpty()
                val list = filteredList(query)
                val topApp = list.firstOrNull()
                if (topApp != null) {
                    val ctx = LaunchContext.capture(this, launcherResumeMs, topApp.packageName)
                    viewModel.recordLaunchAndGetIntent(topApp.packageName, ctx)?.let { startActivity(it) }
                    true
                } else false
            } else false
        }

        needsRefresh = true // treat first launch as coming from outside

        viewModel.apps.observe(this) { apps ->
            fullList = apps
            updateListAndEmptyState(search.text?.toString()?.trim().orEmpty()) {
                recycler.scrollToPosition(0)
            }
        }

        checkAndShowOnboarding()
    }

    override fun onPause() {
        super.onPause()
        needsRefresh = true
    }

    override fun onResume() {
        super.onResume()
        (recycler.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount = gridStore.getColumnCount()
        adapter.iconSizeDp = gridStore.getIconSizeDp()
        adapter.itemHeightDp = gridStore.getItemHeightDp()
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
        search.animate().alpha(1f).translationY(0f).setDuration(180).withEndAction {
            search.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }.start()
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode

            // Printable key -> open search and feed character
            if (!search.hasFocus() && DpadNavigation.isPrintableKey(event.unicodeChar)) {
                showSearch()
                search.append(event.unicodeChar.toChar().toString())
                return true
            }

            // D-pad UP from top row -> focus search
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && !search.hasFocus()) {
                val focusedView = currentFocus
                val pos = focusedView?.let { recycler.getChildAdapterPosition(it) } ?: -1
                val cols = (recycler.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount ?: 1
                if (pos in 0 until cols || pos == -1) {
                    showSearch()
                    search.requestFocus()
                    return true
                }
            }

            // D-pad DOWN from search -> focus first grid item
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && search.hasFocus()) {
                if (adapter.itemCount > 0) {
                    search.clearFocus()
                    val firstHolder = recycler.findViewHolderForAdapterPosition(0)
                    if (firstHolder != null) {
                        firstHolder.itemView.requestFocus()
                    } else {
                        recycler.post { recycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                    }
                    return true
                }
            }

            // Escape closes search
            if (keyCode == KeyEvent.KEYCODE_ESCAPE && search.visibility == View.VISIBLE) {
                hideSearch()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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

        val shortcutsContainer = view.findViewById<android.widget.LinearLayout>(R.id.layout_shortcuts)
        if (android.os.Build.VERSION.SDK_INT >= 25) {
            val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
            if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                    setPackage(entry.packageName)
                    setQueryFlags(
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                }
                val shortcuts = runCatching {
                    launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
                }.getOrNull().orEmpty()

                if (shortcuts.isNotEmpty()) {
                    shortcutsContainer.visibility = android.view.View.VISIBLE
                    val typedValue = android.util.TypedValue()
                    ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)

                    shortcuts.take(4).forEach { shortcut ->
                        val tv = TextView(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                (48 * resources.displayMetrics.density).toInt()
                            )
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            text = shortcut.shortLabel ?: shortcut.longLabel ?: "Shortcut"
                            textSize = 16f
                            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                            setTextColor(0xFF81D4FA.toInt())
                            setBackgroundResource(typedValue.resourceId)
                            setOnClickListener {
                                runCatching {
                                    launcherApps.startShortcut(shortcut, null, null)
                                }
                                dialog.dismiss()
                            }
                        }
                        shortcutsContainer.addView(tv)
                    }
                }
            }
        }

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

    private fun checkAndShowOnboarding() {
        if (onboardingStore.isOnboardingCompleted()) {
            requestRuntimePermissions()
            return
        }
        layoutOnboarding = findViewById(R.id.layout_onboarding)
        layoutOnboarding?.visibility = View.VISIBLE
        currentOnboardingStep = 0
        renderOnboardingStep()

        findViewById<View>(R.id.btn_onboarding_next)?.setOnClickListener {
            if (currentOnboardingStep < OnboardingContent.steps.size - 1) {
                currentOnboardingStep++
                renderOnboardingStep()
            } else {
                finishOnboarding()
            }
        }

        findViewById<View>(R.id.btn_onboarding_skip)?.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun renderOnboardingStep() {
        val step = OnboardingContent.steps.getOrNull(currentOnboardingStep) ?: return
        findViewById<android.widget.ImageView>(R.id.iv_onboarding_icon)?.setImageResource(step.iconRes)
        findViewById<TextView>(R.id.tv_onboarding_title)?.text = step.title
        findViewById<TextView>(R.id.tv_onboarding_subtitle)?.text = step.subtitle

        val nextBtn = findViewById<android.widget.Button>(R.id.btn_onboarding_next)
        nextBtn?.text = if (currentOnboardingStep == OnboardingContent.steps.size - 1) "Get Started" else "Continue"

        val dotsContainer = findViewById<android.widget.LinearLayout>(R.id.layout_onboarding_dots) ?: return
        dotsContainer.removeAllViews()
        for (i in OnboardingContent.steps.indices) {
            val dot = View(this).apply {
                val size = (6 * resources.displayMetrics.density).toInt()
                val margin = (4 * resources.displayMetrics.density).toInt()
                val params = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                layoutParams = params
                setBackgroundResource(R.drawable.circle)
                alpha = if (i == currentOnboardingStep) 1f else 0.25f
            }
            dotsContainer.addView(dot)
        }
    }

    private fun finishOnboarding() {
        onboardingStore.setOnboardingCompleted()
        layoutOnboarding?.animate()
            ?.alpha(0f)
            ?.setDuration(250)
            ?.withEndAction {
                layoutOnboarding?.visibility = View.GONE
                requestRuntimePermissions()
            }
            ?.start()
    }

    private fun requestRuntimePermissions() {
        val perms = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CALENDAR)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.READ_CALENDAR)
            if (android.os.Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_PERMS)
        }
    }

    companion object {
        private const val REQ_PERMS = 1001
    }
}

