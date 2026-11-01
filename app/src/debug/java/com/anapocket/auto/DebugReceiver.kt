package com.anapocket.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/**
 * 仅存在于 debug 变体的本机调试通道（release 清单不合并，正式 APK 无此外部入口）。
 *
 * 安全改进（对应验收意见 1）：
 *  - 不再使用固定口令 "anapocket"；
 *  - 每次安装随机生成 32 位十六进制令牌，存于应用私有 SharedPreferences，
 *    首次使用时写入日志（tag=DEBUG），仅本机 adb 可读；
 *  - 比较使用 MessageDigest.isEqual，避免时序侧信道。
 */
class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val request = intent ?: return
        Logger.init(context)
        val expected = token(context)
        val got = request.getStringExtra("token") ?: ""
        if (!MessageDigest.isEqual(expected.toByteArray(), got.toByteArray())) {
            Logger.w("DEBUG", "bad token, ignored")
            return
        }
        val cmd = request.getStringExtra("cmd") ?: return
        val svc = AutoAccessibilityService.instance
        if (svc == null) {
            Logger.w("DEBUG", "cmd=$cmd but accessibility service not connected")
            return
        }
        Logger.i("DEBUG", "cmd=$cmd")
        val ui = Ui(svc)

        CoroutineScope(Dispatchers.Default).launch {
            try {
            when (cmd) {
                "r47-selection-test" -> {R47SelectionProbe.run(context)}
                "r47-selection-status" -> {val s=Prefs.loadBlocking(context);val a=org.json.JSONArray();Cat.values().forEach{cat->a.put(org.json.JSONObject().put("cat",cat.name).put("selected",s.catOn(cat)).put("km",s.kmOf(cat)))};Evidence.write(context,"r47_selection_status.json",org.json.JSONObject().put("settings",org.json.JSONObject(s.toJson())).put("categories",a).put("queue",org.json.JSONArray(TravelSelectionPolicy.queue(TravelSelectionPolicy.categoryOrder,s).map{it.name})).toString(2))}
                "r47-stale-alarm" -> {val app=CheckinApp.from(request.getStringExtra("app"))?:error("app_required");context.sendBroadcast(Intent(context,CheckinAlarmReceiver::class.java).putExtra("app",app.name).putExtra("generation",request.getLongExtra("generation",-1)).putExtra("scheduledAt",System.currentTimeMillis()))}
                "r46-checkin-store-test" -> {CheckinStoreProbe.run(context)}
                "r46-ocr-file" -> {
                    val name=request.getStringExtra("file")?:error("file_required");check(Regex("r46_input_[a-z0-9_]+\\.png").matches(name))
                    val bytes=File(context.getExternalFilesDir(null),"evidence/"+name).readBytes()
                    val lines=CheckinOcr.normalizeGreenStatus(bytes,CheckinOcr.read(context.applicationContext,bytes))
                    val dart=CheckinOcr.dartPull(bytes,lines)
                    Evidence.write(context,"r46_ocr_"+name+".json",org.json.JSONObject().put("source","on_device_japanese_ocr_of_real_saved_capture").put("dartPull",dart?.let{org.json.JSONObject().put("x",it.arrow.x).put("y",it.arrow.y).put("endY",it.endY)}).put("lines",org.json.JSONArray().also{a->lines.forEach{l->a.put(org.json.JSONObject().put("text",l.text).put("left",l.left).put("top",l.top).put("right",l.right).put("bottom",l.bottom))}}).toString(2))
                }
                "r46-checkin-run" -> {CheckinRuntime.request(context,CheckinApp.from(request.getStringExtra("app"))?:error("app_required"))}
                "r46-checkin-stop" -> {CheckinRuntime.stop()}
                "r46-checkin-isolation" -> {CheckinNativeProbe.run(context)}
                "r47-schedule-status" -> {
                    val wm=androidx.work.WorkManager.getInstance(context)
                    val out=org.json.JSONObject().put("capturedAt",System.currentTimeMillis()).put("anaTime",Prefs.loadBlocking(context).let{String.format("%02d:%02d",it.hour,it.minute)})
                    listOf("ana_backup_daily","checkin_backup_RAKUTEN","checkin_backup_STEAK").forEach{name->out.put(name,org.json.JSONArray().also{a->wm.getWorkInfosForUniqueWork(name).get(3,java.util.concurrent.TimeUnit.SECONDS).forEach{w->a.put(org.json.JSONObject().put("id",w.id.toString()).put("state",w.state.name).put("nextScheduleTimeMillis",w.nextScheduleTimeMillis))}})}
                    out.put("ordinarySchedules",org.json.JSONObject(context.getSharedPreferences("checkin_schedules",0).all))
                    Evidence.write(context,"r47_schedule_status.json",out.toString(2))
                }
                "r46-checkin-status" -> {
                    val c=Prefs.loadBlocking(context)
                    val body=org.json.JSONObject().put("phoneTime",TokyoDate.time()).put("businessDate",TokyoDate.of()).put("anaTime",String.format("%02d:%02d",c.hour,c.minute)).put("anaRunning",AutoAccessibilityService.isRunning).put("uiOwner",CheckinRuntime.ownership.owner()?.app).put("protected",CheckinRuntime.protected(context))
                    CheckinApp.entries.forEach{app->val s=CheckinStore(context,app);body.put(app.name,org.json.JSONObject().put("enabled",s.config().enabled).put("hour",s.config().hour).put("minute",s.config().minute).put("tasks",org.json.JSONArray().also{a->s.all().forEach{a.put(it)}}).put("hold",s.hold()))}
                    Evidence.write(context,"r46_status_"+System.currentTimeMillis()+".json",body.toString(2))
                }
                "r43-fault-capture" -> {check(!AutoAccessibilityService.isRunning);FaultStore.capture(svc,"CAP"+System.currentTimeMillis(),"manual_native_diagnostic","read_only_native_capture_no_ad")}
                "r43-export" -> {check(!AutoAccessibilityService.isRunning);FaultStore.export(context)}
                "r44-resume-existing" -> {svc.resumeExistingChallengeCheck()}
                "r44-readiness" -> {svc.startReadinessCheck()}
                "r44-selftest" -> {check(!AutoAccessibilityService.isRunning);SafetySelfTest.run(context)}
                "r43-startup-only" -> {check(!AutoAccessibilityService.isRunning);svc.startStartupCheck()}
                "r43-snapshot" -> {
                    check(!AutoAccessibilityService.isRunning)
                    val date=request.getStringExtra("date")?:TokyoDate.of();check(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date));val all=org.json.JSONArray();DailyAdStore.allAttempts(context,date).forEach{all.put(it.toJson())}
                    val body=org.json.JSONObject().put("date",date).put("deviceTime",TokyoDate.time()).put("source","real_production_read_only_no_fixture")
                        .put("legacyAttempts",org.json.JSONArray().also{a->DailyAdStore.attempts(context,date).forEach{a.put(it.toJson())}})
                        .put("allAttempts",all).put("officialObservation",OfficialAdStore.observation(context,date)).put("officialAttempts",org.json.JSONArray().also{a->OfficialAdStore.attempts(context,date).forEach{a.put(it.toJson())}})
                        .put("challenges",ChallengeStore.rows(context,date)).put("login",LoginBonusStore.read(context,date)).put("routine",RoutineStore.read(context,date)).put("settings",org.json.JSONObject(Prefs.loadBlocking(context).toJson()))
                    Evidence.write(context,"r43_production_"+System.currentTimeMillis()+".json",body.toString(2))
                }
                "r43-remote-nav","r43-remote-one","r43-remote-scope","r43-category-nav" -> {
                    val cat=Cat.ofName(request.getStringExtra("cat"))?:error("cat_required")
                    svc.startCategoryTest(cat,cmd!="r43-remote-one"&&cmd!="r43-remote-scope",cmd=="r43-remote-scope")
                }
                "routine-selftest" -> { check(!AutoAccessibilityService.isRunning); RoutineSelfTest.run(context) }
                "routine-native-nav" -> {
                    check(!AutoAccessibilityService.isRunning)
                    val id="NAV"+System.currentTimeMillis();val date=TokyoDate.of()
                    fun challengeStarts():Int {val a=ChallengeStore.rows(context,date);return (0 until a.length()).sumOf{a.getJSONObject(it).optJSONArray("ads")?.length()?:0}}
                    val dailyBefore=DailyAdStore.counters(context,date).started;val challengeBefore=challengeStarts()
                    val loginBefore=LoginBonusStore.read(context,date)?.optLong("startedAt",0)?:0L
                    val startup=StartupFlow(svc).prepare(id)
                    val passed=startup.ok && AutomationEngine(svc,Prefs.loadBlocking(context)).prepareFilteredCategory(Cat.WALK)
                    val dailyNew=DailyAdStore.counters(context,date).started-dailyBefore;val challengeNew=challengeStarts()-challengeBefore
                    val loginAfter=LoginBonusStore.read(context,date)
                    Evidence.write(context,"routine_navigation_"+id+".json",org.json.JSONObject().put("nativeFilteredNavigation",passed)
                        .put("dateJst",date).put("deviceTimeJst",TokyoDate.time()).put("newDailyAdStarts",dailyNew).put("newChallengeAdStarts",challengeNew)
                        .put("newLoginAdReserved",(loginAfter?.optLong("startedAt",0)?:0L)>loginBefore).put("loginState",loginAfter?.optString("state")?:"NOT_CHECKED")
                        .put("closedStartupPopups",startup.closedPopups).put("purpose","native_navigation_no_challenge_or_daily_watch_button").toString(2))
                }
                "telegram-selftest" -> { check(!AutoAccessibilityService.isRunning); TelegramSelfTest.run(context) }
                "challenge-pilot" -> svc.startChallenges(listOf(Cat.ofName(request.getStringExtra("cat")) ?: Cat.WALK), pilot = true)
                "challenge-continue-pilot" -> svc.startChallenges(resumePilot = Cat.ofName(request.getStringExtra("cat")) ?: Cat.WALK)
                "challenge-review-no-gain" -> svc.startChallenges(resumePilot = Cat.ofName(request.getStringExtra("cat")) ?: error("cat_required"), reviewedNoGain = true)
                "challenge-review-receipt" -> svc.startChallenges(resumePilot = Cat.ofName(request.getStringExtra("cat")) ?: error("cat_required"), reviewedReceipt = true)
                "challenge-review-initial" -> svc.startChallenges(resumePilot = Cat.ofName(request.getStringExtra("cat")) ?: error("cat_required"), reviewedInitial = true, reviewedTitle = request.getStringExtra("title") ?: error("title_required"))
                "challenge-run" -> svc.startChallenges(adBudget = request.getIntExtra("budget", 6))
                "run" -> svc.startRun("manual")
                "ads-close-only" -> Logger.i("DEBUG", "close-only=" + DailyAdTaskController(svc).closeObservedOnly("CLOSE" + System.currentTimeMillis()))
                "ads-nav" -> DebugAdProbe.navigate(svc)
                "ads-dump" -> DebugAdProbe.dump(svc)
                "ads-single" -> svc.startDailyAds("manual-single-test", 1, manualNew = true)
                "stop" -> svc.requestStop()
                "challenge-ledger-snapshot" -> {
                    check(!AutoAccessibilityService.isRunning) { "readonly_export_requires_idle_controller" }
                    val date = request.getStringExtra("date") ?: TokyoDate.of()
                    check(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) { "invalid_ledger_date" }
                    val dir = File(context.filesDir.parentFile, "shared_prefs")
                    val dailyFile = File(dir, "ana_daily_ads.xml")
                    val challengeFile = File(dir, "challenge_boost_v1.xml")
                    val dailyBefore = dailyFile.readBytes()
                    val challengeBefore = challengeFile.readBytes()
                    val rows = ChallengeStore.rows(context, date)
                    val counts = DailyAdStore.counters(context, date)
                    check(!AutoAccessibilityService.isRunning && dailyBefore.contentEquals(dailyFile.readBytes()) &&
                        challengeBefore.contentEquals(challengeFile.readBytes())) { "ledger_changed_during_readonly_export" }
                    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    val body = org.json.JSONObject().put("readOnly", true).put("dateJst", date)
                        .put("deviceDateJst", TokyoDate.of()).put("deviceTimeJst", TokyoDate.time())
                        .put("source", "authenticated_debug_reader_of_existing_production_preferences_no_fixture_namespace")
                        .put("challenges", rows).put("dailyCounters", counts.toString())
                        .put("loginBonus",LoginBonusStore.read(context,date)).put("dailyRoutine",RoutineStore.read(context,date))
                        .put("routineHistory",RoutineStore.history(context))
                        .put("dailyPreferencesXml", String(dailyBefore, Charsets.UTF_8))
                        .put("challengePreferencesXml", String(challengeBefore, Charsets.UTF_8))
                        .put("dailyFileSha256", hash(dailyBefore)).put("challengeFileSha256", hash(challengeBefore))
                        .put("rawFilesUnchangedAfterRead", true)
                    val name = "readonly_production_ledger_" + date + "_" + System.currentTimeMillis() + ".json"
                    Evidence.write(context, name, body.toString(2))
                    Logger.i("DEBUG", "READ_ONLY production ledger exported: $name tasks=" + rows.length())
                }
                "dump" -> {
                    val d = ui.dump()
                    val f = File(context.getExternalFilesDir(null) ?: context.filesDir, "ui_dump.txt")
                    f.writeText(d)
                    Logger.i("DEBUG", "dump -> ${f.absolutePath} (${d.length} chars)")
                }
                "dump-nodes" -> {
                    val body = ui.foregroundNodes().joinToString("\n") {
                        val r = android.graphics.Rect(); it.getBoundsInScreen(r)
                        "${it.className} id=${it.viewIdResourceName} visible=${it.isVisibleToUser} enabled=${it.isEnabled} clickable=${it.isClickable} bounds=$r label=${ui.label(it)}"
                    }
                    File(context.getExternalFilesDir(null) ?: context.filesDir, "ui_nodes.txt").writeText(body)
                }
                "back" -> Logger.i("DEBUG", "back=${ui.back()}")
                "home" -> Logger.i("DEBUG", "home=${ui.home()}")
                "tap" -> {
                    val x = request.getIntExtra("x", -1)
                    val y = request.getIntExtra("y", -1)
                    if (x >= 0 && y >= 0) Logger.i("DEBUG", "tap $x,$y -> ${ui.tap(x, y)}")
                    else Logger.w("DEBUG", "tap without coords")
                }
                "click" -> {
                    val text = request.getStringExtra("text")
                    if (text.isNullOrBlank()) {
                        Logger.w("DEBUG", "click without text")
                    } else {
                        val n = ui.find(listOf(text))
                        if (n == null) Logger.w("DEBUG", "not found: $text")
                        else Logger.i("DEBUG", "click '$text' -> ${ui.click(n)}")
                    }
                }
                "swipe" -> {
                    val x1 = request.getIntExtra("x1", 360)
                    val y1 = request.getIntExtra("y1", 1200)
                    val x2 = request.getIntExtra("x2", 360)
                    val y2 = request.getIntExtra("y2", 600)
                    Logger.i("DEBUG", "swipe -> ${ui.swipe(x1, y1, x2, y2, 320)}")
                }
                else -> Logger.w("DEBUG", "unknown cmd=$cmd")
            }
            }catch(e:Exception){Logger.e("DEBUG","command failed: "+cmd,e);Evidence.write(context,"debug_command_error_"+System.currentTimeMillis()+".txt",cmd+"\n"+e.toString())}
        }
    }

    private fun token(ctx: Context): String {
        val sp = ctx.getSharedPreferences("ana_debug", Context.MODE_PRIVATE)
        var t = sp.getString("token", null)
        if (t.isNullOrBlank()) {
            val hex = "0123456789abcdef"
            t = (1..32).map { hex[(Math.random() * 16).toInt()] }.joinToString("")
            sp.edit().putString("token", t).apply()
            Logger.i("DEBUG", "generated per-install token=$t")
        }
        return t
    }
}
