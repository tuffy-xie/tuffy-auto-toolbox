package com.anapocket.auto

/** The observed opaque ad has an unnamed clickable full-screen wrapper, not a CTA. */
object ControlRevealTarget {
    fun blocks(clickable: Boolean, containsPoint: Boolean, label: String, canvasAreaFraction: Double): Boolean =
        clickable && containsPoint && (label.isNotBlank() || canvasAreaFraction < .8)
}
