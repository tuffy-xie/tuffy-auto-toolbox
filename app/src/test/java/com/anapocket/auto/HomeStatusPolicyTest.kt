package com.anapocket.auto

import com.anapocket.auto.HomeStatusPolicy.Primary
import com.anapocket.auto.HomeStatusPolicy.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusPolicyTest {
    @Test fun finishedDayOffersResultsNotAnotherRun() {
        // Nov 8: routine DONE, 24/24 challenges, ads 25/25, login confirmed; the button still said "继续未完成任务".
        val done = HomeStatusPolicy.resultsOnly(finished = true, failed = false, incomplete = false, running = false, safetyPending = false)
        assertTrue(done)
        assertEquals(Primary.DONE, HomeStatusPolicy.primary(running = false, safetyPending = false, resultsOnly = done, hasTodayWork = true))
    }

    @Test fun anyOpenItemKeepsContinue() {
        assertFalse(HomeStatusPolicy.resultsOnly(finished = true, failed = false, incomplete = true, running = false, safetyPending = false))
        assertFalse(HomeStatusPolicy.resultsOnly(finished = true, failed = true, incomplete = false, running = false, safetyPending = false))
        assertFalse(HomeStatusPolicy.resultsOnly(finished = false, failed = false, incomplete = false, running = false, safetyPending = false))
        assertEquals(Primary.CONTINUE, HomeStatusPolicy.primary(running = false, safetyPending = false, resultsOnly = false, hasTodayWork = true))
        assertEquals(Primary.START, HomeStatusPolicy.primary(running = false, safetyPending = false, resultsOnly = false, hasTodayWork = false))
    }

    @Test fun runningAndSafetyWinOverDone() {
        assertEquals(Primary.STOP, HomeStatusPolicy.primary(running = true, safetyPending = true, resultsOnly = true, hasTodayWork = true))
        assertEquals(Primary.SAFETY, HomeStatusPolicy.primary(running = false, safetyPending = true, resultsOnly = true, hasTodayWork = true))
        assertFalse(HomeStatusPolicy.resultsOnly(finished = true, failed = false, incomplete = false, running = false, safetyPending = true))
    }

    @Test fun tileSaysProgressDuringRunAndUnfinishedAfter() {
        assertEquals("进行中 3/7" to Tone.ACTIVE, HomeStatusPolicy.tile(true, 5, covered = false, unknown = true, total = 7, done = 3, running = true, scanLabel = ""))
        assertEquals("未完成 3/7" to Tone.WARN, HomeStatusPolicy.tile(true, 5, covered = false, unknown = true, total = 7, done = 3, running = false, scanLabel = ""))
        assertEquals("已完成 7 项" to Tone.DONE, HomeStatusPolicy.tile(true, 5, covered = true, unknown = false, total = 7, done = 7, running = false, scanLabel = ""))
        assertEquals("官方暂无" to Tone.MUTED, HomeStatusPolicy.tile(true, 40, covered = true, unknown = false, total = 0, done = 0, running = false, scanLabel = ""))
        assertEquals("需设公里" to Tone.WARN, HomeStatusPolicy.tile(true, 0, covered = false, unknown = false, total = 0, done = 0, running = false, scanLabel = ""))
        assertEquals("未选中" to Tone.MUTED, HomeStatusPolicy.tile(false, 5, covered = true, unknown = false, total = 7, done = 7, running = false, scanLabel = ""))
        assertEquals("待扫描" to Tone.MUTED, HomeStatusPolicy.tile(true, 5, covered = false, unknown = false, total = 0, done = 0, running = false, scanLabel = "待扫描"))
    }

    @Test fun travelRingCountsConfirmedRows() {
        assertEquals(1f, HomeStatusPolicy.travelProgress(cats = 4, covered = 4, total = 24, done = 24), 0f)
        assertEquals(1f, HomeStatusPolicy.travelProgress(cats = 1, covered = 1, total = 0, done = 0), 0f)
        assertEquals(0.5f, HomeStatusPolicy.travelProgress(cats = 4, covered = 2, total = 10, done = 5), 0f)
        assertEquals(0f, HomeStatusPolicy.travelProgress(cats = 0, covered = 0, total = 3, done = 3), 0f)
    }
}
