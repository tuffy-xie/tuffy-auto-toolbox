package com.anapocket.auto
import kotlinx.coroutines.delay

data class StartupAdFrame(val ad:Boolean=false,val receipt:Boolean=false,val normal:Boolean=false,val login:Boolean=false,
    val close:Boolean=false,val store:Boolean=false,val part:AdPart?=null,val error:Boolean=false,val popup:Boolean=false,val manualRequired:Boolean=false,val advanceControl:Boolean=false)
data class StartupAdOutcome(val state:String,val reason:String="")
interface StartupAdPort {
    fun now():Long
    fun stopped():Boolean
    fun sameDate():Boolean
    suspend fun frame():StartupAdFrame
    suspend fun close()
    suspend fun dismissPopup():Boolean=false
    suspend fun diagnostic(reason:String) {}
    suspend fun observedAd()
    suspend fun pause(ms:Long){delay(ms)}
}
/** Startup dismissal is navigation evidence only; a login reward needs a new official receipt after an observed ad. */
class StartupAdSequence(private val p:StartupAdPort){
    suspend fun run(requireReward:Boolean):StartupAdOutcome{
        val diagnostic=DiagnosticCapturePolicy()
        val start=p.now();var departed=false;var nativeClosed=0;var advanceClosed=0;var storeClosed=0;var lastClose=-100_000L
        var part:AdPart?=null;var returned:Long?=null;var stable=0;var popupCloses=0
        while(p.now()-start<300_000L){
            if(p.stopped())return StartupAdOutcome("UNCONFIRMED","user_stopped")
            if(!p.sameDate())return StartupAdOutcome("UNCONFIRMED","phone_date_changed")
            val f=p.frame()
            if(f.manualRequired)return StartupAdOutcome("BLOCKED","account_safety_human_required")
            if(diagnostic.shouldCapture(p.now()-start,f.ad,!f.normal&&!f.receipt&&!f.close&&!f.popup))p.diagnostic("slow_or_unrecognized_startup_ad")
            if(f.error)return StartupAdOutcome("FAILED","official_login_or_startup_ad_error")
            if(f.popup){
                if(popupCloses>=6||!p.dismissPopup())return StartupAdOutcome("UNCONFIRMED","native_popup_not_dismissed")
                popupCloses++;p.pause(750);continue
            }
            if(f.ad&&!departed){departed=true;p.observedAd()}
            if(StartupPolicy.loginConfirmed(requireReward,departed,f.receipt,p.sameDate()))return StartupAdOutcome("REWARD_CONFIRMED")
            if(!requireReward&&!f.ad&&!f.store&&(f.normal||f.login)){
                if(++stable>=3)return StartupAdOutcome("DISMISSED")
            }else stable=0
            if(requireReward&&departed&&!f.ad&&(f.normal||f.login)&&!f.receipt){
                if(returned==null)returned=p.now()
                if(p.now()-returned!!>=12_000L)return StartupAdOutcome("UNCONFIRMED",if(f.login)"login_reward_not_confirmed_after_offer_return" else "login_reward_not_confirmed")
            }
            val next=f.part
            if(next!=null&&(part==null||next.total==part!!.total&&next.current>part!!.current)){
                part=next;nativeClosed=0;advanceClosed=0;storeClosed=0
            }
            if(f.close&&!f.login&&!f.receipt&&p.now()-lastClose>=2000L&&(if(f.store)storeClosed<2 else if(f.advanceControl)advanceClosed<3 else nativeClosed<3)){
                if(f.store)storeClosed++ else if(f.advanceControl)advanceClosed++ else nativeClosed++
                lastClose=p.now();p.close()
            }
            p.pause(750)
        }
        return StartupAdOutcome("UNCONFIRMED","startup_ad_timeout_300s")
    }
}
