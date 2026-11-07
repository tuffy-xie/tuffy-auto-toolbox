package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class RecoveryIdentityAndBudgetTest {
    private val title="移動手段問わず20km移動しよう！"
    private val date="2026-11-03"
    private val steps=listOf(25,25,25,50,50)
    private fun home(pending:Boolean=false,sdk:Boolean=false,day:Boolean=true,used:Boolean=false,count:Int=0,live:Boolean=true,native:Boolean=true)=
        HomeNavigationPolicy.restartAllowed(native,pending,sdk,day,used,count,live)
    @Test fun stalledHomeKeepsBoundedRestartButSdkUnknownAndRepeatedStopCannotEnter(){
        assertTrue(home());assertTrue(home(count=2));assertFalse(home(count=3));assertFalse(home(used=true))
        assertFalse(home(pending=true));assertFalse(home(sdk=true));assertFalse(home(day=false));assertFalse(home(live=false));assertFalse(home(native=false))
    }
    @Test fun foregroundTaskTitleDayAndAttendanceMustAllMatch(){
        assertTrue(ChallengeDetailReceipt.identityMatches(title,date,title,"2026/11/3","参加日：2026/11/3"))
        assertFalse(ChallengeDetailReceipt.identityMatches(title,date,null,null,null))
        assertFalse(ChallengeDetailReceipt.identityMatches(title,date,"移動手段問わず30km移動しよう！","2026/11/3","参加日：2026/11/3"))
        assertFalse(ChallengeDetailReceipt.identityMatches(title,date,title,"2026/11/2","参加日：2026/11/3"))
        assertFalse(ChallengeDetailReceipt.identityMatches(title,date,title,"2026/11/3","参加日：2026/11/2"))
    }
    @Test fun anonymousSameAmountAndOrdinalNeverSupplyIdentity(){
        val anonymous=listOf("4回目のブーストをしますか？","現在のチャレンジ達成後の報酬：100pt")+steps.map{"+$it"}
        assertEquals("NO_GAIN",OpaqueRecoveryPolicy.outcome(3,100,150,steps,anonymous))
        assertFalse(ChallengeDetailReceipt.identityMatches(title,date,null,null,null))
        val identity=ChallengeDetailReceipt.identityMatches(title,date,null,null,null)
        assertNull(if(identity)OpaqueRecoveryPolicy.outcome(3,100,150,steps,anonymous) else null)
    }
    @Test fun exactOfficialAdvanceNoGainAndAmbiguousResultRemainDifferent(){
        fun panel(next:Int?,reward:Int)=listOfNotNull(next?.let{"${it}回目のブーストをしますか？"},"現在のチャレンジ達成後の報酬：${reward}pt")+steps.map{"+$it"}
        assertEquals("ADVANCED",OpaqueRecoveryPolicy.outcome(3,100,150,steps,panel(5,150)))
        assertEquals("NO_GAIN",OpaqueRecoveryPolicy.outcome(3,100,150,steps,panel(4,100)))
        assertNull(OpaqueRecoveryPolicy.outcome(3,100,150,steps,panel(4,150)))
        assertTrue(OpaqueRecoveryPolicy.recoveryAlreadyUsed("OFFICIAL_RESUME_OBSERVED",1))
        assertTrue(OpaqueRecoveryPolicy.recoveryAlreadyUsed("REMOVE_ONLY_OBSERVED_ANA_TASK",1))
    }
    @Test fun staleMissingOrClockReversedStallProofCannotAuthorizeRemoval(){
        assertTrue(OpaqueRecoveryPolicy.freshStallReview(30_000,100_000,110_000))
        assertFalse(OpaqueRecoveryPolicy.freshStallReview(29_999,100_000,110_000))
        assertFalse(OpaqueRecoveryPolicy.freshStallReview(30_000,0,110_000))
        assertFalse(OpaqueRecoveryPolicy.freshStallReview(30_000,100_000,99_999))
        assertFalse(OpaqueRecoveryPolicy.freshStallReview(30_000,100_000,160_001))
    }
    @Test fun visualReviewDistinguishesMaterialMovementFromSmallNoise(){
        val a=IntArray(256){0x303030};val noise=IntArray(256){0x333333}
        assertFalse(OpaqueRecoveryPolicy.visualProgress(a,a));assertFalse(OpaqueRecoveryPolicy.visualProgress(a,noise))
        val moved=a.copyOf();repeat(8){moved[it]=0xc0c0c0};assertTrue(OpaqueRecoveryPolicy.visualProgress(a,moved))
    }
    @Test fun stopClaimPersistsBudgetAndReloadCannotRepeatIdentity(){
        val file=java.io.File.createTempFile("tuffy-home-", ".journal").apply{deleteOnExit()}
        var actions=0
        val claimed=StalledAdRestartPolicy.reserve(false,0){
            java.io.FileOutputStream(file).use{it.write("HOME|2026-11-03|RT-original|REQUESTED|1".toByteArray());it.fd.sync()};true
        }
        if(claimed)actions++
        val reload=file.readText().split('|')
        val repeated=StalledAdRestartPolicy.reserve(reload[3]=="REQUESTED",reload[4].toInt()){fail("must not overwrite claimed identity");true}
        if(repeated)actions++
        assertEquals(1,actions);assertEquals(listOf("HOME","2026-11-03","RT-original","REQUESTED","1"),reload)
    }
    @Test fun failedOrExhaustedClaimCannotDispatchSystemStop(){
        var actions=0;var writes=0
        if(StalledAdRestartPolicy.reserve(false,0){writes++;false})actions++
        assertEquals(1,writes);assertEquals(0,actions)
        assertFalse(StalledAdRestartPolicy.reserve(false,3){writes++;true});assertEquals(1,writes)
    }
    @Test fun interruptionAfterClaimKeepsUnknownAndStopBudgetOnReload(){
        val file=java.io.File.createTempFile("tuffy-recovery-", ".journal").apply{deleteOnExit()}
        try{StalledAdRestartPolicy.reserve(false,0){
            java.io.FileOutputStream(file).use{it.write("CH-original|5|1234|UNKNOWN|REMOVE_ONLY_OBSERVED_ANA_TASK|1".toByteArray());it.fd.sync()}
            throw IllegalStateException("simulated process interruption before gesture")
        };fail()}catch(_:IllegalStateException){}
        val reload=file.readText().split('|');assertEquals("UNKNOWN",reload[3]);assertEquals("CH-original",reload[0])
        assertTrue(OpaqueRecoveryPolicy.recoveryAlreadyUsed(reload[4],reload[5].toInt()))
    }
}
