package com.example.again

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.content.pm.PackageManager

class MyAccessibilityService : AccessibilityService() {

    private var lastApp = ""
    private var lastTime = 0L
    private var isSmartViewTriggered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e("TRACKER_SERVICE", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: ""
        
        // Identify the application label
        val pm = applicationContext.packageManager
        val appLabel = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
        // Remove spaces for consistent "SMARTVIEW" formatting
        val cleanLabel = appLabel.uppercase().replace(" ", "").trim()

        // Keywords to identify Smart View
        val smartViewKeywords = listOf("smart view", "smartview", "mirroring", "screen share", "cast")

        // 1. Check for explicit interactions (Clicks, etc.)
        // This handles clicking the "Smart View" tile in the panel
        val isInteraction = eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || 
                            eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                            eventType == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED

        if (isInteraction) {
            // Handle Torch interaction
            if (containsKeywords(event, listOf("torch", "flashlight"))) {
                Log.d("TRACKER_DEBUG", "!!! Torch interaction detected !!!")
                broadcastAppName("TORCH")
                return
            }

            // Handle Smart View interaction (Panel or Settings)
            if (containsKeywords(event, smartViewKeywords)) {
                Log.d("TRACKER_DEBUG", "!!! Smart View interaction detected !!!")
                isSmartViewTriggered = true
                broadcastAppName("SMARTVIEW")
                return
            }
        }

        // 2. Identify if we are currently "in" a Smart View related page or app
        // We exclude SystemUI from the window title check because pulling down the notification panel 
        // lists all tiles in the event text, causing a false positive.
        val isSmartViewPackage = packageName.contains("smartview", ignoreCase = true) || 
                                 packageName.contains("smartmirroring", ignoreCase = true)
        
        val windowText = event.text.joinToString(" ").lowercase()
        val isSmartViewInTitle = windowText.contains("smart view") || windowText.contains("smartview")

        if (isSmartViewPackage || (packageName != "com.android.systemui" && isSmartViewInTitle && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            Log.d("TRACKER_DEBUG", "!!! In Smart View app or settings page !!!")
            isSmartViewTriggered = true
            broadcastAppName("SMARTVIEW")
            return
        }

        // 3. Handle System UI (Notification Panel)
        if (packageName == "com.android.systemui") {
            // If Smart View was previously triggered (by a click), keep advertising it while in SystemUI
            // (e.g., while the panel is still closing or if the UI is an overlay)
            if (isSmartViewTriggered) {
                broadcastAppName("SMARTVIEW")
                return
            }
            // Pulling down the panel without clicking Smart View should advertise SYSTEMUI
            broadcastAppName("SYSTEMUI")
            return
        }

        // 4. Reset logic for app transitions
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // If we move to any other REAL app that is not related to Smart View, reset the trigger.
            if (packageName != "com.android.systemui" && !isSmartViewPackage && !isSmartViewInTitle) {
                Log.d("TRACKER_DEBUG", "New app focused: $cleanLabel ($packageName). Resetting Smart View trigger.")
                isSmartViewTriggered = false
            }
        }

        // 5. Normal broadcast for all other applications
        broadcastAppName(cleanLabel)
    }

    private fun containsKeywords(event: AccessibilityEvent, keywords: List<String>): Boolean {
        // Check event's own text and description
        val text = event.text.joinToString(" ").lowercase()
        val contentDesc = event.contentDescription?.toString()?.lowercase() ?: ""
        
        if (keywords.any { text.contains(it) || contentDesc.contains(it) }) return true
        
        // Search the node tree of the element that triggered the event
        if (searchInNodeTree(event.source, keywords)) return true
        
        return false
    }

    private fun searchInNodeTree(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        try {
            val text = node.text?.toString()?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            
            if (keywords.any { text.contains(it) || contentDesc.contains(it) }) return true
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (searchInNodeTree(child, keywords)) return true
            }
        } catch (e: Exception) {}
        return false
    }

    private fun broadcastAppName(name: String) {
        val currentTime = System.currentTimeMillis()
        // Debounce only if the name is the same; allow immediate broadcast if the name changes
        if (name != lastApp || currentTime - lastTime > 300) {
            lastApp = name
            lastTime = currentTime

            Log.e("TRACKER_EVENT", "Broadcasting: $name")

            sendBroadcast(Intent("ACTION_USER_EVENT").apply {
                setPackage(this@MyAccessibilityService.packageName)
                putExtra("data", name)
            })
        }
    }

    override fun onInterrupt() {
        Log.e("TRACKER_SERVICE", "Service Interrupted")
    }
}
