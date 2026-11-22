package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/23 03:00: a removal that left the ANA task in place no longer holds the daily ads for the rest of the day. */
class DailyRemovalRetryTest {
    @Test fun nov23LegacyMarkerFromTheFailedRemovalGetsOneMoreTry() {
        // Written at 03:00:05 by the old build: BLOCKED_NO_REPEAT with no try count, counted as the first try.
        assertTrue(StalledAdRestartPolicy.mayRetryDailyRemoval("BLOCKED_NO_REPEAT",1))
        assertTrue(StalledAdRestartPolicy.mayRetryDailyRemoval(StalledAdRestartPolicy.REMOVAL_NOT_CONFIRMED,1))
    }
    @Test fun neverMoreThanTwoTries() {
        assertFalse(StalledAdRestartPolicy.mayRetryDailyRemoval("BLOCKED_NO_REPEAT",2))
        assertFalse(StalledAdRestartPolicy.mayRetryDailyRemoval(StalledAdRestartPolicy.REMOVAL_NOT_CONFIRMED,2))
        assertEquals(2,StalledAdRestartPolicy.DAILY_REMOVAL_TRIES)
    }
    @Test fun aRemovalThatHappenedIsNeverRepeated() {
        for(s in listOf("REQUESTED","BLANK_TASK_REMOVED","BLANK_RELAUNCHED","RELAUNCHED_REVIEW_REQUIRED","SYSTEM_STOP_CONFIRMED",""))
            assertFalse(s,StalledAdRestartPolicy.mayRetryDailyRemoval(s,1))
        assertFalse(StalledAdRestartPolicy.mayRetryDailyRemoval(null,1))
    }
    @Test fun recentsWaitMatchesTheTravelRecovery() {
        // 24 x 300 ms, as ChallengeBoost's recentsNodes() that removed the task at 01:12 the same night.
        assertEquals(24,ManagedCleanupPolicy.RECENTS_POLLS)
        assertTrue(ManagedCleanupPolicy.REMOVAL_CHECKS*450>=2000)
    }
}
