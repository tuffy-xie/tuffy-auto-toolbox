package com.anapocket.auto

/** Touch reveals controls only; elapsed time never confirms an ad or a reward. */
object ControlRevealPolicy {
    fun canReveal(opaqueElapsed: Long, count: Int, sinceLast: Long, closeVisible: Boolean): Boolean =
        !closeVisible && opaqueElapsed >= 30_000L && count in 0..2 && (count == 0 || sinceLast >= 25_000L)
}
