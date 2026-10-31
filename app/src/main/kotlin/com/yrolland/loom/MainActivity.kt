package com.yrolland.loom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.app.Dialog
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
    private lateinit var btnOpenChrome: TextView
    private lateinit var btnOpenGemini: View
    private lateinit var greetingRow: View
    private lateinit var dock: View
    private lateinit var btnClearSearch: View
    private lateinit var btnSettings: View
    private lateinit var gridStore: GridStore
    private lateinit var onboardingStore: OnboardingStore
    private lateinit var hiddenStore: HiddenStore
    private var layoutOnboarding: View? = null
    private var fullList: List<AppEntry> = emptyList()
    private var searchShownAt = 0L
    private var needsRefresh = false
    private var launcherResumeMs = System.currentTimeMillis()
    /** Adapter position of the app hidden from the sheet, to put focus back on the next row. */
    private var focusAfterHidePos: Int? = null
    private var sheetDialog: Dialog? = null
    private val announceResults = Runnable {
        val count = adapter.itemCount
        val text = if (count == 0) getString(R.string.search_results_none)
            else resources.getQuantityString(R.plurals.search_results_count, count, count)
        search.announceForAccessibility(text)
    }

    private fun filteredList(query: String) =
        SearchFallback.filterApps(fullList, query)

    private fun updateListAndEmptyState(query: String, onSubmitted: (() -> Unit)? = null) {
        val list = filteredList(query)
        adapter.submitList(list) {
            onSubmitted?.invoke()
        }
        btnOpenChrome.text = getString(R.string.search_web_for, query)
        layoutEmptyState.visibility = if (SearchFallback.shouldShowEmptyState(query, list.isNotEmpty(), hiddenStore.isWebSearchFallbackDisabled())) View.VISIBLE else View.GONE
        // Announce the result count once typing settles (no spam per keystroke).
        if (query.isNotEmpty()) {
            search.removeCallbacks(announceResults)
            search.postDelayed(announceResults, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: content is laid out under the status and navigation bars;
        // padding is applied from the window insets in applyWindowInsets().
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        gridStore = GridStore(this)
        onboardingStore = OnboardingStore(this)
        hiddenStore = HiddenStore(this)
        greeting = findViewById(R.id.tv_greeting)
        greetingRow = findViewById(R.id.layout_greeting_row)
        dock = findViewById(R.id.layout_dock)
        btnSettings = findViewById(R.id.btn_settings)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        search = findViewById(R.id.et_search)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        btnOpenChrome = findViewById(R.id.btn_open_chrome)
        btnOpenGemini = findViewById(R.id.btn_open_gemini)

        // The empty-state rows are TextViews: announce them to TalkBack as buttons (minSdk 26 has no XML className).
        listOf(btnOpenChrome, btnOpenGemini).forEach { row ->
            ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name
                }
            })
        }

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
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, gridStore.getColumnCount()).also {
                it.spanSizeLookup = this@MainActivity.adapter.spanSizeLookup(it)
            }
            this.adapter = this@MainActivity.adapter
            (itemAnimator as? DefaultItemAnimator)?.apply {
                addDuration = 120; removeDuration = 80; moveDuration = 150; changeDuration = 100
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Search open: Back closes the keyboard, then clears the text. Nothing open: no-op, as on the home screen.
                closeSearchStep()
            }
        })

        attachGestures()
        applyWindowInsets()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
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
            val restoreAt = focusAfterHidePos
            focusAfterHidePos = null
            updateListAndEmptyState(search.text?.toString()?.trim().orEmpty()) {
                if (restoreAt != null) restoreFocusAt(restoreAt) else recycler.scrollToPosition(0)
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
        recycler.layoutAnimation = if (reduceMotion()) null
            else AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fall_down)
        viewModel.refresh(LaunchContext.capture(this, launcherResumeMs))
    }

    /** Animator duration scale 0 (system "remove animations"): skip decorative motion. */
    private fun reduceMotion(): Boolean = android.provider.Settings.Global.getFloat(
        contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f

    override fun onStop() {
        super.onStop()
        // Recreation or leaving the app: drop the item sheet now (no window leak, no stale action).
        sheetDialog?.dismiss()
        sheetDialog = null
    }

    // The search pill is always visible; "open" means focused or holding text.
    private fun isSearchOpen(): Boolean = search.hasFocus() || !search.text.isNullOrEmpty()

    private fun showSearch() {
        searchShownAt = System.currentTimeMillis()
        search.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun dismissKeyboard() {
        search.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun isImeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(search)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    /**
     * Back / Escape step: close the keyboard first, then clear the text, then drop focus.
     * On API 30+ the system may close the keyboard itself on Back, so when it is already
     * hidden the step goes straight to clearing the text (and focus). Returns false when nothing was open.
     */
    private fun closeSearchStep(): Boolean {
        if (search.hasFocus() && isImeVisible()) {
            dismissKeyboard()
            return true
        }
        if (!search.text.isNullOrEmpty()) {
            search.text?.clear()
            search.clearFocus()
            return true
        }
        if (search.hasFocus()) {
            search.clearFocus()
            return true
        }
        return false
    }

    /** Full reset (swipe up, resume): keyboard closed and text cleared. */
    private fun hideSearch() {
        dismissKeyboard()
        search.text?.clear()
    }

    /** After "Hide": focus the row that took the hidden app's place, or the settings gear when the list is empty. */
    private fun restoreFocusAt(pos: Int) {
        val count = adapter.itemCount
        if (count == 0) {
            btnSettings.requestFocus()
            return
        }
        val target = minOf(pos, count - 1)
        recycler.scrollToPosition(target)
        recycler.post {
            val holder = recycler.findViewHolderForAdapterPosition(target)
            (holder?.itemView ?: btnSettings).requestFocus()
        }
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
                if (pos == -1 || DpadNavigation.calculateNextPosition(pos, DpadDirection.UP, cols, adapter.spanSizes(cols)) == -1) {
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
            if (keyCode == KeyEvent.KEYCODE_ESCAPE && closeSearchStep()) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun attachGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val lm = recycler.layoutManager as LinearLayoutManager
                if (distanceY < -10f && lm.findFirstCompletelyVisibleItemPosition() <= 0 && !search.hasFocus()) showSearch()
                if (distanceY > 10f && isSearchOpen()
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

        // Settings is the gear only (the greeting is a heading, not a control).
        btnSettings.setOnClickListener { openSettings() }

        btnClearSearch.setOnClickListener { showSearch(); search.text?.clear() }
        // Tap on the pill background (not the field) focuses the field.
        val pill = findViewById<View>(R.id.search_pill)
        pill.setOnClickListener { showSearch() }

        // Focus ring on the pill (state_selected, see search_bg.xml) whether the field or the pill has focus.
        val onPillFocus = View.OnFocusChangeListener { _, _ -> pill.isSelected = search.hasFocus() || pill.hasFocus() }
        search.onFocusChangeListener = onPillFocus
        pill.onFocusChangeListener = onPillFocus
    }

    private fun openSettings() {
        startActivity(android.content.Intent(this, SettingsActivity::class.java))
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root_main)
        val content = findViewById<View>(R.id.layout_main_content)
        val onboarding = findViewById<View>(R.id.layout_onboarding)
        val greetingRowTop = greetingRow.paddingTop
        val dockBottom = dock.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // Greeting row sits below the status bar (XML base = 16dp).
            greetingRow.updatePadding(top = bars.top + greetingRowTop)
            // Search dock sits above the nav/gesture bar, or above the keyboard when it is open.
            dock.updatePadding(bottom = maxOf(bars.bottom, ime) + dockBottom)
            content.updatePadding(left = bars.left, right = bars.right)
            onboarding.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun loadAppIcon(packageName: String): Drawable =
        runCatching { packageManager.getApplicationIcon(packageName) }
            .getOrElse { packageManager.defaultActivityIcon }

    /** "Opened 12m ago · 14 launches" (or "Never opened"). */
    private fun sheetCaption(entry: AppEntry): String {
        if (entry.launchCount == 0) return getString(R.string.sheet_never_opened)
        val launches = resources.getQuantityString(R.plurals.sheet_launch_count, entry.launchCount, entry.launchCount)
        val lastMillis = entry.lastLaunchedMillis ?: return launches
        val diffMs = System.currentTimeMillis() - lastMillis
        val opened = when {
            diffMs < 60_000 -> getString(R.string.sheet_opened_now)
            diffMs < 3_600_000 -> getString(R.string.sheet_opened_ago, getString(R.string.stats_minutes, diffMs / 60_000))
            diffMs < 86_400_000 -> getString(R.string.sheet_opened_ago, getString(R.string.stats_hours, diffMs / 3_600_000))
            else -> getString(R.string.sheet_opened_ago, getString(R.string.stats_days, diffMs / 86_400_000))
        }
        return getString(R.string.sheet_caption, opened, launches)
    }

    private fun showItemSheet(entry: AppEntry) {
        val ctx = this
        val view = layoutInflater.inflate(R.layout.sheet_item, null)
        // Bottom-gravity dialog window: no title, transparent window, dimmed scrim, slide-up animation below.
        val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.4f)
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        sheetDialog = dialog
        val rowPosition = adapter.currentList.indexOf(entry)
        val sheetBottomBase = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = sheetBottomBase + nav)
            insets
        }
        val reduceMotion = reduceMotion()
        // Navigation bar height of the activity: the sheet body gets this bottom padding, so the 70% cap must count it.
        val navBottomPx = ViewCompat.getRootWindowInsets(window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0

        var closing = false
        fun close(after: () -> Unit = {}) {
            // A second tap during the close animation must not run the action twice.
            if (closing) return
            closing = true
            if (reduceMotion) {
                dialog.dismiss()
                after()
                return
            }
            view.animate().translationY(view.height.toFloat()).setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    dialog.dismiss()
                    // The activity may have been destroyed during the animation: skip the action then.
                    if (!isFinishing && !isDestroyed) after()
                }
                .start()
        }

        ViewCompat.setAccessibilityPaneTitle(view, entry.label)
        view.findViewById<TextView>(R.id.sheet_title).text = entry.label
        view.findViewById<ImageView>(R.id.sheet_icon).setImageDrawable(loadAppIcon(entry.packageName))
        view.findViewById<TextView>(R.id.sheet_caption).text = sheetCaption(entry)

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

                    val hPad = resources.getDimensionPixelSize(R.dimen.screen_margin)
                    val vPad = (12 * resources.displayMetrics.density).toInt()
                    shortcuts.take(4).forEach { shortcut ->
                        val tv = TextView(ctx).apply {
                            // Min 48dp, grows with the font scale; at most 2 lines, then ellipsis.
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            minHeight = (48 * resources.displayMetrics.density).toInt()
                            setPadding(hPad, vPad, hPad, vPad)
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            text = shortcut.shortLabel ?: shortcut.longLabel ?: getString(R.string.shortcut_fallback_label)
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            textSize = 16f
                            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                            setTextColor(ContextCompat.getColor(ctx, R.color.loom_text))
                            setBackgroundResource(typedValue.resourceId)
                            foreground = ContextCompat.getDrawable(ctx, R.drawable.focus_ring_loom)
                            isFocusable = true
                            isClickable = true
                            setOnClickListener {
                                runCatching {
                                    launcherApps.startShortcut(shortcut, null, null)
                                }
                                close()
                            }
                        }
                        shortcutsContainer.addView(tv)
                    }
                }
            }
        }

        view.findViewById<TextView>(R.id.sheet_hide).setOnClickListener {
            close {
                if (rowPosition >= 0) focusAfterHidePos = rowPosition
                viewModel.setHidden(entry.packageName, true)
            }
        }
        view.findViewById<TextView>(R.id.sheet_info).setOnClickListener {
            close {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${entry.packageName}"))
                startActivity(intent)
            }
        }
        view.findViewById<TextView>(R.id.sheet_reset).setOnClickListener {
            close {
                androidx.appcompat.app.AlertDialog.Builder(ctx, R.style.ThemeOverlay_Loom_Dialog)
                    .setTitle(R.string.dialog_reset_app_title)
                    .setMessage(getString(R.string.dialog_reset_app_message, entry.label))
                    .setPositiveButton(R.string.dialog_reset_action) { _, _ ->
                        viewModel.resetApp(entry.packageName)
                    }
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show()
            }
        }

        // Cap the body at 70% of the screen height; the rows scroll beyond that.
        val maxHeight = (resources.displayMetrics.heightPixels * 0.7f).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        // The nav-bar padding is added later by the insets listener, so count it here.
        val totalHeight = view.measuredHeight + navBottomPx
        val scroll = view.findViewById<ScrollView>(R.id.sheet_scroll)
        if (totalHeight > maxHeight) {
            scroll.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                scroll.measuredHeight - (totalHeight - maxHeight)
            )
        }

        // Slide up from the bottom edge (skipped when animator duration scale is 0).
        if (!reduceMotion) {
            view.translationY = minOf(totalHeight, maxHeight).toFloat()
        }
        dialog.show()
        if (!reduceMotion) {
            view.animate().translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun applyAccent() {
        val a = accentForNow(this)
        greeting.text = greetingText()
        greeting.setTextColor(a.greeting)
        adapter.accent = a
        adapter.notifyDataSetChanged()
    }

    private fun greetingText(): String = getString(
        when (LocalTime.now().hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            in 18..21 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    )

    private fun checkAndShowOnboarding() {
        if (onboardingStore.isOnboardingCompleted()) {
            requestRuntimePermissions()
            return
        }
        val root = findViewById<View>(R.id.layout_onboarding)
        layoutOnboarding = root
        root.visibility = View.VISIBLE
        // Step UI, swipe, back and buttons live in OnboardingController; it calls finishOnboarding() when done.
        OnboardingController(this, root) { finishOnboarding() }
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

