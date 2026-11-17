package com.anapocket.auto

/** A task whose exact joined detail showed ブースト5回 (BOOST_MAX_OBSERVED) can take no further Boost. */
object OfficialMaxSettlement {
    fun settled(state:String,boost:Int,observedBoost:Int,observedAt:Long)=
        state=="BOOST_MAX_OBSERVED"&&boost==5&&observedBoost==5&&observedAt>0
    fun blocksNavigation(unknownReceipt:Boolean,settled:Boolean)=unknownReceipt&&!settled
}

/** Native joined-detail identity/date/Boost fields prove task state, not missing ad receipts. */
object ChallengeDetailReceipt {
    private val attendedTime = Regex("^(\\d{4}/\\d{1,2}/\\d{1,2})\\s+\\d{1,2}:\\d{2}$")
    /**
     * ANA showed "参加日：2026/11/3" until mid-November and "参加日時：2026/11/18 03:20" from 2026-11-18.
     * Returns only the calendar date part; anything else is rejected.
     */
    fun attendedDate(attendance: String?): String? {
        val a = attendance?.trim() ?: return null
        return when {
            a.startsWith("参加日：") -> a.removePrefix("参加日：")
            a.startsWith("参加日時：") -> attendedTime.matchEntire(a.removePrefix("参加日時：").trim())?.groupValues?.get(1)
            else -> null
        }
    }
    fun attendedOn(attendance: String?, expectedDate: String) = AnaCalendarDate.same(attendedDate(attendance), expectedDate)
    fun identityMatches(expectedTitle:String,expectedDate:String,title:String?,deadline:String?,attendance:String?)=
        title==expectedTitle&&AnaCalendarDate.same(deadline,expectedDate)&&attendedOn(attendance,expectedDate)
    fun maxMatches(expectedTitle: String, expectedDate: String, title: String?, deadline: String?,
        attendance: String?, boost: String?, reward: Int?): Boolean =
        identityMatches(expectedTitle,expectedDate,title,deadline,attendance) &&
            boost == "ブースト5回" && reward != null && reward > 0
}
