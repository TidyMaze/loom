package com.yrolland.loom

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/** Singleton: latest detected user activity (still / walking / in_vehicle / etc).
 *  Updated by ActivityRecognitionReceiver. Read by LaunchContext.capture. */
object ActivityState {
    @Volatile var lastActivity: String? = null
    @Volatile var lastConfidence: Int = -1
    @Volatile var lastUpdateMs: Long = 0L

    fun update(activityType: Int, confidence: Int) {
        lastActivity = when (activityType) {
            DetectedActivity.IN_VEHICLE -> "in_vehicle"
            DetectedActivity.ON_BICYCLE -> "on_bicycle"
            DetectedActivity.ON_FOOT -> "on_foot"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.STILL -> "still"
            DetectedActivity.TILTING -> "tilting"
            else -> null
        }
        lastConfidence = confidence
        lastUpdateMs = System.currentTimeMillis()
    }

    /** Returns secs since last update, or null if never updated. */
    fun secsSinceUpdate(nowMs: Long = System.currentTimeMillis()): Int? =
        if (lastUpdateMs > 0) ((nowMs - lastUpdateMs) / 1000).toInt().coerceIn(0, 86400) else null

    /** Subscribe to activity updates. Call once after ACTIVITY_RECOGNITION permission is granted. */
    fun startUpdates(context: Context) {
        runCatching {
            val client = ActivityRecognition.getClient(context.applicationContext)
            val intent = Intent(context, ActivityRecognitionReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            // Request updates every 60s (low battery impact)
            client.requestActivityUpdates(60_000L, pi)
        }.onFailure { Log.w(TAG, "activity updates failed: $it") }
    }

    private const val TAG = "ActivityState"
    private const val REQ_CODE = 42
}

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val most = result.mostProbableActivity ?: return
        ActivityState.update(most.type, most.confidence)
    }
}
