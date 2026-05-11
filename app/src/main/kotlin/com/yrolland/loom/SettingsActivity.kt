package com.yrolland.loom

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.BLACK
            navigationBarColor = Color.BLACK
            decorView.setBackgroundColor(Color.BLACK)
        }
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.btn_clear_usage).setOnClickListener { confirmClearUsage() }
        findViewById<TextView>(R.id.btn_reset_all).setOnClickListener { confirmResetAll() }

        viewModel.apps.observe(this) { _ -> render() }
        viewModel.refresh()
    }

    private fun render() {
        val repo = AppRepository(this)
        val pinned = repo.pinnedPackages()
        val hidden = repo.getHiddenApps()
        val all = viewModel.apps.value.orEmpty()

        val pinnedSection = findViewById<LinearLayout>(R.id.section_pinned)
        pinnedSection.removeAllViews()
        val pinnedEntries = all.filter { it.packageName in pinned }.sortedBy { it.label.lowercase() }
        if (pinnedEntries.isEmpty()) {
            pinnedSection.addView(emptyText("No pinned apps. Long-press an app to pin it."))
        } else {
            pinnedEntries.forEach { entry ->
                pinnedSection.addView(actionRow(entry.label, "Unpin") {
                    viewModel.setPinned(entry.packageName, false)
                })
            }
        }

        val hiddenSection = findViewById<LinearLayout>(R.id.section_hidden)
        hiddenSection.removeAllViews()
        if (hidden.isEmpty()) {
            hiddenSection.addView(emptyText("No hidden apps."))
        } else {
            hidden.forEach { entry ->
                hiddenSection.addView(actionRow(entry.label, "Unhide") {
                    viewModel.setHidden(entry.packageName, false)
                })
            }
        }
    }

    private fun actionRow(label: String, action: String, onClick: () -> Unit): View {
        val row = LayoutInflater.from(this).inflate(R.layout.row_setting, findViewById(R.id.section_pinned), false)
        row.findViewById<TextView>(R.id.row_label).text = label
        row.findViewById<TextView>(R.id.row_action).text = action
        row.setOnClickListener { onClick() }
        return row
    }

    private fun emptyText(text: String): View {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(0xFF666666.toInt())
        tv.textSize = 13f
        tv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8; bottomMargin = 12 }
        return tv
    }

    private fun confirmClearUsage() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Clear usage data?")
            .setMessage("Removes all launch history. Pins and hidden apps stay.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> viewModel.clearUsage() }
            .show()
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Reset everything?")
            .setMessage("Clears usage data, pins, and hidden apps.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ -> viewModel.clearAll() }
            .show()
    }
}
