package com.example.again

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.content.pm.PackageManager

class MyAccessibilityService : AccessibilityService() {

    private var lastApp = ""
    private var lastTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e("TRACKER_SERVICE", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return
        
        // Log every event to see what is happening
        Log.d("TRACKER_DEBUG", "Event: ${AccessibilityEvent.eventTypeToString(eventType)} from $packageName")

        // Filter for significant events (Clicks and Window Changes are best for app detection)
        if (eventType != AccessibilityEvent.TYPE_VIEW_CLICKED && 
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val pm = applicationContext.packageManager
        val appLabel = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }

        val finalName = appLabel.uppercase()

        // Debounce: prevent duplicate broadcasts within 300ms (more responsive than 1s)
        val currentTime = System.currentTimeMillis()
        if (finalName != lastApp || currentTime - lastTime > 300) {
            lastApp = finalName
            lastTime = currentTime

            Log.e("TRACKER_EVENT", "Broadcasting App Name: $finalName")

            sendBroadcast(Intent("ACTION_USER_EVENT").apply {
                setPackage(this@MyAccessibilityService.packageName)
                putExtra("data", finalName)
            })
        }
    }

    override fun onInterrupt() {
        Log.e("TRACKER_SERVICE", "Service Interrupted")
    }
}