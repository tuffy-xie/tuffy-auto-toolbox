package com.anapocket.auto

enum class CategoryViewportAction { KEEP_POSITION, RESELECT_CATEGORY, STOP }

/** Some SDK returns reset ANA to All even without our cold recovery. Never dispatch from a mismatched list. */
object CategoryViewportPolicy {
    fun action(expected: Cat, observed: Set<Cat>, repairs: Int): CategoryViewportAction = when {
        observed.all { it == expected } -> CategoryViewportAction.KEEP_POSITION
        repairs == 0 -> CategoryViewportAction.RESELECT_CATEGORY
        else -> CategoryViewportAction.STOP
    }
}
