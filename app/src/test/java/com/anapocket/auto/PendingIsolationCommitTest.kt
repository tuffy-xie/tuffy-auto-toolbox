package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class PendingIsolationCommitTest {
    private val day="2026-11-04"
    private val a=SubmittedUnknown(day,"CAR|車、電車、モノレールで40km移動しよう！","CH-original",1793729755524,4,4,false,true,"DISPATCHED",1)
    private val boost=PendingInitialJoin(day,a.key,a.run,a.startedAt,4,4,false,true,2,"UNCONFIRMED","UNCONFIRMED",false,true,"ad_timeout_95s","DISPATCHED")
    private fun save(f:File,s:String){FileOutputStream(f).use{it.write(s.toByteArray());it.fd.sync()}}
    @Test fun capturedCar40ModalCannotBeTreatedAsACleanListAfterAnIsolationRestart(){
        val lines=javaClass.getResourceAsStream("/h14_car40_pending_modal.tsv")!!.bufferedReader().readLines().map{it.split('\t',limit=2)}
        val ids=lines.map{it[0]}.toSet();val labels=lines.map{it[1]}+"チャレンジ"
        assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","us.ana_pocket.ANAPocket.views.home.HomeActivity",ids,labels,false))
        assertTrue(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","us.ana_pocket.ANAPocket.views.home.HomeActivity",emptySet(),listOf("チャレンジ","参加中"),false))
        val detail=setOf("design_bottom_sheet","text_name","text_date","close","text_attended").map{"us.ana_pocket.ANAPocket:id/$it"}.toSet()
        assertTrue(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","us.ana_pocket.ANAPocket.views.home.HomeActivity",detail,listOf("車、電車、モノレールで40km移動しよう！"),false))
        assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","us.ana_pocket.ANAPocket.views.home.HomeActivity",detail+"us.ana_pocket.ANAPocket:id/title_count",labels,false))
        assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","com.applovin.adview.AppLovinFullscreenActivity",emptySet(),listOf("参加中"),true))
    }
    @Test fun observedNativeHomeCanReachExistingReadOnlyAuditButCannotConfirmOrBypassItsIdentityFence(){
        val pkg="us.ana_pocket.ANAPocket";val activity="$pkg.views.home.HomeActivity"
        val home=listOf("ホーム","ためる","ガチャ","移動記録")
        assertTrue(SubmittedUnknownIsolationPolicy.cleanNativePage(pkg,activity,emptySet(),home,false))
        assertFalse(SubmittedUnknownIsolationPolicy.mayIsolate(a,day,false,true,15_000,false))
        assertTrue(SubmittedUnknownIsolationPolicy.mayIsolate(a,day,true,true,15_000,false))
        assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage(pkg,activity,setOf("$pkg:id/title_count"),home,false))
        assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage(pkg,activity,emptySet(),home,true))
        assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage(pkg,"com.applovin.adview.AppLovinFullscreenActivity",emptySet(),home,false))
        assertFalse(a.confirmed);assertEquals("DISPATCHED",a.phase);assertEquals(4,a.index)
    }
    @Test fun isolationMetadataCommitsOnceAcrossReloadWithoutConfirmingOrDispatching(){
        val f=File.createTempFile("isolation-", ".journal").apply{deleteOnExit()};val original="${a.date}|${a.key}|${a.run}|${a.startedAt}|${a.index}|UNKNOWN";save(f,original)
        var writes=0
        assertTrue(SubmittedUnknownIsolationPolicy.commitOnce(a,a,day,false){save(f,original+"|ISOLATED");writes++})
        val reload=f.readText();assertTrue(reload.startsWith(original));assertTrue(reload.endsWith("|ISOLATED"));assertFalse(a.confirmed)
        assertTrue(SubmittedUnknownIsolationPolicy.commitOnce(a,a,day,reload.endsWith("|ISOLATED")){fail("no repeated metadata write or replacement advertisement")})
        assertEquals(1,writes);assertEquals(4,a.count)
    }
    @Test fun staleOrCrossDayIsolationCannotAlterAnotherAttempt(){
        val changed=listOf(a.copy(key="CAR|other"),a.copy(run="CH-other"),a.copy(startedAt=1),a.copy(index=3),a.copy(count=5),a.copy(confirmed=true),a.copy(phase="ARMED"),a.copy(pendingPerTask=2))
        for(current in changed)assertFalse(SubmittedUnknownIsolationPolicy.commitOnce(a,current,day,false){fail()})
        assertFalse(SubmittedUnknownIsolationPolicy.commitOnce(a,a,"2026-11-05",false){fail()})
        assertFalse(SubmittedUnknownIsolationPolicy.mayIsolate(a,day,true,true,15000,true))
        assertFalse(SubmittedUnknownIsolationPolicy.mayIsolate(a,day,false,true,15000,false))
    }
    @Test fun commitFailurePreservesUnknownAndNeverAcknowledgesIsolation(){
        val f=File.createTempFile("isolation-fail-", ".journal").apply{deleteOnExit()};save(f,"original UNKNOWN");val before=f.readBytes()
        try{SubmittedUnknownIsolationPolicy.commitOnce(a,a,day,false){throw IllegalStateException("write interrupted before FULL commit")};fail()}catch(_:IllegalStateException){}
        assertArrayEquals(before,f.readBytes());assertFalse(a.confirmed)
    }
    @Test fun boostCommitFenceRejectsAllChangedIdentityAndAlreadyConfirmedSnapshots(){
        assertTrue(NativeChallengeReceiptPolicy.samePendingBoost(boost,boost,day))
        for(current in listOf(boost.copy(key="other"),boost.copy(runId="CH-other"),boost.copy(startedAt=2),boost.copy(index=5,attempts=5),boost.copy(dispatchPhase="ARMED"),boost.copy(boost=3),boost.copy(confirmed=true)))
            assertFalse(NativeChallengeReceiptPolicy.samePendingBoost(boost,current,day))
        assertFalse(NativeChallengeReceiptPolicy.samePendingBoost(boost,boost,"2026-11-05"))
    }
    @Test fun cachedNoGainPanelIsNotPromotedToAReceipt(){
        val steps=listOf(50,50,50,100,100);val labels=listOf("3回目のブーストをしますか？","現在のチャレンジ達成後の報酬：","150pt")+steps.map{"+$it"}
        assertEquals("NO_GAIN",OpaqueRecoveryPolicy.outcome(2,150,200,steps,labels))
        assertFalse(BoundLateBoostReceiptPolicy.matches("UNCONFIRMED","UNCONFIRMED","ad_timeout_95s",false,false,4,4,1,true,true,2,150,200,steps,true,labels))
        assertFalse(ChallengeRecoveryPolicy.officialNoGainReady(14999,false));assertFalse(ChallengeRecoveryPolicy.officialNoGainReady(16000,true))
        assertFalse(ChallengeRecoveryPolicy.canResume("UNCONFIRMED","UNCONFIRMED","ad_timeout_95s",false,4,4,false))
    }
    @Test fun isolatedMetadataCannotAuthorizeAnotherDayOrUnknownReplay(){
        assertTrue(SubmittedUnknownIsolationPolicy.bound(a,day,a.key,a.run,a.startedAt,4,4,SubmittedUnknownIsolationPolicy.KIND))
        assertFalse(SubmittedUnknownIsolationPolicy.bound(a,"2026-11-05",a.key,a.run,a.startedAt,4,4,SubmittedUnknownIsolationPolicy.KIND))
        assertFalse(SubmittedUnknownIsolationPolicy.cleanNativePage("us.ana_pocket.ANAPocket","us.ana_pocket.ANAPocket.views.home.HomeActivity",setOf("us.ana_pocket.ANAPocket:id/button_see_ad"),listOf("参加中"),false))
    }
}
