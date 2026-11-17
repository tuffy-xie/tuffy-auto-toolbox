package com.anapocket.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-18 03:20 CAR 10km joined detail (CH435b1515 evidence): ANA now shows the attendance with a time. */
class AttendedDateTimeFormatTest {
    private val title = "車、電車、モノレールで10km移動しよう！"
    private val date = "2026-11-18"

    @Test fun newDateTimeFormatMatchesSameDay() {
        assertEquals("2026/11/18", ChallengeDetailReceipt.attendedDate("参加日時：2026/11/18 03:20"))
        assertTrue(ChallengeDetailReceipt.maxMatches(title, date, title, "2026/11/18", "参加日時：2026/11/18 03:20", "ブースト5回", 400))
        assertTrue(ChallengeDetailReceipt.identityMatches(title, date, title, "2026/11/18", "参加日時：2026/11/18 3:20"))
    }

    @Test fun oldDateOnlyFormatStillMatches() {
        assertTrue(ChallengeDetailReceipt.maxMatches(title, date, title, "2026/11/18", "参加日：2026/11/18", "ブースト5回", 400))
    }

    @Test fun otherDaysAndMalformedTextAreRejected() {
        assertFalse(ChallengeDetailReceipt.identityMatches(title, date, title, "2026/11/18", "参加日時：2026/11/17 23:59"))
        assertNull(ChallengeDetailReceipt.attendedDate("参加日時：2026/11/18"))
        assertNull(ChallengeDetailReceipt.attendedDate("参加日時：2026/11/18 03:20 済"))
        assertNull(ChallengeDetailReceipt.attendedDate("2026/11/18 03:20"))
        assertNull(ChallengeDetailReceipt.attendedDate(null))
        assertFalse(ChallengeDetailReceipt.maxMatches(title, date, title, "2026/11/18", "参加日時：2026/11/18 03:20", "ブースト4回", 350))
        assertFalse(ChallengeDetailReceipt.maxMatches(title, date, "車、電車、モノレールで5km移動しよう！", "2026/11/18", "参加日時：2026/11/18 03:20", "ブースト5回", 400))
    }

    @Test fun maxBoostResumeIdentityAcceptsNewFormat() {
        assertTrue(ChallengeDetailReceipt.attendedOn("参加日時：2026/11/18 03:20", date))
        assertFalse(ChallengeDetailReceipt.attendedOn("参加日時：2026/11/19 00:01", date))
    }

    @Test fun officialMaxObservationReleasesNavigationButKeepsUnknownReceipt() {
        val settled = OfficialMaxSettlement.settled("BOOST_MAX_OBSERVED", 5, 5, 1794949200000L)
        assertTrue(settled)
        assertFalse(OfficialMaxSettlement.blocksNavigation(unknownReceipt = true, settled = settled))
    }

    @Test fun anythingShortOfObservedMaxStillBlocks() {
        assertFalse(OfficialMaxSettlement.settled("UNCONFIRMED", 4, 5, 1794949200000L))
        assertFalse(OfficialMaxSettlement.settled("BOOST_MAX_OBSERVED", 5, 0, 0L))
        assertFalse(OfficialMaxSettlement.settled("BOOST_MAX_OBSERVED", 4, 5, 1794949200000L))
        assertFalse(OfficialMaxSettlement.settled("BOOST_MAX_OBSERVED", 5, 5, 0L))
        assertTrue(OfficialMaxSettlement.blocksNavigation(unknownReceipt = true, settled = false))
        assertFalse(OfficialMaxSettlement.blocksNavigation(unknownReceipt = false, settled = false))
    }
}
