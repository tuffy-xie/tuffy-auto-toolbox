package com.anapocket.auto

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** 每日广告固定上限；不是用户参数。 */
const val DAILY_AD_LIMIT = 25

/** 广告流程显式状态，禁止把点击、关闭、返回混成完成或奖励。 */
enum class AdState {
    IDLE,
    AD_ENTRY_READY,
    AD_OPENING,
    AD_PLAYING,
    AD_FINISHED,
    AD_CLOSE_AVAILABLE,
    AD_RETURNED,
    REWARD_CONFIRMED,
    UNCONFIRMED,
    FAILED,
    BLOCKED,
    LIMIT_REACHED
}

object TokyoDate {
    private val tz = TimeZone.getTimeZone("Asia/Tokyo")

    fun of(nowMs: Long = System.currentTimeMillis()): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        f.timeZone = tz
        return f.format(Date(nowMs))
    }

    fun time(nowMs: Long = System.currentTimeMillis()): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        f.timeZone = tz
        return f.format(Date(nowMs))
    }
}

data class AdAttempt(
    val runId: String,
    val date: String,
    val index: Int,
    val startedAt: Long,
    val state: AdState = AdState.AD_OPENING,
    val completed: Boolean = false,
    val rewardConfirmed: Boolean = false,
    val finishedAt: Long = 0L,
    val failureReason: String = "",
    val officialBefore: Int = -1,
    val officialAfter: Int = -1,
    val evidence: String = "",
    val startState: String = "AD_OPENING",
    val dispatchPhase:String="",
    val dispatchArmedAt:Long=0,
    val dispatchCompletedAt:Long=0,
    val adDepartureObserved:Boolean=false,
    val officialBinding:String=""
) {
    val terminal: Boolean
        get() = finishedAt > 0L

    fun toJson(): JSONObject = JSONObject().apply {
        put("startState", startState)
        if(officialBinding.isNotBlank())put("officialBinding",JSONObject(officialBinding))
        if(adDepartureObserved)put("adDepartureObserved",true)
        if(dispatchPhase.isNotBlank()){put("dispatchPhase",dispatchPhase);put("dispatchArmedAt",dispatchArmedAt);put("dispatchCompletedAt",dispatchCompletedAt)}
        put("runId", runId)
        put("date", date)
        put("index", index)
        put("startedAt", startedAt)
        put("state", state.name)
        put("completed", completed)
        put("rewardConfirmed", rewardConfirmed)
        put("finishedAt", finishedAt)
        put("failureReason", failureReason)
        put("officialBefore", officialBefore)
        put("officialAfter", officialAfter)
        put("evidence", evidence)
    }

    companion object {
        fun fromJson(o: JSONObject): AdAttempt = AdAttempt(
            runId = o.optString("runId"),
            date = o.optString("date"),
            index = o.optInt("index"),
            startedAt = o.optLong("startedAt"),
            state = runCatching { AdState.valueOf(o.optString("state").let { if (it in listOf("ENTRY_READY", "OPENING", "PLAYING", "FINISHED", "CLOSE_AVAILABLE", "RETURNED")) "AD_$it" else it }) }.getOrDefault(AdState.FAILED),
            completed = o.optBoolean("completed"),
            rewardConfirmed = o.optBoolean("rewardConfirmed"),
            finishedAt = o.optLong("finishedAt"),
            failureReason = o.optString("failureReason"),
            officialBefore = o.optInt("officialBefore", -1),
            officialAfter = o.optInt("officialAfter", -1),
            evidence = o.optString("evidence"),
            startState = o.optString("startState", "AD_OPENING"),
            dispatchPhase=o.optString("dispatchPhase"),dispatchArmedAt=o.optLong("dispatchArmedAt"),dispatchCompletedAt=o.optLong("dispatchCompletedAt"),adDepartureObserved=o.optBoolean("adDepartureObserved"),officialBinding=o.optJSONObject("officialBinding")?.toString().orEmpty()
        )
    }
}

data class AdCounters(
    val date: String,
    val started: Int,
    val completed: Int,
    val rewardConfirmed: Int,
    val failed: Int,
    val unconfirmed: Int
) {
    val remaining: Int get() = (DAILY_AD_LIMIT - started).coerceAtLeast(0)
}

data class DailyAdResult(
    val runId: String,
    val date: String,
    val started: Int,
    val completed: Int,
    val rewardConfirmed: Int,
    val failed: Int,
    val unconfirmed: Int,
    val limitReached: Boolean,
    val stopped: Boolean,
    val blockedReason: String,
    val detail: String
) {
    fun line(): String =
        "started=$started completed=$completed reward=$rewardConfirmed failed=$failed unconfirmed=$unconfirmed " +
            "limit=$limitReached stopped=$stopped" + (if (blockedReason.isNotEmpty()) " blocked=$blockedReason" else "")

    fun toJson(): JSONObject = JSONObject().apply {
        put("runId", runId)
        put("date", date)
        put("started", started)
        put("completed", completed)
        put("rewardConfirmed", rewardConfirmed)
        put("failed", failed)
        put("unconfirmed", unconfirmed)
        put("limitReached", limitReached)
        put("stopped", stopped)
        put("blockedReason", blockedReason)
        put("detail", detail)
        put("finishedAt", System.currentTimeMillis())
    }

    companion object {
        fun fromJson(s: String?): DailyAdResult? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                DailyAdResult(
                    o.optString("runId"), o.optString("date"), o.optInt("started"),
                    o.optInt("completed"), o.optInt("rewardConfirmed"), o.optInt("failed"),
                    o.optInt("unconfirmed"), o.optBoolean("limitReached"),
                    o.optBoolean("stopped"), o.optString("blockedReason"), o.optString("detail")
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/** 纯逻辑：便于单测上限、分计数和中断恢复。 */
object AdDayLogic {
    fun counters(date: String, attempts: List<AdAttempt>): AdCounters = AdCounters(
        date = date,
        started = forDate(date, attempts).size,
        completed = forDate(date, attempts).count { it.completed },
        rewardConfirmed = forDate(date, attempts).count { it.rewardConfirmed },
        failed = forDate(date, attempts).count { it.state == AdState.FAILED },
        unconfirmed = forDate(date, attempts).count { it.state == AdState.UNCONFIRMED }
    )

    fun canStart(attempts: List<AdAttempt>): Boolean = attempts.size < DAILY_AD_LIMIT &&
        attempts.none { !it.terminal || it.state == AdState.UNCONFIRMED || it.state == AdState.BLOCKED }

    /** A deliberate new manual run may reserve one NEW attempt; never retry an old RUN_ID. */
    fun canStartFreshManual(attempts: List<AdAttempt>, runId: String, haltReason: String): Boolean =
        attempts.size < DAILY_AD_LIMIT && attempts.any { it.state == AdState.UNCONFIRMED } &&
        attempts.all { it.terminal && it.runId != runId && it.state in setOf(AdState.UNCONFIRMED, AdState.REWARD_CONFIRMED, AdState.AD_FINISHED) } &&
        haltReason in setOf("", "ad_timeout_95s", "ad_part_timeout_95s", "ad_timeout_300s", "completion_not_confirmed", "reward_receipt_missing", "user_stopped", "interrupted_attempt")

    /** Explicitly authorized batch: old unknown records remain immutable; this run only advances after proven success. */
    fun canContinueManualBatch(attempts: List<AdAttempt>, runId: String, haltReason: String): Boolean =
        attempts.size < DAILY_AD_LIMIT && haltReason in setOf("", "ad_timeout_95s", "ad_part_timeout_95s", "ad_timeout_300s", "completion_not_confirmed", "reward_receipt_missing", "user_stopped", "interrupted_attempt") &&
        attempts.all { it.terminal && if (it.runId == runId)
            it.state == AdState.REWARD_CONFIRMED && it.completed && it.rewardConfirmed
        else it.state in setOf(AdState.UNCONFIRMED, AdState.REWARD_CONFIRMED, AdState.AD_FINISHED) }

    fun forDate(date: String, attempts: List<AdAttempt>): List<AdAttempt> = attempts.filter { it.date == date }

    /** 进程被杀时，未终结的尝试只能记 UNCONFIRMED，不能自动当成完成或重复提交。 */
    fun reconcileInterrupted(attempts: List<AdAttempt>, now: Long): List<AdAttempt> {
        return attempts.map { a ->
            if (!a.terminal && a.finishedAt == 0L) {
                a.copy(
                    state = AdState.UNCONFIRMED,
                    finishedAt = now,
                    failureReason = "process_interrupted"
                )
            } else a
        }
    }

    fun finish(
        attempts: List<AdAttempt>,
        runId: String,
        index: Int,
        state: AdState,
        completed: Boolean,
        reward: Boolean,
        failure: String,
        officialBefore: Int,
        officialAfter: Int,
        evidence: String,
        now: Long = System.currentTimeMillis()
    ): List<AdAttempt> {
        return attempts.map { a ->
            if (a.runId == runId && a.index == index && !a.terminal) {
                a.copy(
                    state = state,
                    completed = completed,
                    rewardConfirmed = reward,
                    finishedAt = now,
                    failureReason = failure,
                    officialBefore = officialBefore,
                    officialAfter = officialAfter,
                    evidence = evidence
                )
            } else a
        }
    }
}

/** 独立持久化：每天最多 25 条尝试记录，计数从记录派生，避免双写漂移。 */
object DailyAdStore {
    private const val PREF = "ana_daily_ads"
    private const val KEY_LAST_RESULT = "last_result"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized
    fun attempts(ctx: Context, date: String): List<AdAttempt> {
        val raw = sp(ctx).getString("day_$date", null)
        if (raw == null) {
            val legacy = RunStore.adDone(ctx, date)
            check(legacy == 0) { "legacy_counter_requires_review:$date:$legacy" }
            return emptyList()
        }
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<AdAttempt>(a.length())
            for (i in 0 until a.length()) out.add(AdAttempt.fromJson(a.getJSONObject(i)))
            check(out.size <= DAILY_AD_LIMIT && out.map { it.index }.distinct().size == out.size &&
                out.all { it.date == date && it.runId.isNotBlank() && it.index in 1..DAILY_AD_LIMIT }) { "invalid_ledger" }
            if (sp(ctx).getInt("schema_$date", 0) < 2) {
                val migrated = out.map { it.copy(state = AdState.UNCONFIRMED, completed = false, rewardConfirmed = false,
                    finishedAt = System.currentTimeMillis(), failureReason = "legacy_evidence_requires_review:" + it.failureReason) }
                save(ctx, date, migrated)
                migrated
            } else out
        } catch (t: Throwable) {
            throw IllegalStateException("ledger_corrupt:$date", t)
        }
    }

    @Synchronized
    fun save(ctx: Context, date: String, list: List<AdAttempt>) {
        val a = JSONArray()
        for (x in list) a.put(x.toJson())
        check(sp(ctx).edit().putString("day_$date", a.toString()).putInt("schema_$date", 2).commit()) { "ledger_write_failed" }
    }

    @Synchronized
    fun reconcileInterrupted(ctx: Context, date: String): Int {
        val before = attempts(ctx, date)
        val after = AdDayLogic.reconcileInterrupted(before, System.currentTimeMillis())
        if (after != before) save(ctx, date, after)
        return after.count { it.failureReason == "process_interrupted" }
    }

    @Synchronized
    fun beginAttempt(ctx: Context, date: String, runId: String, freshManual: Boolean = false, refreshedCounter: Int? = null, officialBefore: Int = -1): AdAttempt? {
        val list = attempts(ctx, date).toMutableList()
        if (TokyoDate.of() != date) return null
        val permitted = if (freshManual && refreshedCounter != null) DailyAdCounterRefreshPolicy.canContinue(list, runId, haltReason(ctx, date), refreshedCounter)
            else if (freshManual) AdDayLogic.canContinueManualBatch(list, runId, haltReason(ctx, date))
            else !isHalted(ctx, date) && AdDayLogic.canStart(list)
        if (!permitted) return null
        val a = AdAttempt(
            runId = runId,
            date = date,
            index = list.size + 1,
            startedAt = System.currentTimeMillis(),
            officialBefore = officialBefore,
            state = AdState.AD_OPENING
        )
        list.add(a)
        save(ctx, date, list)
        return a
    }

    @Synchronized
    fun finishAttempt(
        ctx: Context,
        date: String,
        runId: String,
        index: Int,
        state: AdState,
        completed: Boolean,
        reward: Boolean,
        failure: String,
        officialBefore: Int,
        officialAfter: Int,
        evidence: String
    ): AdCounters {
        val list = attempts(ctx, date)
        val after = AdDayLogic.finish(
            list, runId, index, state, completed, reward, failure,
            officialBefore, officialAfter, evidence
        )
        save(ctx, date, after)
        return AdDayLogic.counters(date, after)
    }

    @Synchronized
    fun transition(ctx: Context, date: String, runId: String, index: Int, state: AdState) {
        val list = attempts(ctx, date).map {
            if (it.runId == runId && it.index == index && !it.terminal) it.copy(state = state) else it
        }
        save(ctx, date, list)
    }

    fun haltReason(ctx: Context, date: String): String = sp(ctx).getString("halt_$date", "") ?: ""
    fun isHalted(ctx: Context, date: String): Boolean = sp(ctx).contains("halt_$date")
    fun halt(ctx: Context, date: String, reason: String) {
        check(sp(ctx).edit().putString("halt_$date", reason).commit()) { "halt_write_failed" }
    }
    fun state(ctx: Context): String = if (sp(ctx).getString("state_date", "") == TokyoDate.of())
        sp(ctx).getString("current_state", "IDLE") ?: "IDLE" else "IDLE"
    fun setState(ctx: Context, state: AdState, date: String = TokyoDate.of()) {
        check(sp(ctx).edit().putString("current_state", state.name).putString("state_date", date).commit()) { "state_write_failed" }
    }

    fun allAttempts(ctx:Context,date:String):List<AdAttempt> = attempts(ctx,date)+OfficialAdStore.attempts(ctx,date)

    fun counters(ctx: Context, date: String): AdCounters =
        AdDayLogic.counters(date, allAttempts(ctx, date))

    fun saveResult(ctx: Context, result: DailyAdResult) {
        check(sp(ctx).edit().putString(KEY_LAST_RESULT, result.toJson().toString()).commit()) { "result_write_failed" }
    }

    fun lastResult(ctx: Context): DailyAdResult? =
        DailyAdResult.fromJson(sp(ctx).getString(KEY_LAST_RESULT, null))

    fun history(ctx: Context, date: String, limit: Int = 30): List<AdAttempt> =
        allAttempts(ctx, date).takeLast(limit).reversed()
}


/** Independent daily controller. No challenge engine or Boost operations are called. */
class DailyAdTaskController(private val svc: AutoAccessibilityService) {
    private val ui = Ui(svc)
    private val pkg = "us.ana_pocket.ANAPocket"
    private val overlay = OverlayKeeper(svc)
    private fun labels() = if (ui.currentPackage() == pkg) ui.foregroundNodes().filter { it.isVisibleToUser }.map { ui.label(it) }.filter { it.isNotBlank() } else emptyList()
    private fun entry(): Boolean {
        val l = labels()
        return l.any { it.contains("今日の視聴済み回数") } && !AdSignals.receipt(l)
    }
    private val closeDriver = AdUiClose(svc)
    private val observedStoreOverlay get() = closeDriver.observedStoreOverlay
    private var observedClose: android.view.accessibility.AccessibilityNodeInfo? = null
    private suspend fun identifyClose() = closeDriver.identifyClose()
    private fun watchNode() = ui.foregroundNodes().firstOrNull {
        it.isVisibleToUser && it.isEnabled && ui.label(it).trim() in Sel.WATCH_AD
    }
    private fun blocked(): Boolean {
        val km = svc.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        return !AutoAccessibilityService.isConnected() || km.isKeyguardLocked
    }
    private suspend fun evidence(runId: String, suffix: String, state: String) {
        val name = "ad_" + runId + "_" + suffix
        Evidence.write(svc, name + ".txt", "state=" + state + "\ndateJst=" + TokyoDate.of() + "\n" + ui.dump())
        Evidence.capture(svc,ui,name+".png")
    }
    private suspend fun dismissKnownReceipt(): Boolean {
        val n = ui.foregroundNodes().firstOrNull {
            it.isVisibleToUser && it.isEnabled && ui.label(it).trim() in setOf("OK", "閉じる")
        } ?: return false
        val r = android.graphics.Rect()
        n.getBoundsInScreen(r)
        // This ANA WebView reports ACTION_CLICK success without dismissing its dialog.
        return r.width() > 0 && r.height() > 0 && ui.tap(r.centerX(), r.centerY())
    }
    private suspend fun navigate(runId: String, reviewOnly:Boolean=false): Boolean {
        if (blocked() || AutoAccessibilityService.stopRequested) return false
        if (ui.currentPackage() != pkg) { ui.launchApp(pkg); delay(2500L) }
        var staleDismissals = 0
        repeat(16) {
            if (blocked() || AutoAccessibilityService.stopRequested) return false
            val gate = StartupFlow(svc).prepare(runId + "_navigation", launch=false,allowLoginSubmission=!reviewOnly)
            if (!gate.ok) return false
            val l = labels()
            if(MaxBoostCoordinator.visible(svc)||ChallengeSignals.dialog(l)||
                ui.foregroundNodes().any{it.isVisibleToUser&&it.viewIdResourceName?.substringAfterLast('/') in setOf("title_join","title_count","button_see_ad")})return false
            if (AdSignals.receipt(l)) {
                if (staleDismissals >= 2) return false
                evidence(runId, "stale_receipt_" + staleDismissals, "preexisting_receipt_not_attributed")
                staleDismissals++
                if (!dismissKnownReceipt()) return false
                delay(1000L)
                return@repeat
            }
            if (entry() || AdSignals.limit(l) || AdSignals.error(l)) return true
            val nodes = if (ui.currentPackage() == pkg) ui.foregroundNodes() else emptyList()
            // Use the existing earn navigation, with foreground nodes and bounded waits.
            val tab = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled && ui.label(it) == "広告視聴" }
            val earn = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled && ui.label(it) == "ためる" }
            if (tab != null) ui.click(tab) else if (earn != null) ui.click(earn)
            delay(800L)
        }
        return false
    }
    private suspend fun stableCounter(): Int? {
        val stable = DailyAdBaseline()
        val until = android.os.SystemClock.elapsedRealtime() + 15_000L
        while (android.os.SystemClock.elapsedRealtime() < until) {
            if (blocked() || AutoAccessibilityService.stopRequested) return null
            ui.refreshCache()
            val ls = labels()
            if (AdSignals.error(ls) || AdSignals.receipt(ls)) return null
            stable.observe(android.os.SystemClock.elapsedRealtime(), AdSignals.watched(ls), entry())?.let { return it }
            delay(750L)
        }
        return null
    }
    private suspend fun refreshOfficialEntry(runId: String): Int? {
        val old = AdSignals.watched(labels())
        evidence(runId, "counter_before_refresh", "officialBeforeRefresh=$old")
        // Recreate the ad tab's page, not merely read the retained WebView again.
        ui.refreshCache()
        val other = ui.foregroundNodes().firstOrNull { it.isVisibleToUser && it.isEnabled && ui.label(it) == "歩数" } ?: return null
        if (!ui.click(other)) return null
        var left = false
        repeat(10) { if (!left) { delay(500L); ui.refreshCache(); left = ui.currentPackage() == pkg && !entry() } }
        if (!left || blocked() || AutoAccessibilityService.stopRequested) return null
        val tab = ui.foregroundNodes().firstOrNull { it.isVisibleToUser && it.isEnabled && ui.label(it) == "広告視聴" } ?: return null
        if (!ui.click(tab)) return null
        delay(1500L)
        val current = stableCounter() ?: return null
        evidence(runId, "counter_after_refresh", "officialBeforeRefresh=$old;officialAfterRefresh=$current;old_records_preserved")
        Logger.i("ADBASELINE", "run=$runId refreshed=$old->$current")
        return current
    }
    /** Debug harness calls this only when no task is running; never reserves or changes a viewing record. */
    suspend fun closeObservedOnly(runId: String): Boolean {
        if (AutoAccessibilityService.isRunning || blocked()) return false
        val n = identifyClose() ?: return false
        evidence(runId, "close_only_before", "existing_ad_cleanup_no_ledger_change")
        val r = closeDriver.controlBounds(n) ?: return false
        Logger.i("ADCLOSE", "probe=" + runId + " store=" + observedStoreOverlay + " label=" + ui.label(n) + " bounds=" + r)
        val ok = if (closeDriver.observedTapOnly && r.width() > 0 && r.height() > 0) ui.tap(r.centerX(), r.centerY()) else ui.click(n)
        delay(1000L)
        evidence(runId, "close_only_after", "clicked=" + ok)
        return ok
    }
    /** Read-only navigation probe, used by the controlled instrumentation harness. */
    suspend fun navigateOnly(runId: String): Boolean {
        val ok = navigate(runId)
        evidence(runId, "navigation", if (ok) "AD_ENTRY_READY" else "BLOCKED")
        return ok
    }
    private fun blockedResult(runId: String, date: String, reason: String): DailyAdResult {
        DailyAdStore.setState(svc, AdState.BLOCKED, date)
        val c = DailyAdStore.counters(svc, date)
        val r = DailyAdResult(runId, date, c.started, c.completed, c.rewardConfirmed, c.failed, c.unconfirmed, false, AutoAccessibilityService.stopRequested, reason, "")
        Evidence.write(svc, "ad_result_" + runId + ".json", r.toJson().toString(2))
        return r
    }
    private fun interactionPort(runId:String,date:String,attempt:AdAttempt):AdInteractionPort {
        return object : AdInteractionPort {
            override fun now() = android.os.SystemClock.elapsedRealtime()
            override fun stopped() = AutoAccessibilityService.stopRequested || !AutoAccessibilityService.isConnected() || TokyoDate.of()!=date
            override fun stopReason() = if (!AutoAccessibilityService.isConnected()) "accessibility_disconnected" else if (TokyoDate.of() != date) "phone_jst_date_changed" else "user_stopped"
            override suspend fun frame(): AdFrame {
                if(!AccountSafety.authorize(svc,"daily_ad"))return AdFrame(ui.currentPackage()==pkg,false,manualRequired=true)
                closeDriver.restoreAdAfterStore()
                ui.refreshCache()
                val ls = labels()
                // Persist a real receipt/counter before any optional visual recognition or diagnostics.
                if(ui.currentPackage()==pkg&&AdSignals.receipt(ls))return AdFrame(true,entry(),AdSignals.watched(ls),receipt=true,blocked=blocked())
                if(ui.currentPackage()==pkg&&entry()&&AdSignals.watched(ls)!=null)return AdFrame(true,true,AdSignals.watched(ls),blocked=blocked())
                observedClose = identifyClose()
                if (observedClose == null && closeDriver.revealIfNeeded()) observedClose = identifyClose()
                return AdFrame(ui.currentPackage() == pkg, entry(), AdSignals.watched(ls),
                    AdSignals.receipt(ls), observedClose != null, AdSignals.limit(ls), AdSignals.error(ls), blocked(),
                    ui.currentPackage() == pkg && (closeDriver.isAdSurface() ||
                        ui.foregroundNodes().any { it.isVisibleToUser && (ui.label(it).contains("Skip Ad") || AdSignals.part(listOf(ui.label(it))) != null) }),
                    part = AdSignals.part(ls), storeOverlay = observedStoreOverlay,
                    nativePangleRender = closeDriver.isNativePangleRender(),advanceControl=closeDriver.observedAdvanceControl)
            }
            override suspend fun close(): Boolean {
                val n = observedClose ?: return false
                if (!n.refresh() || !n.isVisibleToUser || !n.isEnabled || entry() || AdSignals.receipt(labels())) return false
                val r = closeDriver.controlBounds(n) ?: return false
                Logger.i("ADCLOSE", "run=" + runId + " #" + attempt.index + " store=" + observedStoreOverlay + " label=" + ui.label(n) + " bounds=" + r)
                return if (closeDriver.observedTapOnly && r.width() > 0 && r.height() > 0) ui.tap(r.centerX(), r.centerY()) else ui.click(n)
            }
            override suspend fun receiptObserved(frame:AdFrame,before:Int) {
                check(frame.anaForeground&&frame.receipt){"receipt_foreground_missing"}
                OfficialAdStore.recordReceipt(svc,date,attempt,before)
            }
            override suspend fun completionObserved(frame:AdFrame,before:Int,reward:Boolean) {
                if(reward&&frame.anaForeground&&frame.watched==before+1)
                    OfficialAdStore.commitReceipt(svc,date,attempt,before,frame.watched!!)
            }
            override suspend fun dismissReceipt() {
                dismissKnownReceipt()
            }
            override suspend fun diagnostic(reason:String){FaultStore.capture(svc,runId,"daily_ads",reason,attempt.index.toString())}
            override suspend fun progress(part: AdPart) {
                Logger.i("ADPART", "run=" + runId + " #" + attempt.index + " part=" + part.current + "/" + part.total)
                evidence(runId, attempt.index.toString() + "_part_" + part.current, "ad_part=" + part.current + "/" + part.total)
            }
            override suspend fun state(state: AdState) {
                OfficialAdStore.transition(svc, date, attempt.runId, attempt.index, state)
                DailyAdStore.setState(svc, state, date)
                Logger.i("ADTASK", "run=$runId #" + attempt.index + " state=" + state)
                if (state != AdState.AD_CLOSE_AVAILABLE || !closeDriver.recentlyRevealed)
                    evidence(runId, attempt.index.toString() + "_" + state.name, state.name)
            }
        }
    }
    private suspend fun recoverExisting(runId:String,date:String,original:AdAttempt):String? {
        val base=java.io.File(svc.getExternalFilesDir(null)?:svc.filesDir,"evidence")
        val departure=java.io.File(base,"ad_"+original.runId+"_"+original.index+"_AD_PLAYING.txt")
        val departed=original.adDepartureObserved||(departure.isFile&&departure.readText().let{it.startsWith("state=AD_PLAYING")&&it.contains("dateJst="+date)})
        suspend fun fail(why:String):String {
            evidence(runId,"recover_existing_"+original.index+"_blocked",why+";original_identity_preserved;no_new_start")
            Logger.w("ADRECOVER","run=$runId originalRun="+original.runId+" index="+original.index+" blocked="+why+" no_new_start=true")
            return why
        }
        evidence(runId,"recover_existing_"+original.index+"_before",original.toJson().toString())
        if(!departed)return fail("original_ad_departure_proof_missing")
        if(ui.currentPackage()!=pkg&&!AdTaskReturn.restoreOrRelaunchIfGone(svc,date,runId))return fail("original_ana_task_unavailable")
        ui.refreshCache();closeDriver.beginFlow()
        if(!closeDriver.isRecoverableSurface()&&!AdSignals.receipt(labels())){
            val token=CheckinRuntime.ownership.owner()
            val mayInspect=BoundAdReviewPolicy.mayInspect(AutoAccessibilityService.isConnected(),TokyoDate.of()==date,
                token?.app=="ANA"&&token.run==AutoAccessibilityService.currentRunId&&CheckinRuntime.ownership.valid(token,android.os.SystemClock.elapsedRealtime()),
                AutoAccessibilityService.stopRequested,ui.currentPackage()==pkg,AutoAccessibilityService.foregroundActivity,labels())
            var fresh:Int?=null
            if(mayInspect&&navigate(runId,reviewOnly=true))fresh=refreshOfficialEntry(runId)
            if(fresh!=null)OfficialAdStore.observe(svc,date,fresh,runId,"original_sdk_missing_fresh_counter_only_unknown_not_resolved")
            return fail(BoundAdReviewPolicy.missingSurfaceReason(original.officialBefore,fresh))
        }
        // Only an actual original SDK/receipt consumes one of the existing two bounded reviews.
        if(!OfficialAdStore.beginBoundRecovery(svc,date,original,departed))return fail("original_ad_recovery_binding_or_budget_rejected")
        RunForegroundService.start(svc,"继续原每日广告 · 未新增观看")
        val receipt=AdInteraction(interactionPort(runId,date,original)).awaitReceipt(original.officialBefore,previousDepartureObserved=true)
        val tag="recover_existing_"+original.index+"_receipt"
        evidence(runId,tag,receipt.toString())
        if(!OfficialAdStore.finishBoundRecovery(svc,date,original,receipt,"ad_"+runId+"_"+tag+".txt"))return fail(
            if(receipt.reason.isNotBlank())receipt.reason else "existing_official_ad_recovery_not_confirmed")
        OfficialAdStore.observe(svc,date,receipt.after,runId,"same_original_ad_fresh_reward_receipt_and_exact_plus_one_after_recovery")
        Logger.i("ADRECOVER","run="+runId+" originalRun="+original.runId+" index="+original.index+" startedAtPreserved="+original.startedAt+" after="+receipt.after+" no_new_start=true")
        return null
    }
    /** Review one explicit native load failure; no advertisement is opened by this method. */
    private suspend fun reviewKnownLoadFailure(runId:String,date:String):Boolean {
        val rows=OfficialAdStore.attempts(svc,date);val a=rows.lastOrNull()?:return false
        if(OfficialLoadFailurePolicy.key(a) in OfficialAdStore.reviewedKeys(svc,date)||
            OfficialAdStore.nativeLoadFailureProof(svc,date,a)==null)return false
        fun live()=TokyoDate.of()==date&&!blocked()&&!AutoAccessibilityService.stopRequested&&AccountSafety.authorize(svc,"daily_load_review")
        if(!live())return false
        ui.refreshCache()
        if(ui.currentPackage()==pkg&&AdSignals.loadFailure(labels())){
            val ok=ui.foregroundNodes().singleOrNull{it.isVisibleToUser&&it.isEnabled&&it.viewIdResourceName=="android:id/button1"&&ui.label(it)=="OK"}?:return false
            evidence(runId,"load_failure_"+a.index+"_before_dismiss",a.toJson().toString())
            if(!live()||!ui.click(ok))return false
            delay(800L)
        }
        val waitMs=(a.finishedAt+30_000L-System.currentTimeMillis()).coerceIn(0L,30_000L)
        repeat(((waitMs+799L)/800L).toInt()){if(!live())return false;delay(800L)}
        if(!live()||!navigate(runId))return false
        val fresh=refreshOfficialEntry(runId)?:return false
        if(!live()||!OfficialAdStore.reviewLoadFailure(svc,date,runId,fresh))return false
        OfficialAdStore.observe(svc,date,fresh,runId,"native_load_failure_review_fresh_unchanged_counter_original_failed_kept")
        evidence(runId,"load_failure_"+a.index+"_reviewed_no_gain",a.toJson().toString()+";freshOfficial="+fresh+";original_failed_preserved")
        Logger.i("ADLOAD_REVIEW","run="+runId+" originalRun="+a.runId+" index="+a.index+" unchanged="+fresh+" originalFailedKept=true")
        return true
    }
    /** Review one timed-out ad once its window is gone; no advertisement is opened by this method. */
    private suspend fun reviewBlankStall(runId:String,date:String):Boolean {
        val rows=OfficialAdStore.attempts(svc,date);val a=rows.lastOrNull()?:return false
        if(!OfficialLoadFailurePolicy.blankStall(a)||OfficialLoadFailurePolicy.key(a) in OfficialAdStore.reviewedKeys(svc,date)||
            AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity))return false
        fun live()=TokyoDate.of()==date&&!blocked()&&!AutoAccessibilityService.stopRequested&&AccountSafety.authorize(svc,"daily_blank_review")
        val waitMs=(a.finishedAt+60_000L-System.currentTimeMillis()).coerceIn(0L,60_000L)
        repeat(((waitMs+799L)/800L).toInt()){if(!live())return false;delay(800L)}
        if(!live()||!navigate(runId,reviewOnly=true))return false
        val fresh=refreshOfficialEntry(runId)?:return false
        val adClosed=!AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity)
        if(!live()||!OfficialAdStore.reviewBlankStall(svc,date,runId,fresh,adClosed)){
            Logger.w("ADBLANK","run=$runId review_rejected originalRun="+a.runId+" index="+a.index+" before="+a.officialBefore+" fresh="+fresh);return false}
        OfficialAdStore.observe(svc,date,fresh,runId,"blank_ad_review_fresh_unchanged_counter_original_unknown_kept")
        evidence(runId,"blank_ad_"+a.index+"_reviewed",a.toJson().toString()+";freshOfficial="+fresh+";original_unconfirmed_preserved")
        Logger.i("ADBLANK","run=$runId reviewed originalRun="+a.runId+" index="+a.index+" unchanged="+fresh+" originalUnconfirmedKept=true")
        return true
    }
    suspend fun run(runId: String, reason: String, maxAttempts: Int = DAILY_AD_LIMIT, manualNew: Boolean = false): DailyAdResult {
        val date = TokyoDate.of()
        var detail = ""
        var blockedReason = ""
        var limit = false
        var active: AdAttempt? = null
        val pm = svc.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ana:dailyads")
        try {
            /* No multi-hour wake hold: native readiness and active UI ownership remain bounded. */
            DailyAdStore.reconcileInterrupted(svc, date)
            OfficialAdStore.reconcile(svc,date)
            val old = OfficialAdStore.attempts(svc,date)
            val runLimit = maxAttempts.coerceIn(1, DAILY_AD_LIMIT)
            overlay.show()
            val readiness=DeviceReady.ensure(svc,runId)
            val independentIsolation=if(readiness.ready&&!blocked()&&!AutoAccessibilityService.stopRequested&&
                Prefs.loadBlocking(svc).adEnabled&&AccountSafety.authorize(svc,"daily_ads_isolation")&&ChallengeStore.hasPending(svc,date)){
                PendingInitialJoinStore.onlyIsolatedPending(svc,date)||ChallengeTaskController(svc).isolateForIndependentDaily(runId,date)
            }else false
            if (!readiness.ready) { blockedReason=readiness.reason }
            else if (!AccountSafety.authorize(svc,"daily_ads")){blockedReason="account_safety_human_required"}
            else if (PendingInitialJoinPolicy.blocksDailyNavigation(ChallengeStore.hasPending(svc,date),
                independentIsolation)) { blockedReason = "unfinished_challenge_blocks_daily_navigation" }
            else if (!Prefs.loadBlocking(svc).adEnabled) { detail = "disabled" }
            else if (blocked()) { blockedReason = "accessibility_or_keyguard" }
            else {
                overlay.show()
                reviewBlankStall(runId,date)
                if(old.any{!it.completed||!it.rewardConfirmed}){
                    val original=old.lastOrNull()?.takeIf{OfficialLoadFailurePolicy.key(it) !in OfficialAdStore.reviewedKeys(svc,date)}
                    if(original!=null&&OfficialAdStore.hasRecordedReceipt(svc,date,original)){
                        if(!navigate(runId,reviewOnly=true))return blockedResult(runId,date,"recorded_daily_receipt_page_unavailable")
                        val fresh=refreshOfficialEntry(runId)
                        if(fresh==null)return blockedResult(runId,date,"recorded_daily_receipt_counter_unconfirmed_no_replay")
                        val savedCounter=OfficialAdStore.counter(svc,date)
                        if(savedCounter!=null&&fresh<savedCounter)return blockedResult(runId,date,"official_counter_regressed")
                        val reconciled=OfficialAdStore.reconcileRecordedReceipt(svc,date,original,fresh)
                        // An observed counter is not a receipt for this UNKNOWN. Keep both facts separately.
                        OfficialAdStore.observe(svc,date,fresh,runId,if(reconciled)"original_durable_receipt_fresh_exact_counter_no_new_start" else "recorded_receipt_review_counter_only_no_reward_inferred")
                        if(!reconciled)return blockedResult(runId,date,"recorded_daily_receipt_counter_unconfirmed_no_replay")
                        Logger.i("ADRECOVER","run=$runId originalRun="+original.runId+" index="+original.index+" recorded_receipt_committed no_new_start=true")
                    }else if(original!=null&&(OfficialAdRecoveryPolicy.mayBind(old,date,TokyoDate.of(),OfficialAdStore.counter(svc,date),true)||
                        ReviewedAdRecoveryPolicy.mayBind(old,date,TokyoDate.of(),OfficialAdStore.counter(svc,date),true,OfficialAdStore.reviewedKeys(svc,date)))){
                        val recoveryFailure=recoverExisting(runId,date,original)
                        if(recoveryFailure!=null)return blockedResult(runId,date,recoveryFailure)
                    }
                }
                if(old.lastOrNull()?.state==AdState.FAILED)reviewKnownLoadFailure(runId,date)
                if (!navigate(runId)) blockedReason = "ads_page_unreachable"
                else {
                    val initialCounter = refreshOfficialEntry(runId)
                    if (initialCounter == null) return blockedResult(runId, date, "fresh_official_counter_unavailable")
                    val previous=OfficialAdStore.counter(svc,date)
                    if(previous!=null && initialCounter<previous)return blockedResult(runId,date,"official_counter_regressed")
                    OfficialAdStore.observe(svc,date,initialCounter,runId,"fresh_native_reopened_entry_stable_4samples_3seconds")
                    if(initialCounter<25 && !OfficialAdStore.allResolved(svc,date))return blockedResult(runId,date,"new_official_attempt_requires_review")
                    var expectedCounter = initialCounter
                    var runStarts = 0
                    val reviewsAtStart=OfficialAdStore.reviewedKeys(svc,date).size
                    while (!AutoAccessibilityService.stopRequested) {
                        if (TokyoDate.of() != date) { detail = "jst_date_changed_reschedule_next_run"; break }
                        val l = labels()
                        if (AdSignals.limit(l)) { limit = true; detail = "official_limit"; break }
                        if (AdSignals.error(l)) { blockedReason = "official_error_dialog"; DailyAdStore.halt(svc, date, blockedReason); break }
                        if (blocked()) { blockedReason = "accessibility_or_keyguard"; break }
                        val rows = OfficialAdStore.attempts(svc, date)
                        if (rows.size >= OfficialAdStore.startCap(svc,date)) { blockedReason="official_attempt_safety_limit"; break }
                        if (runStarts >= OfficialLoadFailurePolicy.runCap(runLimit,reviewsAtStart,OfficialAdStore.reviewedKeys(svc,date).size)) { detail = "controlled_run_finished"; break }
                        val before = stableCounter()
                        if (before != expectedCounter) { blockedReason = "official_baseline_changed_before_start"; break }
                        val watch = watchNode()
                        if (!entry() || before == null || watch == null || AdSignals.receipt(l)) {
                            blockedReason = "entry_or_official_counter_unavailable"; break
                        }
                        DailyAdStore.setState(svc, AdState.AD_ENTRY_READY, date)
                        // Official quota was explicitly approved by the user; old unknown rows are never resolved or replayed.
                        val attempt = OfficialAdStore.reserve(svc,date,runId,before)
                        if(attempt==null){blockedReason="official_reservation_blocked";break}
                        active = attempt
                        RunForegroundService.start(svc,"每日广告 · 正在观看第"+(before+1)+"次")
                        runStarts++
                        closeDriver.beginFlow()
                        evidence(runId, attempt.index.toString() + "_start", "AD_OPENING")
                        if (TokyoDate.of() != date || blocked() || AutoAccessibilityService.stopRequested) {
                            detail = "stopped_before_click"; break
                        }
                        val port = interactionPort(runId,date,attempt)
                        DailyAdStore.setState(svc, AdState.AD_OPENING, date)
                        val sent=AdDispatchProtocol.dispatch({phase->OfficialAdStore.fence(svc,date,attempt,phase)},
                            {!blocked()&&!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date&&ui.click(watch)})
                        var receipt=try{if(sent)AdInteraction(port).awaitReceipt(before)
                            else AdReceipt(AdState.UNCONFIRMED,reason="watch_dispatch_unconfirmed")}
                        finally{observedClose=null;closeDriver.releaseVisualCache()}
                        if(receipt.reward&&!receipt.completed&&receipt.reason=="completion_not_confirmed"&&
                            !blocked()&&!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date&&entry()&&!closeDriver.isRecoverableSurface()){
                            val fresh=refreshOfficialEntry(runId)
                            if(fresh!=null&&OfficialAdStore.reconcileRecordedReceipt(svc,date,attempt,fresh))
                                receipt=AdReceipt(AdState.REWARD_CONFIRMED,completed=true,reward=true,after=fresh)
                        }
                        OfficialAdStore.finish(svc, date, runId, attempt.index, receipt.state,
                            receipt.completed, receipt.reward, receipt.reason, before, receipt.after,
                            "ad_" + runId + "_" + attempt.index + "_result.txt")
                        evidence(runId, attempt.index.toString()+"_result",receipt.toString())
                        active = null
                        DailyAdStore.setState(svc, receipt.state, date)
                        if (receipt.state != AdState.REWARD_CONFIRMED || !receipt.completed) {
                            detail = receipt.reason
                            FaultStore.capture(svc,runId,"daily_ads",receipt.reason,attempt.index.toString())
                            if(receipt.state==AdState.FAILED&&receipt.reason=="official_error_dialog"&&reviewKnownLoadFailure(runId,date)){
                                expectedCounter=OfficialAdStore.counter(svc,date)?:expectedCounter
                                continue
                            }
                            DailyAdStore.halt(svc, date, detail)
                            val afterUnknown=stableCounter()
                            if(afterUnknown!=null){
                                OfficialAdStore.observe(svc,date,afterUnknown,runId,"stable_official_counter_after_unconfirmed_receipt_no_local_credit")
                                if(afterUnknown==25){limit=true;detail="official_limit_with_unconfirmed_attempt"}
                            }
                            if (receipt.state == AdState.LIMIT_REACHED || receipt.after >= DAILY_AD_LIMIT) limit = true
                            break
                        }
                        OfficialAdStore.observe(svc,date,receipt.after,runId,"new_reserved_ad_fresh_receipt_and_exact_plus_one")
                        expectedCounter = receipt.after
                        delay(1200L)
                    }
                }
            }
        } catch (t: Exception) {
            blockedReason = "execution_error:" + t.javaClass.simpleName + ":" + t.message
            Logger.e("ADTASK", blockedReason, t)
            runCatching { evidence(runId, "exception", blockedReason) }
            FaultStore.capture(svc,runId,"daily_ads",blockedReason)
        } finally {
            active?.let { a ->
                OfficialAdStore.finish(svc, date, runId, a.index, AdState.UNCONFIRMED, false, false,
                    "run_interrupted:" + detail + blockedReason, -1, -1, "ad_" + runId + "_exception.txt")
                DailyAdStore.halt(svc, date, "interrupted_attempt")
            }
            overlay.hide()
            if (wake.isHeld) wake.release()
        }
        if (limit) DailyAdStore.setState(svc, AdState.LIMIT_REACHED, date)
        else if (blockedReason.isNotEmpty()) DailyAdStore.setState(svc, AdState.BLOCKED, date)
        val c = DailyAdStore.counters(svc, date)
        val r = DailyAdResult(runId, date, c.started, c.completed, c.rewardConfirmed, c.failed,
            c.unconfirmed, limit, AutoAccessibilityService.stopRequested, blockedReason, detail)
        Evidence.write(svc, "ad_result_" + runId + ".json", r.toJson().toString(2))
        return r
    }
}
