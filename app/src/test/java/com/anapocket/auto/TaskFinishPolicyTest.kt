package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class TaskFinishPolicyTest {
 @Test fun lockOnlyAfterConfirmedExitOfSameUnblockedRun(){assertTrue(TaskExitPolicy.canLock(true,false,false,true));assertFalse(TaskExitPolicy.canLock(false,false,false,true));assertFalse(TaskExitPolicy.canLock(true,true,false,true));assertFalse(TaskExitPolicy.canLock(true,false,true,true));assertFalse(TaskExitPolicy.canLock(true,false,false,false))}
 @Test fun commandAcceptanceDoesNotProveActualLock(){assertTrue(TaskExitPolicy.lockConfirmed(true,true));assertFalse(TaskExitPolicy.lockConfirmed(false,true));assertFalse(TaskExitPolicy.lockConfirmed(true,false));assertFalse(TaskExitPolicy.lockConfirmed(false,false))}
}
