package com.anapocket.auto

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.security.MessageDigest

/** Navigation-only journal. Existing challenge rows and every advertisement remain read-only. */
object MaxBoostCompletionStore {
    private const val AREA="max_boost_completion_v1"
    fun fromRow(row:JSONObject,today:String):MaxBoostBinding? {
        val ads=row.optJSONArray("ads")?:return null;val ad=ads.optJSONObject(ads.length()-1)?:return null
        if(ad.optInt("index")!=ads.length()||!MaxBoostDialogPolicy.savedTask(row.optString("date"),today,row.optString("key"),row.optString("cat"),
            row.optString("title"),row.optInt("meters"),row.optBoolean("joined"),row.optInt("boost"),row.optString("state"),
            ad.optString("runId"),ad.optInt("index"),ad.optLong("startedAt"),ad.optBoolean("confirmed"),row.optInt("offeredReward")))return null
        val digest=MessageDigest.getInstance("SHA-256").digest(ads.toString().toByteArray()).joinToString(""){"%02x".format(it.toInt()and 255)}
        return MaxBoostBinding(today,row.getString("key"),row.getString("cat"),row.getString("title"),row.getInt("meters"),
            ad.getString("runId"),ad.getInt("index"),ad.getLong("startedAt"),row.getInt("offeredReward"),digest)
    }
    private fun json(v:MaxBoostBinding)=JSONObject().put("date",v.date).put("key",v.key).put("cat",v.cat).put("title",v.title).put("meters",v.meters)
        .put("adRun",v.adRun).put("adIndex",v.adIndex).put("adStarted",v.adStarted).put("reward",v.reward).put("adsDigest",v.adsDigest)
    private fun decode(v:JSONObject)=MaxBoostBinding(v.getString("date"),v.getString("key"),v.getString("cat"),v.getString("title"),v.getInt("meters"),
        v.getString("adRun"),v.getInt("adIndex"),v.getLong("adStarted"),v.getInt("reward"),v.getString("adsDigest"))
    fun read(c:android.content.Context,v:MaxBoostBinding):JSONObject?=ReliableLedger.read(c,AREA,v.date,v.key){null}?.let{JSONObject(it)}
    fun pending(c:android.content.Context,date:String):MaxBoostBinding? {
        val rows=ChallengeStore.rows(c,date);val found=mutableListOf<MaxBoostBinding>()
        for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i);val raw=ReliableLedger.read(c,AREA,date,row.getString("key")){null}?:continue
            val j=JSONObject(raw);if(j.optString("phase")!="ROUTED")found.add(decode(j.getJSONObject("binding")))
        }
        check(found.size<=1){"MAX_BOOST_DIALOG_DISMISS_FAILED:multiple_pending_completions"}
        return found.singleOrNull()
    }
    fun diagnostics(c:android.content.Context,date:String):org.json.JSONArray=org.json.JSONArray().also{out->
        val rows=ChallengeStore.rows(c,date)
        for(i in 0 until rows.length())ReliableLedger.read(c,AREA,date,rows.getJSONObject(i).getString("key")){null}?.let{out.put(JSONObject(it))}
    }
    fun valid(c:android.content.Context,v:MaxBoostBinding)=ChallengeStore.find(c,v.date,v.key)?.let{fromRow(it,TokyoDate.of())}==v
    @Synchronized fun begin(c:android.content.Context,v:MaxBoostBinding){
        check(valid(c,v)){"MAX_BOOST_DIALOG_DISMISS_FAILED:saved_task_binding_changed"}
        val previous=pending(c,v.date);check(previous==null||previous==v){"MAX_BOOST_DIALOG_DISMISS_FAILED:another_completion_pending"}
        ReliableLedger.mutate(c,AREA,v.date,v.key){old->
            if(old!=null){check(decode(old.getJSONObject("binding"))==v){"MAX_BOOST_DIALOG_DISMISS_FAILED:journal_binding_changed"};old}
            else JSONObject().put("binding",json(v)).put("attempts",0).put("phase","PENDING").put("createdAt",System.currentTimeMillis())
        }
    }
    @Synchronized fun update(c:android.content.Context,v:MaxBoostBinding,change:(JSONObject)->Unit){
        ReliableLedger.mutate(c,AREA,v.date,v.key){old->
            check(old!=null&&decode(old.getJSONObject("binding"))==v){"MAX_BOOST_DIALOG_DISMISS_FAILED:journal_missing_or_changed"}
            change(old!!);old.put("updatedAt",System.currentTimeMillis())
        }
    }
}

private data class MaxBoostUiSnapshot(val frame:MaxBoostFrame,val fields:Map<String,String>,val trace:String)
/** All AccessibilityNodeInfo objects are local to a fresh capture or click; only value snapshots survive a wait. */
private class MaxBoostUi(private val svc:AutoAccessibilityService) {
    private val ui=Ui(svc);private val pkg=MaxBoostDialogPolicy.PACKAGE
    private fun hasNativeContainer(text:AccessibilityNodeInfo?,windowId:Int):Boolean {
        var parent=text?.parent
        repeat(6){
            val current=parent?:return false
            if(current.windowId!=windowId)return false
            if(current.viewIdResourceName==pkg+":id/button_no")return true
            parent=current.parent
        }
        return false
    }
    fun capture(binding:MaxBoostBinding?=null):MaxBoostUiSnapshot {
        ui.refreshCache();val root=svc.rootInActiveWindow
        val rootId=root?.windowId?:-1
        val window=root?.let{r->svc.windows.singleOrNull{it.id==r.windowId&&(it.isActive||it.isFocused)}}
        val ns=ui.foregroundNodes().filter{it.isVisibleToUser&&it.windowId==rootId}
        val same=root?.packageName?.toString()==pkg&&window!=null
        val labels=ns.map{ui.label(it)};val ids=ns.mapNotNull{it.viewIdResourceName}.toSet()
        val fields=ns.filter{it.viewIdResourceName?.startsWith(pkg+":id/")==true}.groupBy{it.viewIdResourceName!!.substringAfterLast('/')}
            .mapNotNull{(id,n)->n.singleOrNull()?.let{id to ui.label(it)}}.toMap()
        val home=same&&MaxBoostDialogPolicy.nativeHost(AutoAccessibilityService.foregroundActivity)
        val ok=ns.filter{it.viewIdResourceName==pkg+":id/button_no_text"&&ui.label(it)=="OK"}
        val container=ns.singleOrNull{it.viewIdResourceName==pkg+":id/button_no"}
        val rect=Rect();container?.getBoundsInScreen(rect)
        val windowRect=Rect();window?.getBoundsInScreen(windowRect)
        val textRect=Rect();ok.singleOrNull()?.getBoundsInScreen(textRect)
        val controlReady=container?.className?.toString() in setOf("android.view.ViewGroup","android.widget.Button","android.widget.FrameLayout")&&
            ok.singleOrNull()?.className?.toString() in setOf("android.widget.TextView","android.widget.Button")&&
            container?.isEnabled==true&&ok.singleOrNull()?.isEnabled==true&&rect.width()>0&&rect.height()>0&&
            hasNativeContainer(ok.singleOrNull(),rootId)&&textRect.width()>0&&textRect.height()>0&&windowRect.contains(rect)&&rect.contains(textRect)&&rect.left>=0&&rect.top>=0&&rect.right<=svc.resources.displayMetrics.widthPixels&&rect.bottom<=svc.resources.displayMetrics.heightPixels
        val core=home&&fields["title_count"]?.contains("チャレンジ達成後の報酬が")==true&&listOf("title_count","current_point","boost_list").all{pkg+":id/"+it in ids}&&ns.count{it.viewIdResourceName==pkg+":id/title_count"}==1
        val reward=fields["current_point"]?.removeSuffix("pt")?.replace(",","")?.toIntOrNull()
        val page=MaxBoostDialogPolicy.classify(root?.packageName?.toString(),AutoAccessibilityService.foregroundActivity,same,ids,labels,fields,binding)
        val signature="$page:${root?.windowId}:$rect:${ok.size}:$reward:${fields["text_name"]}:${fields["text_date"]}"
        val trusted=if(page==MaxBoostPage.MAX_DIALOG)core else same&&(home||page==MaxBoostPage.DETAIL)
        val frame=MaxBoostFrame(page,root?.windowId?:-1,signature,trusted,ok.size,controlReady&&MaxBoostDialogPolicy.nativeShape(root?.packageName?.toString(),AutoAccessibilityService.foregroundActivity,same,ids,labels),reward)
        val trace="activity=${AutoAccessibilityService.foregroundActivity};window=${root?.windowId};active=${window?.isActive};focused=${window?.isFocused};page=$page;trusted=$trusted;OKs=${ok.size};bounds=$rect;ready=$controlReady;reward=$reward;button_no_clickable=${container?.isClickable};OK_clickable=${ok.singleOrNull()?.isClickable};text_bounds=$textRect;title_count=${fields["title_count"]};button_class=${container?.className};OK_class=${ok.singleOrNull()?.className};native_boost_list=${pkg+":id/boost_list" in ids}"
        return MaxBoostUiSnapshot(frame,fields,trace)
    }
    suspend fun click(binding:MaxBoostBinding,expected:MaxBoostFrame,number:Int):MaxBoostClick {
        val latest=capture(binding)
        if(latest.frame!=expected||!latest.frame.ready)return MaxBoostClick("NONE",false,null,"fresh_window_or_bounds_changed")
        if(!MaxBoostCompletionStore.valid(svc,binding))return MaxBoostClick("NONE",false,null,"durable_binding_changed")
        ui.refreshCache();val ns=ui.foregroundNodes().filter{it.isVisibleToUser}
        val text=ns.singleOrNull{it.windowId==expected.windowId&&it.isEnabled&&it.viewIdResourceName==pkg+":id/button_no_text"&&ui.label(it)=="OK"}
            ?:return MaxBoostClick("NONE",false,null,"fresh_OK_missing")
        val button=ns.singleOrNull{it.windowId==expected.windowId&&it.isEnabled&&it.viewIdResourceName==pkg+":id/button_no"}
            ?:return MaxBoostClick("NONE",false,null,"fresh_container_missing")
        val rect=Rect();button.getBoundsInScreen(rect);val tr=Rect();text.getBoundsInScreen(tr)
        if(!rect.contains(tr)||!hasNativeContainer(text,expected.windowId)||!button.refresh()||!text.refresh())return MaxBoostClick("NONE",false,null,"node_refresh_failed")
        val refreshed=capture(binding)
        if(refreshed.frame!=expected)return MaxBoostClick("NONE",false,null,"layout_changed_before_action")
        // The observed native button_no is the actionable ancestor of the non-clickable OK TextView.
        val action=MaxBoostDialogPolicy.action(number,button.isClickable,text.isClickable)
        if(action!="GESTURE"){
            val target=if(action=="NATIVE_CONTAINER")button else text
            val sent=target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return MaxBoostClick("NATIVE_ACTION_CLICK",sent,null,"actual_page_change_still_required")
        }
        val result=ui.tapObserved(rect.centerX(),rect.centerY())
        return MaxBoostClick("FRESH_BOUNDS_GESTURE",result.dispatched,result.completed,result.detail)
    }
}

object MaxBoostCoordinator {
    fun visible(svc:AutoAccessibilityService):Boolean {
        // Cheap candidate check on ordinary pages: no bitmap, full tree dump, value map or trace allocation.
        val ui=Ui(svc);val root=svc.rootInActiveWindow?:return false
        if(root.packageName?.toString()!=MaxBoostDialogPolicy.PACKAGE||!MaxBoostDialogPolicy.nativeHost(AutoAccessibilityService.foregroundActivity))return false
        val id=root.windowId;val prefix=MaxBoostDialogPolicy.PACKAGE+":id/"
        fun relevant(n:AccessibilityNodeInfo)=n.isVisibleToUser&&n.windowId==id
        val title=root.findAccessibilityNodeInfosByViewId(prefix+"title_count").filter{relevant(it)}
        if(title.size!=1||!ui.label(title.single()).contains("チャレンジ達成後の報酬が"))return false
        val suffix=root.findAccessibilityNodeInfosByText("最大値に到達しました").filter{relevant(it)}
        if(!MaxBoostDialogPolicy.hasCopy(title.map{ui.label(it)}+suffix.map{ui.label(it)}))return false
        if(svc.windows.none{it.id==id&&(it.isActive||it.isFocused)})return false
        return listOf("current_point","boost_list").all{name->root.findAccessibilityNodeInfosByViewId(prefix+name).count{relevant(it)}==1}
    }
    fun hasPending(svc:AutoAccessibilityService,date:String)=MaxBoostCompletionStore.pending(svc,date)!=null
    suspend fun recover(svc:AutoAccessibilityService,engine:AutomationEngine,run:String,date:String){
        val journal=MaxBoostCompletionStore.pending(svc,date)
        if(journal==null&&!visible(svc))return
        if(journal!=null&&Ui(svc).currentPackage()!=MaxBoostDialogPolicy.PACKAGE){
            // Restore the existing system task only; a failed completion never grants a blind launcher reset.
            if(!AdTaskReturn.restore(svc,date,run))throw MaxBoostCompletionFailure("MAX_BOOST_DIALOG_DISMISS_FAILED","existing_completion_task_unavailable")
        }
        val snapshot=MaxBoostUi(svc).capture(journal)
        val rows=ChallengeStore.rows(svc,date)
        val visibleBinding=if(journal==null)(0 until rows.length()).mapNotNull{MaxBoostCompletionStore.fromRow(rows.getJSONObject(it),date)}.singleOrNull{
            snapshot.fields["text_name"]==it.title&&AnaCalendarDate.same(snapshot.fields["text_date"],it.date)&&
                snapshot.fields["text_attended"]?.removePrefix("参加日：")?.let{d->AnaCalendarDate.same(d,it.date)}==true
        } else null
        val binding=journal?:visibleBinding
        if(binding==null){
            FaultStore.maxBoostFailure(svc,run,"MAX_BOOST_DIALOG_DISMISS_FAILED:binding_required_no_reward_amount_guess",snapshot.trace)
            throw MaxBoostCompletionFailure("MAX_BOOST_DIALOG_DISMISS_FAILED","binding_required_no_reward_amount_guess")
        }
        finish(svc,engine,run,binding)
    }
    suspend fun finish(svc:AutoAccessibilityService,engine:AutomationEngine,run:String,row:JSONObject){
        val binding=MaxBoostCompletionStore.fromRow(row,TokyoDate.of())
            ?:throw MaxBoostCompletionFailure("MAX_BOOST_DIALOG_DISMISS_FAILED","saved_boost5_receipt_not_bound")
        finish(svc,engine,run,binding)
    }
    private suspend fun finish(svc:AutoAccessibilityService,engine:AutomationEngine,run:String,binding:MaxBoostBinding){
        val screen=MaxBoostUi(svc)
        val port=object:MaxBoostCompletionPort {
            override fun now()=SystemClock.elapsedRealtime()
            override fun allowed()=!AutoAccessibilityService.stopRequested&&TokyoDate.of()==binding.date&&AutoAccessibilityService.isConnected()&&
                !(svc.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked&&AccountSafety.authorize(svc,"max_boost_completion")
            override suspend fun ensureDurableBinding(){MaxBoostCompletionStore.begin(svc,binding)}
            override fun persistedAttempts()=MaxBoostCompletionStore.read(svc,binding)!!.optInt("attempts")
            override fun persistedResumeRequested()=MaxBoostCompletionStore.read(svc,binding)!!.optBoolean("finalResumeRequested")
            override suspend fun frame()=screen.capture(binding).frame
            override suspend fun reserveAttempt(number:Int){
                check(MaxBoostCompletionStore.valid(svc,binding)){"MAX_BOOST_DIALOG_DISMISS_FAILED:binding_changed_before_action"}
                MaxBoostCompletionStore.update(svc,binding){j->
                    check(j.optInt("attempts")==number-1&&number<=3)
                    val actions=j.optJSONArray("actions")?:org.json.JSONArray().also{j.put("actions",it)}
                    actions.put(JSONObject().put("attempt",number).put("intentAt",System.currentTimeMillis()).put("result","PENDING"))
                    j.put("attempts",number).put("phase","DISMISS_PENDING")
                }
            }
            override suspend fun click(frame:MaxBoostFrame,number:Int)=screen.click(binding,frame,number)
            override suspend fun actionResult(number:Int,result:MaxBoostClick){
                MaxBoostCompletionStore.update(svc,binding){j->
                    val action=j.getJSONArray("actions").getJSONObject(number-1)
                    action.put("method",result.method).put("dispatched",result.dispatched).put("completed",result.completed?:JSONObject.NULL).put("detail",result.detail).put("result","OBSERVED")
                }
                Logger.i("MAXBOOST","run=$run key=${binding.key} attempt=$number outcome=$result")
            }
            override suspend fun resumeFinalDialog():Boolean {
                val old=MaxBoostCompletionStore.read(svc,binding)?:return false
                val unresolved=TravelSelection.hasUnresolvedAd(svc,binding.date)||OfficialAdStore.attempts(svc,binding.date).any{!it.terminal||it.state==AdState.UNCONFIRMED}
                val observed=screen.capture(binding)
                // A saved completed row alone cannot identify an anonymous official resume prompt.
                if(!allowed()||!MaxBoostDialogPolicy.finalResumeAllowed(MaxBoostCompletionStore.valid(svc,binding),TokyoDate.of()==binding.date,
                    unresolved,AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity),old.optBoolean("finalResumeRequested"),
                    MaxBoostDialogPolicy.resumeIdentity(observed.fields,binding)))return false
                if(observed.frame.page!=MaxBoostPage.RESUME_PROMPT)return false
                MaxBoostCompletionStore.update(svc,binding){it.put("finalResumeRequested",true).put("finalResumeIntentAt",System.currentTimeMillis()).put("finalResumeRun",run)}
                val ui=Ui(svc);ui.refreshCache();val root=svc.rootInActiveWindow?:return false
                val ns=ui.foregroundNodes().filter{it.windowId==root.windowId&&it.isVisibleToUser}
                val labels=ns.map{ui.label(it)};val ids=ns.mapNotNull{it.viewIdResourceName}.toSet()
                val fresh=screen.capture(binding)
                if(!allowed()||fresh.frame!=observed.frame||!MaxBoostDialogPolicy.resumeIdentity(fresh.fields,binding)||!MaxBoostDialogPolicy.resumeShape(root.packageName?.toString(),AutoAccessibilityService.foregroundActivity,
                    svc.windows.any{it.id==root.windowId&&(it.isActive||it.isFocused)},ids,labels))return false
                val button=ns.singleOrNull{it.isEnabled&&it.viewIdResourceName=="android:id/button1"&&ui.label(it)=="再開する"}?:return false
                val sent=ui.clickSemantic(button)
                MaxBoostCompletionStore.update(svc,binding){it.put("finalResumeDispatched",sent)}
                Logger.i("MAXBOOST","run=$run key=${binding.key} bound_final_resume=$sent original_ads_unchanged=true")
                return sent
            }
            override suspend fun dismissed(page:MaxBoostPage){
                check(MaxBoostCompletionStore.valid(svc,binding)){"MAX_BOOST_DIALOG_DISMISS_FAILED:binding_changed_after_close"}
                MaxBoostCompletionStore.update(svc,binding){it.put("phase","DISMISSED").put("verifiedPage",page.name)}
                Logger.i("MAXBOOST","run=$run key=${binding.key} closed_verified=true page=$page")
            }
            override suspend fun restoreCategory(cat:String,page:MaxBoostPage):Boolean {
                if(!allowed()||visible(svc))return false
                return try{engine.prepareFilteredCategory(Cat.ofName(cat)?:return false,allowLoginSubmission=false)}
                catch(t:MaxBoostCompletionFailure){throw t}
                catch(t:Exception){Logger.w("MAXBOOST","category restore failed: "+t.message);false}
            }
            override suspend fun routed(){
                MaxBoostCompletionStore.update(svc,binding){it.put("phase","ROUTED")}
                Logger.i("MAXBOOST","run=$run key=${binding.key} category=${binding.cat} today_verified=true continue_scan=true")
            }
            override suspend fun failure(code:String,detail:String){
                MaxBoostCompletionStore.update(svc,binding){it.put("phase","FAILED").put("failure","$code:$detail")}
                FaultStore.maxBoostFailure(svc,run,"$code:$detail","key=${binding.key};"+screen.capture(binding).trace+";actions="+MaxBoostCompletionStore.read(svc,binding)?.optJSONArray("actions"))
            }
            override suspend fun pause(ms:Long){delay(ms)}
        }
        try{MaxBoostCompletionFlow(binding,port).run()}
        catch(t:MaxBoostCompletionFailure){throw t}
        catch(t:Exception){
            FaultStore.maxBoostFailure(svc,run,"MAX_BOOST_DIALOG_DISMISS_FAILED:"+t.javaClass.simpleName,screen.capture(binding).trace)
            throw MaxBoostCompletionFailure("MAX_BOOST_DIALOG_DISMISS_FAILED",t.message?:t.javaClass.simpleName)
        }
    }
    fun refuseGenericNavigation(svc:AutoAccessibilityService):Nothing {
        FaultStore.maxBoostFailure(svc,AutoAccessibilityService.currentRunId,"MAX_BOOST_DIALOG_DISMISS_FAILED:still_visible_before_navigation",MaxBoostUi(svc).capture().trace)
        throw MaxBoostCompletionFailure("MAX_BOOST_DIALOG_DISMISS_FAILED","still_visible_before_navigation")
    }
}
