package com.anapocket.auto

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

object CheckinRuntime {
    val ownership=UiOwnership()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO+CoroutineExceptionHandler{_,t->runCatching{Logger.w("CHECKIN","isolated controller failure: "+t.javaClass.simpleName)}})
    @Volatile private var ana:UiOwnership.Token?=null
    @Volatile var foregroundPackage="";private set
    fun windowEvent(pkg:String){if(pkg.isNotBlank())foregroundPackage=pkg}
    @Volatile private var job:Job?=null
    @Volatile private var run=""
    @Volatile private var activeDate=""
    @Volatile var activeApp:CheckinApp?=null;private set
    @Volatile private var hour=0
    @Volatile private var minute=3
    @Volatile private var anaEnabled=true
    @Volatile private var registeredDue=0L
    @Volatile private var handledDue=0L
    @Synchronized fun updateAnaWindow(ctx:Context){
        val c=Prefs.loadBlocking(ctx);hour=c.hour;minute=c.minute;anaEnabled=c.enabled
        val p=ctx.getSharedPreferences("checkin_ana_guard",0);handledDue=p.getLong("handled",0)
        var due=p.getLong("due",0)
        // Initial registration and an explicit time change target the NEXT real ANA schedule.
        // After that, retain an overdue unhandled window across process death / delayed alarms.
        if(due==0L||p.getInt("hour",-1)!=hour||p.getInt("minute",-1)!=minute||handledDue>=due){
            due=CheckinClock.next(System.currentTimeMillis(),hour,minute)
            check(p.edit().putLong("due",due).putInt("hour",hour).putInt("minute",minute).commit())
        };registeredDue=due
    }
    fun anaDue()=registeredDue
    fun protected(ctx:Context):Boolean=AutoAccessibilityService.isRunning||CheckinClock.registeredProtection(System.currentTimeMillis(),registeredDue,handledDue,anaEnabled)
    fun onAnaStarted(ctx:Context){
        // P0 only touches the small RAM arbiter. It never acquires the ordinary controller/config lock.
        ana=ownership.owner()?.takeIf{it.app=="ANA"}?:ownership.acquire("ANA",AutoAccessibilityService.currentRunId,SystemClock.elapsedRealtime(),Long.MAX_VALUE,false)
        val acquired=System.currentTimeMillis();val victim=activeApp?.name
        preempt(ctx,"ANA_preempted")
        scope.launch{runCatching{CheckinStore(ctx,CheckinApp.RAKUTEN).dir("priority_audit").resolve("ana_"+acquired+".json").writeText(org.json.JSONObject().put("event","ANA_UI_OWNERSHIP_ACQUIRED").put("actualAcquiredAt",acquired).put("preemptedApp",victim).put("businessDate",CheckinClock.date(acquired)).put("source","real_runtime_ownership_hook").toString(2))}}
    }
    fun onAnaFinished(ctx:Context){
        if(ana!=null&&registeredDue>0&&System.currentTimeMillis()>=registeredDue){handledDue=registeredDue;ctx.getSharedPreferences("checkin_ana_guard",0).edit().putLong("handled",handledDue).apply()}
        ana?.let{ownership.release(it)};ana=null
        scope.launch{delay(1000);updateAnaWindow(ctx);CheckinScheduler.protection(ctx);resumePending(ctx)}
    }
    private fun preempt(ctx:Context,reason:String){
        val old=ownership.revokeOrdinary();val app=activeApp;val oldRun=run;val date=activeDate
        val oldJob=job;activeApp=null;run="";job=null
        oldJob?.cancel(CancellationException(reason))
        // Persistence and cleanup never hold up P0, and never depend on a stalled controller's finally.
        scope.launch {
            if(app!=null&&oldRun.isNotEmpty()){
                val store=CheckinStore(ctx,app)
                store.tasks().forEach{task->val row=store.read(task,date);if(row?.optString("state")=="RUNNING"&&row.optString("runId")==oldRun)store.update(task,oldRun,CheckinState.PREEMPTED,reason,date=date)}
                store.finishRun(oldRun,reason)
                store.log(oldRun,"PREEMPTED oldLeaseInvalid="+(ownership.owner()?.run!=oldRun)+" reason="+reason)
            }
            synchronized(this@CheckinRuntime){if(activeApp==null){CheckinForeground.stop(ctx);CheckinOcr.stop(ctx)}}
        }
    }
    fun protect(ctx:Context){preempt(ctx,"ANA_protection_window");scope.launch{updateAnaWindow(ctx)}}
    fun liveRun(app:CheckinApp):String?=ownership.owner()?.takeIf{it.app==app.name&&ownership.valid(it,SystemClock.elapsedRealtime())}?.run
    fun serviceConnected(ctx:Context){scope.launch{updateAnaWindow(ctx);CheckinScheduler.restore(ctx).join();resumePending(ctx)}}
    fun serviceLost(){ownership.revokeOrdinary();job?.cancel(CancellationException("accessibility_disconnected"))}
    fun configurationChanged(app:CheckinApp){if(activeApp==app)stop()}
    fun stop(){ownership.revokeOrdinary();job?.cancel(CancellationException("user_stopped"))}
    fun request(ctx:Context,app:CheckinApp,reason:String="manual",scheduledAt:Long=0,generation:Long=CheckinStore(ctx,app).config().generation){scope.launch{start(ctx.applicationContext,app,reason,scheduledAt,generation)}}
    fun readOnlyProbe(ctx:Context,app:CheckinApp){check(BuildConfig.DEBUG);scope.launch{start(ctx.applicationContext,app,"manual_probe",0)}}
    @Synchronized private fun start(ctx:Context,app:CheckinApp,reason:String,scheduledAt:Long,expectedGeneration:Long=CheckinStore(ctx,app).config().generation){
        val manual=reason=="manual"||reason=="manual_probe"
        val store=CheckinStore(ctx,app);if(reason!="manual_probe"&&!store.config().let{CheckinDispatchPolicy.accepts(it.enabled,it.generation,expectedGeneration)})return
        if(manual)store.noteDispatch(TokyoDate.of(),0,reason)
        if(store.pendingDate()!=TokyoDate.of()&&!manual)return
        if(Build.VERSION.SDK_INT<30){
            store.pending(null)
            val blocked="CI_"+CheckinReceiptPolicy.id();store.tasks().forEach{store.update(it,blocked,CheckinState.BLOCKED,"new_module_requires_android_11_screenshot")}
            notify(ctx,app,"此签到模块需要Android 11或更新系统的屏幕识别能力。",blocked,expectedGeneration=expectedGeneration,statusReason="permission_requires_user",source=reason);return
        }
        if(activeApp==app&&job?.isActive==true)return
        updateAnaWindow(ctx)
        if(protected(ctx)){
            store.waiting(TokyoDate.of(),"WAITING_FOR_ANA")
            store.log("dispatch_"+TokyoDate.of(),"WAITING_FOR_ANA reason=$reason generation=$expectedGeneration no_failure_notification=true")
            return
        }
        val svc=AutoAccessibilityService.instance
        if(svc==null){store.waiting(TokyoDate.of(),"accessibility_disconnected");notify(ctx,app,"未执行：请开启无障碍服务",CheckinReceiptPolicy.id(),expectedGeneration=expectedGeneration,statusReason="accessibility_disconnected",source=reason);return}
        if(store.hold().isNotEmpty()){notify(ctx,app,"已暂停：请在手机处理验证或账号提示后复核",CheckinReceiptPolicy.id(),expectedGeneration=expectedGeneration,statusReason="accessibility_or_account_requires_user",source=reason);return}
        val id="CI_"+app.name+"_"+CheckinReceiptPolicy.id()
        val token=ownership.acquire(app.name,id,SystemClock.elapsedRealtime(),SystemClock.elapsedRealtime()+10*60_000,protected(ctx))
        if(token==null){val why="owner="+ownership.owner()?.app+" ANA_protected="+protected(ctx);store.waiting(TokyoDate.of(),why);store.log(id,"WAIT "+why);return}
        val date=TokyoDate.of();store.beginRun(id,date)
        val acquiredAt=System.currentTimeMillis()
        val timing=store.dispatchInfo(date)?:org.json.JSONObject().put("scheduledAt",scheduledAt)
        timing.put("runId",id).put("appId",app.pkg).put("businessDate",date).put("actualAcquiredAt",acquiredAt).put("acquireReason",reason)
        val originalDue=timing.optLong("scheduledAt",0)
        if(originalDue>0)timing.put("acquisitionDelayMs",acquiredAt-originalDue)
        store.dir(id).resolve("run.json").writeText(timing.toString(2))
        store.log(id,"ACQUIRED scheduledAt=$originalDue actualTriggerAt="+timing.optLong("actualTriggerAt",0)+" acquiredAt=$acquiredAt reason="+reason)
        var newJob:Job?=null
        val accepted=ownership.action(token,SystemClock.elapsedRealtime(),protected(ctx)) {
        run=id;activeDate=date;activeApp=app
        newJob=scope.launch(start=CoroutineStart.LAZY) {
            val ui=CheckinUi(svc,app,token,date,reason=="manual_probe")
            val overlay=OverlayKeeper(svc)
            @Suppress("DEPRECATION") val wake=(ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,"tuffy:checkin")
            var preempted=false
            var endReason=""
            var resumeAfterCancellation:String?=null
            var perceptionBlocked=false
            try {
                wake.acquire(11*60_000L);CheckinForeground.start(ctx,app.title);overlay.show()
                withTimeout(9*60_000L){
                    check(ui.ready()){ "unlock_requires_user_or_timeout" }
                    ui.launch();CheckinOcr.warm(ctx);delay(1500)
                    if(reason=="manual_probe"){check(BuildConfig.DEBUG);store.log(id,"READ_ONLY_PROBE_WAIT no_reward_submission");awaitCancellation()}
                    else CheckinController(ui,store,id,date).execute()
                }
            }catch(e:CancellationException){
                endReason=if(e is TimeoutCancellationException)"execution_timeout" else e.message.orEmpty()
                preempted=e.message?.contains("ANA")==true
                if(preempted||e.message=="accessibility_disconnected")resumeAfterCancellation=e.message
                val state=if(preempted)CheckinState.PREEMPTED else CheckinState.UNCONFIRMED
                store.tasks().forEach{t->val r=store.read(t,date);if(r?.optString("state")=="RUNNING"&&r.optString("runId")==id)store.update(t,id,state,e.message.orEmpty(),date=date)}
                store.log(id,"CANCEL reason="+e.message)
                if(e is TimeoutCancellationException)runCatching{withContext(NonCancellable){withTimeoutOrNull(3000){ui.evidence(store,id,"execution_timeout")}}}
            }catch(e:Exception){
                perceptionBlocked=e is CheckinPerceptionUnavailable
                store.tasks().forEach{t->val r=store.read(t,date);if(r==null&&ownership.valid(token,SystemClock.elapsedRealtime())||r?.optString("state")=="RUNNING"&&r.optString("runId")==id)store.update(t,id,if(r?.optBoolean("reserved")==true)CheckinState.UNCONFIRMED else CheckinState.BLOCKED,e.message?.take(160).orEmpty(),date=date)}
                runCatching{ui.evidence(store,id,"fault")}
            }finally {
                withContext(NonCancellable){
                    store.finishRun(id,resumeAfterCancellation)
                    notify(ctx,app,if(reason=="manual_probe")"只读调度测试已结束，未提交领取或抽奖。" else store.summary(date),id,date,expectedGeneration,resumeAfterCancellation?:endReason,reason)
                    overlay.hide();if(wake.isHeld)wake.release()
                    // A revoked controller never sends Home/Back/lock after ANA acquires ownership.
                    if(!perceptionBlocked&&ui.owns()){
                        val atTaskHome=app==CheckinApp.RAKUTEN&&ui.hasNativeNode("nav_home")
                        val bonusError=!atTaskHome&&app==CheckinApp.RAKUTEN&&ui.nativeLabel(CheckinBonusPolicy.TITLE)!=null&&CheckinBonusPolicy.loadingError(ui.nativeText())
                        val unfinished=!atTaskHome&&store.all(date).any{it.optBoolean("reserved")&&it.optString("state") in setOf("RUNNING","UNCONFIRMED","PREEMPTED","NEEDS_USER_ACTION")&&!(it.optString("taskId")=="bonus"&&bonusError)}
                        if(CheckinExitPolicy.preserveScene(ui.pkg()==app.pkg,reason=="manual_probe",store.hold().isNotBlank(),unfinished)){
                            store.dir(id).resolve("exit.json").writeText(org.json.JSONObject().put("runId",id)
                                .put("scenePreserved",true).put("returnedHome",false).put("lockConfirmed",false)
                                .put("forcedProcessTermination",false).put("reason","unfinished_reserved_scene_or_human_required")
                                .put("finishedAt",System.currentTimeMillis()).toString(2))
                            store.log(id,"SCENE_PRESERVED no_new_submission owner_will_be_released=true")
                        }else withTimeoutOrNull(30_000){ui.finish()}
                    }
                    synchronized(this@CheckinRuntime){ownership.release(token);if(run==id){activeApp=null;job=null;CheckinForeground.stop(ctx);CheckinOcr.stop(ctx)}}
                    if(!preempted)scope.launch{delay(700);resumePending(ctx)}
                }
            }
        }
        job=newJob
        }
        if(accepted){newJob?.start();deadline(ctx,app,id,date,token,expectedGeneration)} else store.finishRun(id,"UI_lease_lost_before_controller")
    }
    private fun deadline(ctx:Context,app:CheckinApp,id:String,date:String,token:UiOwnership.Token,expectedGeneration:Long){scope.launch{
        delay(9*60_000L+1000)
        // Independent watchdog: no join of a stuck UI/OCR/Binder controller, no UI actions here.
        val victim:Job?=synchronized(ownership){
            if(!ownership.release(token))return@launch
            if(run==id){val old=job;run="";activeApp=null;job=null;old}else null
        }
        victim?.cancel(CancellationException("execution_deadline"))
        val store=CheckinStore(ctx,app)
        store.finishRun(id)
        store.tasks().forEach{task->val row=store.read(task,date);if(row?.optString("state")=="RUNNING"&&row.optString("runId")==id)store.update(task,id,CheckinState.UNCONFIRMED,"execution_deadline",date=date)}
        store.log(id,"WATCHDOG lease_revoked controller_detached")
        notify(ctx,app,store.summary(date),id,date,expectedGeneration,"execution_deadline","watchdog")
        synchronized(this@CheckinRuntime){if(activeApp==null){CheckinForeground.stop(ctx);CheckinOcr.stop(ctx)}}
        if(!protected(ctx))resumePending(ctx)
    }}
    fun resumePending(ctx:Context){if(AutoAccessibilityService.isRunning||activeApp!=null)return;CheckinApp.entries.firstOrNull{val s=CheckinStore(ctx,it);s.config().enabled&&s.pendingDate()==TokyoDate.of()&&s.hold().isBlank()}?.let{request(ctx,it,"resume")}}
    fun hasPending(ctx:Context)=CheckinApp.entries.any{val s=CheckinStore(ctx,it);s.config().enabled&&s.pendingDate()==TokyoDate.of()&&s.hold().isBlank()}
    private fun notify(ctx:Context,app:CheckinApp,text:String,id:String,date:String=TokyoDate.of(),
        expectedGeneration:Long=CheckinStore(ctx,app).config().generation,statusReason:String="",source:String="runtime"){
        val store=CheckinStore(ctx,app);val config=store.config()
        val rows=store.all(date)
        val status=CheckinNoticePolicy.status(rows.map{it.optString("state")},statusReason)
        val allowed=CheckinNoticePolicy.mayPublish(config.enabled,config.generation,expectedGeneration,date,TokyoDate.of(),status,protected(ctx))
        val at=System.currentTimeMillis()
        val audit=org.json.JSONObject().put("appId",app.pkg).put("runId",id).put("businessDate",date)
            .put("status",status).put("source",source).put("generatedAt",at).put("generation",expectedGeneration)
            .put("published",allowed).put("taskIds",org.json.JSONArray(rows.map{it.optString("taskId")}))
        store.dir(id).resolve("notification.json").writeText(audit.toString(2))
        store.log(id,"NOTICE status=$status source=$source generatedAt=$at published=$allowed")
        if(!allowed)return
        val body="状态："+status+"\n发生时间："+TokyoDate.time(at)+" JST\n来源："+source+"\nRUN_ID："+id+"\n"+text+
            (if(rows.any{it.optString("failureReason")=="app_launch_foreground_timeout_after_two_attempts"})"\n原因：两次打开此App均未进入前台；未提交任务" else "")
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("checkin_results","各App执行结果",NotificationManager.IMPORTANCE_DEFAULT))
        val pi=PendingIntent.getActivity(ctx,9300+app.ordinal,Intent(ctx,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching{nm.notify(9300+app.ordinal,NotificationCompat.Builder(ctx,"checkin_results").setSmallIcon(R.drawable.ic_stat_ana).setContentTitle(app.title+" · "+date+" · "+status).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true).build())}
        TelegramNotifier.enqueue(ctx,"checkin_"+app.name+"_"+id,app.title+" · "+date+"\n"+body,app.name,expectedGeneration,date)
    }

}

data class CheckinFrame(val png:ByteArray,val lines:List<CheckinLine>,val pkg:String,val capturedAt:Long){val text get()=lines.joinToString("\n"){it.text}}
class CheckinUi(val svc:AutoAccessibilityService,val app:CheckinApp,private val token:UiOwnership.Token,private val date:String,private val readOnlyProbe:Boolean=false) {
    private val screenshotLock=kotlinx.coroutines.sync.Mutex()
    private var lastScreenshotRequest=0L
    private fun refreshNodes(){if(Build.VERSION.SDK_INT>=33)runCatching{svc.clearCache()}}
    private val generation=CheckinStore(svc,app).config().generation
    fun owns()=(readOnlyProbe||CheckinStore(svc,app).config().let{it.enabled&&it.generation==generation})&&CheckinRuntime.ownership.valid(token,SystemClock.elapsedRealtime())&&!CheckinRuntime.protected(svc)&&TokyoDate.of()==date
    private fun assertOwn(){check(owns()){ "UI_lease_expired_or_preempted" }}
    fun pkg()=svc.rootInActiveWindow?.packageName?.toString().orEmpty().also{CheckinRuntime.windowEvent(it)}
    private suspend fun action(expected:String?=app.pkg,block:()->Unit)=withContext(Dispatchers.Main.immediate){
        check(CheckinRuntime.ownership.action(token,SystemClock.elapsedRealtime(),CheckinRuntime.protected(svc)){
            check(owns()){"configuration_disabled_or_generation_changed"}
            check(TokyoDate.of()==date){"business_date_changed"}
            check(expected==null||CheckinRuntime.foregroundPackage==expected){"unexpected_foreground"};block()
        }){"UI_lease_revoked"}
    }
    suspend fun launch(){
        refreshNodes()
        if(pkg()==app.pkg&&CheckinRuntime.foregroundPackage==app.pkg){awaitForeground();return}
        repeat(2){attempt->
            action(null){val i=svc.packageManager.getLaunchIntentForPackage(app.pkg)?:error("app_not_installed");svc.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))}
            try{awaitForeground();return}catch(e:TimeoutCancellationException){
                currentCoroutineContext().ensureActive()
                CheckinStore(svc,app).log(token.run,"LAUNCH_FOREGROUND_TIMEOUT attempt="+(attempt+1)+" actualForeground="+pkg()+" no_business_actions_started=true")
                if(attempt==1)throw IllegalStateException("app_launch_foreground_timeout_after_two_attempts")
                delay(2000)
            }
        }
    }
    private suspend fun awaitForeground(){
        var consecutive=0
        withTimeout(20_000){while(consecutive<3){assertOwn();delay(400);consecutive=if(pkg()==app.pkg)consecutive+1 else 0}}
        delay(500)
    }
    suspend fun back(){action{svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)};delay(700);awaitForeground()}
    suspend fun tap(x:Int,y:Int)=action{val path=Path().apply{moveTo(x.toFloat(),y.toFloat())};svc.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,55)).build(),null,null)}
    private fun nodes():List<AccessibilityNodeInfo> {
        val out=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo?,depth:Int){if(n==null||depth>24||out.size>1000)return;out.add(n);for(i in 0 until n.childCount)walk(n.getChild(i),depth+1)};walk(svc.rootInActiveWindow,0);return out
    }
    suspend fun node(suffix:String,beforeDispatch:(suspend()->Boolean)?=null):Boolean {
        // App accessibility Binder queries can stall. Keep them off Main and outside the UI arbiter.
        assertOwn()
        val rect=withTimeoutOrNull(2000){withContext(Dispatchers.IO){
            refreshNodes();if(pkg()!=app.pkg)return@withContext null
            val n=svc.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(app.pkg+":id/"+suffix)?.firstOrNull{it.isVisibleToUser}
            n?.let{android.graphics.Rect().also{r->it.getBoundsInScreen(r)}}
        }}?:return false
        if(rect.width()<=0||rect.height()<=0)return false
        if(beforeDispatch!=null&&!beforeDispatch())return false
        tap(rect.centerX(),rect.centerY());delay(600);return true
    }
    suspend fun nativeText():String {assertOwn();return withContext(Dispatchers.IO){refreshNodes();nodes().filter{!it.isPassword&&!it.isEditable}.joinToString("\n"){it.text?.toString().orEmpty()}}.also{assertOwn()}}
    suspend fun hasNativeNode(suffix:String):Boolean {
        assertOwn()
        return withTimeoutOrNull(2000){withContext(Dispatchers.IO){
            refreshNodes();pkg()==app.pkg&&svc.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(app.pkg+":id/"+suffix)?.any{it.isVisibleToUser}==true
        }}.also{assertOwn()}==true
    }
    suspend fun nativeLabel(label:String):CheckinLine? {
        assertOwn();val caller=currentCoroutineContext();return withContext(Dispatchers.IO){
            refreshNodes()
            val root=svc.rootInActiveWindow
            fun matches(n:AccessibilityNodeInfo)=n.isVisibleToUser&&(n.text?.toString().equals(label,true)||n.contentDescription?.toString().equals(label,true))
            var visited=0;val deadline=SystemClock.elapsedRealtime()+3000
            fun find(n:AccessibilityNodeInfo?,depth:Int):AccessibilityNodeInfo? {
                caller.ensureActive()
                if(n==null||depth>24||visited++>1000||SystemClock.elapsedRealtime()>deadline)return null
                if(matches(n))return n
                for(i in 0 until n.childCount){val found=find(n.getChild(i),depth+1);if(found!=null)return found}
                return null
            }
            // Current lottery canvas exposes its Start image as a virtual WebView child,
            // but its text-search API does not return it. Stop traversal at that exact label.
            val found=root?.findAccessibilityNodeInfosByText(label)?.firstOrNull{matches(it)}?:find(root,0)
            found?.let{
                val b=android.graphics.Rect();it.getBoundsInScreen(b)
                if(b.width()>0&&b.height()>0)CheckinLine(label,b.left,b.top,b.right,b.bottom)else null
            }
        }.also{assertOwn()}
    }
    suspend fun png():ByteArray {
        screenshotLock.lock()
        try{
            val gap=500-(SystemClock.elapsedRealtime()-lastScreenshotRequest)
            if(gap>0)delay(gap)
            lastScreenshotRequest=SystemClock.elapsedRealtime()
            return screenshot()
        }finally{screenshotLock.unlock()}
    }
    private suspend fun screenshot():ByteArray {
        assertOwn();check(Build.VERSION.SDK_INT>=30){"screenshot_api_unavailable"}
        return withTimeout(2500){suspendCancellableCoroutine{c->svc.takeScreenshot(Display.DEFAULT_DISPLAY,svc.mainExecutor,object:AccessibilityService.TakeScreenshotCallback{
            override fun onSuccess(result:AccessibilityService.ScreenshotResult){
                val buffer=result.hardwareBuffer
                try{if(!c.isActive)return;val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace);val bmp=hardware?.copy(Bitmap.Config.ARGB_8888,false);hardware?.recycle();if(bmp==null){c.cancel();return};val stream=ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.PNG,100,stream);bmp.recycle();if(c.isActive)c.resume(stream.toByteArray())}finally{buffer.close()}
            }
            override fun onFailure(errorCode:Int){if(c.isActive)c.resumeWith(Result.failure(CheckinPerceptionUnavailable("screenshot_"+errorCode)))}
        })}}
    }
    suspend fun frame():CheckinFrame {
        assertOwn();refreshNodes();val pkg=pkg();check(pkg==app.pkg){"unexpected_foreground"};val at=SystemClock.elapsedRealtime();val p=png();val raw=CheckinOcr.read(svc,p);val lines=CheckinOcr.normalizeGreenStatus(p,raw);assertOwn()
        val dir=CheckinStore(svc,app).dir(token.run)
        dir.resolve("last_ocr.png").writeBytes(p)
        dir.resolve("last_ocr.json").writeText(org.json.JSONObject().put("runId",token.run).put("appId",app.pkg).put("phoneTime",TokyoDate.time()).put("capturedElapsed",at).put("lines",org.json.JSONArray().also{a->lines.forEachIndexed{i,l->a.put(org.json.JSONObject().put("text",l.text).put("rawText",raw[i].text).put("left",l.left).put("top",l.top).put("right",l.right).put("bottom",l.bottom))}}).toString(2))
        return CheckinFrame(p,lines,pkg,at)
    }
    suspend fun click(frame:CheckinFrame,line:CheckinLine,beforeDispatch:(suspend()->Boolean)?=null):Boolean {
        assertOwn();if(pkg()!=frame.pkg)return false
        delay(350);val fresh=png();if(!CheckinOcr.stable(frame.png,fresh,line))return false
        if(beforeDispatch!=null&&!beforeDispatch())return false
        tap(line.x,line.y);delay(650);return true
    }
    suspend fun pullDart(frame:CheckinFrame,pull:CheckinOcr.DartPull):Boolean {
        assertOwn();if(pkg()!=frame.pkg)return false
        val fresh=png();if(!CheckinOcr.stable(frame.png,fresh,pull.arrow))return false
        val store=CheckinStore(svc,app)
        check(store.withActiveRun(token.run){store.submitDartsPull(token.run,date)}==true){"darts_pull_already_submitted_or_run_replaced"}
        var accepted=false
        action{
            val path=Path().apply{moveTo(pull.arrow.x.toFloat(),pull.arrow.y.toFloat());lineTo(pull.arrow.x.toFloat(),pull.endY.toFloat())}
            accepted=svc.dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,650)).build(),null,null)
        }
        CheckinStore(svc,app).log(token.run,"DARTS_PULL_GESTURE x="+pull.arrow.x+" fromY="+pull.arrow.y+" toY="+pull.endY+" accepted="+accepted)
        delay(1000);assertOwn();return accepted
    }
    suspend fun evidence(store:CheckinStore,run:String,label:String):String {
        val name=label+"_"+System.currentTimeMillis();val dir=store.dir(run);dir.resolve(name+".png").writeBytes(png())
        val n=nodes().filter{it.isVisibleToUser}.joinToString("\n"){if(it.isPassword||it.isEditable)"[EDITABLE_REDACTED]" else it.className.toString()+" id="+it.viewIdResourceName+" text="+DiagnosticPrivacy.redact(it.text?.toString().orEmpty())}
        dir.resolve(name+".json").writeText(org.json.JSONObject().put("runId",run).put("appId",app.pkg).put("businessDate",date).put("phoneTime",TokyoDate.time()).put("phase",label).put("foregroundPackage",pkg()).put("foregroundActivity",AutoAccessibilityService.foregroundActivity).put("source","real_accessibility_screenshot_no_fixture").put("nodes",n).toString(2))
        store.log(run,"EVIDENCE "+name);return app.name+"/"+run+"/"+name
    }
    suspend fun ready():Boolean {
        val km=svc.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if(km.isDeviceSecure||km.isKeyguardSecure)return false
        if(!km.isKeyguardLocked&&(svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive)return true
        action(null){svc.startActivity(Intent(svc,CheckinUnlockActivity::class.java).putExtra("run",token.run).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))}
        repeat(40){assertOwn();delay(250);if(!km.isKeyguardLocked&&(svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive){
            delay(3000);assertOwn();return !km.isKeyguardLocked&&(svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        }};return false
    }
    suspend fun finish(){
        if(!owns())return
        val store=CheckinStore(svc,app);val proof=org.json.JSONObject().put("runId",token.run).put("forcedProcessTermination",false).put("startedAt",System.currentTimeMillis())
        fun save(){store.dir(token.run).resolve("exit.json").writeText(proof.toString(2))}
        if(pkg()!=app.pkg){proof.put("returnedHome",false).put("reason","foreground_not_owned_app");save();return}
        proof.put("cleanup",ManagedAppCleanup.run(svc,app.pkg,{owns()},{block->if(!owns())false else CheckinRuntime.ownership.action(token,SystemClock.elapsedRealtime(),CheckinRuntime.protected(svc),block)}));save()
        if(!owns())return
        action(null){svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)}
        val homeIntent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePackage=svc.packageManager.resolveActivity(homeIntent,0)?.activityInfo?.packageName
        var stableHome=0;var after="";val until=SystemClock.elapsedRealtime()+6000
        while(SystemClock.elapsedRealtime()<until&&stableHome<3){delay(250);if(!owns())return;after=pkg();stableHome=if(after==homePackage)stableHome+1 else 0}
        val returned=stableHome>=3
        proof.put("foregroundAfterHome",after).put("returnedHome",returned).put("processState","not_force_stopped");save()
        if(!returned||CheckinRuntime.hasPending(svc))return
        val km=svc.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        var confirmed=false;var attempts=0
        while(!confirmed&&attempts<2){
            if(!owns()||CheckinRuntime.hasPending(svc))return
            attempts++;proof.put("lockAttempts",attempts).put("requiredStableMillis",2500).put("verificationWindowMillis",6500);save()
            val stable=LockScreenConfirmation()
            action(null){svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)}
            val lockUntil=SystemClock.elapsedRealtime()+6500
            while(!confirmed&&SystemClock.elapsedRealtime()<lockUntil){
                delay(250);if(!owns()||CheckinRuntime.hasPending(svc))return
                val locked=km.isKeyguardLocked;val off=!(svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                confirmed=stable.observe(SystemClock.elapsedRealtime(),off,locked)
            }
        }
        proof.put("lockConfirmed",confirmed).put("finishedAt",System.currentTimeMillis());save()
    }
}
class CheckinUnlockActivity:Activity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);val owner=CheckinRuntime.ownership.owner();if(owner==null||owner.run!=intent.getStringExtra("run")||owner.app=="ANA"){finish();return}
        val km=getSystemService(KEYGUARD_SERVICE) as KeyguardManager;if(km.isKeyguardSecure||km.isDeviceSecure){finish();return}
        setShowWhenLocked(true);setTurnScreenOn(true)
        setContentView(android.widget.TextView(this).apply{text="Tuffy正在准备签到…"})
        Handler(Looper.getMainLooper()).postDelayed({finish()},10_000)
    }
    override fun onWindowFocusChanged(focus:Boolean){super.onWindowFocusChanged(focus);if(!focus)return
        val owner=CheckinRuntime.ownership.owner();if(owner==null||owner.run!=intent.getStringExtra("run")||owner.app=="ANA"){finish();return}
        val km=getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        km.requestDismissKeyguard(this,object:KeyguardManager.KeyguardDismissCallback(){override fun onDismissSucceeded(){finish()};override fun onDismissCancelled(){finish()};override fun onDismissError(){finish()}})
    }
}
class CheckinForeground:Service(){
    companion object{fun start(ctx:Context,text:String){ContextCompat.startForegroundService(ctx,Intent(ctx,CheckinForeground::class.java).putExtra("text",text))};fun stop(ctx:Context){ctx.stopService(Intent(ctx,CheckinForeground::class.java))}}
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="STOP"){CheckinRuntime.stop();return START_NOT_STICKY}
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("checkin_running","签到运行状态",NotificationManager.IMPORTANCE_LOW))
        startForeground(9350,NotificationCompat.Builder(this,"checkin_running").setSmallIcon(R.drawable.ic_stat_ana).setContentTitle("Tuffy的自动百宝箱").setContentText(intent?.getStringExtra("text")).setOngoing(true).addAction(0,"停止签到",PendingIntent.getService(this,9351,Intent(this,CheckinForeground::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build());return START_NOT_STICKY
    }
}
