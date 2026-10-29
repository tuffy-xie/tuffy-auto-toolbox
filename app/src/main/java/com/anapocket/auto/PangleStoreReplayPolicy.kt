package com.anapocket.auto

/** A bounded return from a repeated Pangle document, never a reward/completion heuristic. */
class PangleStoreReplayPolicy {
    private var current: String? = null
    private var previous: String? = null
    private var inSheet = false
    private var repeated = false
    var returnedViaLauncher = false
        private set
    fun observeDocument(ids: List<String>) { current = signature(ids) }
    fun observeSheet(visible: Boolean) {
        if (visible && !inSheet) { repeated = current != null && current == previous; previous = current }
        inSheet = visible
    }
    fun shouldReturn(isPlayHome: Boolean) = isPlayHome && repeated && !returnedViaLauncher
    fun markReturned() { returnedViaLauncher = true }
    fun description() = "sameDocument=$repeated previous=$previous current=$current returnedViaLauncher=$returnedViaLauncher"
    companion object {
        fun signature(ids: List<String>): String? = ids.filter {
            it != "null" && it != "__root_view__" && !it.startsWith("android:") && !Regex("^cl-\\d+$").matches(it)
        }.distinct().sorted().takeIf { it.size >= 8 }?.joinToString("|")
        /** An unchanged panel is only a review candidate; delayed official callbacks are real. */
        fun unchangedBaselineCandidate(returned: Boolean, sameRun: Boolean, initial: Boolean, departed: Boolean,
            failures: Int, boost: Int, before: Int, expected: Int, steps: List<Int>, labels: List<String>): Boolean =
            returned && sameRun && !initial && departed && failures == 0 &&
                OpaqueRecoveryPolicy.outcome(boost, before, expected, steps, labels) == "NO_GAIN"
        fun mayRetryFromUnchangedPanel() = false
        fun confirmedLateAdvance(returned: Boolean, sameRun: Boolean, initial: Boolean, departed: Boolean,
            boost: Int, before: Int, expected: Int, steps: List<Int>, labels: List<String>): Boolean =
            returned && sameRun && !initial && departed &&
                OpaqueRecoveryPolicy.outcome(boost, before, expected, steps, labels) == "ADVANCED"
    }
}
