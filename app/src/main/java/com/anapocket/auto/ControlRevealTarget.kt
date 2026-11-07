package com.anapocket.auto

/** A clickable wrapper can be a full-screen store link even when its label is empty. */
object ControlRevealTarget {
    fun blocks(clickable: Boolean, containsPoint: Boolean): Boolean = clickable && containsPoint
}
