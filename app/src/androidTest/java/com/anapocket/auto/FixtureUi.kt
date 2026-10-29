package com.anapocket.auto

import android.app.UiAutomation
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream

/** Test-only driver: actual Android Accessibility nodes and click actions; never packaged in release. */
class FixtureUi(private val automation: UiAutomation) {
    fun foregroundNodes(): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || out.size >= 2000) return
            out.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(automation.rootInActiveWindow, 0)
        return out
    }
    fun label(n: AccessibilityNodeInfo) = n.text?.toString()?.trim().orEmpty().ifEmpty { n.contentDescription?.toString()?.trim().orEmpty() }
    fun currentPackage() = automation.rootInActiveWindow?.packageName?.toString()
    fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        repeat(6) {
            val current = n ?: return false
            if (current.isEnabled && current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = current.parent
        }
        return false
    }
    fun dump() = foregroundNodes().joinToString("\n") { it.packageName.toString() + " " + label(it) }
    fun capture(): ByteArray? {
        val bmp = automation.takeScreenshot() ?: return null
        return try { ByteArrayOutputStream().use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() } }
            finally { bmp.recycle() }
    }
    fun back() = automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
}
