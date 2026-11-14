package com.anapocket.auto

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Monthly badge (マンスリーバッジ) video: the home chip shows "N/50"; its page offers 「動画を見て1点」 once a
 * day, which plays a Google rewarded ad and adds yesterday's point. Observed on the phone, Nov 8 19:15:
 * the pod opened Play install sheets by itself, closed to ANA home, and the chip went 13/50 -> 14/50.
 */
object MedalVideoPolicy {
    fun normalize(s:String)=s.replace(Regex("\\s"),"").map{c->if(c in '０'..'９')'0'+(c-'０') else c}.joinToString("")
    /** The home chip: one "13/50" label, or "15" and "/50" as neighbouring labels (Nov 9). */
    fun counter(labels:List<String>):Int? {
        val n=labels.map{normalize(it)}
        n.firstNotNullOfOrNull{Regex("^(\\d{1,2})/50$").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()}?.let{return it}
        val i=n.indexOf("/50")
        return if(i>0&&Regex("\\d{1,2}").matches(n[i-1]))n[i-1].toInt() else null
    }
    /** A label that is part of the chip, and therefore safe to tap to open the badge page. */
    fun chipLabel(label:String)=normalize(label).let{it=="/50"||Regex("\\d{1,2}/50").matches(it)}
    fun badgePage(labels:List<String>)=labels.any{normalize(it)=="マンスリーバッジ"}
    fun offerButton(label:String)=normalize(label)=="動画を見て1点"
    /** The button's two text runs when ANA exposes them separately (Nov 9). */
    fun offerParts(first:String,second:String)=normalize(first)=="動画を見て"&&normalize(second)=="1点"
    /** 「動画視聴で、昨日の●1点を獲得しました！」 replaces the button once claimed. */
    fun claimed(labels:List<String>)=labels.any{val t=normalize(it);t.contains("動画視聴で")&&t.contains("獲得しました")}
    /** A visible reward countdown: an X shown now would give up the reward. */
    fun rewardPending(labels:List<String>)=labels.any{Regex("Reward\\s+in\\s+\\d+|報酬(獲得)?まで|秒後に(報酬|特典)",RegexOption.IGNORE_CASE).containsMatchIn(it)}
    /** Google's "close and lose the reward?" prompt: keep watching instead. */
    fun resumeLabel(label:String)=normalize(label).lowercase() in setOf("resumevideo","resume","動画を再開","再開","続ける","視聴を続ける")
    /** Once a day, and never again after the button was pressed: an unconfirmed ad is not replayed. */
    fun mayStart(state:String?)=state==null||state=="NOT_OFFERED"||state=="FAILED_BEFORE_TAP"
    fun confirmed(before:Int?,after:Int?,claimedText:Boolean)=claimedText||(before!=null&&after==before+1)
}

object MedalVideoStore {
    private fun sp(c:Context)=c.getSharedPreferences("ana_medal_video_v1",Context.MODE_PRIVATE)
    fun read(c:Context,date:String=TokyoDate.of()):JSONObject?=sp(c).getString(date,null)?.let{JSONObject(it)}
    fun write(c:Context,date:String,state:String,detail:String,before:Int?=null,after:Int?=null,run:String=""){
        val row=(read(c,date)?:JSONObject().put("date",date)).put("state",state).put("detail",detail).put("at",System.currentTimeMillis())
        if(before!=null)row.put("before",before);if(after!=null)row.put("after",after);if(run.isNotBlank())row.put("runId",run)
        check(sp(c).edit().putString(date,row.toString()).commit()){"medal_state_write_failed"}
        Logger.i("MEDAL","date=$date state=$state before=$before after=$after $detail")
    }
    /** For the daily report: null when the step never ran. */
    fun label(c:Context,date:String)=when(read(c,date)?.optString("state")){
        "CONFIRMED","ALREADY_CLAIMED"->"奖牌已领";"UNCONFIRMED"->"奖牌结果未确认";"DISPATCHED"->"奖牌结果未确认"
        "NOT_OFFERED"->null;null->null;else->"奖牌未领"}
}

class MedalVideoTask(private val svc:AutoAccessibilityService) {
    private val ui=Ui(svc)
    private val pkg="us.ana_pocket.ANAPocket"
    private val close=AdUiClose(svc)
    private fun labels()=if(ui.currentPackage()==pkg)ui.foregroundNodes().filter{it.isVisibleToUser}.map{ui.label(it)}.filter{it.isNotBlank()} else emptyList()
    private fun node(match:(String)->Boolean)=ui.foregroundNodes().firstOrNull{it.isVisibleToUser&&match(ui.label(it))}
    private suspend fun tapNode(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
        val r=android.graphics.Rect();n.getBoundsInScreen(r)
        // ANA's WebView buttons report ACTION_CLICK success without acting; tap the observed bounds.
        return r.width()>0&&r.height()>0&&ui.tap(r.centerX(),r.centerY())
    }
    private suspend fun evidence(run:String,tag:String){runCatching{Evidence.write(svc,"medal_"+run+"_"+tag+".txt","dateJst="+TokyoDate.of()+"\n"+ui.dump())}}

    /** Never throws and never blocks the routine: four minutes at most, then ANA is left on its home. */
    suspend fun runOnce(run:String) {
        val date=TokyoDate.of()
        if(!MedalVideoPolicy.mayStart(MedalVideoStore.read(svc,date)?.optString("state")?.takeIf{it.isNotBlank()}))return
        try{
            val done=withTimeoutOrNull(4*60_000L){attempt(run,date)}
            if(done==null){
                val state=MedalVideoStore.read(svc,date)?.optString("state")
                MedalVideoStore.write(svc,date,if(state=="DISPATCHED")"UNCONFIRMED" else "FAILED","step_timeout_4min")
            }
        }catch(e:kotlinx.coroutines.CancellationException){
            // The run itself was cancelled (stop or watchdog): record, then let the cancellation through.
            val state=MedalVideoStore.read(svc,date)?.optString("state")
            runCatching{MedalVideoStore.write(svc,date,if(state=="DISPATCHED")"UNCONFIRMED" else "FAILED","cancelled")}
            throw e
        }catch(e:Exception){
            val state=MedalVideoStore.read(svc,date)?.optString("state")
            runCatching{MedalVideoStore.write(svc,date,if(state=="DISPATCHED")"UNCONFIRMED" else "FAILED","error:"+(e.message?:e.javaClass.simpleName).take(120))}
        }finally{
            runCatching{withTimeoutOrNull(20_000L){backToHome()}}
        }
    }

    private suspend fun backToHome(){
        repeat(4){
            ui.refreshCache()
            if(ui.currentPackage()==pkg&&MedalVideoPolicy.counter(labels())!=null)return
            if(ui.currentPackage()!=pkg)ui.launchApp(pkg) else if(MedalVideoPolicy.badgePage(labels()))ui.back() else return
            delay(1500)
        }
    }

    /** ためる is also the earn page's title, so only the bottom navigation row counts. */
    private fun bottomTab(label:String)=ui.foregroundNodes().lastOrNull{n->val r=android.graphics.Rect();n.getBoundsInScreen(r)
        n.isVisibleToUser&&ui.label(n).trim()==label&&r.top>ui.screenSize().second*.8}
    private suspend fun attempt(run:String,date:String){
        ui.refreshCache()
        if(ui.currentPackage()!=pkg){ui.launchApp(pkg);delay(2500)}
        var before:Int?=null
        // The chip is only on home. On the earn page ANA still marks ホーム as selected and ignores a tap on it; ためる, then ホーム, works.
        repeat(12){i->if(before==null){ui.refreshCache();before=MedalVideoPolicy.counter(labels())
            if(before==null){if(i%3==0){bottomTab("ためる")?.let{tapNode(it);delay(1500)};ui.refreshCache();bottomTab("ホーム")?.let{tapNode(it)}};delay(800)}}}
        val chip=node{MedalVideoPolicy.chipLabel(it)}
        if(before==null||chip==null){MedalVideoStore.write(svc,date,"FAILED_BEFORE_TAP","home_badge_chip_not_found",run=run);evidence(run,"no_chip");return}
        if(before!!>=50){MedalVideoStore.write(svc,date,"NOT_OFFERED","badge_already_50",before,run=run);return}
        tapNode(chip);delay(2500)
        var button:android.view.accessibility.AccessibilityNodeInfo?=null
        repeat(8){i->
            if(button!=null)return@repeat
            ui.refreshCache();val l=labels()
            if(MedalVideoPolicy.claimed(l)){MedalVideoStore.write(svc,date,"ALREADY_CLAIMED","claimed_text_on_badge_page",before,run=run);return}
            button=node{MedalVideoPolicy.offerButton(it)}?:ui.foregroundNodes().filter{it.isVisibleToUser&&ui.label(it).isNotBlank()}
                .zipWithNext().firstOrNull{(a,b)->MedalVideoPolicy.offerParts(ui.label(a),ui.label(b))}?.first
            if(button==null){
                if(i==0&&!MedalVideoPolicy.badgePage(l)){delay(1500);return@repeat}
                val (w,h)=ui.screenSize();ui.swipe(w/2,(h*0.7).toInt(),w/2,(h*0.35).toInt(),400);delay(1200)
            }
        }
        val b=button
        if(b==null){MedalVideoStore.write(svc,date,"NOT_OFFERED","video_button_not_found",before,run=run);evidence(run,"no_button");return}
        // Recorded before the tap: from here an unconfirmed result is kept, never replayed.
        MedalVideoStore.write(svc,date,"DISPATCHED","video_button_tapped",before,run=run)
        if(!tapNode(b)){MedalVideoStore.write(svc,date,"FAILED_BEFORE_TAP","tap_not_dispatched",before,run=run);return}
        delay(3000);close.beginFlow()
        val until=android.os.SystemClock.elapsedRealtime()+180_000L
        var left=false
        while(android.os.SystemClock.elapsedRealtime()<until){
            close.restoreAdAfterStore();ui.refreshCache()
            val inAd=ui.currentPackage()!=pkg||close.isRecoverableSurface()
            if(inAd)left=true
            val l=labels()
            if(!inAd&&left&&(MedalVideoPolicy.counter(l)!=null||MedalVideoPolicy.badgePage(l)))break
            // Close controls are looked for only on an ad or store surface, never on ANA's own pages.
            if(!inAd){delay(1000);continue}
            val all=ui.foregroundNodes().filter{it.isVisibleToUser}.map{ui.label(it)}
            val resume=node{MedalVideoPolicy.resumeLabel(it)}
            if(resume!=null){ui.click(resume);delay(1500);continue}
            if(MedalVideoPolicy.rewardPending(all)){delay(1500);continue}
            var x=close.identifyClose()
            if(x==null&&close.revealIfNeeded())x=close.identifyClose()
            if(x!=null){
                val r=close.controlBounds(x)
                if(close.observedTapOnly&&r!=null&&r.width()>0&&r.height()>0)ui.tap(r.centerX(),r.centerY()) else ui.click(x)
                delay(2000)
            }else delay(1500)
        }
        // Read the result: the claimed text on the badge page, or the home chip one higher.
        var after:Int?=null;var claimedText=false
        repeat(8){
            if(after!=null||claimedText)return@repeat
            ui.refreshCache();val l=labels()
            after=MedalVideoPolicy.counter(l);claimedText=MedalVideoPolicy.claimed(l)
            if(after==null&&!claimedText)delay(1000)
        }
        if(MedalVideoPolicy.confirmed(before,after,claimedText))MedalVideoStore.write(svc,date,"CONFIRMED","reward_observed",before,after,run)
        else {MedalVideoStore.write(svc,date,"UNCONFIRMED","returned_without_receipt",before,after,run);evidence(run,"unconfirmed")}
    }
}
