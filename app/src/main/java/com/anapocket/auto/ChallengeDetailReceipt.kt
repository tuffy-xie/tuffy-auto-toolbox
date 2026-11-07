package com.anapocket.auto

/** Native joined-detail identity/date/Boost fields prove task state, not missing ad receipts. */
object ChallengeDetailReceipt {
    fun identityMatches(expectedTitle:String,expectedDate:String,title:String?,deadline:String?,attendance:String?)=
        title==expectedTitle&&AnaCalendarDate.same(deadline,expectedDate)&&attendance?.startsWith("参加日：")==true&&
            AnaCalendarDate.same(attendance.removePrefix("参加日："),expectedDate)
    fun maxMatches(expectedTitle: String, expectedDate: String, title: String?, deadline: String?,
        attendance: String?, boost: String?, reward: Int?): Boolean =
        identityMatches(expectedTitle,expectedDate,title,deadline,attendance) &&
            boost == "ブースト5回" && reward != null && reward > 0
}
