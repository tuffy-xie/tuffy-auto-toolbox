package com.anapocket.auto

/** Native joined-detail identity/date/Boost fields prove task state, not missing ad receipts. */
object ChallengeDetailReceipt {
    fun maxMatches(expectedTitle: String, expectedDate: String, title: String?, deadline: String?,
        attendance: String?, boost: String?, reward: Int?): Boolean =
        title == expectedTitle && deadline == expectedDate.replace('-', '/') &&
            attendance == "参加日：" + expectedDate.replace('-', '/') &&
            boost == "ブースト5回" && reward != null && reward > 0
}
