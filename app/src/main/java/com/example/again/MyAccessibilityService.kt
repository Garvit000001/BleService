package com.example.again

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e("TRACKER_SERVICE", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.e("TRACKER_SERVICE", "Received event: ${AccessibilityEvent.eventTypeToString(event?.eventType ?: -1)}")

        event ?: return

        val sourceNode = event.source
        if (sourceNode == null) {
            Log.w("TRACKER_SERVICE", "Event came without a source node, skipping.")
            return // Can't get detailed information without a source.
        }

        try {
            val eventType = event.eventType
            val action = when (eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT_CHANGED"
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_CHANGED"
                else -> {
                    Log.d("TRACKER_SERVICE", "Ignoring event type: ${AccessibilityEvent.eventTypeToString(eventType)}")
                    return // Exit for unhandled event types
                }
            }

            val packageName = event.packageName?.toString() ?: "Unknown Package"
            val className = sourceNode.className?.toString() ?: ""
            val viewId = sourceNode.viewIdResourceName ?: ""

            // Determine the most descriptive text based on event type
            val descriptiveText = when (eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    // For clicks, contentDescription or text are most useful.
                    sourceNode.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                        ?: sourceNode.text?.toString()
                        ?: ""
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // For text changes, event.text has the most up-to-date content.
                    event.text?.joinToString(" ") ?: ""
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // For window changes, text from the event is a good summary.
                     event.text?.joinToString(" ") ?: ""
                }
                else -> ""
            }

            // Create a more structured data string with the new information.
            // Format: ACTION | PACKAGE | CLASS | DESCRIPTION | VIEW_ID
            val data = "$action | $packageName | $className | $descriptiveText | $viewId"

            Log.e("TRACKER_EVENT", "Broadcasting data: $data")

            sendBroadcast(Intent("ACTION_USER_EVENT").apply {
                setPackage(this@MyAccessibilityService.packageName)
                putExtra("data", data)
            })

        } finally {
            // IMPORTANT: Always recycle the node info object.
            sourceNode.recycle()
        }
    }

    override fun onInterrupt() {
        Log.e("TRACKER_SERVICE", "Service Interrupted")
    }
}