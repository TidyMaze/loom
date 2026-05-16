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
        findViewById<TextView>(R.id.btn_usage_access).setOnClickListener { UsageStatsSync.openSettings(this) }

        viewModel.apps.observe(this) { _ -> render() }
        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        val granted = UsageStatsSync.hasPermission(this)
        findViewById<TextView>(R.id.usage_access_status).text =
            if (granted) "✓ Granted — captures system-wide app launches"
            else "Not granted — only launcher-tap launches recorded. Tap to enable."
    }

    private fun render() {
        val hidden = AppRepository(this).getHiddenApps()

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
        val row = LayoutInflater.from(this).inflate(R.layout.row_setting, findViewById(R.id.section_hidden), false)
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
            .setMessage("Removes all launch history. Hidden apps stay.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> viewModel.clearUsage() }
            .show()
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Reset everything?")
            .setMessage("Clears usage data and hidden apps.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ -> viewModel.clearAll() }
            .show()
    }
}
