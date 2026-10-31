package com.yrolland.loom

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import kotlin.math.abs

/**
 * Bottom-anchored onboarding pager over the layout in view_onboarding.xml.
 * Steps change by primary button, swipe (fling left = next, right = previous) or system back
 * (previous step while not on the first one). Skip and the last button call [onFinished].
 */
class OnboardingController(
    private val activity: ComponentActivity,
    private val root: View,
    private val onFinished: () -> Unit
) {
    private val steps = OnboardingContent.steps
    private val density = activity.resources.displayMetrics.density
    private val minSwipeDistance = SWIPE_MIN_DP * density
    private val minFlingVelocity = ViewConfiguration.get(activity).scaledMinimumFlingVelocity

    private val scroll: ScrollView = root.findViewById(R.id.onboarding_scroll)
    private val icon: ImageView = root.findViewById(R.id.iv_onboarding_icon)
    private val title: TextView = root.findViewById(R.id.tv_onboarding_title)
    private val body: TextView = root.findViewById(R.id.tv_onboarding_subtitle)
    private val dots: LinearLayout = root.findViewById(R.id.layout_onboarding_dots)
    private val next: Button = root.findViewById(R.id.btn_onboarding_next)
    private val skip: TextView = root.findViewById(R.id.btn_onboarding_skip)

    private var index = 0
    private var finished = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            goTo(index - 1, direction = -1)
        }
    }

    private val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.x - start.x
            val dy = e2.y - start.y
            // Horizontal fling only: ignore vertical scrolls of the step text.
            if (abs(dx) < minSwipeDistance || abs(dx) < abs(dy) || abs(velocityX) < minFlingVelocity) return false
            return if (dx < 0) {
                goTo(index + 1, direction = 1)
                true
            } else {
                goTo(index - 1, direction = -1)
                true
            }
        }
    })

    init {
        // The step survives activity recreation (rotation, theme or language change) through the saved-state registry.
        // Must run after the activity's super.onCreate, which is where MainActivity creates this controller.
        val restored = activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)
        index = clampOnboardingStep(restored?.getInt(STATE_STEP) ?: 0, steps.size)
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) {
            Bundle().apply { putInt(STATE_STEP, index) }
        }

        scroll.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false // let the ScrollView keep handling vertical scrolling
        }
        next.setOnClickListener {
            if (index < steps.lastIndex) goTo(index + 1, direction = 1) else finish()
        }
        skip.setOnClickListener { finish() }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        render()
    }

    private fun goTo(target: Int, direction: Int) {
        if (finished) return
        val clamped = clampOnboardingStep(target, steps.size)
        if (clamped == index) return
        index = clamped
        render()
        slideIn(direction)
    }

    /** Idempotent: a double tap on "Get started" during the exit fade must not run the finish path twice. */
    private fun finish() {
        if (finished) return
        finished = true
        backCallback.isEnabled = false
        onFinished()
    }

    private fun render() {
        val step = steps[index]
        icon.setImageResource(step.iconRes)
        title.setText(step.titleRes)
        body.setText(step.bodyRes)
        next.setText(if (index == steps.lastIndex) R.string.onboarding_get_started else R.string.onboarding_continue)
        ViewCompat.setStateDescription(next, activity.getString(R.string.onboarding_progress, index + 1, steps.size))
        backCallback.isEnabled = index > 0
        scroll.scrollTo(0, 0)
        renderDots()
    }

    private fun renderDots() {
        dots.removeAllViews()
        val height = (DOT_SIZE_DP * density).toInt()
        val activeWidth = (DOT_ACTIVE_WIDTH_DP * density).toInt()
        val margin = (DOT_MARGIN_DP * density).toInt()
        steps.indices.forEach { i ->
            val active = i == index
            val dot = View(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setBackgroundResource(
                    if (active) R.drawable.onboarding_dot_active else R.drawable.onboarding_dot_inactive
                )
            }
            val params = LinearLayout.LayoutParams(if (active) activeWidth else height, height)
            params.setMargins(margin, 0, margin, 0)
            dots.addView(dot, params)
        }
    }

    /** Slides the step in from the side it comes from. Skipped entirely when animations are disabled. */
    private fun slideIn(direction: Int) {
        scroll.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            scroll.translationX = 0f
            scroll.alpha = 1f
            return
        }
        scroll.translationX = SLIDE_DP * density * direction
        scroll.alpha = 0f
        scroll.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(SLIDE_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private companion object {
        const val STATE_KEY = "loom.onboarding"
        const val STATE_STEP = "step"
        const val SWIPE_MIN_DP = 64f
        const val SLIDE_DP = 32f
        const val SLIDE_MS = 180L
        const val DOT_SIZE_DP = 8f
        const val DOT_ACTIVE_WIDTH_DP = 20f
        const val DOT_MARGIN_DP = 4f
    }
}
