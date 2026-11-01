package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class CheckinExitPolicyTest {
    @Test fun unknownReservedSceneAndHumanPromptArePreserved(){assertTrue(CheckinExitPolicy.preserveScene(true,false,false,true));assertTrue(CheckinExitPolicy.preserveScene(true,false,true,false))}
    @Test fun completedTaskStillUsesNormalCleanupAndLock(){assertFalse(CheckinExitPolicy.preserveScene(true,false,false,false))}
    @Test fun oldControllerCannotPreserveOrOperateAnaForeground(){assertFalse(CheckinExitPolicy.preserveScene(false,false,false,true));assertFalse(CheckinExitPolicy.preserveScene(false,false,true,true))}
    @Test fun readOnlyProbeRetainsItsOriginalCleanupContract(){assertFalse(CheckinExitPolicy.preserveScene(true,true,false,true))}
}
