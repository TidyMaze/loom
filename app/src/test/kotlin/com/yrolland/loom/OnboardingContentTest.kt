package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingContentTest {

    @Test
    fun clampKeepsIndexInRange() {
        assertEquals(0, clampOnboardingStep(-1, 5))
        assertEquals(2, clampOnboardingStep(2, 5))
        assertEquals(4, clampOnboardingStep(7, 5))
    }

    @Test
    fun clampWithNoStepsIsZero() {
        assertEquals(0, clampOnboardingStep(3, 0))
    }
}
