package com.anapocket.auto

/** The Today tab also contains weekly/sponsored cards. Read the same card's native deadline. */
object DailyCardDeadline {
    fun matches(deadline: String?, date: String): Boolean = deadline?.trim() == date.replace('-', '/') + "まで"
}

/** Do not silently call a category complete when a new single-mode daily distance title is unreadable. */
object UnknownDailyCardPolicy {
    fun mustReview(title: String, hasDistance: Boolean, modeIcons: Int): Boolean =
        hasDistance && modeIcons == 1 && !Regex("週間|毎週").containsMatchIn(title) &&
            !title.contains("徒歩や自転車") && Cards.fromTitle(title) == null
}
