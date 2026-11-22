package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/22 user request: every run and every check-in ends with the screen locked. */
class LockAfterEveryRunTest {
    @Test fun unfinishedRoutineEndingsStillLock() {
        // 11/21: six BLOCKED retries, no stop request.
        assertTrue(TaskExitPolicy.lockUnfinished("",true,true))
        // 11/22 03:03: the 3 h watchdog stopped the run.
        assertTrue(TaskExitPolicy.lockUnfinished("watchdog",true,true))
        assertTrue(TaskExitPolicy.lockUnfinished("account_safety_LOGIN",true,true))
    }
    @Test fun theUsersOwnStopNeverLocks() {
        assertFalse(TaskExitPolicy.lockUnfinished("user",true,true))
    }
    @Test fun anotherRunOrAReadinessCheckIsNeverLocked() {
        assertFalse(TaskExitPolicy.lockUnfinished("",false,true))
        // The readiness check passes lockAfter=false.
        assertFalse(TaskExitPolicy.lockUnfinished("",true,false))
    }
    @Test fun aFinishedRunStillGoesThroughTheFullExit() {
        // The scene-keeping lock is only the fallback; a clean run keeps Home, cleanup and lock.
        assertTrue(TaskExitPolicy.allowed(true,false,false,false,true))
        assertTrue(TaskExitPolicy.canLock(true,false,false,true))
    }
    @Test fun checkinLocksEvenWithAKeptScene() {
        // 11/22 TikTok NEEDS_USER_ACTION: the scene is kept and the screen now locks.
        assertTrue(CheckinExitPolicy.preserveScene(true,false,false,true))
        assertTrue(CheckinExitPolicy.lockAfter(true,false))
    }
    @Test fun checkinSkipsTheLockOnlyForADueCheckinOrLostOwnership() {
        assertFalse(CheckinExitPolicy.lockAfter(true,true))
        assertFalse(CheckinExitPolicy.lockAfter(false,false))
    }
}
