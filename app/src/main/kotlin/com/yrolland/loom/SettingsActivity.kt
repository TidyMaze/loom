package com.yrolland.loom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.widget.TextViewCompat

class SettingsActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()
    private lateinit var gridStore: GridStore
    private lateinit var hiddenStore: HiddenStore

    private lateinit var cardDetox: LinearLayout
    private lateinit var dividerHidden: View
    private lateinit var sectionHidden: LinearLayout

    private lateinit var rowGridColumns: View
    private lateinit var rowIconScale: View
    private lateinit var rowWebSearch: View
    private lateinit var rowUsage: View
    private lateinit var rowNotif: View
    private lateinit var rowHide: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gridStore = GridStore(this)
        hiddenStore = HiddenStore(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        applyWindowInsets()

        findViewById<View>(R.id.btn_back).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        buildRows()

        viewModel.apps.observe(this) { _ -> render() }
        viewModel.refresh()
    }

    private fun applyWindowInsets() {
        val content = findViewById<View>(R.id.settings_content)
        // XML base paddings (8dp top, 32dp bottom, screen margin sides) plus the system bar insets.
        val top = content.paddingTop
        val bottom = content.paddingBottom
        val start = content.paddingStart
        val end = content.paddingEnd
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_scroll)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Header and last row clear the status bar and the gesture/nav bar.
            content.setPadding(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom)
            insets
        }
    }

    /** Builds the four cards. Rows keep the behaviour of the former flat list. */
    private fun buildRows() {
        val cardLayout = findViewById<LinearLayout>(R.id.card_layout)
        rowGridColumns = navRow(cardLayout, getString(R.string.setting_grid_columns)) { pickGridColumns() }
        rowIconScale = navRow(cardLayout, getString(R.string.setting_icon_size)) { pickIconScale() }

        cardDetox = findViewById(R.id.card_detox)
        rowWebSearch = switchRow(cardDetox, getString(R.string.setting_web_search_button)) { toggleWebSearchFallback() }
        rowHide = navRow(cardDetox, getString(R.string.setting_choose_hide), labelColor = R.color.loom_accent) { pickAppsToHide() }
        dividerHidden = divider().also { cardDetox.addView(it) }
        sectionHidden = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cardDetox.addView(sectionHidden)

        val cardTracking = findViewById<LinearLayout>(R.id.card_tracking)
        rowUsage = navRow(cardTracking, getString(R.string.setting_usage_access)) { UsageStatsSync.openSettings(this) }
        rowNotif = navRow(cardTracking, getString(R.string.setting_notif_access)) { NotificationCounts.openPermissionSettings(this) }
        navRow(cardTracking, getString(R.string.setting_privacy)) { showPrivacy() }

        val cardData = findViewById<LinearLayout>(R.id.card_data)
        plainRow(cardData, getString(R.string.setting_clear_usage)) { confirmClearUsage() }
            .also { setSummary(it, getString(R.string.dialog_clear_message)) }
        plainRow(cardData, getString(R.string.setting_reset_all), labelColor = R.color.loom_danger) { confirmResetAll() }
            .also { setSummary(it, getString(R.string.dialog_reset_all_message)) }
    }

    override fun onResume() {
        super.onResume()
        refreshSummaries()
    }

    private fun refreshSummaries() {
        setSummary(rowGridColumns, getString(R.string.grid_columns_status, gridStore.getColumnCount()))
        setSummary(
            rowIconScale,
            getString(R.string.icon_scale_status, scaleName(gridStore.getIconScale()), gridStore.getIconSizeDp())
        )

        val hiddenCount = hiddenStore.all().size
        setSummary(rowHide, resources.getQuantityString(R.plurals.hidden_count_status, hiddenCount, hiddenCount))

        val webDisabled = hiddenStore.isWebSearchFallbackDisabled()
        setSummary(rowWebSearch, getString(if (webDisabled) R.string.web_search_disabled else R.string.web_search_enabled))
        setChecked(rowWebSearch, !webDisabled)

        val usageGranted = UsageStatsSync.hasPermission(this)
        setSummary(rowUsage, getString(if (usageGranted) R.string.usage_granted else R.string.usage_not_granted))
        val notifGranted = NotificationCounts.hasPermission(this)
        val n = NotificationCounts.totalCount()
        setSummary(
            rowNotif,
            if (notifGranted) getString(R.string.notif_granted, n) else getString(R.string.notif_not_granted)
        )
    }

    private fun render() {
        val hidden = AppRepository(this).getHiddenApps()
        setSummary(rowHide, resources.getQuantityString(R.plurals.hidden_count_status, hidden.size, hidden.size))

        sectionHidden.removeAllViews()
        dividerHidden.visibility = if (hidden.isEmpty()) View.GONE else View.VISIBLE
        if (hidden.isEmpty()) {
            sectionHidden.addView(emptyText(getString(R.string.no_hidden_apps)))
        } else {
            hidden.forEach { entry ->
                val row = newRow(sectionHidden, entry.label)
                row.findViewById<TextView>(R.id.row_action).apply {
                    text = getString(R.string.unhide)
                    visibility = View.VISIBLE
                }
                row.setOnClickListener { viewModel.setHidden(entry.packageName, false) }
            }
        }
    }

    // region Row building

    /** Inflates one row in [container]; a 1dp divider is inserted before every row but the first. */
    private fun newRow(container: ViewGroup, label: CharSequence, labelColor: Int? = null): View {
        if (container.childCount > 0) container.addView(divider())
        val row = LayoutInflater.from(this).inflate(R.layout.row_setting, container, false)
        row.findViewById<TextView>(R.id.row_label).apply {
            text = label
            if (labelColor != null) setTextColor(ContextCompat.getColor(this@SettingsActivity, labelColor))
        }
        container.addView(row)
        return row
    }

    /** Row that opens something (dialog or system screen): chevron on the end. */
    private fun navRow(container: ViewGroup, label: CharSequence, labelColor: Int? = null, onClick: () -> Unit): View {
        val row = newRow(container, label, labelColor)
        row.findViewById<View>(R.id.row_chevron).visibility = View.VISIBLE
        row.setOnClickListener { onClick() }
        return row
    }

    /** Row with no trailing element (destructive actions). */
    private fun plainRow(container: ViewGroup, label: CharSequence, labelColor: Int? = null, onClick: () -> Unit): View {
        val row = newRow(container, label, labelColor)
        row.setOnClickListener { onClick() }
        return row
    }

    /** Toggle row: the whole row toggles; the switch only shows state (announced as checkable). */
    private fun switchRow(container: ViewGroup, label: CharSequence, onToggle: () -> Unit): View {
        val row = newRow(container, label)
        row.findViewById<SwitchCompat>(R.id.row_switch).visibility = View.VISIBLE
        ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isCheckable = true
                info.isChecked = host.findViewById<SwitchCompat>(R.id.row_switch).isChecked
            }
        })
        row.setOnClickListener { onToggle() }
        return row
    }

    /** Sets the switch state and its spoken state ("On"/"Off"); no announcement, so resume stays silent. */
    private fun setChecked(row: View, checked: Boolean) {
        row.findViewById<SwitchCompat>(R.id.row_switch).isChecked = checked
        ViewCompat.setStateDescription(row, getString(if (checked) R.string.state_on else R.string.state_off))
    }

    private fun setSummary(row: View, text: CharSequence) {
        row.findViewById<TextView>(R.id.row_summary).apply {
            this.text = text
            visibility = View.VISIBLE
        }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.loom_stroke))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.stroke_hairline)
        ).apply { marginStart = resources.getDimensionPixelSize(R.dimen.space_16) }
    }

    private fun emptyText(text: String): View {
        val tv = TextView(this)
        tv.text = text
        TextViewCompat.setTextAppearance(tv, R.style.Loom_Text_Caption)
        tv.setPadding(
            resources.getDimensionPixelSize(R.dimen.space_16), resources.getDimensionPixelSize(R.dimen.space_12),
            resources.getDimensionPixelSize(R.dimen.space_16), resources.getDimensionPixelSize(R.dimen.space_12)
        )
        tv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return tv
    }

    // endregion

    private fun scaleName(key: String): String = getString(
        when (key) {
            "compact" -> R.string.icon_scale_name_compact
            "spacious" -> R.string.icon_scale_name_spacious
            else -> R.string.icon_scale_name_medium
        }
    )

    private fun themedDialog(): AlertDialog.Builder = AlertDialog.Builder(this, R.style.ThemeOverlay_Loom_Dialog)

    private fun pickGridColumns() {
        val options = resources.getStringArray(R.array.columns_options)
        val current = gridStore.getColumnCount()
        val selectedIndex = (current - 3).coerceIn(0, 3)

        themedDialog()
            .setTitle(R.string.dialog_grid_columns_title)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val newCols = which + 3
                gridStore.setColumnCount(newCols)
                setSummary(rowGridColumns, getString(R.string.grid_columns_status, newCols))
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun pickIconScale() {
        val options = resources.getStringArray(R.array.icon_scale_options)
        val keys = arrayOf("compact", "medium", "spacious")
        val current = gridStore.getIconScale()
        val selectedIndex = keys.indexOf(current).coerceAtLeast(1)

        themedDialog()
            .setTitle(R.string.dialog_icon_size_title)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val newScale = keys[which]
                gridStore.setIconScale(newScale)
                setSummary(
                    rowIconScale,
                    getString(R.string.icon_scale_status, scaleName(newScale), gridStore.getIconSizeDp())
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun confirmClearUsage() {
        themedDialog()
            .setTitle(R.string.dialog_clear_title)
            .setMessage(R.string.dialog_clear_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_clear_action) { _, _ -> viewModel.clearUsage() }
            .show()
    }

    private fun toggleWebSearchFallback() {
        val nowDisabled = !hiddenStore.isWebSearchFallbackDisabled()
        hiddenStore.setWebSearchFallbackDisabled(nowDisabled)
        setSummary(rowWebSearch, getString(if (nowDisabled) R.string.web_search_disabled else R.string.web_search_enabled))
        setChecked(rowWebSearch, !nowDisabled)
    }

    private fun pickAppsToHide() {
        val repo = AppRepository(this)
        val apps = repo.getAllInstalledApps()
        if (apps.isEmpty()) return

        val names = apps.map { it.label }.toTypedArray()
        val checked = apps.map { hiddenStore.isHidden(it.packageName) }.toBooleanArray()

        themedDialog()
            .setTitle(R.string.dialog_hide_title)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                hiddenStore.setHidden(pkg, isChecked)
            }
            .setPositiveButton(R.string.dialog_done) { _, _ ->
                viewModel.refresh()
                render()
            }
            .show()
    }

    private fun showPrivacy() {
        themedDialog()
            .setTitle(R.string.onboarding_privacy_title)
            .setMessage(R.string.privacy_full_body)
            .setPositiveButton(R.string.dialog_done, null)
            .show()
    }

    private fun confirmResetAll() {
        themedDialog()
            .setTitle(R.string.dialog_reset_all_title)
            .setMessage(R.string.dialog_reset_all_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_reset_action) { _, _ -> viewModel.clearAll() }
            .show()
    }
}
