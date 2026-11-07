package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class LateInitialReceiptIntegrationTest {
    private val date="2026-11-04"
    private val steps=listOf(50,50,50,100,100)
    private val labels=listOf("チャレンジをはじめました！","1回目のブーストをしますか？","現在のチャレンジ達成後の報酬：100pt")+steps.map{"+$it"}
    private val original=PendingInitialJoin(date,"WALK|徒歩で3km移動しよう！","CH-original",1793718980474,1,1,
        true,false,0,"UNCONFIRMED","UNCONFIRMED",false,true,"ad_timeout_95s","DISPATCHED")
    private fun apply(current:PendingInitialJoin?=original,day:String=date,pending:Int=1,start:Boolean=true,
        recorded:Boolean=false,identity:Boolean=true,native:Boolean=true,observed:List<String> = labels,expected:PendingInitialJoin=original,commit:()->Unit = {})=
        NativeChallengeReceiptPolicy.commitInitialOnce(expected,current,day,pending,start,recorded,identity,native,100,steps,observed,persist=commit)
    private fun confirmed(a:PendingInitialJoin)=a.copy(joined=true,taskState="JOINED",adState="REWARD_CONFIRMED",confirmed=true)
    private fun save(file:File,a:PendingInitialJoin){FileOutputStream(file).use{
        it.write(listOf(a.date,a.key,a.runId,a.startedAt,a.index,a.attempts,a.dispatchPhase,a.confirmed,a.joined,a.taskState,a.adState).joinToString("\t").toByteArray());it.fd.sync()}}
    @Test fun exactLateReceiptCommitsOriginalIdentityOnceWithoutAReplacementAttempt(){
        val file=File.createTempFile("tuffy-late-", ".journal").apply{deleteOnExit()};var current=original;var writes=0
        save(file,current)
        assertTrue(apply(current){current=confirmed(current);save(file,current);writes++})
        val reload=file.readText().split('\t');assertEquals("CH-original",reload[2]);assertEquals("1793718980474",reload[3])
        assertEquals("1",reload[4]);assertEquals("1",reload[5]);assertEquals("DISPATCHED",reload[6]);assertEquals("true",reload[7])
        current=original.copy(confirmed=reload[7].toBoolean(),joined=reload[8].toBoolean(),taskState=reload[9],adState=reload[10])
        assertTrue(apply(current){fail("a repeated review cannot write, confirm twice, or reserve an ad")})
        assertEquals(1,writes);assertEquals(0,current.boost)
    }
    @Test fun missingOrWrongTaskIdentityNeverUsesAnAmountAsAttribution(){
        assertFalse(apply(identity=false){fail()})
        assertFalse(ChallengeDetailReceipt.identityMatches("徒歩で3km移動しよう！",date,"徒歩で2km移動しよう！","2026/11/4","参加日：2026/11/4"))
        assertFalse(apply(identity=false,recorded=false){fail()})
    }
    @Test fun latestLedgerIdentityAndDateMustStillMatchThePinnedAttempt(){
        val changed=listOf(original.copy(key="WALK|other"),original.copy(runId="CH-other"),original.copy(startedAt=1),
            original.copy(index=2),original.copy(attempts=2),original.copy(date="2026-11-03"),original.copy(initial=false))
        for(a in changed)assertFalse(apply(a){fail("stale review must not mutate a different attempt")})
        assertFalse(apply(null));assertFalse(apply(day="2026-11-05"))
    }
    @Test fun reservedArmedMultiplePendingSdkAndMissingDepartureNeverConfirm(){
        assertFalse(apply(original.copy(dispatchPhase="ARMED")));assertFalse(apply(original.copy(dispatchPhase="RESERVED")))
        assertFalse(apply(original.copy(departed=false)));assertFalse(apply(pending=2));assertFalse(apply(native=false));assertFalse(apply(start=false))
        assertFalse(apply(observed=labels.map{if(it.contains("100pt"))it.replace("100pt","150pt") else it}))
    }
    @Test fun interruptedCommitCannotReturnSuccessOrChangeUnknown(){
        val file=File.createTempFile("tuffy-late-fail-", ".journal").apply{deleteOnExit()};save(file,original);val before=file.readBytes()
        try{apply{throw IllegalStateException("simulated commit failure before durable write")};fail()}catch(_:IllegalStateException){}
        assertArrayEquals(before,file.readBytes());assertFalse(original.confirmed)
    }
    @Test fun interruptedAfterCommitReloadsConfirmedAndCannotDispatchAgain(){
        val file=File.createTempFile("tuffy-late-done-", ".journal").apply{deleteOnExit()};save(file,original)
        try{apply{save(file,confirmed(original));throw IllegalStateException("simulated process death after commit")};fail()}catch(_:IllegalStateException){}
        val r=file.readText().split('\t');val reloaded=original.copy(confirmed=r[7].toBoolean(),joined=r[8].toBoolean(),taskState=r[9],adState=r[10])
        assertTrue(apply(reloaded){fail("no second commit or UI dispatch after durable confirmation")})
    }
    @Test fun oldRecordedReceiptPathRemainsBoundToOriginalStart(){
        val legacy=original.copy(dispatchPhase="");assertTrue(apply(legacy,recorded=true,identity=false,expected=legacy))
        assertFalse(apply(legacy,recorded=true,identity=false))
        assertFalse(apply(recorded=true,identity=false,start=false));assertFalse(apply(recorded=true,identity=false,native=false))
    }
    @Test fun previouslyConfirmedRowNeverRegressesOrResetsAnotherBoost(){
        assertTrue(apply(confirmed(original)){fail()})
        assertFalse(apply(confirmed(original).copy(boost=1,attempts=2,index=2)){fail()})
        assertFalse(apply(confirmed(original).copy(joined=false)){fail()})
    }
}
