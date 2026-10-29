package com.anapocket.auto

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import kotlinx.coroutines.runBlocking

/** Controlled on-device harness: isolated ledger, scripted ad transport, zero live ad starts. */
class AdTestInstrumentation : Instrumentation() {
    private var args = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); args = arguments ?: Bundle(); start() }
    override fun onStart() {
        val lines = mutableListOf("PROCESS_PID=" + android.os.Process.myPid())
        val ctx = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences("r36_fixture_" + name, mode)
        }
        val date = TokyoDate.of()
        val runId = "DEVICE-FIXTURE-" + System.currentTimeMillis()
        try {
            when (args.getString("phase", "suite")) {
                "ui", "ui-error" -> runBlocking {
                    val expectError = args.getString("phase") == "ui-error"
                    // Instrumentation restarts the target process; use its own Accessibility automation connection.
                    val automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    val ui = FixtureUi(automation)
                    ctx.getSharedPreferences("ana_daily_ads", 0).edit().clear().commit()
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                        "am start -n com.anapocket.auto.test/com.anapocket.auto.AdFixtureActivity --ez networkFailure " + expectError)).use { it.readBytes() }
                    var ready = false
                    repeat(30) {
                        if (ui.foregroundNodes().any { ui.label(it) == "視聴する" }) ready = true
                        if (!ready) kotlinx.coroutines.delay(400L)
                    }
                    check(ready) { "fixture_not_ready" }
                    val a = checkNotNull(DailyAdStore.beginAttempt(ctx, date, runId))
                    check(ui.click(checkNotNull(ui.foregroundNodes().firstOrNull { ui.label(it) == "視聴する" })))
                    val port = object : AdInteractionPort {
                        override fun now() = android.os.SystemClock.elapsedRealtime()
                        override fun stopped() = false
                        override suspend fun frame(): AdFrame {
                            val ls = ui.foregroundNodes().filter { it.isVisibleToUser }.map { ui.label(it) }
                            val fixture = ui.currentPackage() == "com.anapocket.auto.test"
                            return AdFrame(fixture, ls.any { it.contains("今日の視聴済み回数") }, AdSignals.watched(ls),
                                AdSignals.receipt(ls), ls.contains("閉じる"), error = AdSignals.error(ls), adSurface = ls.any { it.startsWith("Ad 1 of 1") })
                        }
                        override suspend fun close() = ui.foregroundNodes().firstOrNull { ui.label(it) == "閉じる" }?.let { ui.click(it) } ?: false
                        override suspend fun dismissReceipt() {
                            ui.foregroundNodes().firstOrNull { ui.label(it) == "OK" }?.let { ui.click(it) }
                        }
                        override suspend fun state(state: AdState) {
                            DailyAdStore.transition(ctx, date, runId, a.index, state)
                            lines += "REAL_UI " + state
                            Evidence.write(targetContext, "fixture_" + runId + "_" + state + ".txt", ui.dump())
                            ui.capture()?.let {
                                Evidence.writeBytes(targetContext, "fixture_" + runId + "_" + state + ".png", it)
                            }
                        }
                    }
                    val r = AdInteraction(port).awaitReceipt(0)
                    if (expectError) check(r.state == AdState.FAILED && !r.completed && !r.reward) { r.toString() }
                    else check(r.completed && r.reward && r.after == 1) { r.toString() }
                    DailyAdStore.finishAttempt(ctx, date, runId, a.index, r.state, r.completed, r.reward, r.reason, 0, r.after, "fixture_ui")
                    lines += if (expectError) "PASS actual Accessibility error dialog: FAILED, started=1, completed=0, reward=0"
                        else "PASS actual Accessibility UI: start -> playback -> close node -> fixture receipt -> watched counter 0 to 1"
                    lines += "WARNING fixture receipt is simulated; official ANA reward NOT CONFIRMED"
                    ctx.getSharedPreferences("ana_daily_ads", 0).edit().clear().commit()
                    ui.back()
                }
                "seed", "seed-kill" -> {
                    ctx.getSharedPreferences("ana_daily_ads", 0).edit().clear().commit()
                    val a = checkNotNull(DailyAdStore.beginAttempt(ctx, date, runId))
                    DailyAdStore.transition(ctx, date, a.runId, a.index, AdState.AD_PLAYING)
                    lines += "SEED persisted index=1 AD_PLAYING; completion=0 reward=0"
                    if (args.getString("phase") == "seed-kill") {
                        Evidence.write(targetContext, "controlled_seed_before_kill.txt", lines.joinToString("\n"))
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
                "recover" -> {
                    DailyAdStore.reconcileInterrupted(ctx, date)
                    val rows = DailyAdStore.attempts(ctx, date)
                    check(rows.size == 1 && rows[0].state == AdState.UNCONFIRMED && !rows[0].completed)
                    check(DailyAdStore.beginAttempt(ctx, date, runId) == null)
                    lines += "PASS process restart: one UNCONFIRMED preserved, second reservation rejected"
                }
                "schedule" -> {
                    val at = Scheduler.scheduleSelfTest(targetContext, 15_000L)
                    lines += "SCHEDULE armed JST=" + TokyoDate.time(at) + "; no live ads scheduled by this selftest"
                }
                "snapshot" -> {
                    val rows = DailyAdStore.attempts(targetContext, date)
                    val array = org.json.JSONArray()
                    rows.forEach { array.put(it.toJson()) }
                    val body = org.json.JSONObject().apply {
                        put("runId", runId); put("dateJst", date); put("attempts", array)
                        put("counters", DailyAdStore.counters(targetContext, date).toString())
                    }.toString(2)
                    Evidence.write(targetContext, "ledger_snapshot_" + runId + ".json", body)
                    lines += body
                }
                "challenge-snapshot" -> {
                    // Read the real production journal after execution; no fixture namespace and no UI actions.
                    val ledgerDate = args.getString("ledgerDate", date) ?: date
                    check(Regex("\\d{4}-\\d{2}-\\d{2}").matches(ledgerDate))
                    val prefsDir = java.io.File(targetContext.filesDir.parentFile, "shared_prefs")
                    val body = org.json.JSONObject().apply {
                        put("runId", runId); put("readOnly", true); put("dateJst", ledgerDate); put("deviceDateJst", date)
                        put("deviceTimeJst", TokyoDate.time())
                        put("challenges", ChallengeStore.rows(targetContext, ledgerDate))
                        put("dailyCounters", DailyAdStore.counters(targetContext, ledgerDate).toString())
                        put("dailyPreferencesXml", java.io.File(prefsDir, "ana_daily_ads.xml").readText())
                        put("challengePreferencesXml", java.io.File(prefsDir, "challenge_boost_v1.xml").readText())
                    }.toString(2)
                    Evidence.write(targetContext, "challenge_journal_snapshot_" + runId + ".json", body)
                    lines += "READ_ONLY production challenge journal exported; no ads or challenge actions started"
                    lines += body
                }
                "inspect-ads" -> {
                    val svc = AutoAccessibilityService.instance ?: error("accessibility_not_connected")
                    val ui = Ui(svc)
                    val body = "ACTIVE_PACKAGE=" + ui.currentPackage() + "\nACTIVE_LABELS=" +
                        ui.foregroundNodes().map { ui.label(it) }.joinToString(" | ") + "\nWINDOWS=" + ui.detailWindows().joinToString("\n")
                    Evidence.write(targetContext, "ad_inspect_" + runId + ".txt", body)
                    lines += body
                }
                "navigate" -> runBlocking {
                    val svc = AutoAccessibilityService.instance ?: error("accessibility_not_connected")
                    check(DailyAdTaskController(svc).navigateOnly(runId)) { "ads_page_unreachable" }
                    lines += "PASS navigation from APK to foreground ANA advertising entry; zero ad starts"
                }
                else -> runBlocking {
                    ctx.getSharedPreferences("ana_daily_ads", 0).edit().clear().commit()
                    repeat(25) { i ->
                        val a = checkNotNull(DailyAdStore.beginAttempt(ctx, date, runId))
                        var time = 0L
                        var closed = false
                        var dismissed = false
                        val port = object : AdInteractionPort {
                            override fun now() = time
                            override fun stopped() = false
                            override suspend fun pause(ms: Long) { time += ms }
                            override suspend fun state(state: AdState) {
                                DailyAdStore.transition(ctx, date, runId, a.index, state)
                                if (i == 0) lines += "t=" + time + " " + state
                            }
                            override suspend fun close(): Boolean { closed = true; return true }
                            override suspend fun dismissReceipt() { dismissed = true }
                            override suspend fun frame(): AdFrame = when {
                                time < 800 -> AdFrame(true, false, adSurface = true)
                                !closed -> AdFrame(true, false, closeAvailable = true, adSurface = true)
                                !dismissed -> AdFrame(true, false, receipt = true)
                                else -> AdFrame(true, true, i + 1)
                            }
                        }
                        val r = AdInteraction(port).awaitReceipt(i)
                        check(r.completed && r.reward)
                        DailyAdStore.finishAttempt(ctx, date, runId, a.index, r.state, r.completed, r.reward, r.reason, i, r.after, "scripted_fixture")
                        // An identical callback must not double-count.
                        DailyAdStore.finishAttempt(ctx, date, runId, a.index, r.state, true, true, "duplicate", i, r.after, "duplicate")
                    }
                    val c = DailyAdStore.counters(ctx, date)
                    check(c.started == 25 && c.completed == 25 && c.rewardConfirmed == 25 && c.remaining == 0)
                    check(DailyAdStore.beginAttempt(ctx, date, runId) == null)
                    lines += "PASS isolated persisted ledger: 25/25/25; 26th rejected; duplicate callbacks idempotent"
                    check(DailyAdStore.counters(ctx, "2099-01-01").started == 0)
                    lines += "PASS separate date bucket: zero counts without deleting prior day"
                    ctx.getSharedPreferences("ana_daily_ads", 0).edit().putString("day_" + date, "corrupted").commit()
                    check(runCatching { DailyAdStore.beginAttempt(ctx, date, runId) }.isFailure)
                    lines += "PASS corrupt ledger fails closed; no click can follow failed reservation"
                    ctx.getSharedPreferences("ana_daily_ads", 0).edit().clear().commit()
                    val failCtx = object : ContextWrapper(ctx) {
                        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                            val delegate = baseContext.getSharedPreferences(name, mode)
                            return object : SharedPreferences by delegate {
                                override fun edit(): SharedPreferences.Editor {
                                    val editor = delegate.edit()
                                    return object : SharedPreferences.Editor by editor {
                                        override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                                        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { editor.putInt(key, value); return this }
                                        override fun commit() = false
                                    }
                                }
                            }
                        }
                    }
                    check(runCatching { DailyAdStore.beginAttempt(failCtx, date, "DISK_FAILURE") }.isFailure)
                    check(DailyAdStore.counters(ctx, date).started == 0)
                    lines += "PASS injected commit failure: no reservation returned, zero starts"
                    val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
                    val gate = java.util.concurrent.CountDownLatch(1)
                    val futures = (1..2).map { n -> pool.submit<AdAttempt?> { gate.await(); DailyAdStore.beginAttempt(ctx, date, "CONCURRENT_" + n) } }
                    gate.countDown()
                    check(futures.map { it.get() }.count { it != null } == 1)
                    pool.shutdown()
                    check(DailyAdStore.counters(ctx, date).started == 1)
                    lines += "PASS concurrent reservation: exactly one in-flight attempt"
                    ctx.getSharedPreferences("ana_daily_ads", 0).edit().clear().commit()
                }
            }
            lines += "RUN_ID=" + runId + "; real_reward=NOT_TESTED; source=controlled_instrumentation"
            val report = lines.joinToString("\n")
            Evidence.write(targetContext, "controlled_" + runId + ".txt", report)
            finish(android.app.Activity.RESULT_OK, Bundle().apply { putString("stream", report) })
        } catch (t: Throwable) {
            finish(android.app.Activity.RESULT_CANCELED, Bundle().apply { putString("stream", lines.joinToString("\n") + "\nFAIL " + t.stackTraceToString()) })
        }
    }
}
