package com.anapocket.auto

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/** Longest real run on record was 96 min (Nov 3); three hours means something is stuck, not slow. */
private const val ROUTINE_WATCHDOG_MS = 3 * 60 * 60 * 1000L

/**
 * 常驻无障碍服务：系统绑定后保持存活，所有自动化动作都在此服务内执行。
 * 不依赖 PC / ADB，安装并授权后由系统闹钟即可独立运行。
 */
class AutoAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoAccessibilityService? = null
            private set

        @Volatile
        var currentRunId: String = ""
            private set

        @Volatile
        var stopRequested: Boolean = false
            private set

        @Volatile
        var isRunning: Boolean = false
            private set(value) {
                field=value
                instance?.let{if(value)CheckinRuntime.onAnaStarted(it) else CheckinRuntime.onAnaFinished(it)}
            }

        @Volatile var foregroundActivity: String = ""
            private set

        fun isConnected(): Boolean = instance != null

        fun trigger(reason: String): Boolean = triggerRoutine(reason)

        fun triggerRoutine(reason:String):Boolean { val s=instance?:return false; s.startDailyRoutine(reason); return true }

        fun triggerDailyAds(reason: String, maxAttempts: Int = DAILY_AD_LIMIT, manualNew: Boolean = false): Boolean {
            val s = instance ?: return false
            s.startDailyAds(reason, maxAttempts, manualNew)
            return true
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastSafetyScan=0L
    /** One event-driven safety scan at a time; each scan walks every window, so overlapping ones only add garbage. */
    private val safetyScanBusy=java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    var lastSummary: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = false
        runCatching {
            OfficialAdStore.reconcileInterruptedDays(this)
            if (DailyAdStore.reconcileInterrupted(this, TokyoDate.of()) > 0) DailyAdStore.setState(this, AdState.UNCONFIRMED)
        }
            .onFailure { Logger.e("ADTASK", "ledger recovery blocked", it) }
        Logger.init(this)
        Notifier.ensureChannel(this)
        val interruptedRoutine=RoutineStore.read(this)
        runCatching { RoutineStore.reconcile(this) }
        Logger.i("A11Y", "service connected")
        MemoryTrace.record(this,"service_connected",true)
        CheckinRuntime.serviceConnected(this)
        DailyDigest.schedule(this)
        Notifier.serviceRestored(this)
        try {
            Scheduler.schedule(this)
        } catch (t: Throwable) {
            Logger.e("A11Y", "schedule on connect failed", t)
        }
        // 闹钟在服务未连接时触发过 -> 现在补跑
        val pending = RunStore.pendingTrigger(this)
        if (pending != null) {
            Logger.i("A11Y", "resume pending trigger: $pending")
            RunStore.clearPendingTrigger(this)
            Scheduler.dispatchDaily(this, "$pending-resumed")
        } else {
            runCatching { AnaProcessRecovery.schedule(this,scope,interruptedRoutine) }
                .onFailure { Logger.e("PROCESS_RECOVERY","safe recovery unavailable",it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CheckinRuntime.windowEvent(event.packageName?.toString().orEmpty())
            val component = event.className?.toString().orEmpty()
            // Generic FrameLayout/View announcements must not erase the actual SDK Activity.
            if (component.contains("Activity") || component.endsWith("Dialog") ||
                AdSdkWindows.contains(component)) foregroundActivity = component
        }
        if(isRunning && android.os.SystemClock.elapsedRealtime()-lastSafetyScan>500 && safetyScanBusy.compareAndSet(false,true)){
            lastSafetyScan=android.os.SystemClock.elapsedRealtime()
            scope.launch{try{runCatching{AccountSafety.authorize(this@AutoAccessibilityService,"accessibility_event")}.onFailure{requestStop("safety_check_failed")}}finally{safetyScanBusy.set(false)}}
        }
        // 保留分阶段轮询，事件仅作窗口/内容更新遥测，避免事件风暴直接驱动状态迁移。
        if (event?.packageName?.toString() != "us.ana_pocket.ANAPocket") return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> DetailEvents.onWindowEvent()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> DetailEvents.onContentEvent()
        }
    }

    override fun onInterrupt() {
        Logger.w("A11Y", "onInterrupt")
    }

    override fun onDestroy() {
        CheckinRuntime.serviceLost()
        requestStop("service_destroyed")
        scope.cancel()
        instance = null
        Notifier.needService(this)
        Logger.w("A11Y", "service destroyed")
        super.onDestroy()
    }

    fun requestStop(reason:String="user") {
        stopRequested = true
        if(reason=="user")AnaProcessRecovery.markUserStop(this,currentRunId)
        Logger.w("RUN", "stop requested: "+reason)
    }


    @Synchronized private fun auxiliary(prefix:String,label:String,work:suspend(String)->Unit){
        if(isRunning){Logger.w("AUX","busy, ignored "+prefix);return}
        val run=prefix+java.util.UUID.randomUUID().toString();currentRunId=run;stopRequested=false;isRunning=true
        scope.launch{
            val overlay=OverlayKeeper(this@AutoAccessibilityService)
            @Suppress("DEPRECATION") val wake=(getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK,"tuffy:auxiliary")
            try{wake.acquire(20*60*1000L);overlay.show();RunForegroundService.start(this@AutoAccessibilityService,label);work(run)}
            catch(e:Exception){Logger.e("AUX","run="+run+" "+e.message,e);FaultStore.capture(this@AutoAccessibilityService,run,"auxiliary",e.message.orEmpty())}
            finally{overlay.hide();if(wake.isHeld)wake.release();if(currentRunId==run){isRunning=false;stopRequested=false;RunForegroundService.stop(this@AutoAccessibilityService)}}
        }
    }
    fun startSafetyReview()=auxiliary("REVIEW","复核人工处理结果") { run->
        val ok=AccountSafety.review(this,run)
        Logger.i("SAFETY_REVIEW","run="+run+" cleared="+ok+" no_tasks_started")
        startActivity(android.content.Intent(this,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
    fun startReadinessCheck()=auxiliary("WAKE","检查锁屏启动，不观看广告") { run->
        fun journal()=org.json.JSONObject().put("challenges",ChallengeStore.rows(this,TokyoDate.of())).put("daily",org.json.JSONArray().also{a->DailyAdStore.allAttempts(this,TokyoDate.of()).forEach{a.put(it.toJson())}}).put("login",LoginBonusStore.read(this,TokyoDate.of())).toString()
        val before=journal();val ready=DeviceReady.ensure(this,run)
        val ui=Ui(this);var ana=false
        if(ready.ready&&AccountSafety.authorize(this,"readiness_test")){ui.launchApp(StartupPolicy.ANA);repeat(16){if(!ana){kotlinx.coroutines.delay(500);ui.refreshCache();ana=AccountSafety.cleanNative(this,AccountSafety.frame(this))}}}
        val unchanged=before==journal()
        Evidence.write(this,"r44_readiness_"+run+".json",org.json.JSONObject().put("runId",run).put("ready",ready.ready).put("reason",ready.reason).put("nativeAna",ana).put("journalsUnchanged",unchanged).put("deviceTime",TokyoDate.time()).put("newAdDispatches",0).toString(2))
        Logger.i("READY_TEST","run="+run+" ready="+ready.ready+" nativeAna="+ana+" journalsUnchanged="+unchanged)
        val ok=ready.ready&&ana&&unchanged&&!stopRequested&&AccountSafety.authorize(this,"readiness_finish")
        Notifier.readinessResult(this,run,ok,if(!ready.ready)ready.reason else if(!ana)"ANA页面未稳定，任务未启动" else if(!unchanged)"记录发生变化，请检查" else AccountSafety.message(this))
        if(ok)TaskExit.finish(this,run,true,lockAfter=false)
    }
    fun resumeExistingChallengeCheck(){
        check(BuildConfig.DEBUG)
        auxiliary("RECOVER","恢复已记录的同一广告") { run->
            val date=TokyoDate.of();val a=ChallengeStore.rows(this,date)
            val pending=(0 until a.length()).map{a.getJSONObject(it)}.filter{it.optString("state") in setOf("AD_OPENING","UNCONFIRMED","JOINED")}.single()
            val cat=Cat.ofName(pending.getString("cat"))!!;val km=(pending.getInt("meters")+999)/1000
            val settings=Prefs.loadBlocking(this).copy(catKm=Prefs.loadBlocking(this).catKm+(cat.name to km))
            val result=ChallengeTaskController(this).run(run,listOf(cat),allCards=true,settingsOverride=settings).detail
            Evidence.write(this,"r44_existing_challenge_"+run+".txt",result)
            Notifier.challengeSummary(this,run,result)
            Logger.i("RECOVER_TEST","run="+run+" "+result.lineSequence().firstOrNull())
            TaskExit.finish(this,run,!ChallengeStore.hasPending(this,date)&&CategoryCompletionPolicy.covers(ChallengeStore.coverage(this,date,cat),km))
        }
    }

    @Synchronized
    fun startCategoryTest(cat:Cat,navigateOnly:Boolean,resumeScope:Boolean=false){
        check(BuildConfig.DEBUG){"debug_only_category_test"};if(isRunning)return
        check(navigateOnly||cat.longDistance){"acceptance_test_only_for_new_modes"}
        val run=(if(navigateOnly)"NAV" else "CH")+java.util.UUID.randomUUID().toString();val date=TokyoDate.of()
        currentRunId=run;stopRequested=false;isRunning=true
        scope.launch{
            val overlay=OverlayKeeper(this@AutoAccessibilityService)
            @Suppress("DEPRECATION") val wake=(getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK,"tuffy:category_test")
            try{
                wake.acquire(2*60*60*1000L);overlay.show();RunForegroundService.start(this@AutoAccessibilityService,"核验"+cat.title+"任务")
                val settings=Prefs.loadBlocking(this@AutoAccessibilityService)
                val stored=ChallengeStore.rows(this@AutoAccessibilityService,date)
                val pending=(0 until stored.length()).map{stored.getJSONObject(it)}.filter{it.optString("state") in setOf("AD_OPENING","UNCONFIRMED","JOINED")}
                val bound=pending.singleOrNull()?.takeIf{it.optString("cat")==cat.name} ?: if(resumeScope)(0 until stored.length()).map{stored.getJSONObject(it)}.filter{it.optString("cat")==cat.name&&it.optInt("boost")==5}.minByOrNull{it.optInt("meters")} else null
                var card:ChallengeCard?=null
                if(bound==null){
                    val engine=AutomationEngine(this@AutoAccessibilityService,settings)
                    check(engine.prepareFilteredCategory(cat)){"test_category_unreachable"}
                    card=engine.nextFilteredChallenge(cat,20_000_000,emptySet())
                }
                val meters=bound?.optInt("meters")?:card?.meters
                val meta=org.json.JSONObject().put("date",date).put("cat",cat.name).put("nativeFilteredNavigation",bound==null).put("resumingBoundAttempt",bound!=null)
                    .put("actualTitle",bound?.optString("title")?:card?.title?:"").put("meters",meters).put("navigateOnly",navigateOnly)
                Evidence.write(this@AutoAccessibilityService,"r43_category_"+cat.name+"_"+run+".json",meta.toString(2))
                if(!navigateOnly){
                    check(meters!=null&&meters>0){"no_real_today_remote_card"};val km=(meters+999)/1000
                    val result=ChallengeTaskController(this@AutoAccessibilityService).run(run,listOf(cat),allCards=true,adBudget=6,settingsOverride=settings.copy(catKm=settings.catKm+(cat.name to km))).detail
                    Evidence.write(this@AutoAccessibilityService,"challenge_result_"+run+".txt",result)
                    RunStore.appendHistory(this@AutoAccessibilityService,run,result);Notifier.challengeSummary(this@AutoAccessibilityService,run,result)
                    Logger.i("REMOTE_TEST","END run="+run+" cat="+cat+" "+result.lineSequence().firstOrNull())
                    TaskExit.finish(this@AutoAccessibilityService,run,CategoryCompletionPolicy.covers(ChallengeStore.coverage(this@AutoAccessibilityService,date,cat),km)&&!ChallengeStore.hasPending(this@AutoAccessibilityService,date))
                }
            }catch(e:Exception){Logger.e("REMOTE_TEST","run="+run+" "+e.message,e);Evidence.write(this@AutoAccessibilityService,"r43_category_error_"+run+".txt",e.toString());FaultStore.capture(this@AutoAccessibilityService,run,"category",e.message.orEmpty())}
            finally{overlay.hide();if(wake.isHeld)wake.release();if(currentRunId==run){isRunning=false;stopRequested=false;RunForegroundService.stop(this@AutoAccessibilityService)}}
        }
    }

    @Synchronized
    fun startStartupCheck(){
        check(BuildConfig.DEBUG){"debug_only_startup_test"};if(isRunning)return
        val run="LOGIN"+java.util.UUID.randomUUID().toString();val date=TokyoDate.of()
        currentRunId=run;stopRequested=false;isRunning=true
        scope.launch{
            val overlay=OverlayKeeper(this@AutoAccessibilityService)
            @Suppress("DEPRECATION") val wake=(getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK,"tuffy:startup_check")
            try{
                wake.acquire(10*60*1000L);overlay.show();RunForegroundService.start(this@AutoAccessibilityService,"检查每日签到与启动弹窗")
                val result=StartupFlow(this@AutoAccessibilityService).prepare(run)
                Evidence.write(this@AutoAccessibilityService,"r43_startup_"+run+".json",org.json.JSONObject().put("date",date).put("ok",result.ok).put("loginState",result.loginState).put("closedPopups",result.closedPopups).put("reason",result.reason).put("loginRecord",LoginBonusStore.read(this@AutoAccessibilityService,date)).toString(2))
                Logger.i("STARTUP_TEST","run="+run+" state="+result.loginState+" ok="+result.ok)
            }finally{overlay.hide();if(wake.isHeld)wake.release();if(currentRunId==run){isRunning=false;stopRequested=false;RunForegroundService.stop(this@AutoAccessibilityService)}}
        }
    }

    @Synchronized
    fun startDailyRoutine(reason:String="manual",originalMode:RoutineRunMode?=null){
        if(isRunning)return
        val mode=RoutineRunModes.choose(reason,originalMode)
        val run="RT"+java.util.UUID.randomUUID().toString()
        currentRunId=run;stopRequested=false;isRunning=true
        val main=scope.launch{
            var endState="BLOCKED";var endDetail=""
            try{
                AnaProcessRecovery.controllerStarting(this@AutoAccessibilityService,run,mode)
                if(!RunForegroundService.ensure(this@AutoAccessibilityService,"每日流程执行中")){
                    endState="DEFERRED" // AnaProcessRecovery owns this restart
                    if(reason.startsWith("persistent_"))AnaProcessRecovery.defer(this@AutoAccessibilityService,"foreground_start_temporarily_disallowed")
                    else AnaProcessRecovery.deferUnstarted(this@AutoAccessibilityService,run)
                    return@launch
                }
                Logger.i("ROUTINE","START run=$run reason=$reason")
                scope.launch{while(isRunning&&currentRunId==run){MemoryTrace.record(this@AutoAccessibilityService,"routine_active");kotlinx.coroutines.delay(60_000)}}
                val (outcome,summary)=DailyRoutineController(this@AutoAccessibilityService).run(run,mode.manual,mode.challengesOnly)
                endState=outcome.state;endDetail=outcome.detail
                AnaProcessRecovery.controllerFinished(this@AutoAccessibilityService,outcome.state)
                lastSummary=summary
                RunStore.appendHistory(this@AutoAccessibilityService,run,summary)
                Notifier.routineSummary(this@AutoAccessibilityService,run,summary)
                Logger.i("ROUTINE","END run=$run state="+outcome.state)
                if(outcome.state!="DONE")FaultStore.capture(this@AutoAccessibilityService,run,"routine",outcome.detail)
                TaskExit.finish(this@AutoAccessibilityService,run,outcome.state in setOf("DONE","PARTIALLY_COMPLETED"))
            }catch(e:Exception){endDetail=e.message.orEmpty();runCatching{AnaProcessRecovery.controllerFinished(this@AutoAccessibilityService,"BLOCKED")};Logger.e("ROUTINE","execution failed",e);Notifier.routineSummary(this@AutoAccessibilityService,run,"每日流程中止，请查看手机记录");runCatching{FaultStore.capture(this@AutoAccessibilityService,run,"routine",e.message.orEmpty())}}
            finally{
                if(currentRunId==run){isRunning=false;stopRequested=false;RunStore.setLastRunAt(this@AutoAccessibilityService,System.currentTimeMillis());RunForegroundService.stop(this@AutoAccessibilityService)}
                RoutineRetry.afterRun(this@AutoAccessibilityService,run,endState,endDetail)
                DailyDigest.evaluate(this@AutoAccessibilityService)
            }
        }
        // Each step has its own bound, the run had none: a hang would hold the phone and every check-in.
        scope.launch{
            kotlinx.coroutines.delay(ROUTINE_WATCHDOG_MS)
            if(!isRunning||currentRunId!=run)return@launch
            Logger.w("ROUTINE","watchdog run=$run exceeded "+ROUTINE_WATCHDOG_MS/60_000+"min; safe stop")
            RoutineRetry.watchdogRun=run;requestStop("watchdog")
            kotlinx.coroutines.delay(10*60_000L)
            if(isRunning&&currentRunId==run){Logger.w("ROUTINE","watchdog run=$run still active after stop; cancelling");main.cancel()}
        }
    }

    /**
     * 每日广告任务：固定 25 次上限，与挑战引擎完全独立。
     * 同一时刻只允许一个广告任务实例。
     */
    @Synchronized
    fun startDailyAds(reason: String, maxAttempts: Int = DAILY_AD_LIMIT, manualNew: Boolean = false) {
        if (isRunning) {
            Logger.w("ADTASK", "already running, ignore trigger: $reason")
            return
        }
        val st = Prefs.loadBlocking(this)
        if (!st.adEnabled) {
            Logger.i("ADTASK", "ad task disabled, skip trigger: $reason")
            return
        }
        val runId = "AD" + java.util.UUID.randomUUID().toString()
        currentRunId = runId
        stopRequested = false
        isRunning = true
        scope.launch {
            var result: DailyAdResult? = null
            try {
                check(RunForegroundService.ensure(this@AutoAccessibilityService,"广告任务 $runId")){"foreground_execution_unavailable"}
                Logger.i("ADTASK", "=== $runId START reason=$reason ===")
                val r = DailyAdTaskController(this@AutoAccessibilityService).run(runId, reason, maxAttempts, manualNew)
                result = r
                DailyAdStore.saveResult(this@AutoAccessibilityService, r)
                Logger.i("ADTASK", "=== $runId END ${r.line()} detail=${r.detail} ===")
                RunStore.appendHistory(this@AutoAccessibilityService, runId, "AD ${r.line()}")
                lastSummary = r.line()
                Notifier.dailyAdSummary(this@AutoAccessibilityService, r)
                TaskExit.finish(this@AutoAccessibilityService,runId,r.limitReached&&!r.stopped&&r.blockedReason.isBlank())
            } catch (t: Throwable) {
                Logger.e("ADTASK", "run crashed: ${t.message}", t)
            } finally {
                if (currentRunId == runId) {
                    isRunning = false
                    stopRequested = false
                }
                RunStore.setLastRunAt(this@AutoAccessibilityService, System.currentTimeMillis())
                if (result == null) {
                    val date = TokyoDate.of()
                    val c = runCatching { DailyAdStore.counters(this@AutoAccessibilityService, date) }.getOrNull()
                    if (c != null) {
                    val fallback = DailyAdResult(
                        runId, date, c.started, c.completed, c.rewardConfirmed,
                        c.failed, c.unconfirmed, false, false, "crashed", "crashed"
                    )
                    DailyAdStore.saveResult(this@AutoAccessibilityService, fallback)
                    Notifier.dailyAdSummary(this@AutoAccessibilityService, fallback)
                    } else Notifier.storageBlocked(this@AutoAccessibilityService)
                }
                RunForegroundService.stop(this@AutoAccessibilityService)
            }
        }
    }

    /** 独立详情诊断：只识别/关闭详情，不进入任何参加或广告领取流程。 */
    @Synchronized
    fun startDetailDiagnostic(observeOnly: Boolean) {
        if (isRunning) {
            Logger.w("RUN", "already running, ignore detail diagnostic")
            return
        }
        val runId = "D" + System.currentTimeMillis()
        currentRunId = runId
        stopRequested = false
        isRunning = true
        scope.launch {
            RunForegroundService.start(this@AutoAccessibilityService, "详情诊断 $runId")
            var summary = "crashed"
            try {
                Logger.i("DIAG", "=== $runId START observeOnly=$observeOnly ===")
                val engine = AutomationEngine(this@AutoAccessibilityService, Prefs.loadBlocking(this@AutoAccessibilityService))
                summary = engine.runDetailDiagnostic(runId, observeOnly)
            } catch (t: Throwable) {
                Logger.e("DIAG", "detail diagnostic crashed: ${t.message}", t)
                summary = "crashed:${t.javaClass.simpleName}"
            } finally {
                isRunning = false
                stopRequested = false
                lastSummary = "$runId $summary"
                Logger.i("DIAG", "=== $runId END $summary ===")
                Evidence.write(
                    this@AutoAccessibilityService,
                    "detail_result_$runId.json",
                    org.json.JSONObject().apply {
                        put("runId", runId)
                        put("observeOnly", observeOnly)
                        put("summary", summary)
                        put("finishedAt", System.currentTimeMillis())
                    }.toString(2)
                )
                RunForegroundService.stop(this@AutoAccessibilityService)
            }
        }
    }

    @Synchronized
    fun startChallenges(categories: List<Cat> = listOf(Cat.WALK, Cat.BIKE, Cat.MOV_ANY, Cat.CAR), allCards: Boolean = false, adBudget: Int = 6, pilot: Boolean = false, resumePilot: Cat? = null, reviewedNoGain: Boolean = false, reviewedReceipt: Boolean = false, reviewedInitial: Boolean = false, reviewedTitle: String? = null, settingsOverride:AppSettings?=null) {
        if (isRunning) return
        val runId = "CH" + java.util.UUID.randomUUID().toString()
        currentRunId = runId; stopRequested = false; isRunning = true
        scope.launch {
            try {
                check(RunForegroundService.ensure(this@AutoAccessibilityService,"挑战接取与 Boost")){"foreground_execution_unavailable"}
                Logger.i("CHBOOST", "START run=$runId categories=$categories budget=$adBudget pilot=$pilot")
                lastSummary = if (BuildConfig.DEBUG && resumePilot != null) ChallengeTaskController(this@AutoAccessibilityService).continuePilot(runId, resumePilot, reviewedNoGain, reviewedReceipt, reviewedInitial, reviewedTitle)
                    else ChallengeTaskController(this@AutoAccessibilityService).run(runId, categories, allCards, adBudget, pilot,settingsOverride).detail
                Evidence.write(this@AutoAccessibilityService, "challenge_result_" + runId + ".txt", lastSummary)
                RunStore.appendHistory(this@AutoAccessibilityService, runId, lastSummary)
                Notifier.challengeSummary(this@AutoAccessibilityService, runId, lastSummary)
                val settings=settingsOverride?:Prefs.loadBlocking(this@AutoAccessibilityService)
                TaskExit.finish(this@AutoAccessibilityService,runId,allCards&&!pilot&&!ChallengeStore.hasPending(this@AutoAccessibilityService,TokyoDate.of())&&categories.all{CategoryCompletionPolicy.covers(ChallengeStore.coverage(this@AutoAccessibilityService,TokyoDate.of(),it),settings.kmOf(it))})
            } catch (t: Throwable) {
                Logger.e("CHBOOST", "controller crashed", t)
                Notifier.challengeSummary(this@AutoAccessibilityService, runId, "挑战执行中止：" + t.message)
            } finally {
                isRunning = false; stopRequested = false
                RunForegroundService.stop(this@AutoAccessibilityService)
            }
        }
    }

    fun startRun(reason: String) {
        Logger.w("RUN", "challenge execution paused; ignoring trigger: $reason")
        return
    }

    @Synchronized
    private fun retainedChallengeRun(reason: String) {
        if (isRunning) {
            Logger.w("RUN", "already running, ignore trigger: $reason")
            return
        }
        val st = Prefs.loadBlocking(this)
        val bypass = reason.contains("manual") || reason.contains("selftest")
        if (!st.enabled && !bypass) {
            Logger.i("RUN", "disabled in settings, skip trigger: $reason")
            return
        }
        if (!bypass) {
            val last = RunStore.lastRunAt(this)
            if (last > 0 && System.currentTimeMillis() - last < 3L * 60 * 60 * 1000) {
                Logger.i("RUN", "ran within 3h, skip trigger: $reason")
                return
            }
        }

        val runId = "R" + System.currentTimeMillis()
        currentRunId = runId
        stopRequested = false
        isRunning = true

        scope.launch {
            var outcome: RunOutcome? = null
            RunForegroundService.start(this@AutoAccessibilityService, "执行中 $runId")
            try {
                Logger.i("RUN", "=== $runId START reason=$reason ===")
                val queue = QueueBuilder.build(this@AutoAccessibilityService, st).toMutableList()
                Logger.i("RUN", "queue size=${queue.size} :: " + queue.joinToString(",") { it.id })
                Evidence.write(this@AutoAccessibilityService, "settings_snapshot.json", st.toJson())
                Evidence.write(this@AutoAccessibilityService, "queue_${runId}_before.json", AutoTask.listToJson(queue))
                RunStore.saveQueue(this@AutoAccessibilityService, runId, queue)

                val engine = AutomationEngine(this@AutoAccessibilityService, st)
                outcome = engine.execute(runId, queue)
                RunStore.saveQueue(this@AutoAccessibilityService, runId, queue)
                Evidence.write(this@AutoAccessibilityService, "queue_${runId}_after.json", AutoTask.listToJson(queue))
            } catch (t: Throwable) {
                Logger.e("RUN", "run crashed: ${t.message}", t)
            } finally {
                isRunning = false
                val o = outcome ?: RunOutcome(runId, 0, 0, 0, 0, 0, stopRequested, "crashed")
                stopRequested = false
                lastSummary = o.line()
                RunStore.setLastRunAt(this@AutoAccessibilityService, System.currentTimeMillis())
                RunStore.record(this@AutoAccessibilityService, runId, o.line())
                RunStore.appendHistory(this@AutoAccessibilityService, runId, o.line())
                Logger.i("RUN", "=== $runId END: ${o.line()} ===")
                Logger.i("RUN", "=== $runId DETAIL: ${o.detail} ===")
                Evidence.write(
                    this@AutoAccessibilityService,
                    "result_$runId.json",
                    org.json.JSONObject().apply {
                        put("runId", runId)
                        put("summary", o.line())
                        put("detail", o.detail)
                        put("stopped", o.stopped)
                        put("finishedAt", System.currentTimeMillis())
                    }.toString(2)
                )
                Notifier.summary(this@AutoAccessibilityService, o)
                RunForegroundService.stop(this@AutoAccessibilityService)
            }
        }
    }
}
