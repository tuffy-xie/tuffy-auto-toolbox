package com.anapocket.auto

import android.content.Context
import android.graphics.Rect
import android.os.Build
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Local, bounded evidence. No credentials, no automatic upload and no changes to task ledgers. */
object FaultStore {
    private fun root(c:Context)=File(c.getExternalFilesDir(null)?:c.filesDir,"faults").apply{mkdirs()}
    private fun recentLog():String = runCatching {
        RandomAccessFile(File(Logger.path()),"r").use { r ->
            val n=minOf(r.length(),512*1024L).toInt();r.seek(r.length()-n)
            ByteArray(n).also{r.readFully(it)}.toString(Charsets.UTF_8)
        }
    }.getOrDefault(Logger.tail(500)).let(DiagnosticPrivacy::redact)
    private fun state(c:Context):JSONObject {
        val date=TokyoDate.of()
        return JSONObject().put("date",date).put("deviceTime",TokyoDate.time())
            .put("version",BuildConfig.VERSION_NAME).put("model",Build.MODEL).put("android",Build.VERSION.RELEASE)
            .put("settings",JSONObject(Prefs.loadBlocking(c).toJson()))
            .put("checkinConfig",JSONObject().also{all->CheckinApp.entries.forEach{app->val s=CheckinStore(c,app);val cfg=s.config();all.put(app.name,JSONObject().put("enabled",cfg.enabled).put("hour",cfg.hour).put("minute",cfg.minute).put("generation",cfg.generation).put("dispatch",s.dispatchInfo(date)))}})
            .put("officialObservation",OfficialAdStore.observation(c,date))
            .put("challengeScans",ChallengeScanStore.audit(c,date))
            .put("dailyAttempts",org.json.JSONArray().also{a->DailyAdStore.allAttempts(c,date).forEach{a.put(it.toJson())}})
            .put("accountSafety",AccountSafetyStore.pending(c)).put("challenges",ChallengeStore.rows(c,date)).put("login",LoginBonusStore.read(c,date)).put("routine",RoutineStore.read(c,date))
    }
    private fun prune(c:Context) {
        val base=root(c).canonicalFile
        var bytes=base.walkTopDown().filter{it.isFile}.sumOf{it.length()}
        val dirs=base.listFiles()?.filter{it.isDirectory}?.sortedBy{it.lastModified()}.orEmpty()
        var count=dirs.size
        for(d in dirs){
            if(count<=30&&bytes<=128L*1024*1024)break
            if(d.canonicalFile.parentFile!=base)continue
            val size=d.walkTopDown().filter{it.isFile}.sumOf{it.length()}
            if(d.deleteRecursively()){bytes-=size;count--}
        }
    }
    suspend fun capture(svc:AutoAccessibilityService,run:String,module:String,reason:String,attempt:String=""):String? {
        return try {
            val ui=Ui(svc);ui.refreshCache();val pkg=ui.currentPackage()
            val locked=(svc.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
            val dir=File(root(svc),DiagnosticPrivacy.safeName(run+"_"+module+"_"+attempt+"_"+System.currentTimeMillis())).apply{mkdirs()}
            val metadata=state(svc).put("runId",run).put("module",module).put("attempt",attempt).put("reason",reason)
                .put("foregroundPackage",pkg).put("foregroundActivity",AutoAccessibilityService.foregroundActivity)
                .put("captureSource","real_accessibility_screen_no_fixture").put("screenshotSaved",false)
            if(DiagnosticPrivacy.canCapture(pkg,locked)) {
                val nodes=ui.foregroundNodes().filter{it.isVisibleToUser}.joinToString("\n") { n->
                    val b=Rect();n.getBoundsInScreen(b)
                    "${n.className} id=${n.viewIdResourceName} enabled=${n.isEnabled} clickable=${n.isClickable} bounds=$b actions=${n.actionList.map{it.id}} label="+
                        (if(n.isPassword||n.isEditable)"[PASSWORD_REDACTED]" else DiagnosticPrivacy.redact(ui.label(n).take(500)))
                }
                val png=ui.captureVisual(includePng=true).png
                ui.refreshCache()
                if(ui.currentPackage()==pkg){
                    File(dir,"nodes.txt").writeText(nodes)
                    if(png!=null){File(dir,"screen.png").writeBytes(png);metadata.put("screenshotSaved",true)}
                    else metadata.put("screenshotOmitted","accessibility_capture_unavailable")
                }else metadata.put("screenshotOmitted","foreground_changed_during_capture")
            } else metadata.put("screenshotOmitted","unrelated_app_or_locked_screen")
            File(dir,"manifest.json").writeText(DiagnosticPrivacy.redact(metadata.toString(2)))
            File(dir,"recent.log").writeText(recentLog());prune(svc)
            Logger.i("FAULT","saved run=$run module=$module reason=$reason folder=${dir.name}")
            dir.absolutePath
        } catch(e:Exception){Logger.w("FAULT","capture unavailable: "+e.javaClass.simpleName);null}
    }
    fun export(c:Context):File {
        check(!AutoAccessibilityService.isRunning&&CheckinRuntime.activeApp==null){"请先停止任务，再导出故障包"}
        val out=File(c.getExternalFilesDir(null)?:c.filesDir,"exports").apply{mkdirs()}
        val dst=File(out,"Tuffy_diagnostics_"+System.currentTimeMillis()+".zip")
        val base=root(c).canonicalFile
        ZipOutputStream(dst.outputStream().buffered()).use{z->
            fun add(name:String,data:ByteArray){z.putNextEntry(ZipEntry(name));z.write(data);z.closeEntry()}
            add("current_state.json",DiagnosticPrivacy.redact(state(c).toString(2)).toByteArray())
            add("recent.log",recentLog().toByteArray())
            CheckinDiagnostics.append(c,::add)
            add("README.txt","故障现场来自实际手机。screen.png缺失原因见manifest.json；checkins含各新App最近最多10次记录，附件总量最多64MB。日志已脱敏，不含Telegram设置或密钥。截图可能含广告或会员页面，请确认后手动分享。".toByteArray())
            for(f in base.walkTopDown().filter{it.isFile}){
                if(!f.canonicalFile.toPath().startsWith(base.toPath()))continue
                if(f.extension !in setOf("json","txt","log","png"))continue
                val data=if(f.extension=="png")f.readBytes() else DiagnosticPrivacy.redact(f.readText()).toByteArray()
                add("faults/"+f.relativeTo(base).invariantSeparatorsPath,data)
            }
        }
        out.listFiles()?.filter{it!=dst&&it.extension=="zip"}?.sortedByDescending{it.lastModified()}?.drop(2)?.forEach{it.delete()}
        return dst
    }
}

object TaskExit {
    suspend fun finish(svc:AutoAccessibilityService,run:String,finished:Boolean,lockAfter:Boolean=true) {
        val ui=Ui(svc);ui.refreshCache();val date=TokyoDate.of()
        fun pending()=AccountSafetyStore.pending(svc)!=null||(TravelSelection.hasSelectedPending(svc,date)||TravelSelection.hasUnresolvedAd(svc,date))||OfficialAdStore.attempts(svc,date).any{it.state in setOf(AdState.AD_OPENING,AdState.AD_PLAYING,AdState.AD_CLOSE_AVAILABLE)}
        val n=ui.foregroundNodes().filter{it.isVisibleToUser};val labels=n.map{ui.label(it)}
        val ids=n.mapNotNull{it.viewIdResourceName?.substringAfterLast('/')}.toSet()
        val native=HomeNavigationPolicy.nativeDetail(ui.currentPackage(),labels,ids)||labels.any{it in setOf("ホーム","ためる","参加可能","広告視聴")}
        if(!TaskExitPolicy.allowed(finished,AutoAccessibilityService.stopRequested,pending(),!native&&AdUiClose(svc).isAdSurface(),ui.currentPackage()==StartupPolicy.ANA))return
        val proof=JSONObject().put("runId",run).put("date",date).put("startedAt",System.currentTimeMillis()).put("lockAfter",lockAfter).put("nextStartLaunchesAna",true)
        fun save(){Evidence.write(svc,"exit_"+run+".json",proof.toString(2))}
        proof.put("cleanup",ManagedAppCleanup.run(svc,StartupPolicy.ANA,{!AutoAccessibilityService.stopRequested&&AutoAccessibilityService.currentRunId==run&&AccountSafetyStore.pending(svc)==null},{block->val owner=CheckinRuntime.ownership.owner();if(owner?.app=="ANA"&&owner.run==run&&!AutoAccessibilityService.stopRequested)CheckinRuntime.ownership.action(owner,android.os.SystemClock.elapsedRealtime(),false,block) else false}));save()
        var left=false
        repeat(2){if(!left){ui.home();delay(800);ui.refreshCache();val p=ui.currentPackage();left=!p.isNullOrBlank()&&p!=StartupPolicy.ANA}}
        proof.put("returnedHome",left).put("foregroundAfterExit",ui.currentPackage()).put("exitedAt",System.currentTimeMillis());save()
        Logger.i("EXIT","run=$run returnedHome=$left")
        if(!left){FaultStore.capture(svc,run,"exit","home_action_not_confirmed");return}
        if(!lockAfter)return
        if(!TaskExitPolicy.canLock(left,AutoAccessibilityService.stopRequested,pending(),AutoAccessibilityService.currentRunId==run)){
            proof.put("lockSkipped","state_changed_before_lock");save();return
        }
        val km=svc.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val pm=svc.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        var confirmed=false;var tries=0
        while(!confirmed&&tries<2){
            if(!TaskExitPolicy.canLock(left,AutoAccessibilityService.stopRequested,pending(),AutoAccessibilityService.currentRunId==run))break
            tries++;proof.put("lockRequestedAt",System.currentTimeMillis()).put("lockAttempts",tries);save()
            val accepted=android.os.Build.VERSION.SDK_INT>=28&&svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            proof.put("lockActionAccepted",accepted)
            val stable=LockScreenConfirmation()
            val verifyUntil=android.os.SystemClock.elapsedRealtime()+6500L
            proof.put("requiredStableMillis",2500).put("verificationWindowMillis",6500)
            while(!confirmed&&android.os.SystemClock.elapsedRealtime()<verifyUntil){
                if(!TaskExitPolicy.canLock(left,AutoAccessibilityService.stopRequested,pending(),AutoAccessibilityService.currentRunId==run)){proof.put("lockSkipped","state_changed_during_verification");save();return}
                delay(250)
                confirmed=stable.observe(android.os.SystemClock.elapsedRealtime(),!pm.isInteractive,km.isKeyguardLocked)
            }
        }
        proof.put("screenOff",!pm.isInteractive).put("keyguardLocked",km.isKeyguardLocked).put("lockConfirmed",confirmed).put("finishedAt",System.currentTimeMillis());save()
        Logger.i("EXIT","run=$run lockConfirmed=$confirmed attempts=$tries screenOff="+!pm.isInteractive+" keyguard="+km.isKeyguardLocked)
        if(!confirmed){FaultStore.capture(svc,run,"exit","task_complete_but_lock_not_confirmed");Notifier.finishIssue(svc,run)}
    }
}
