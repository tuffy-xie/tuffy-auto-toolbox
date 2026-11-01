package com.anapocket.auto

/** ANA renders calendar components without zero padding on single digit days/months. */
object AnaCalendarDate {
    fun same(native: String?, iso: String): Boolean {
        val actual = Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$").matchEntire(native?.trim().orEmpty()) ?: return false
        val expected = iso.split('-').map { it.toIntOrNull() }
        return expected.size == 3 && actual.groupValues.drop(1).map { it.toIntOrNull() } == expected
    }
}
/** Only the exact native deadline of the same card qualifies, never weekly/sponsored titles. */
object DailyCardDeadline {
    fun matches(deadline: String?, date: String): Boolean = deadline?.trim()?.takeIf { it.endsWith("まで") }
        ?.removeSuffix("まで")?.let { AnaCalendarDate.same(it, date) } == true
}
object UnknownDailyCardPolicy {
    fun mustReview(title: String, hasDistance: Boolean, modeIcons: Int): Boolean =
        hasDistance && modeIcons == 1 && !Regex("週間|毎週").containsMatchIn(title) &&
            !title.contains("徒歩や自転車") && Cards.fromTitle(title) == null
}
