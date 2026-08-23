package com.yrolland.loom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsSyncTest {

    @Test
    fun `collapseDuplicates keeps same app if separated by launcher return`() {
        val launcher = "com.google.android.apps.nexuslauncher"
        val own = "com.yrolland.loom"
        val msg = "com.google.android.apps.messaging"

        val rawEvents = listOf(
            UsageEvent(msg, 1000L),
            UsageEvent(msg, 2000L), // rapid in-app transition -> should be dropped
            UsageEvent(launcher, 10000L), // returned to home
            UsageEvent(msg, 15000L), // legitimate reopen of same app -> should be KEPT
            UsageEvent(msg, 16000L)  // rapid in-app transition -> should be dropped
        )

        val result = UsageStatsSync.collapseEvents(rawEvents, ownPackage = own)

        assertEquals(2, result.size)
        assertEquals(1000L, result[0].timestampMillis)
        assertEquals(msg, result[0].packageName)
        assertEquals(15000L, result[1].timestampMillis)
        assertEquals(msg, result[1].packageName)
    }

    @Test
    fun `collapseDuplicates keeps different apps in quick succession`() {
        val own = "com.yrolland.loom"
        val appA = "com.app.a"
        val appB = "com.app.b"

        val rawEvents = listOf(
            UsageEvent(appA, 1000L),
            UsageEvent(appB, 2000L),
            UsageEvent(appA, 3000L)
        )

        val result = UsageStatsSync.collapseEvents(rawEvents, ownPackage = own)

        assertEquals(3, result.size)
    }
}
