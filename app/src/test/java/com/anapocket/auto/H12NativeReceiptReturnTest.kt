package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class H12NativeReceiptReturnTest {
    private val pkg="us.ana_pocket.ANAPocket"
    private val steps=listOf(25,25,25,50,50)
    private val labels=listOf("チャレンジをはじめました！","1回目のブーストをしますか？","現在のチャレンジ達成後の報酬：","25pt","+25","+25","+25","+50","+50")
    private val ids=setOf("title_count","current_point","boost_list").map{pkg+":id/"+it}.toSet()
    private fun fixture(name:String)=javaClass.getResource("/h12/"+name)!!.readText()
    private fun bound(pending:Int=1,sameDate:Boolean=true,departed:Boolean=true,originalStart:Boolean=true,originalReceipt:Boolean=true,initial:Boolean=true,index:Int=1,count:Int=1,confirmed:Boolean=false,native:Boolean=true,state:String="UNCONFIRMED",adState:String="UNCONFIRMED") =
        NativeChallengeReceiptPolicy.boundInitial(state,adState,initial,index,count,pending,sameDate,departed,confirmed,originalStart,originalReceipt,native,25,steps,labels)
    @Test fun actualNativeReceiptIsNotAnSdkSurface(){assertTrue(NativeChallengeReceiptPolicy.panel(pkg,ids,labels))}
    @Test fun actualSdkTreeStillBlocksNativeReturn(){assertFalse(NativeChallengeReceiptPolicy.panel(pkg,ids+"__root_view__",labels));assertFalse(NativeChallengeReceiptPolicy.panel(pkg,ids+(pkg+":id/ia_endcard_video_overlay"),labels))}
    @Test fun missingOrForeignNativeFieldsAreNotReceipts(){assertFalse(NativeChallengeReceiptPolicy.panel("other",ids,labels));assertFalse(NativeChallengeReceiptPolicy.panel(pkg,ids-pkg.plus(":id/boost_list"),labels));assertFalse(NativeChallengeReceiptPolicy.panel(pkg,ids,listOf("チャレンジをはじめました！")))}
    @Test fun recordedStartBindsExactTaskAndNativeUnpaddedDate(){val t=fixture("bike_initial_start_nodes.txt");assertTrue(NativeChallengeReceiptPolicy.originalStartFields(t,"自転車で500m移動しよう！","2026-11-01",25));assertFalse(NativeChallengeReceiptPolicy.originalStartFields(t,"自転車で1km移動しよう！","2026-11-01",25));assertFalse(NativeChallengeReceiptPolicy.originalStartFields(t,"自転車で500m移動しよう！","2026-10-31",25))}
    @Test fun recordedReceiptMustMatchFullRewardTable(){val t=fixture("bike_initial_receipt_nodes.txt");assertTrue(NativeChallengeReceiptPolicy.recordedInitialReceipt(t,25,steps));assertFalse(NativeChallengeReceiptPolicy.recordedInitialReceipt(t,50,steps));assertFalse(NativeChallengeReceiptPolicy.recordedInitialReceipt(t,25,listOf(25,25,25,25,25)))}
    @Test fun anonymousCurrentPanelCannotAdoptUnknown(){assertFalse(bound(originalStart=false));assertFalse(bound(originalReceipt=false));assertFalse(bound(departed=false))}
    @Test fun onlyOneOriginalUnconfirmedInitialIdentityMayReconcile(){assertTrue(bound());assertFalse(bound(pending=2));assertFalse(bound(sameDate=false));assertFalse(bound(initial=false));assertFalse(bound(index=2));assertFalse(bound(count=2));assertFalse(bound(confirmed=true));assertFalse(bound(native=false));assertFalse(bound(state="JOINED"));assertFalse(bound(adState="FAILED"))}
    @Test fun staleActivityDoesNotPreventFinishAfterVerifiedNativeReceipt()=runBlocking{
        val p=object:AdInteractionPort{
            var time=0L
            override fun now()=time
            override fun stopped()=false
            override suspend fun pause(ms:Long){time+=ms}
            override suspend fun state(state:AdState){}
            override suspend fun close()=false
            override suspend fun dismissReceipt(){}
            override suspend fun frame():AdFrame {val native=time>=1600&&NativeChallengeReceiptPolicy.panel(pkg,ids,labels);return if(native)AdFrame(true,true,1,receipt=true,adSurface=false) else AdFrame(true,false,adSurface=true)}
        }
        val r=AdInteraction(p).awaitReceipt(0);assertTrue(r.completed);assertTrue(r.reward);assertEquals(1,r.after);assertEquals(1600L,p.time)
    }
}
