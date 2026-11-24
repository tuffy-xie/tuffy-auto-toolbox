package com.anapocket.auto

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How far ahead a claim deadline may lie for the card to be claimed now (user, 11/23: judged by the deadline). */
enum class RewardRange(val days:Int?,val label:String){
    TODAY(0,"今天到期"),WEEK(7,"7天内到期"),MONTH(30,"30天内到期"),ALL(null,"全部");
    companion object{fun of(name:String?)=values().firstOrNull{it.name==name}?:TODAY}
}

/**
 * ANA ためる → チャレンジ → 参加中 → 報酬未受取, observed 11/23 22:00 on the phone:
 * - each card: text_name, text_status 「11/24まで報酬を受け取りましょう！」, text_period 「参加日：2026/10/25」;
 *   the list runs from the oldest participation (earliest deadline) to the newest, with MAX native ads in between;
 * - the card opens a sheet: button_receive 「報酬を受け取る」, text_period 「受取期限：2026/11/24」;
 * - 報酬を受け取る opens 「受け取りたい報酬を選んでください」 with button_reward_ad (「動画を見て」 「250pt」) or
 *   button_reward (「200pt」); the ad option played a Google rewarded ad, then 「報酬を受け取りました おつかれさまでした!」
 *   with an OK (close_button), and the sheet turned to text_status 「報酬受け取り済み」. Points went 35,350 → 35,600.
 */
object RewardClaimPolicy {
    private fun n(s:String)=s.replace(Regex("[\\s　]"),"").map{c->if(c in '０'..'９')'0'+(c-'０') else c}.joinToString("")
    private val cardStatus=Regex("^(\\d{1,2})/(\\d{1,2})まで報酬を受け取りましょう[！!]?$")
    private val detailPeriod=Regex("^受取期限[：:](\\d{4})/(\\d{1,2})/(\\d{1,2})$")

    /** The card's deadline. It carries no year: the one nearest to today is meant (a December list read in January). */
    fun cardDeadline(status:String,today:LocalDate):LocalDate? {
        val m=cardStatus.matchEntire(n(status))?:return null
        val d=runCatching{LocalDate.of(today.year,m.groupValues[1].toInt(),m.groupValues[2].toInt())}.getOrNull()?:return null
        return when{d.isBefore(today.minusDays(180))->d.plusYears(1);d.isAfter(today.plusDays(185))->d.minusYears(1);else->d}
    }
    fun detailDeadline(label:String):LocalDate? = detailPeriod.matchEntire(n(label))?.let{m->
        runCatching{LocalDate.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt())}.getOrNull()}
    fun daysLeft(deadline:LocalDate,today:LocalDate)=ChronoUnit.DAYS.between(today,deadline)
    fun inRange(deadline:LocalDate,today:LocalDate,range:RewardRange):Boolean {
        val d=daysLeft(deadline,today);return d>=0&&(range.days==null||d<=range.days)
    }
    /** Past the range. The list is ordered by deadline, so a page of only these ends the search. */
    fun beyond(deadline:LocalDate,today:LocalDate,range:RewardRange)=range.days!=null&&daysLeft(deadline,today)>range.days
    fun claimed(status:String)=n(status)=="報酬受け取り済み"
    fun receiptDialog(labels:List<String>)=labels.any{n(it).startsWith("報酬を受け取りました")}
    fun choiceSheet(labels:List<String>)=labels.any{n(it)=="受け取りたい報酬を選んでください"}
    fun receiveButton(label:String)=n(label)=="報酬を受け取る"
    fun points(label:String)=Regex("^([\\d,]{1,7})pt$").matchEntire(n(label))?.groupValues?.get(1)?.replace(",","")?.toIntOrNull()
    /** Plain points are taken instead of the ad only when the ad cannot be had and the reward would expire today. */
    fun plainFallback(adOffered:Boolean,adAttempts:Int,daysLeft:Long)=daysLeft==0L&&(!adOffered||adAttempts>=MAX_AD_ATTEMPTS)
    const val MAX_AD_ATTEMPTS=2
    /** Only the user's reward switch: with no travel category and no daily ads, the run is for the rewards alone. */
    fun rewardsOnly(s:AppSettings)=s.rewardEnabled&&!s.adEnabled&&Cat.values().none{s.catOn(it)}
    /** Nothing open is left behind: no MAX dialog, safety check, daily viewing in flight, unresolved travel ad or ad on screen. */
    fun mayRun(maxBoost:Boolean,safety:Boolean,openDaily:Boolean,openTravel:Boolean,adOnScreen:Boolean)=
        !maxBoost&&!safety&&!openDaily&&!openTravel&&!adOnScreen
}

/** 11/24: a login offer that appears after STARTUP gave up blocks the travel scan; it is handled once, then the scan reruns. */
object LateLoginPolicy {
    private val reasons=listOf("startup_popup_blocks_category_navigation","category_popup_blocks_navigation")
    fun retry(detail:String,loginState:String?,openDaily:Boolean,challengesOnly:Boolean)=
        !challengesOnly&&!openDaily&&(loginState==null||loginState=="NOT_PRESENT")&&reasons.any{detail.contains(it)}
}

object RewardClaimStore {
    private fun sp(c:Context)=c.getSharedPreferences("ana_reward_claim_v1",Context.MODE_PRIVATE)
    @Synchronized fun read(c:Context,date:String=TokyoDate.of()):JSONObject?=sp(c).getString(date,null)?.let{JSONObject(it)}
    @Synchronized fun update(c:Context,date:String,change:(JSONObject)->Unit):JSONObject{
        val row=read(c,date)?:JSONObject().put("date",date).put("items",JSONArray())
        change(row);row.put("updatedAt",System.currentTimeMillis())
        val items=row.optJSONArray("items")?:JSONArray()
        if(items.length()>400){val keep=JSONArray();for(i in items.length()-400 until items.length())keep.put(items.get(i));row.put("items",keep)}
        check(sp(c).edit().putString(date,row.toString()).commit()){"reward_state_write_failed"}
        return row
    }
    fun item(c:Context,date:String,key:String,state:String,points:Int?=null,reason:String=""){
        update(c,date){row->
            row.getJSONArray("items").put(JSONObject().put("key",key).put("state",state).put("at",System.currentTimeMillis())
                .put("points",points?:JSONObject.NULL).put("reason",reason))
            if(state=="CLAIMED_AD")row.put("claimedAd",row.optInt("claimedAd")+1)
            if(state=="CLAIMED_PLAIN")row.put("claimedPlain",row.optInt("claimedPlain")+1)
            if(state in setOf("CLAIMED_AD","CLAIMED_PLAIN"))row.put("points",row.optInt("points")+(points?:0))
        }
        Logger.i("REWARD","date=$date state=$state points=$points key=${key.take(120)} $reason")
    }
    fun claimed(row:JSONObject?)=(row?.optInt("claimedAd")?:0)+(row?.optInt("claimedPlain")?:0)
    /** Finished for today only when the last pass with this very range found nothing left in it. */
    fun done(c:Context,date:String,range:RewardRange)=read(c,date)?.let{it.optString("state")=="DONE"&&it.optString("range")==range.name}==true
    fun label(c:Context,date:String,s:AppSettings):String {
        if(!s.rewardEnabled)return "未开启"
        val row=read(c,date);val range=RewardRange.of(s.rewardRange)
        val n=claimed(row)
        return range.label+" · "+when{done(c,date,range)->"已领 $n 条";n>0->"已领 $n 条";row?.optString("state")=="PARTIAL"->"未领完";else->"待领取"}
    }
}

private data class RewardCard(val key:String,val title:String,val deadline:LocalDate,val tapX:Int,val tapY:Int)

/**
 * Claims finished challenges' rewards, ad option first, one card at a time. ANA's own sheet is the only receipt:
 * a reward can be received once, so a card still offering 報酬を受け取る after a failed ad is safe to try again.
 * Never throws (except cancellation), never blocks the day: what is left stays for the next run.
 */
class RewardClaimTask(private val svc:AutoAccessibilityService) {
    private val ui=Ui(svc)
    private val pkg="us.ana_pocket.ANAPocket"
    private val close=AdUiClose(svc)
    private fun nodes()=if(ui.currentPackage()==pkg)ui.foregroundNodes().filter{it.isVisibleToUser} else emptyList()
    private fun labels()=nodes().map{ui.label(it)}.filter{it.isNotBlank()}
    private fun byId(id:String)=nodes().firstOrNull{it.isEnabled&&it.viewIdResourceName=="$pkg:id/$id"}
    private fun text(id:String)=byId(id)?.let{ui.label(it)}
    private fun stopped(date:String)=AutoAccessibilityService.stopRequested||!AutoAccessibilityService.isConnected()||TokyoDate.of()!=date
    private suspend fun tapNode(n:AccessibilityNodeInfo):Boolean{val r=Rect();n.getBoundsInScreen(r);return r.width()>0&&r.height()>0&&ui.tap(r.centerX(),r.centerY())}
    private suspend fun waitFor(ms:Long,date:String,ok:()->Boolean):Boolean{
        val until=SystemClock.elapsedRealtime()+ms
        while(SystemClock.elapsedRealtime()<until&&!stopped(date)){ui.refreshCache();if(ok())return true;delay(400)}
        ui.refreshCache();return ok()
    }
    private fun evidence(run:String,tag:String){runCatching{Evidence.write(svc,"reward_"+run+"_"+tag+"_"+System.currentTimeMillis()+".txt","JST="+TokyoDate.time()+"\n"+ui.dump())}}

    suspend fun run(run:String,date:String,range:RewardRange):RoutineStepResult {
        val today=LocalDate.parse(date)
        RewardClaimStore.update(svc,date){it.put("range",range.name).put("state","RUNNING").put("runId",run)}
        val before=RewardClaimStore.claimed(RewardClaimStore.read(svc,date))
        // failed: given up this run; handled: claimed or found claimed, kept out even if the list is slow to refresh.
        val failed=mutableSetOf<String>();val handled=mutableSetOf<String>();val adAttempts=mutableMapOf<String,Int>()
        var outcome="PARTIAL";var reason=""
        try{
            val until=SystemClock.elapsedRealtime()+BUDGET_MS
            var claims=0
            while(true){
                if(stopped(date)){reason="stopped";break}
                if(SystemClock.elapsedRealtime()>until){reason="time_budget";break}
                if(claims>=MAX_CLAIMS){reason="claim_cap";break}
                if(!AccountSafety.authorize(svc,"reward_claim")){reason="account_safety";break}
                if(!openUnclaimedList(date)){reason="unclaimed_list_unreachable";evidence(run,"list_unreachable");break}
                val target=findTarget(date,today,range,failed+handled)
                if(target==null){outcome="DONE";break}
                RunForegroundService.start(svc,"领取报酬 · "+target.title.take(20))
                when(val r=claim(run,date,today,target,adAttempts.getOrDefault(target.key,0))){
                    "CLAIMED_AD","CLAIMED_PLAIN","ALREADY"->{claims++;handled.add(target.key)}
                    "AD_FAILED"->{adAttempts[target.key]=adAttempts.getOrDefault(target.key,0)+1
                        if(adAttempts.getValue(target.key)>=RewardClaimPolicy.MAX_AD_ATTEMPTS&&RewardClaimPolicy.daysLeft(target.deadline,today)>0)failed.add(target.key)}
                    "AD_STUCK"->{reason="ad_stuck";break}
                    else->{failed.add(target.key);RewardClaimStore.item(svc,date,target.key,"FAILED",reason=r)}
                }
            }
        }catch(e:kotlinx.coroutines.CancellationException){
            runCatching{RewardClaimStore.update(svc,date){it.put("state","PARTIAL").put("detail","cancelled")}};throw e
        }catch(e:Exception){
            reason="error:"+(e.message?:e.javaClass.simpleName).take(100);evidence(run,"error")
        }
        if(failed.isNotEmpty()&&outcome=="DONE"){outcome="PARTIAL";reason="failed_cards="+failed.size}
        val row=RewardClaimStore.update(svc,date){it.put("state",outcome).put("detail",reason)}
        val now=RewardClaimStore.claimed(row)-before
        Logger.i("REWARD","run=$run range=$range outcome=$outcome claimedThisRun=$now total=${RewardClaimStore.claimed(row)} reason=$reason")
        runCatching{leaveToList(date)}
        val text=range.label+"：本次领取 $now 条，今日共 "+RewardClaimStore.claimed(row)+" 条（看广告 "+row.optInt("claimedAd")+"）"
        return if(outcome=="DONE")RoutineStepResult("DONE",text)
            else RoutineStepResult("PARTIALLY_COMPLETED",text+"；未领完："+TaskFailureText.short(reason))
    }

    /** Back to the list, closing a sheet or dialog that is still up. Never touches an ad. */
    private suspend fun leaveToList(date:String){
        repeat(3){
            ui.refreshCache()
            byId("close_button")?.let{tapNode(it);delay(1200);return@repeat}
            // 11/24 14:58: ANA's own 「受け取りたい報酬を選んでください」 sheet has no close control; Back closes it (no ad is up).
            if(ui.currentPackage()==pkg&&!close.isRecoverableSurface()&&RewardClaimPolicy.choiceSheet(labels())){ui.back();delay(1200);return@repeat}
            if(byId("button_receive")!=null||byId("text_attended")!=null){byId("close")?.let{tapNode(it);delay(1200)}}
        }
    }

    private fun bottomTab(label:String)=nodes().lastOrNull{n->val r=Rect();n.getBoundsInScreen(r);ui.label(n).trim()==label&&r.top>ui.screenSize().second*.8}
    private fun topLabel(label:String,maxTop:Int)=nodes().firstOrNull{n->val r=Rect();n.getBoundsInScreen(r);ui.label(n).trim()==label&&r.top<maxTop}
    private fun onChallengeList()=labels().let{l->l.contains("参加可能")&&l.contains("参加中")&&l.contains("履歴")}
    private fun unclaimedChip()=nodes().firstOrNull{ui.label(it).trim()=="報酬未受取"}
    private fun list()=nodes().firstOrNull{it.viewIdResourceName=="$pkg:id/list"}

    /** ためる → チャレンジ → 参加中 → 報酬未受取, each step checked on the fresh tree. */
    private suspend fun openUnclaimedList(date:String):Boolean {
        repeat(4){attempt->
            if(stopped(date))return false
            ui.refreshCache()
            if(ui.currentPackage()!=pkg){ui.launchApp(pkg);waitFor(8000,date){ui.currentPackage()==pkg};delay(1500);ui.refreshCache()}
            // A sheet or dialog left from an earlier card.
            if(byId("close_button")!=null||byId("button_receive")!=null||byId("text_attended")!=null||RewardClaimPolicy.choiceSheet(labels())){
                leaveToList(date);ui.refreshCache()
            }
            if(!onChallengeList()){
                bottomTab("ためる")?.let{tapNode(it);waitFor(4000,date){topLabel("チャレンジ",320)!=null}}
                topLabel("チャレンジ",320)?.let{tapNode(it);waitFor(4000,date){onChallengeList()}}
            }
            if(!onChallengeList()){if(attempt>=1)ui.back();delay(1500);return@repeat}
            nodes().firstOrNull{ui.label(it).trim()=="参加中"}?.let{tapNode(it)}
            if(!waitFor(4000,date){unclaimedChip()!=null}){delay(800);return@repeat}
            unclaimedChip()?.let{tapNode(it)};delay(1500)
            if(waitFor(5000,date){listVerified()}){scrollTop(date);return true}
        }
        return false
    }
    /** The 報酬未受取 filter: every card on it asks to receive by a date, or ANA shows no card at all. */
    private fun listVerified():Boolean {
        if(list()==null||unclaimedChip()==null)return false
        val statuses=nodes().filter{it.viewIdResourceName=="$pkg:id/text_status"}.map{ui.label(it)}
        return statuses.isEmpty()&&nodes().none{it.viewIdResourceName=="$pkg:id/text_name"}||
            statuses.isNotEmpty()&&statuses.all{it.contains("まで報酬を受け取りましょう")}
    }
    private suspend fun scrollTop(date:String){
        repeat(80){if(stopped(date))return;val l=list()?:return;if(!l.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return;delay(150);ui.refreshCache()}
    }

    private fun cards(today:LocalDate):List<RewardCard> {
        val l=list()?:return emptyList();val lr=Rect();l.getBoundsInScreen(lr)
        // ANA's floating notice banner (30,1328-690,1424 on 11/23) sits over the list's lower edge and is clickable.
        val covers=nodes().filter{it.viewIdResourceName?.substringAfterLast('/') in setOf("banner","banner_ad_layout")}.map{val r=Rect();it.getBoundsInScreen(r);r}
        return nodes().filter{it.viewIdResourceName=="$pkg:id/text_name"}.mapNotNull{name->
            val card=generateSequence(name){it.parent}.drop(1).take(4).firstOrNull{it.isClickable}?:return@mapNotNull null
            val f=descendants(card)
            val status=f.firstOrNull{it.viewIdResourceName=="$pkg:id/text_status"}?.let{ui.label(it)}?:return@mapNotNull null
            val joined=f.firstOrNull{it.viewIdResourceName=="$pkg:id/text_period"}?.let{ui.label(it)}.orEmpty()
            val deadline=RewardClaimPolicy.cardDeadline(status,today)?:return@mapNotNull null
            val r=Rect();name.getBoundsInScreen(r)
            if(r.top<lr.top||r.bottom>lr.bottom||r.width()<=0||covers.any{it.contains(r.centerX(),r.centerY())})return@mapNotNull null
            val title=ui.label(name).trim()
            RewardCard(title+"|"+joined+"|"+deadline,title,deadline,r.centerX(),r.centerY())
        }
    }
    private fun descendants(root:AccessibilityNodeInfo):List<AccessibilityNodeInfo>{
        val out=mutableListOf<AccessibilityNodeInfo>();val q=ArrayDeque(listOf(root to 0))
        while(q.isNotEmpty()&&out.size<80){val(n,d)=q.removeFirst();out.add(n);if(d<5)for(i in 0 until n.childCount)n.getChild(i)?.let{q.add(it to d+1)}}
        return out
    }
    private fun signature()=nodes().filter{it.viewIdResourceName=="$pkg:id/text_name"}.joinToString("|"){val r=Rect();it.getBoundsInScreen(r);ui.label(it)+"@"+r.top}

    private suspend fun findTarget(date:String,today:LocalDate,range:RewardRange,failed:Set<String>):RewardCard? {
        var same=0;var previous=""
        repeat(150){
            if(stopped(date))return null
            ui.refreshCache()
            val visible=cards(today)
            visible.firstOrNull{RewardClaimPolicy.inRange(it.deadline,today,range)&&it.key !in failed}?.let{return it}
            if(visible.isNotEmpty()&&visible.all{RewardClaimPolicy.beyond(it.deadline,today,range)})return null
            val sig=signature();same=if(sig==previous)same+1 else 0;previous=sig
            if(same>=3)return null
            val l=list()?:return null
            if(!l.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)){
                val(w,h)=ui.screenSize();ui.swipe(w/2,(h*.75).toInt(),w/2,(h*.4).toInt(),400)
            }
            delay(600)
        }
        return null
    }

    /** One card: CLAIMED_AD, CLAIMED_PLAIN, ALREADY, AD_FAILED, AD_STUCK or a failure reason. */
    private suspend fun claim(run:String,date:String,today:LocalDate,card:RewardCard,adAttempts:Int):String {
        ui.tap(card.tapX,card.tapY)
        if(!waitFor(8000,date){byId("button_receive")!=null||text("text_status")?.let{RewardClaimPolicy.claimed(it)}==true})return "detail_not_opened"
        delay(500);ui.refreshCache()
        val deadline=text("text_period")?.let{RewardClaimPolicy.detailDeadline(it)}
        if(text("text_name")?.trim()!=card.title||deadline!=card.deadline){evidence(run,"identity");leaveToList(date);return "detail_identity_mismatch"}
        if(text("text_status")?.let{RewardClaimPolicy.claimed(it)}==true){RewardClaimStore.item(svc,date,card.key,"ALREADY");leaveToList(date);return "ALREADY"}
        val receive=byId("button_receive")?:return "receive_button_missing"
        if(!RewardClaimPolicy.receiveButton(ui.label(receive))){leaveToList(date);return "receive_button_changed"}
        tapNode(receive)
        if(!waitFor(6000,date){byId("button_reward_ad")!=null||byId("button_reward")!=null}){evidence(run,"no_choice");leaveToList(date);return "choice_not_shown"}
        delay(400);ui.refreshCache()
        val adButton=byId("button_reward_ad");val plain=byId("button_reward")
        val adPoints=byId("button_reward_ad_point_text")?.let{RewardClaimPolicy.points(ui.label(it))}
        val plainPoints=byId("button_reward_point_text")?.let{RewardClaimPolicy.points(ui.label(it))}
        val daysLeft=RewardClaimPolicy.daysLeft(card.deadline,today)
        val takePlain=RewardClaimPolicy.plainFallback(adButton!=null,adAttempts,daysLeft)
        if(adButton==null||takePlain){
            if(plain==null||!takePlain){
                // No ad offered for a card that does not expire today: leave it for a later run.
                leaveToList(date);return "ad_option_missing"
            }
            tapNode(plain)
            val ok=afterReward(date)
            if(ok){RewardClaimStore.item(svc,date,card.key,"CLAIMED_PLAIN",plainPoints,"ad_unavailable_expires_today")}
            leaveToList(date);return if(ok)"CLAIMED_PLAIN" else "plain_not_confirmed"
        }
        if(!AccountSafety.authorize(svc,"reward_claim_ad")){leaveToList(date);return "account_safety"}
        RewardClaimStore.item(svc,date,card.key,"AD_DISPATCHED",adPoints)
        tapNode(adButton)
        var watched=watchAd(run,date)
        // 11/24 14:58: 「動画を見て 500pt」 was tapped, no ad came up in 20 s and the choice sheet stayed. ANA never left its own
        // page, so nothing was viewed; the same button is pressed once more after a pause.
        if(watched=="NOT_STARTED"&&RewardClaimPolicy.choiceSheet(labels())){
            delay(3000);ui.refreshCache()
            byId("button_reward_ad")?.let{Logger.i("REWARD","run=$run ad_not_started; choice sheet still shown; one more press");tapNode(it);watched=watchAd(run,date)}
        }
        if(watched=="STUCK"){evidence(run,"ad_stuck");FaultStore.capture(svc,run,"reward","ad_timeout_300s");return "AD_STUCK"}
        val ok=afterReward(date)
        if(ok){RewardClaimStore.item(svc,date,card.key,"CLAIMED_AD",adPoints);leaveToList(date);return "CLAIMED_AD"}
        RewardClaimStore.item(svc,date,card.key,"AD_NOT_CONFIRMED",adPoints,watched);evidence(run,"ad_not_confirmed")
        leaveToList(date)
        return "AD_FAILED"
    }

    /** The OK on 「報酬を受け取りました」, then the sheet's own 「報酬受け取り済み」. */
    private suspend fun afterReward(date:String):Boolean {
        val until=SystemClock.elapsedRealtime()+15_000L
        while(SystemClock.elapsedRealtime()<until&&!stopped(date)){
            ui.refreshCache()
            if(text("text_status")?.let{RewardClaimPolicy.claimed(it)}==true)return true
            val ok=if(RewardClaimPolicy.receiptDialog(labels()))byId("close_button") else null
            if(ok!=null){tapNode(ok);delay(1200)} else delay(600)
        }
        ui.refreshCache();return text("text_status")?.let{RewardClaimPolicy.claimed(it)}==true
    }

    /** The rewarded ad, closed only by the shared close recognizer. "RETURNED", "NOT_STARTED" or "STUCK". */
    private suspend fun watchAd(run:String,date:String):String {
        close.beginFlow()
        try{
            val start=SystemClock.elapsedRealtime();var left=false;var lastClose=0L
            while(SystemClock.elapsedRealtime()-start<300_000L){
                if(stopped(date))return "STOPPED"
                close.restoreAdAfterStore();ui.refreshCache()
                val inAd=ui.currentPackage()!=pkg||close.isRecoverableSurface()
                if(inAd)left=true
                if(!inAd){
                    val l=labels()
                    if(RewardClaimPolicy.receiptDialog(l)||text("text_status")?.let{RewardClaimPolicy.claimed(it)}==true)return "RETURNED"
                    if(AdSignals.error(l)){byId("close_button")?.let{tapNode(it)}?:ui.back();delay(1000);return "AD_ERROR"}
                    // The ad never opened, or ANA returned without its receipt.
                    if(!left&&SystemClock.elapsedRealtime()-start>20_000L)return "NOT_STARTED"
                    if(left&&SystemClock.elapsedRealtime()-start>8_000L&&(RewardClaimPolicy.choiceSheet(l)||byId("button_receive")!=null))return "RETURNED_WITHOUT_RECEIPT"
                    delay(1000);continue
                }
                val all=ui.foregroundNodes().filter{it.isVisibleToUser}.map{ui.label(it)}
                val resume=ui.foregroundNodes().firstOrNull{it.isVisibleToUser&&MedalVideoPolicy.resumeLabel(ui.label(it))}
                if(resume!=null){ui.click(resume);delay(1500);continue}
                if(MedalVideoPolicy.rewardPending(all)){delay(1500);continue}
                if(SystemClock.elapsedRealtime()-lastClose<2000L){delay(800);continue}
                var x=close.identifyClose()
                if(x==null&&close.revealIfNeeded())x=close.identifyClose()
                if(x!=null){
                    val r=close.controlBounds(x)
                    Logger.i("REWARD","run=$run close bounds=$r")
                    if(close.observedTapOnly&&r!=null&&r.width()>0&&r.height()>0)ui.tap(r.centerX(),r.centerY()) else ui.click(x)
                    lastClose=SystemClock.elapsedRealtime();delay(2000)
                }else delay(1200)
            }
            return if(ui.currentPackage()!=pkg||close.isRecoverableSurface())"STUCK" else "RETURNED"
        }finally{close.releaseVisualCache()}
    }

    companion object{
        const val BUDGET_MS=120*60_000L
        const val MAX_CLAIMS=400
    }
}
