package com.anapocket.auto

/** Pure completion protocol. No advertisement dispatch or ledger mutation is exposed by this port. */
enum class MaxBoostPage { MAX_DIALOG, RESUME_PROMPT, DETAIL, LIST, HOME, OTHER }
data class MaxBoostBinding(val date:String,val key:String,val cat:String,val title:String,val meters:Int,
    val adRun:String,val adIndex:Int,val adStarted:Long,val reward:Int,val adsDigest:String)
data class MaxBoostFrame(val page:MaxBoostPage,val windowId:Int,val signature:String,val trusted:Boolean=true,
    val controls:Int=1,val ready:Boolean=true,val reward:Int?=null)
data class MaxBoostClick(val method:String,val dispatched:Boolean,val completed:Boolean?,val detail:String="")
data class MaxBoostCompletionResult(val page:MaxBoostPage,val attempts:Int)
class MaxBoostCompletionFailure(val code:String,detail:String):IllegalStateException("$code:$detail")

object MaxBoostDialogPolicy {
    const val PACKAGE="us.ana_pocket.ANAPocket"
    const val ACTIVITY="us.ana_pocket.ANAPocket.views.home.HomeActivity"
    const val MAX_ATTEMPTS=3
    const val RESUME_COPY="ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？"
    fun nativeHost(eventClass:String)=eventClass in setOf(ACTIVITY,"androidx.activity.ComponentDialog","android.app.AlertDialog","androidx.appcompat.app.AlertDialog")
    fun resumeShape(pkg:String?,activity:String,foreground:Boolean,ids:Set<String>,labels:List<String>)=
        pkg==PACKAGE&&nativeHost(activity)&&foreground&&RESUME_COPY in labels&&"再開する" in labels&&"やめる" in labels&&
            setOf("android:id/message","android:id/button1","android:id/button2").all{it in ids}
    fun completedRestartAllowed(sameDay:Boolean,bound:Boolean,unresolved:Boolean,sdk:Boolean,used:Boolean,daily:Int,live:Boolean)=
        sameDay&&bound&&!unresolved&&!sdk&&!used&&daily<3&&live
    fun resumeIdentity(fields:Map<String,String>,binding:MaxBoostBinding)=
        fields["text_name"]==binding.title&&AnaCalendarDate.same(fields["text_date"],binding.date)&&
            fields["text_attended"]?.removePrefix("参加日：")?.let{AnaCalendarDate.same(it,binding.date)}==true
    fun finalResumeAllowed(bound:Boolean,sameDay:Boolean,unresolved:Boolean,sdk:Boolean,used:Boolean,visibleIdentity:Boolean)=
        bound&&sameDay&&!unresolved&&!sdk&&!used&&visibleIdentity
    const val DISMISS_MS=18_000L
    fun hasCopy(labels:List<String>):Boolean {
        val text=labels.joinToString("").replace(Regex("\\s+"),"")
        return text.contains("チャレンジ達成後の報酬が最大値に到達しました！") ||
            text.contains("チャレンジ達成後の報酬が最大値に到達しました!")
    }
    fun nativeShape(pkg:String?,activity:String,foreground:Boolean,ids:Set<String>,labels:List<String>):Boolean =
        pkg==PACKAGE&&nativeHost(activity)&&foreground&&hasCopy(labels)&&
            listOf("title_count","current_point","boost_list","button_no","button_no_text").all{PACKAGE+":id/"+it in ids}
    fun savedTask(date:String,today:String,key:String,cat:String,title:String,meters:Int,joined:Boolean,boost:Int,
        state:String,adRun:String,index:Int,start:Long,lastConfirmed:Boolean,reward:Int):Boolean =
        date==today&&key==cat+"|"+title&&cat in setOf("WALK","BIKE","CAR","MOV_ANY","SHINKANSEN","FLIGHT")&&
            title.isNotBlank()&&meters>0&&joined&&boost==5&&state in setOf("BOOST_MAX_CONFIRMED","JOINED")&&
            adRun.startsWith("CH")&&index>0&&start>0&&lastConfirmed&&reward>0
    fun action(number:Int,containerClickable:Boolean,textClickable:Boolean):String =
        if(number==1&&containerClickable)"NATIVE_CONTAINER" else if(number==1&&textClickable)"NATIVE_TEXT" else "GESTURE"
    fun classify(pkg:String?,activity:String,foreground:Boolean,ids:Set<String>,labels:List<String>,fields:Map<String,String>,binding:MaxBoostBinding?):MaxBoostPage {
        if(pkg!=PACKAGE||!foreground)return MaxBoostPage.OTHER
        val localIds=ids.map{it.substringAfterLast('/')}.toSet()
        val detail=HomeNavigationPolicy.nativeDetail(pkg,labels,localIds)
        // Restoring an existing ANA task may leave our MainActivity as the last event class.
        // Only the exact saved native detail can override that stale controller event.
        if(!nativeHost(activity))return if(activity=="com.anapocket.auto.MainActivity"&&binding!=null&&detail&&
            resumeIdentity(fields,binding)&&!hasCopy(labels)&&RESUME_COPY !in labels&&
            ids.none{it=="android:id/message"||it.startsWith("android:id/button")})MaxBoostPage.DETAIL else MaxBoostPage.OTHER
        if(resumeShape(pkg,activity,foreground,ids,labels))return MaxBoostPage.RESUME_PROMPT
        if(hasCopy(labels))return MaxBoostPage.MAX_DIALOG
        if(detail)return if(binding==null||
            fields["text_name"]==binding.title&&AnaCalendarDate.same(fields["text_date"],binding.date))MaxBoostPage.DETAIL else MaxBoostPage.OTHER
        if(ids.any{it in setOf("android:id/message","android:id/button1","android:id/button2",PACKAGE+":id/image_close",PACKAGE+":id/title_count",PACKAGE+":id/title_join",PACKAGE+":id/button_no",PACKAGE+":id/button_no_text")})return MaxBoostPage.OTHER
        if(labels.any{it in setOf("参加可能","参加中","履歴")}&&ids.any{it==PACKAGE+":id/list"||it==PACKAGE+":id/category_filter_compose_view"})return MaxBoostPage.LIST
        if(HomeNavigationPolicy.nativeHome(pkg,labels))return MaxBoostPage.HOME
        return MaxBoostPage.OTHER
    }
    fun blocksRoutine(result:String,pending:Boolean)=pending||result.contains("MAX_BOOST_")
    fun safePost(page:MaxBoostPage)=page in setOf(MaxBoostPage.DETAIL,MaxBoostPage.LIST,MaxBoostPage.HOME)
    fun minimalFits(existing:Long,bytes:Int)=existing>=0&&bytes in 1..8192&&existing+bytes<=64L*1024
}
interface MaxBoostCompletionPort {
    fun now():Long
    fun allowed():Boolean
    suspend fun ensureDurableBinding()
    fun persistedAttempts():Int
    fun persistedResumeRequested():Boolean = false
    suspend fun frame():MaxBoostFrame
    suspend fun reserveAttempt(number:Int)
    suspend fun click(frame:MaxBoostFrame,number:Int):MaxBoostClick
    suspend fun actionResult(number:Int,result:MaxBoostClick)
    suspend fun resumeFinalDialog():Boolean = false
    suspend fun dismissed(page:MaxBoostPage)
    suspend fun restoreCategory(cat:String,page:MaxBoostPage):Boolean
    suspend fun routed()
    suspend fun failure(code:String,detail:String)
    suspend fun pause(ms:Long)
}
class MaxBoostCompletionFlow(private val binding:MaxBoostBinding,private val port:MaxBoostCompletionPort) {
    private suspend fun fail(code:String,detail:String):Nothing {
        port.failure(code,detail);throw MaxBoostCompletionFailure(code,detail)
    }
    suspend fun run():MaxBoostCompletionResult {
        port.ensureDurableBinding() // A failed durable save must prevent every click.
        var attempts=port.persistedAttempts();val start=port.now()
        var signature="";var stableSince=start;var lastClick=-10_000L;var otherSince:Long?=null;var resumed=port.persistedResumeRequested()
        while(port.now()-start<MaxBoostDialogPolicy.DISMISS_MS){
            if(!port.allowed())fail("MAX_BOOST_DIALOG_DISMISS_FAILED","stopped_date_permission_or_safety_changed")
            val f=port.frame()
            if(f.signature!=signature){signature=f.signature;stableSince=port.now()}
            when(f.page){
                MaxBoostPage.MAX_DIALOG -> {
                    otherSince=null
                    if(!f.trusted||f.reward!=binding.reward)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","window_or_bound_reward_mismatch")
                    if(f.controls>1)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","ambiguous_ok_controls")
                    if(f.controls==1&&f.ready&&port.now()-stableSince>=300&&port.now()-lastClick>=2500){
                        if(attempts>=MaxBoostDialogPolicy.MAX_ATTEMPTS)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","attempt_budget_exhausted")
                        if(port.now()-start>MaxBoostDialogPolicy.DISMISS_MS-3500)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","insufficient_time_for_bounded_gesture")
                        attempts++;port.reserveAttempt(attempts)
                        val outcome=port.click(f,attempts);port.actionResult(attempts,outcome)
                        // Dispatch/callback success is only telemetry. The next native page is the postcondition.
                        lastClick=port.now();signature=""
                    }
                }
                MaxBoostPage.RESUME_PROMPT -> {
                    otherSince=null
                    if(!f.trusted)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","untrusted_final_resume_window")
                    if(port.now()-stableSince>=300){
                        if(resumed||!port.resumeFinalDialog())fail("MAX_BOOST_DIALOG_DISMISS_FAILED","final_resume_identity_or_dispatch_not_verified")
                        resumed=true;signature="";port.pause(650)
                    }
                }
                MaxBoostPage.DETAIL,MaxBoostPage.LIST,MaxBoostPage.HOME -> {
                    otherSince=null
                    if(!f.trusted)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","untrusted_post_window")
                    if(port.now()-stableSince>=300){
                        if(resumed&&f.page!=MaxBoostPage.DETAIL)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","resumed_task_detail_identity_not_verified")
                        port.dismissed(f.page)
                        if(!port.restoreCategory(binding.cat,f.page))fail("MAX_BOOST_CATEGORY_RESTORE_FAILED","native_category_or_today_not_verified")
                        if(!port.allowed())fail("MAX_BOOST_CATEGORY_RESTORE_FAILED","stopped_date_permission_or_safety_changed_before_continuation")
                        port.routed();return MaxBoostCompletionResult(f.page,attempts)
                    }
                }
                MaxBoostPage.OTHER -> {
                    if(otherSince==null)otherSince=port.now()
                    if(port.now()-otherSince!!>=3000)fail("MAX_BOOST_DIALOG_DISMISS_FAILED","unknown_post_page_no_back_or_relaunch")
                }
            }
            port.pause(250)
        }
        fail("MAX_BOOST_DIALOG_DISMISS_FAILED","bounded_wait_expired")
    }
}
