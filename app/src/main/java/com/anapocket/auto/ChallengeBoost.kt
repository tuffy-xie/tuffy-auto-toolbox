package com.anapocket.auto

import android.content.Context
import android.graphics.Rect
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Official Boost ordinals are receipts for an increased offered challenge reward, not account points. */
object ChallengeSignals {
    fun reviewedAdvance(boost: Int, before: Int, expected: Int, labels: List<String>): Boolean {
        val steps = increments(labels)
        return boost in 0..3 && nextBoost(labels) == boost + 2 && offeredReward(labels) == expected &&
            steps.size == 5 && before + steps[boost] == expected && expected > before
    }
    fun reviewedNoGain(boost: Int, before: Int, expected: Int, labels: List<String>): Boolean {
        val steps = increments(labels)
        return boost in 0..4 && nextBoost(labels) == boost + 1 && offeredReward(labels) == before &&
            steps.size == 5 && before + steps[boost] == expected
    }
    fun nextBoost(labels: List<String>): Int? = Regex("([1-5])回目のブースト")
        .find(labels.joinToString(" "))?.groupValues?.get(1)?.toIntOrNull()
    fun joined(labels: List<String>) = labels.any { it.contains("チャレンジをはじめました") }
    fun dialog(labels: List<String>) = joined(labels) || nextBoost(labels) != null
    fun offeredReward(labels: List<String>): Int? = Regex("現在のチャレンジ達成後の報酬[:：]\\s*([\\d,]+)\\s*pt")
        .find(labels.joinToString(" "))?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
    fun increments(labels: List<String>): List<Int> = labels.mapNotNull {
        Regex("^\\+(\\d+)(?:pt)?$").matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull()
    }.takeIf { it.size == 5 && it.all { x -> x > 0 } } ?: emptyList()
    fun verifiedAdvance(before: Int, after: Int?): Boolean = after == before + 1 && before in 0..5
}

/** Checked, date/task scoped journal. An interrupted dispatched ad is never automatically submitted again. */
object ChallengeStore {
    @Synchronized fun setCoverage(ctx: Context, date: String, cat: Cat, maxKm: Int) {
        check(sp(ctx).edit().putInt("covered_" + date + "_" + cat.name, maxKm).commit()) { "category_progress_write_failed" }
    }
    fun coverage(ctx: Context, date: String, cat: Cat): Int = sp(ctx).getInt("covered_" + date + "_" + cat.name, 0)
    fun hasPending(ctx: Context, date: String): Boolean {
        val a = rows(ctx, date)
        return (0 until a.length()).any { a.getJSONObject(it).optString("state") in setOf("AD_OPENING", "UNCONFIRMED", "JOINED", "PILOT_JOIN_CONFIRMED", "AD_BUDGET_REACHED") }
    }
    private fun sp(ctx: Context) = ctx.getSharedPreferences("challenge_boost_v1", Context.MODE_PRIVATE)
    @Synchronized fun rows(ctx: Context, date: String): JSONArray =
        JSONArray(sp(ctx).getString("day_$date", "[]") ?: error("missing_challenge_ledger"))
    @Synchronized fun save(ctx: Context, date: String, row: JSONObject) {
        val a = rows(ctx, date)
        val matches = (0 until a.length()).filter { a.getJSONObject(it).getString("key") == row.getString("key") }
        check(matches.size <= 1) { "duplicate_challenge_identity" }
        if (matches.isEmpty()) a.put(row) else a.put(matches.single(), row)
        check(sp(ctx).edit().putString("day_$date", a.toString()).commit()) { "challenge_ledger_write_failed" }
    }
    fun find(ctx: Context, date: String, key: String): JSONObject? {
        val a = rows(ctx, date)
        return (0 until a.length()).map { a.getJSONObject(it) }.singleOrNull { it.getString("key") == key }
    }
    fun summary(ctx: Context, date: String = TokyoDate.of()): String {
        val a = rows(ctx, date)
        return "任务日 $date（手机 JST）\n" + if (a.length() == 0) "尚未执行挑战接取" else (0 until a.length()).joinToString("\n") {
            val r = a.getJSONObject(it)
            val state = when (r.optString("state")) {
                "BOOST_MAX_CONFIRMED" -> "已接取并升满"
                "BOOST_MAX_OBSERVED" -> "官方详情确认升满（逐次广告回执不完整）"
                "AD_OPENING" -> "观看广告中"
                "JOINED" -> "已接取，叠加中"
                "UNCONFIRMED" -> "回执未确认"
                "PILOT_JOIN_CONFIRMED" -> "接取已确认，待叠加"
                else -> r.optString("state")
            }
            "${r.getString("title")}：$state · Boost ${r.optInt("boost")}/5 · 广告 ${r.optJSONArray("ads")?.length() ?: 0}" +
                (if (r.optInt("offeredReward", -1) >= 0) " · 达成后奖励 ${r.getInt("offeredReward")}pt" else "") +
                (r.optJSONArray("ads")?.let { ads -> (0 until ads.length()).count { ads.getJSONObject(it).optString("state") == AdState.FAILED.name } } ?: 0).let {
                    if (it > 0) " · 失败 $it" else ""
                }
        }
    }
}

class ChallengeTaskController(private val svc: AutoAccessibilityService) {
    private fun startLimit(row: JSONObject): Int {
        val a = row.getJSONArray("ads")
        val verifiedFailures = (0 until a.length()).count { a.getJSONObject(it).optBoolean("officialNoGain") }
        val verifiedLoadFailures=(0 until a.length()).count {
            val ad=a.getJSONObject(it)
            ad.optBoolean("officialNoGain")&&ad.optString("recoveryTrigger")=="official_ad_display_failed_restart_requested"
        }
        return VerifiedLoadFailurePolicy.startLimit(verifiedFailures,verifiedLoadFailures)
    }
    private val ui = Ui(svc)
    private val closeDriver = AdUiClose(svc)
    private val pkg = "us.ana_pocket.ANAPocket"
    private var active: JSONObject? = null
    private var activeRow: JSONObject? = null
    private var navigationEpoch = 0
    private fun nodes() = if (ui.currentPackage() == pkg) ui.foregroundNodes().filter { it.isVisibleToUser } else emptyList()
    private fun labels() = nodes().map { ui.label(it) }.filter { it.isNotBlank() }
    private fun byId(id: String) = nodes().singleOrNull { it.isEnabled && it.viewIdResourceName == "$pkg:id/$id" }
    private fun initialBaselineProof(row: JSONObject, ad: JSONObject): Pair<Int, List<Int>>? {
        if (ad.optBoolean("adDepartureObserved") && ad.optInt("expectedJoinReward") > 0 && ad.optJSONArray("initialBoostSteps")?.length() == 5) {
            val steps = ad.getJSONArray("initialBoostSteps")
            return ad.getInt("expectedJoinReward") to (0 until steps.length()).map { steps.getInt(it) }
        }
        val files = java.io.File(svc.getExternalFilesDir(null) ?: svc.filesDir, "evidence").listFiles()?.toList() ?: return null
        val prefix = "challenge_" + ad.getString("runId") + "_" + row.getString("cat") + "_" + ad.getInt("index") + "_"
        val started = ad.getLong("startedAt")
        val finished = ad.optLong("finishedAt", Long.MAX_VALUE)
        fun stamp(file: java.io.File) = file.name.substringAfterLast('_').substringBefore('.').toLongOrNull() ?: -1L
        val departed = ad.optBoolean("adDepartureObserved") || files.any {
            it.name.startsWith(prefix + "AD_PLAYING_") && it.extension == "txt" && stamp(it) in started..finished
        }
        if (!departed) return null
        for (file in files.filter { it.name.startsWith(prefix + "start_") && it.extension == "txt" && stamp(it) in started..finished }) {
            val text = file.readText()
            val saved = runCatching { JSONObject(text.lineSequence().first()) }.getOrNull() ?: continue
            if (saved.optString("key") != row.getString("key") || saved.optString("date") != row.getString("date")) continue
            val savedAds = saved.optJSONArray("ads") ?: continue
            if (savedAds.length() != 1 || savedAds.getJSONObject(0).optLong("startedAt") != started) continue
            val lines = text.lineSequence().toList()
            if (lines.none { it.contains("id=" + pkg + ":id/text_name ") && it.endsWith("label=" + row.getString("title")) } ||
                lines.none { it.contains("id=" + pkg + ":id/text_date ") && it.endsWith("label=" + row.getString("date").replace('-', '/')) }) continue
            val pointLine = lines.singleOrNull { it.contains("id=" + pkg + ":id/button_join_boost_point_text ") } ?: continue
            val reward = pointLine.substringAfterLast("label=").removeSuffix("pt").replace(",", "").toIntOrNull() ?: continue
            val steps = lines.filter { it.contains("id=" + pkg + ":id/point ") }.mapNotNull { it.substringAfterLast("label=").takeIf { x -> x.startsWith("+") }?.drop(1)?.toIntOrNull() }
            if (steps.size == 5) return reward to steps
        }
        return null
    }

    private fun observedJoinedMax(row: JSONObject): Int? {
        fun field(id: String) = byId(id)?.let { ui.label(it) }
        val reward = field("text_point_value")?.replace(",", "")?.toIntOrNull()
        return reward?.takeIf { ChallengeDetailReceipt.maxMatches(row.getString("title"), row.getString("date"),
            field("text_name"), field("text_date"), field("text_attended"), field("boost_count_text"), it) }
    }
    private suspend fun reconcileJoinedMax(runId: String, date: String, row: JSONObject): Boolean {
        if (AutoAccessibilityService.stopRequested || TokyoDate.of() != date) return false
        val reward = observedJoinedMax(row) ?: return false
        delay(1500); check(observedJoinedMax(row) == reward) { "official_joined_detail_not_stable" }
        evidence(runId, "official_joined_max_before", row.toString())
        row.put("officialObservedBoost", 5).put("officialObservedReward", reward)
            .put("officialObservedAt", System.currentTimeMillis()).put("officialObservationRunId", runId)
            .put("boost", 5).put("offeredReward", reward).put("state", "BOOST_MAX_OBSERVED")
            .put("automationHistoryComplete", false).put("observationSource", "native_joined_detail_exact_title_date_attendance_boost5")
        // Preserve every pending/unknown ad: task state does not identify individual ad receipts.
        row.remove("error")
        ChallengeStore.save(svc, date, row)
        evidence(runId, "official_joined_max_after", row.toString())
        return true
    }
    private fun blocked(): Boolean = !AutoAccessibilityService.isConnected() ||
        (svc.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked
    private suspend fun tap(n: AccessibilityNodeInfo): Boolean {
        if (!n.refresh() || !n.isVisibleToUser || !n.isEnabled) return false
        val r = Rect(); n.getBoundsInScreen(r)
        return r.width() > 0 && r.height() > 0 && ui.tap(r.centerX(), r.centerY())
    }
    private suspend fun evidence(runId: String, tag: String, description: String = "") {
        val name = "challenge_${runId}_${tag}_${System.currentTimeMillis()}"
        val detail = ui.foregroundNodes().joinToString("\n") {
            val r = Rect(); it.getBoundsInScreen(r)
            "${it.className} id=${it.viewIdResourceName} visible=${it.isVisibleToUser} enabled=${it.isEnabled} clickable=${it.isClickable} bounds=$r label=${ui.label(it).take(500)}"
        }
        Evidence.write(svc, "$name.txt", "$description\nJST=${TokyoDate.time()}\n${ui.dump()}\nNODE_DETAILS\n$detail")
        ui.captureVisual(includePng = true).png?.let { Evidence.writeBytes(svc, "$name.png", it) }
        Logger.i("CHBOOST", "run=$runId $tag $description")
    }

    /** Per-card binding is established before the first ad; anonymous dialogs are never adopted after restart. */
    private suspend fun watch(runId: String, date: String, row: JSONObject, button: AccessibilityNodeInfo?, initial: Boolean,
        recovering: JSONObject? = null): Boolean {
        val ads = row.getJSONArray("ads")
        val previous = if (initial) 0 else row.getInt("boost") + 1
        val rewardBefore = if (initial) null else recovering?.getInt("offeredBefore") ?: ChallengeSignals.offeredReward(labels())
        val steps = if (initial) emptyList() else ChallengeSignals.increments(labels())
        if (!initial) check(rewardBefore != null && (recovering != null || steps.size == 5)) { "official_reward_baseline_missing" }
        val expectedReward = if (initial) null else recovering?.getInt("expectedOfferedAfter") ?: (rewardBefore!! + steps[previous - 1])
        val a = recovering ?: JSONObject().put("runId", runId).put("index", ads.length() + 1).put("initialJoin", initial)
            .put("startedAt", System.currentTimeMillis()).put("state", "AD_OPENING").put("confirmed", false)
            .put("offeredBefore", rewardBefore ?: JSONObject.NULL).put("expectedOfferedAfter", expectedReward ?: JSONObject.NULL)
        if (!initial && recovering == null) a.put("boostSteps", JSONArray(steps))
        if (initial && recovering == null) {
            val point = byId("button_join_boost_point_text")?.let { ui.label(it).removeSuffix("pt").replace(",", "").toIntOrNull() }
                ?: error("initial_ad_join_reward_baseline_missing")
            val initialSteps = ChallengeSignals.increments(labels())
            check(initialSteps.size == 5) { "initial_boost_table_missing" }
            a.put("expectedJoinReward", point).put("initialBoostSteps", JSONArray(initialSteps))
        }
        closeDriver.beginFlow(recovering?.optBoolean("storeSheetObserved") ?: false)
        if (recovering == null) {
            val cat=Cat.ofName(row.optString("cat"))?:error("category_missing")
            val accepted=TravelSelection.reserve(svc,cat){check(ads.length()<startLimit(row)){"per_task_ad_limit"};ads.put(a);row.put("state","AD_OPENING");ChallengeStore.save(svc,date,row);true}
            if(accepted!=true){TravelSelection.audit(svc,date,runId,cat);return false}
        }
        else {
            check(!a.getBoolean("confirmed") && a.getInt("index") == ads.length() && closeDriver.isRecoverableSurface()) { "existing_ad_not_observed" }
            a.put("previousFailure", a.optString("reason")).put("recoveryRunId", runId).put("resumedAt", System.currentTimeMillis())
            val history = a.optJSONArray("recoveries") ?: JSONArray().also { a.put("recoveries", it) }
            history.put(JSONObject().put("runId", runId).put("resumedAt", System.currentTimeMillis()).put("previousReason", a.optString("reason")))
        }
        active = a; activeRow = row
        row.put("state", "AD_OPENING")
        ChallengeStore.save(svc, date, row) // reservation before dispatch
        RunForegroundService.start(svc,(Cat.ofName(row.optString("cat"))?.title?:"出行任务")+" · "+(if(initial)"观看接取广告" else "Boost第"+previous+"次"))
        evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_${if (recovering == null) "start" else "recover_existing"}", row.toString())
        var closeNode: AccessibilityNodeInfo? = null
        val port = object : AdInteractionPort {
            override fun now() = android.os.SystemClock.elapsedRealtime()
            override fun stopped() = AutoAccessibilityService.stopRequested || TokyoDate.of() != date
            override fun stopReason() = if (TokyoDate.of() != date) "phone_jst_date_changed" else "user_stopped"
            override suspend fun frame(): AdFrame {
                if(!AccountSafety.authorize(svc,"challenge_ad"))return AdFrame(ui.currentPackage()==pkg,false,manualRequired=true)
                closeDriver.restoreAdAfterStore()
                val ls = labels()
                val next = ChallengeSignals.nextBoost(ls)
                val offered = ChallengeSignals.offeredReward(ls)
                // Initial receipt must explicitly say joined. Boost receipts must advance exactly one ordinal.
                val confirmedSerial = if (initial && InitialJoinReceiptPolicy.matches(a.optInt("expectedJoinReward"),
                    (0 until a.getJSONArray("initialBoostSteps").length()).map{a.getJSONArray("initialBoostSteps").getInt(it)},ls)) 1
                    else if (!initial && offered == expectedReward &&
                        (next == previous + 1 || previous == 5 && next == null)) previous + 1 else null
                closeNode = closeDriver.identifyClose()
                if (closeNode == null && closeDriver.revealIfNeeded()) closeNode = closeDriver.identifyClose()
                if (closeDriver.observedStoreOverlay && !a.optBoolean("storeSheetObserved")) {
                    a.put("storeSheetObserved", true); ChallengeStore.save(svc, date, row)
                }
                if (closeDriver.returnedViaPangleLauncher && !a.optBoolean("pangleReplayReturnObserved")) {
                    a.put("pangleReplayReturnObserved", true); ChallengeStore.save(svc, date, row)
                }
                val ad = ui.currentPackage() == pkg && (closeDriver.isAdSurface() ||
                    nodes().any { AdSignals.part(listOf(ui.label(it))) != null || ui.label(it).contains("Skip Ad") })
                val returned = ui.currentPackage() == pkg && !ad && (ChallengeSignals.dialog(ls) || confirmedSerial != null)
                return AdFrame(ui.currentPackage() == pkg, returned, confirmedSerial, confirmedSerial == previous + 1,
                    closeNode != null, error = AdSignals.error(ls), blocked = blocked(), adSurface = ad,
                    part = AdSignals.part(ls), storeOverlay = closeDriver.observedStoreOverlay,
                    nativePangleRender = closeDriver.isNativePangleRender(),advanceControl=closeDriver.observedAdvanceControl)
            }
            override suspend fun close(): Boolean {
                val n = closeNode ?: return false
                if (ChallengeSignals.dialog(labels())) return false
                val r = closeDriver.controlBounds(n) ?: return false
                Logger.i("CHCLOSE", "run=$runId store=${closeDriver.observedStoreOverlay} bounds=$r")
                return if (closeDriver.observedTapOnly) ui.tap(r.centerX(), r.centerY()) else ui.click(n)
            }
            override suspend fun dismissReceipt() {} // preserve official Boost panel for the next verified action
            override suspend fun state(state: AdState) {
                if (state == AdState.AD_PLAYING) a.put("adDepartureObserved", true)
                a.put("state", state.name); ChallengeStore.save(svc, date, row)
                if (state == AdState.AD_CLOSE_AVAILABLE && closeDriver.recentlyRevealed) {
                    Logger.i("CHBOOST", "run=$runId ephemeral_close_available_after_single_tap")
                } else evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_${state.name}")
            }
            override suspend fun progress(part: AdPart) {
                evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_part_${part.current}")
            }
            override suspend fun diagnostic(reason:String){FaultStore.capture(svc,runId,"challenge",reason,row.getString("cat")+"_"+a.getInt("index"))}
            override suspend fun observationBudgetChanged(ms: Long) {
                a.put("nativePangleObservationBudgetMs", ms); ChallengeStore.save(svc, date, row)
                evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_nativePangle_wait", "same attempt; native SDK close only; absolute300s observation cap")
            }
        }
        val result = if (!blocked() && !AutoAccessibilityService.stopRequested && TokyoDate.of() == date &&
            (recovering != null || button != null && tap(button)))
            AdInteraction(port).awaitReceipt(previous) else AdReceipt(AdState.UNCONFIRMED, reason = "dispatch_interrupted")
        val verified = result.completed && result.reward && ChallengeSignals.verifiedAdvance(previous, result.after)
        a.put("finishedAt", System.currentTimeMillis()).put("state", result.state.name)
            .put("confirmed", verified).put("reason", result.reason).put("afterSerial", result.after)
            .put("offeredAfter", ChallengeSignals.offeredReward(labels()) ?: JSONObject.NULL)
        if (verified) {
            row.put("joined", true).put("boost", if (initial) 0 else previous).put("state", "JOINED")
                .put("offeredReward", ChallengeSignals.offeredReward(labels()) ?: JSONObject.NULL)
                .put("rewardMeaning", "offered_on_real_challenge_completion_not_account_credit")
            row.remove("error")
        } else row.put("state", "UNCONFIRMED").put("error", result.reason)
        ChallengeStore.save(svc, date, row)
        active = null
        if(!verified)FaultStore.capture(svc,runId,"challenge",result.reason,row.getString("cat")+"_"+a.getInt("index"))
        evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_result", row.toString())
        return verified || reconcilePangleLateAdvance(runId, date, row, a) || recoverBlankGoogle(runId,date,row,a) || recoverOpaque(runId, date, row, a)
    }

    private suspend fun recoverBlankGoogle(runId:String,date:String,row:JSONObject,ad:JSONObject):Boolean {
        if(ad.optBoolean("initialJoin")||ad.optBoolean("confirmed")||ad.optString("reason")!="ad_timeout_95s"||
            ad.getInt("index")!=row.getJSONArray("ads").length()||ad.optBoolean("blankGoogleBackRequested"))return false
        fun safe()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&AccountSafety.authorize(svc,"blank_google_return")
        if(!safe()||ui.currentPackage()!=pkg||AutoAccessibilityService.foregroundActivity!="com.google.android.gms.ads.AdActivity")return false
        ui.refreshCache()
        val visible=nodes()
        val controls=visible.count{n->val b=Rect();n.getBoundsInScreen(b);n.isClickable&&b.width()>16&&b.height()>16}
        val png=ui.captureVisual(includePng=true).png?:return false
        val bmp=android.graphics.BitmapFactory.decodeByteArray(png,0,png.size)?:return false
        val black=try{var dark=0;var pixels=0
            for(y in (bmp.height*.12).toInt() until (bmp.height*.85).toInt() step 12)for(x in (bmp.width*.15).toInt() until (bmp.width*.85).toInt() step 12){
                val c=bmp.getPixel(x,y);if((c shr 16 and 255)<12&&(c shr 8 and 255)<12&&(c and 255)<12)dark++;pixels++
            };pixels>0&&dark.toDouble()/pixels>.99
        }finally{bmp.recycle()}
        if(!BlankGoogleAdPolicy.mayReturn(AutoAccessibilityService.foregroundActivity,true,System.currentTimeMillis()-ad.getLong("startedAt"),false,
            visible.map{ui.label(it)},visible.map{it.className.toString()},controls,black))return false
        val stepsJson=ad.optJSONArray("boostSteps")?:return false
        val steps=(0 until stepsJson.length()).map{stepsJson.getInt(it)}
        evidence(runId,"blank_google_before_normal_back",row.toString())
        ad.put("blankGoogleBackRequested",true).put("blankGoogleBackAt",System.currentTimeMillis()).put("blankGoogleBackRunId",runId)
        ChallengeStore.save(svc,date,row)
        check(safe()&&ui.back()){"blank_google_normal_back_failed"}
        // Returning is never completion. Only the same bound official ordinal AND reward can confirm it.
        val until=android.os.SystemClock.elapsedRealtime()+45_000
        while(android.os.SystemClock.elapsedRealtime()<until){
            check(safe()){"blank_google_return_stopped"};delay(700);ui.refreshCache()
            if(officialResumePrompt()!=null)return resumeOfficialInterruptedBoost(runId,date,row,ad)
            fun advanced()=OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,labels())=="ADVANCED"
            if(!advanced())continue
            delay(1500);ui.refreshCache();if(!safe()||!advanced())continue
            evidence(runId,"blank_google_verified_official_receipt",row.toString())
            ad.put("previousFailure",ad.optString("reason")).put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true)
                .put("afterSerial",row.getInt("boost")+2).put("offeredAfter",ad.getInt("expectedOfferedAfter"))
                .put("finishedAt",System.currentTimeMillis()).put("reason","native_boost_advance_after_blank_google_normal_return")
            row.put("boost",row.getInt("boost")+1).put("state","JOINED").put("offeredReward",ad.getInt("expectedOfferedAfter"));row.remove("error")
            ChallengeStore.save(svc,date,row);evidence(runId,"blank_google_reconciled_same_attempt",row.toString());return true
        }
        evidence(runId,"blank_google_return_unconfirmed",row.toString());return false
    }

    private suspend fun reconcilePangleLateAdvance(runId: String, date: String, row: JSONObject, ad: JSONObject): Boolean {
        if (!ad.optBoolean("pangleReplayReturnObserved") || ad.getBoolean("initialJoin")) return false
        val saved = ad.optJSONArray("boostSteps") ?: return false
        val steps = (0 until saved.length()).map { saved.getInt(it) }
        val ads = row.getJSONArray("ads")
        fun valid() = !AutoAccessibilityService.stopRequested && !blocked() && TokyoDate.of() == date &&
            ad.getInt("index") == ads.length() && PangleStoreReplayPolicy.confirmedLateAdvance(
                ad.optBoolean("pangleReplayReturnObserved"), ad.getString("runId") == runId, ad.getBoolean("initialJoin"),
                ad.optBoolean("adDepartureObserved"),
                row.getInt("boost"), ad.getInt("offeredBefore"), ad.getInt("expectedOfferedAfter"), steps, labels())
        evidence(runId, "pangle_return_wait_late_official_receipt", row.toString())
        if (AutoAccessibilityService.stopRequested || blocked() || TokyoDate.of() != date || !ui.launchApp(pkg)) return false
        ad.put("pangleReceiptRefreshAt", System.currentTimeMillis()); ChallengeStore.save(svc, date, row)
        for (i in 0 until 20) {
            if (AutoAccessibilityService.stopRequested || blocked() || TokyoDate.of() != date) return false
            ui.refreshCache(); if (valid()) break
            delay(1000)
        }
        if (!valid()) return false
        delay(1500); if (!valid()) return false
        evidence(runId, "pangle_return_late_receipt_before", row.toString())
        ad.put("previousFailure", ad.optString("reason")).put("state", AdState.REWARD_CONFIRMED.name).put("confirmed", true)
            .put("offeredAfter", ad.getInt("expectedOfferedAfter")).put("afterSerial", row.getInt("boost") + 2)
            .put("reason", "verified_late_official_boost_after_bound_pangle_store_return")
            .put("firstUnconfirmedAt", ad.optLong("finishedAt")).put("finishedAt", System.currentTimeMillis())
        row.put("state", "JOINED").put("boost", row.getInt("boost") + 1).put("offeredReward", ad.getInt("expectedOfferedAfter")); row.remove("error")
        ChallengeStore.save(svc, date, row); navigationEpoch++
        evidence(runId, "pangle_return_late_receipt_after", row.toString())
        return true
    }


    /** Only this run's sole bound Boost may leave a stuck canvas. No clear-all or arbitrary task removal. */
    private suspend fun recoverOpaque(runId: String, date: String, row: JSONObject, ad: JSONObject,
        observedExistingLoadFailure: Boolean = false): Boolean {
        val ads = row.getJSONArray("ads")
        val stepsJson = ad.optJSONArray("boostSteps") ?: return false
        val steps = (0 until stepsJson.length()).map { stepsJson.getInt(it) }
        val failures = (0 until ads.length()).count { ads.getJSONObject(it).optBoolean("officialNoGain") }
        val used = OpaqueRecoveryPolicy.recoveryAlreadyUsed(ad.optString("nativeRecoveryStage"), ad.optInt("nativeRecoveryAttempts",
            if (ad.has("nativeRecoveryStartedAt")) 1 else 0))
        val verifiedLoadFailures=(0 until ads.length()).count {
            val prior=ads.getJSONObject(it)
            prior.optBoolean("officialNoGain")&&prior.optString("recoveryTrigger")=="official_ad_display_failed_restart_requested"
        }
        val loadFailure = OpaqueRecoveryPolicy.mayRecoverLoadFailure(ad.getBoolean("initialJoin"), AdSignals.loadFailure(labels()),
            ad.getString("runId") == runId || observedExistingLoadFailure, used, failures, row.getInt("boost"), steps)||
            VerifiedLoadFailurePolicy.mayReviewSecond(ad.getBoolean("initialJoin"),AdSignals.loadFailure(labels()),
                ad.getString("runId")==runId||observedExistingLoadFailure,used,failures,verifiedLoadFailures,row.getInt("boost"),steps)
        if (!loadFailure && !OpaqueRecoveryPolicy.mayRecover(ad.getBoolean("initialJoin"), ad.optString("reason") == "ad_timeout_95s",
            ad.optBoolean("adDepartureObserved"), ad.getString("runId") == runId, closeDriver.isOpaquePlayable(),
            used, failures,
            row.getInt("boost"), steps)) return false
        fun safe() = !AutoAccessibilityService.stopRequested && !blocked() && TokyoDate.of() == date
        check(safe()) { "native_recovery_stopped" }
        val snapshot = ChallengeStore.rows(svc, date)
        check((0 until snapshot.length()).count { snapshot.getJSONObject(it).optString("state") in setOf("AD_OPENING", "UNCONFIRMED") } == 1) { "ambiguous_recovery_binding" }
        ad.put("nativeRecoveryStartedAt", System.currentTimeMillis()).put("nativeRecoveryRunId", runId)
            .put("nativeRecoveryAttempts", ad.optInt("nativeRecoveryAttempts", if (ad.has("recoveryTrigger")) 1 else 0) + 1)
            .put("recoveryTrigger", if (loadFailure) "official_ad_display_failed_restart_requested" else "opaque_canvas_timeout")
        ChallengeStore.save(svc, date, row)
        evidence(runId, "opaque_recovery_before_recents", row.toString())
        check(svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)) { "recents_unavailable" }
        fun recentsFocused(): Boolean = svc.windows.any { w ->
            (w.isActive || w.isFocused) && w.root?.packageName?.toString() == "com.miui.home"
        }
        suspend fun recentsNodes(): List<AccessibilityNodeInfo> {
            repeat(24) {
                check(safe()) { "native_recovery_stopped" }; ui.refreshCache()
                if (recentsFocused()) {
                    val observed = ui.activeNodes(pkg = "com.miui.home").filter { it.isVisibleToUser }
                    if (observed.any { n -> n.viewIdResourceName == "com.miui.home:id/recents_view" }) return observed
                }
                delay(300)
            }
            error("recents_environment_not_observed_within_budget")
        }
        var card: AccessibilityNodeInfo? = null
        for (attempt in 0..2) {
            val all = recentsNodes()
            val candidates = all.filter { OpaqueRecoveryPolicy.unlockedAnaCard(it.contentDescription?.toString() ?: "") && it.isClickable }
            check(candidates.size == 1) { "ana_task_locked_missing_or_ambiguous" }
            val n = candidates.single(); val b = Rect(); n.getBoundsInScreen(b)
            val width = svc.resources.displayMetrics.widthPixels
            if (b.width() >= width * .45 && b.left > 0 && b.right < width) { card = n; break }
            check(attempt < 2) { "ana_task_not_fully_visible" }
            val direction = if (b.centerX() > width / 2) -1 else 1
            check(ui.swipe(b.centerX().coerceIn(width / 5, width * 4 / 5), b.centerY(),
                (b.centerX() + direction * width * .52).toInt().coerceIn(width / 8, width * 7 / 8), b.centerY(), 450)) { "recents_scroll_failed" }
            delay(700); ui.refreshCache()
        }
        val target = card ?: error("ana_task_not_found")
        check(safe() && recentsFocused() && target.refresh() && OpaqueRecoveryPolicy.unlockedAnaCard(target.contentDescription?.toString() ?: "")) { "ana_task_changed" }
        val b = Rect(); target.getBoundsInScreen(b)
        evidence(runId, "opaque_recovery_exact_unlocked_ana_card", row.toString())
        ad.put("nativeRecoveryStage", "REMOVE_ONLY_OBSERVED_ANA_TASK"); ChallengeStore.save(svc, date, row)
        check(ui.swipe(b.centerX(), b.centerY(), b.centerX(), (b.top - b.height() / 4).coerceAtLeast(80), 500)) { "ana_task_dismiss_failed" }
        delay(900); ui.refreshCache()
        check(recentsFocused() && ui.activeNodes(pkg = "com.miui.home").none {
            it.isVisibleToUser && OpaqueRecoveryPolicy.unlockedAnaCard(it.contentDescription?.toString() ?: "") }) { "ana_task_removal_not_confirmed" }
        evidence(runId, "opaque_recovery_ana_task_removed")
        navigationEpoch++ // Cold SDK recovery resets ANA's selected category. Re-select once after this task.
        check(safe() && ui.launchApp(pkg)) { "ana_relaunch_failed" }
        var resume: AccessibilityNodeInfo? = null
        for (i in 0 until 35) {
            check(safe()) { "native_recovery_stopped" }
            ui.refreshCache()
            if (labels().any { it == "ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？" }) {
                resume = nodes().singleOrNull { it.viewIdResourceName == "android:id/button1" && ui.label(it) == "再開する" && it.isEnabled }
                if (resume != null) break
            }
            delay(1000)
        }
        val button = resume ?: error("official_interrupted_boost_prompt_missing")
        evidence(runId, "opaque_recovery_official_resume_prompt")
        ad.put("nativeRecoveryStage", "OFFICIAL_RESUME_OBSERVED"); ChallengeStore.save(svc, date, row)
        check(safe() && tap(button)) { "official_resume_dispatch_failed" }; delay(1800)
        fun outcome() = OpaqueRecoveryPolicy.outcome(row.getInt("boost"), ad.getInt("offeredBefore"), ad.getInt("expectedOfferedAfter"), steps, labels())
        var result: String? = null
        for (i in 0 until 10) { ui.refreshCache(); result = outcome(); if (result != null) break; check(safe()); delay(700) }
        check(result != null && safe()) { "official_resumed_outcome_unconfirmed" }
        delay(1500); ui.refreshCache(); check(outcome() == result && safe()) { "official_resumed_outcome_unstable" }
        evidence(runId, "opaque_recovery_official_outcome_before", row.toString())
        ad.put("previousFailure", ad.optString("reason")).put("nativeRecoveryFinishedAt", System.currentTimeMillis())
            .put("nativeRecoveryStage", result).put("offeredAfter", ChallengeSignals.offeredReward(labels()))
        if (result == "NO_GAIN") {
            ad.put("state", AdState.FAILED.name).put("confirmed", false).put("officialNoGain", true)
                .put("reason", "official_resumed_session_confirms_boost_not_applied")
        } else {
            ad.put("state", AdState.REWARD_CONFIRMED.name).put("confirmed", true)
                .put("reason", "native_resumed_official_boost_advance").put("afterSerial", row.getInt("boost") + 2)
            row.put("boost", row.getInt("boost") + 1).put("offeredReward", ad.getInt("expectedOfferedAfter"))
        }
        row.put("state", "JOINED"); row.remove("error")
        ChallengeStore.save(svc, date, row)
        evidence(runId, "opaque_recovery_official_outcome_after", row.toString())
        if (loadFailure) {
            evidence(runId, "official_load_failure_cooldown", "30000ms; no ad dispatch during cooldown")
            repeat(30) { check(safe()) { "load_recovery_cooldown_stopped" }; delay(1000) }
        }
        return true
    }

    private suspend fun boost(runId: String, date: String, row: JSONObject, adBudget: Int) {
        val selectedCat=Cat.ofName(row.optString("cat"))?:error("category_missing")
        while (row.getInt("boost") < 5 && row.getJSONArray("ads").length() < minOf(adBudget + (startLimit(row)-6).coerceAtLeast(0), startLimit(row))) {
            check(!AutoAccessibilityService.stopRequested && !blocked() && TokyoDate.of() == date) { "stopped_or_date_changed" }
            if(!TravelSelection.enabled(svc,selectedCat)){TravelSelection.audit(svc,date,runId,selectedCat);return}
            val next = ChallengeSignals.nextBoost(labels())
            check(next == row.getInt("boost") + 1) { "boost_baseline_ordinal_mismatch" }
            val button = nodes().singleOrNull { ui.label(it).contains("動画を見て報酬をブースト") }
                ?: error("observed_boost_button_missing")
            delay(2500) // Let the SDK replenish after the previous verified receipt, without refreshing the page.
            val watched=watch(runId,date,row,button,false)
            if(!watched&&!TravelSelection.enabled(svc,selectedCat)){TravelSelection.audit(svc,date,runId,selectedCat);return}
            check(watched) { "boost_not_confirmed_${row.getString("cat")}_$next" }
        }
        row.put("state", if (row.getInt("boost") == 5) "BOOST_MAX_CONFIRMED" else "AD_BUDGET_REACHED")
        ChallengeStore.save(svc, date, row)
        evidence(runId, "${row.getString("cat")}_final", row.toString())
        // Never select the irreversible 'do not boost' exit before completing all five levels.
        if (row.getInt("boost") == 5) {
            nodes().firstOrNull { ui.label(it).trim() == "OK" }?.let { tap(it); delay(1200) }
        }
    }

    private fun officialResumePrompt():AccessibilityNodeInfo? {
        if(ui.currentPackage()!=pkg)return null
        val observed=nodes()
        if(observed.none{it.viewIdResourceName=="android:id/message"&&ui.label(it)=="ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？"})return null
        return observed.singleOrNull{it.viewIdResourceName=="android:id/button1"&&ui.label(it)=="再開する"&&it.isEnabled}
    }
    private suspend fun reconcileRecordedInitialReceipt(runId:String,date:String,row:JSONObject,ad:JSONObject,pendingCount:Int):Boolean {
        if(!ad.optBoolean("initialJoin")||ad.optBoolean("confirmed")||ad.optString("reason")!="ad_timeout_95s")return false
        val files=java.io.File(svc.getExternalFilesDir(null)?:svc.filesDir,"evidence").listFiles()?.toList()?:return false
        val prefix="challenge_"+ad.getString("runId")+"_"+row.getString("cat")+"_"+ad.getInt("index")+"_"
        val started=ad.getLong("startedAt");val finished=ad.optLong("finishedAt")
        fun stamp(f:java.io.File)=f.name.substringAfterLast('_').substringBefore('.').toLongOrNull()?:-1L
        val reward=ad.optInt("expectedJoinReward");val table=ad.optJSONArray("initialBoostSteps")?:return false
        val steps=(0 until table.length()).map{table.getInt(it)}
        val startProof=files.filter{it.name.startsWith(prefix+"start_")&&it.extension=="txt"&&stamp(it) in started..finished}.any{f->
            val text=f.readText();val saved=runCatching{JSONObject(text.lineSequence().first())}.getOrNull()?:return@any false
            val original=saved.optJSONArray("ads")?.takeIf{it.length()==1}?.optJSONObject(0)?:return@any false
            saved.optString("key")==row.getString("key")&&saved.optString("date")==date&&original.optLong("startedAt")==started&&
                original.optString("runId")==ad.getString("runId")&&NativeChallengeReceiptPolicy.originalStartFields(text,row.getString("title"),date,reward)
        }
        val receiptProof=files.filter{it.name.startsWith(prefix+"REWARD_CONFIRMED_")&&it.extension=="txt"&&stamp(it) in started..finished}
            .firstOrNull{NativeChallengeReceiptPolicy.recordedInitialReceipt(it.readText(),reward,steps)}?:return false
        fun valid():Boolean = !AutoAccessibilityService.stopRequested&&!blocked()&&AccountSafety.authorize(svc,"recorded_initial_receipt")&&
            NativeChallengeReceiptPolicy.boundInitial(row.optString("state"),ad.optString("state"),ad.optBoolean("initialJoin"),ad.optInt("index"),
                row.getJSONArray("ads").length(),pendingCount,row.optString("date")==date&&TokyoDate.of()==date,ad.optBoolean("adDepartureObserved"),
                ad.optBoolean("confirmed"),startProof,true,closeDriver.isNativeChallengeReceipt(),reward,steps,labels())
        ui.refreshCache();if(!valid())return false;delay(1500);ui.refreshCache();if(!valid())return false
        evidence(runId,"recorded_initial_receipt_before",row.toString())
        ad.put("previousFailure",ad.optString("reason")).put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true)
            .put("reason","original_recorded_native_initial_receipt_revalidated").put("afterSerial",1).put("offeredAfter",reward)
            .put("receiptEvidence",receiptProof.name).put("receiptReviewRunId",runId).put("finishedAt",System.currentTimeMillis())
        row.put("joined",true).put("boost",0).put("state","JOINED").put("offeredReward",reward)
            .put("rewardMeaning","offered_on_real_challenge_completion_not_account_credit");row.remove("error")
        ChallengeStore.save(svc,date,row);evidence(runId,"recorded_initial_receipt_after",row.toString());return true
    }

    private suspend fun reconcileBoundLateAdvance(runId:String,date:String,row:JSONObject,ad:JSONObject,pendingCount:Int):Boolean {
        val saved=ad.optJSONArray("boostSteps")?:return false
        val steps=(0 until saved.length()).map{saved.getInt(it)}
        fun valid():Boolean {
            val observed=nodes()
            val nativePanel=ui.currentPackage()==pkg&&!closeDriver.isAdSurface()&&
                listOf("title_count","current_point","boost_list").all{id->observed.any{it.viewIdResourceName=="$pkg:id/$id"}}
            return !AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&
                row.getString("date")==date&&row.optInt("offeredReward")==ad.optInt("offeredBefore")&&
                BoundLateBoostReceiptPolicy.matches(row.optString("state"),ad.optString("state"),ad.optString("reason"),
                    ad.optBoolean("confirmed"),ad.optBoolean("initialJoin"),ad.optInt("index"),row.getJSONArray("ads").length(),
                    pendingCount,TokyoDate.of()==date,ad.optBoolean("adDepartureObserved"),row.getInt("boost"),
                    ad.optInt("offeredBefore"),ad.optInt("expectedOfferedAfter"),steps,nativePanel,labels())
        }
        ui.refreshCache();if(!valid()||!AccountSafety.authorize(svc,"bound_late_boost_receipt"))return false
        delay(1500);ui.refreshCache();if(!valid())return false
        evidence(runId,"bound_late_official_advance_before",row.toString())
        ui.refreshCache();if(!valid())return false
        ad.put("previousFailure",ad.optString("reason")).put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true)
            .put("reason","same_bound_attempt_stable_native_ordinal_and_reward_advance")
            .put("afterSerial",row.getInt("boost")+2).put("offeredAfter",ad.getInt("expectedOfferedAfter"))
            .put("receiptReviewRunId",runId).put("finishedAt",System.currentTimeMillis())
        row.put("boost",row.getInt("boost")+1).put("offeredReward",ad.getInt("expectedOfferedAfter")).put("state","JOINED");row.remove("error")
        ChallengeStore.save(svc,date,row);evidence(runId,"bound_late_official_advance_after",row.toString());return true
    }
    private suspend fun resumeOfficialInterruptedBoost(runId:String,date:String,row:JSONObject,ad:JSONObject):Boolean {
        val ads=row.getJSONArray("ads")
        val stepsJson=ad.optJSONArray("boostSteps")?:return false
        val steps=(0 until stepsJson.length()).map{stepsJson.getInt(it)}
        val pending=ChallengeStore.rows(svc,date)
        val count=(0 until pending.length()).count{pending.getJSONObject(it).optString("state") in setOf("AD_OPENING","UNCONFIRMED","JOINED")}
        fun safe()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&AccountSafety.authorize(svc,"official_boost_resume")
        check(safe()&&row.getString("date")==date&&row.optInt("offeredReward")==ad.optInt("offeredBefore")){"official_resume_binding_changed"}
        check(ChallengeRecoveryPolicy.officialPromptAllowed(row.optString("state"),ad.optString("state"),ad.optString("reason"),
            ad.optBoolean("confirmed"),ad.optBoolean("initialJoin"),ad.getInt("index"),ads.length(),count,
            TokyoDate.of()==date,row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,
            ad.optInt("officialPromptResumeRequests"),officialResumePrompt()!=null)){"official_resume_binding_not_safe"}
        evidence(runId,"bound_official_resume_before",row.toString())
        delay(700)
        val button=officialResumePrompt()?:error("official_resume_prompt_changed")
        ad.put("officialPromptResumeRequests",ad.optInt("officialPromptResumeRequests")+1)
            .put("officialPromptResumeAt",System.currentTimeMillis()).put("officialPromptResumeRunId",runId)
        ChallengeStore.save(svc,date,row) // Same attempt, before dispatch; no new ad/index is reserved.
        check(safe()&&tap(button)){"official_resume_button_dispatch_failed"}
        val resumeDispatchedAt=android.os.SystemClock.elapsedRealtime()
        val until=android.os.SystemClock.elapsedRealtime()+45_000
        while(android.os.SystemClock.elapsedRealtime()<until){
            check(safe()){"official_resume_stopped"};delay(600);ui.refreshCache()
            if(closeDriver.isRecoverableSurface()){
                evidence(runId,"bound_official_resume_existing_ad",row.toString())
                return watch(runId,date,row,null,false,ad)
            }
            val outcome=OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,labels())?:continue
            val loading=nodes().any{n->val b=Rect();n.getBoundsInScreen(b);ChallengeRecoveryPolicy.blockingOfficialLoading(n.viewIdResourceName,n.className.toString(),b.contains(svc.resources.displayMetrics.widthPixels/2,svc.resources.displayMetrics.heightPixels/2))}
            if(outcome=="NO_GAIN"&&!ChallengeRecoveryPolicy.officialNoGainReady(android.os.SystemClock.elapsedRealtime()-resumeDispatchedAt,loading))continue
            delay(1500);check(safe()){"official_resume_stopped"};ui.refreshCache()
            if(closeDriver.isRecoverableSurface()||OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,labels())!=outcome)continue
            evidence(runId,"bound_official_resume_outcome_before",row.toString())
            ad.put("previousFailure",ad.optString("reason")).put("finishedAt",System.currentTimeMillis()).put("officialPromptOutcome",outcome)
                .put("offeredAfter",ChallengeSignals.offeredReward(labels()))
            if(outcome=="ADVANCED"){
                ad.put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true).put("afterSerial",row.getInt("boost")+2)
                    .put("reason","bound_official_resume_confirms_boost_advance")
                row.put("boost",row.getInt("boost")+1).put("offeredReward",ad.getInt("expectedOfferedAfter"))
            }else{
                ad.put("state",AdState.FAILED.name).put("confirmed",false).put("officialNoGain",true)
                    .put("reason","official_resumed_session_confirms_boost_not_applied")
            }
            row.put("state","JOINED").remove("error");ChallengeStore.save(svc,date,row)
            evidence(runId,"bound_official_resume_outcome_after",row.toString());return true
        }
        evidence(runId,"bound_official_resume_unconfirmed",row.toString());return false
    }

    /** Debug-only continuation of the exact pilot panel reviewed on-device; never adopts arbitrary dialogs in release. */
    suspend fun continuePilot(runId: String, cat: Cat, reviewedNoGain: Boolean = false, reviewedReceipt: Boolean = false, reviewedInitial: Boolean = false, reviewedTitle: String? = null): String {
        val date = TokyoDate.of()
        val a = ChallengeStore.rows(svc, date)
        val row = (0 until a.length()).map { a.getJSONObject(it) }.single {
            it.getString("cat") == cat.name && it.getString("state") in setOf("PILOT_JOIN_CONFIRMED", "UNCONFIRMED")
        }
        try {
            if (row.getString("state") == "UNCONFIRMED") {
                val ads = row.getJSONArray("ads")
                val last = ads.getJSONObject(ads.length() - 1)
                check(ChallengeRecoveryPolicy.reviewableFailure(last.optString("reason"), last.getBoolean("initialJoin")) &&
                    (reviewedInitial == last.getBoolean("initialJoin"))) { "pilot_recovery_not_applicable" }
                if (reviewedInitial) {
                    check(BuildConfig.DEBUG && reviewedTitle == row.getString("title") && ads.length() == 1) { "operator_initial_title_review_required" }
                    val proof = initialBaselineProof(row, last) ?: error("original_initial_start_and_departure_proof_missing")
                    val valid = { InitialJoinReceiptPolicy.matches(proof.first, proof.second, labels()) }
                    check(valid()) { "official_late_initial_receipt_not_verified" }; delay(1500)
                    check(valid()) { "official_late_initial_receipt_not_stable" }
                    evidence(runId, "official_initial_receipt_review_before", row.toString())
                    last.put("previousFailure", last.optString("reason")).put("state", AdState.REWARD_CONFIRMED.name)
                        .put("confirmed", true).put("reason", "reviewed_late_official_initial_join_receipt")
                        .put("afterSerial", 1).put("offeredAfter", proof.first).put("reviewRunId", runId)
                        .put("reviewedAt", System.currentTimeMillis()).put("expectedJoinReward", proof.first)
                        .put("initialBoostSteps", JSONArray(proof.second)).put("initialProofSource", "original_start_and_playing_evidence_with_operator_exact_title_review")
                    row.put("joined", true).put("boost", 0).put("state", "JOINED").put("offeredReward", proof.first)
                        .put("rewardMeaning", "offered_on_real_challenge_completion_not_account_credit"); row.remove("error")
                    ChallengeStore.save(svc, date, row)
                    evidence(runId, "official_initial_receipt_review_after", row.toString())
                } else if (reviewedReceipt) {
                    check(BuildConfig.DEBUG) { "operator_review_debug_only" }
                    val valid = { ChallengeSignals.reviewedAdvance(row.getInt("boost"), last.getInt("offeredBefore"), last.getInt("expectedOfferedAfter"), labels()) }
                    check(valid()) { "official_late_advance_not_verified" }
                    delay(1500); check(valid()) { "official_late_receipt_not_stable" }
                    evidence(runId, "official_late_receipt_review_before", row.toString())
                    last.put("previousFailure", last.optString("reason")).put("state", AdState.REWARD_CONFIRMED.name)
                        .put("reason", "reviewed_late_official_boost_receipt").put("confirmed", true)
                        .put("offeredAfter", last.getInt("expectedOfferedAfter")).put("afterSerial", row.getInt("boost") + 2)
                        .put("reviewRunId", runId).put("reviewedAt", System.currentTimeMillis())
                    row.put("boost", row.getInt("boost") + 1).put("offeredReward", last.getInt("expectedOfferedAfter"))
                        .put("state", "JOINED"); row.remove("error")
                    ChallengeStore.save(svc, date, row)
                    evidence(runId, "official_late_receipt_review_after", row.toString())
                } else if (reviewedNoGain) {
                    check(BuildConfig.DEBUG && (0 until ads.length()).none { ads.getJSONObject(it).optBoolean("officialNoGain") }) { "review_or_compensation_limit" }
                    val valid = { ChallengeSignals.reviewedNoGain(row.getInt("boost"), last.getInt("offeredBefore"), last.getInt("expectedOfferedAfter"), labels()) }
                    check(valid()) { "official_resume_no_gain_not_verified" }
                    delay(1500); check(valid()) { "official_resume_panel_not_stable" }
                    evidence(runId, "official_no_gain_review_before", row.toString())
                    last.put("previousFailure", last.optString("reason")).put("state", AdState.FAILED.name)
                        .put("reason", "official_resumed_session_confirms_boost_not_applied")
                        .put("officialNoGain", true).put("offeredAfter", last.getInt("offeredBefore"))
                        .put("reviewRunId", runId).put("reviewedAt", System.currentTimeMillis())
                    row.put("state", "JOINED"); row.remove("error")
                    ChallengeStore.save(svc, date, row)
                    evidence(runId, "official_no_gain_review_after", row.toString())
                } else check(watch(runId, date, row, null, false, last)) { "existing_ad_receipt_not_confirmed" }
            } else {
                check(ChallengeSignals.joined(labels()) && ChallengeSignals.nextBoost(labels()) == 1) { "pilot_panel_changed" }
                val offered = ChallengeSignals.offeredReward(labels()) ?: error("pilot_reward_missing")
                if (row.has("offeredReward")) check(offered == row.getInt("offeredReward")) { "pilot_reward_changed" }
                else {
                    row.put("offeredReward", offered).put("baselineSource", "reviewed_official_pilot_panel_after_confirmed_join")
                    ChallengeStore.save(svc, date, row)
                    evidence(runId, "pilot_resume_baseline", row.toString())
                }
            }
            boost(runId, date, row, 7)
            return ChallengeStore.summary(svc, date)
        } catch (t: Exception) {
            evidence(runId, "pilot_continue_error", t.toString())
            return "试运行叠加中止：${t.message}\n" + ChallengeStore.summary(svc, date)
        }
    }

    private suspend fun processChallenge(engine: AutomationEngine, runId: String, date: String, card: ChallengeCard,
        adBudget: Int, pilot: Boolean, currentViewport: Boolean): Boolean {
        if(!TravelSelection.enabled(svc,card.cat)){TravelSelection.audit(svc,date,runId,card.cat);return false}
        if (ChallengeStore.find(svc, date, card.key) != null) return false
        check(!AutoAccessibilityService.stopRequested && !blocked() && TokyoDate.of() == date) { "stopped_or_date_changed" }
        check(if (currentViewport) engine.openVisibleChallenge(card) else engine.openChallenge(card)) { "target_detail_unreachable_" + card.cat }
        val current = nodes().mapNotNull { Cards.fromTitle(ui.label(it)) }
        check(current.any { it.key == card.key && it.meters == card.meters }) { "detail_identity_mismatch" }
        check(labels().any { AnaCalendarDate.same(it, date) }) { "detail_date_mismatch" }
        val join = byId("button_join_boost") ?: error("observed_video_join_button_missing")
        val row = JSONObject().put("key", card.key).put("date", date).put("cat", card.cat.name).put("title", card.title)
            .put("meters", card.meters).put("runId", runId).put("joined", false).put("boost", 0).put("state", "READY").put("ads", JSONArray())
        val joined=watch(runId,date,row,join,true)
        if(!joined&&!TravelSelection.enabled(svc,card.cat)){TravelSelection.audit(svc,date,runId,card.cat);return false}
        check(joined) { "join_not_confirmed_" + card.cat }
        if (pilot) { row.put("state", "PILOT_JOIN_CONFIRMED"); ChallengeStore.save(svc, date, row); return true }
        boost(runId, date, row, adBudget)
        return false
    }

    suspend fun run(runId: String, categories: List<Cat>, allCards: Boolean = false, adBudget: Int = 6, pilot: Boolean = false, settingsOverride:AppSettings?=null): String {
        val date = TokyoDate.of()
        val settings = settingsOverride ?: Prefs.loadBlocking(svc)
        val engine = AutomationEngine(svc, settings)
        val overlay = OverlayKeeper(svc)
        val wake = (svc.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ana:challengeboost")
        try {
            wake.acquire(3 * 60 * 60 * 1000L)
            overlay.show()
            val readiness=DeviceReady.ensure(svc,runId)
            check(readiness.ready){readiness.reason}
            check(AccountSafety.authorize(svc,"challenge_start")){"account_safety_human_required"}
            check(!blocked()) { "accessibility_or_keyguard" }
            // A pending ad from a killed process cannot be repeated or inferred successful.
            var prior = ChallengeStore.rows(svc, date)
            val pending = (0 until prior.length()).map { prior.getJSONObject(it) }
                .filter { it.optString("state") in setOf("AD_OPENING", "UNCONFIRMED", "JOINED") }.filter { row->TravelSelection.enabled(svc,Cat.ofName(row.optString("cat"))!!) || row.optString("state") in setOf("AD_OPENING","UNCONFIRMED") }
            if (pending.isNotEmpty()) {
                check(pending.size == 1) { "multiple_unconfirmed_challenges_require_review" }
                val row = pending.single()
                // Existing submitted ads may finish even after their category is disabled. No new Boost is allowed.
                if(ui.currentPackage()!=pkg)ui.launchApp(pkg)
                repeat(15) { ui.refreshCache();if (!closeDriver.isRecoverableSurface() && observedJoinedMax(row) == null && !AdSignals.loadFailure(labels()) &&
                    !ChallengeSignals.dialog(labels()) && labels().none { it == "ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？" }) delay(1000) }
                evidence(runId, "manual_resume_observed_window", row.toString())
                val ads = row.getJSONArray("ads")
                val last = ads.getJSONObject(ads.length() - 1)
                if (!reconcileJoinedMax(runId, date, row)) {
                    if (row.optString("state") == "JOINED") {
                        if (labels().any { it == "ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？" }) {
                            val resume = nodes().singleOrNull { it.viewIdResourceName == "android:id/button1" && ui.label(it) == "再開する" }
                                ?: error("known_joined_resume_button_missing")
                            evidence(runId, "known_joined_official_resume_prompt", row.toString()); check(tap(resume)); delay(1800)
                        }
                        val savedSteps = last.optJSONArray("boostSteps") ?: ads.getJSONObject(0).optJSONArray("initialBoostSteps")
                            ?: error("known_joined_increment_baseline_missing")
                        val steps = (0 until savedSteps.length()).map { savedSteps.getInt(it) }
                        fun valid() = JoinedChallengeResumePolicy.matches(JoinedChallengeResumePolicy.resolved(last.optBoolean("confirmed"),
                            last.optBoolean("officialNoGain"), last.optString("state")), last.getInt("index"), ads.length(),
                            row.getInt("boost"), row.getInt("offeredReward"), steps, labels())
                        check(valid()) { "known_joined_baseline_changed" }; delay(1500); check(valid()) { "known_joined_baseline_unstable" }
                        evidence(runId, "known_joined_resume_next_unstarted_boost", row.toString())
                    } else if(reconcileRecordedInitialReceipt(runId,date,row,last,pending.size)){
                        // Revalidate only the native receipt recorded during this exact initial watching.
                    } else if(reconcileBoundLateAdvance(runId,date,row,last,pending.size)){
                        // Only this saved attempt was reconciled; continue at the next unstarted Boost below.
                    } else if(officialResumePrompt()!=null){
                        check(resumeOfficialInterruptedBoost(runId,date,row,last)){"official_resume_outcome_not_confirmed"}
                    } else if (AdSignals.loadFailure(labels()) && !last.getBoolean("initialJoin") && !last.optBoolean("confirmed") && last.getInt("index") == ads.length()) {
                        evidence(runId, "pending_official_load_failure_observed", row.toString())
                        check(recoverOpaque(runId, date, row, last, observedExistingLoadFailure = true)) { "official_load_failure_recovery_not_verified" }
                    } else {
                    check(ChallengeRecoveryPolicy.canResume(row.getString("state"), last.getString("state"),
                        last.optString("reason"), last.optBoolean("confirmed"), last.getInt("index"), ads.length(),
                        closeDriver.isRecoverableSurface())) { "unconfirmed_challenge_requires_evidence_review" }
                    evidence(runId, "manual_resume_existing_ad", row.toString())
                    check(watch(runId, date, row, null, last.getBoolean("initialJoin"), last)) { "existing_ad_receipt_not_confirmed" }
                    }
                    boost(runId, date, row, adBudget)
                }
                prior = ChallengeStore.rows(svc, date)
            }
            val checkedRows = ChallengeStore.rows(svc, date)
            check((0 until checkedRows.length()).filter{TravelSelection.enabled(svc,Cat.ofName(checkedRows.getJSONObject(it).optString("cat"))!!)}.all { checkedRows.getJSONObject(it).optString("state") in
                setOf("BOOST_MAX_CONFIRMED", "BOOST_MAX_OBSERVED") }) { "unfinished_challenge_requires_review" }
            Evidence.write(svc, "challenge_" + runId + "_settings.json", settings.toJson())
            val participating = engine.auditParticipating(runId)
            evidence(runId,"participating_audit",participating.joinToString("\n"){it.key})
            for (cat in categories) {
                if(!TravelSelection.enabled(svc,cat)){TravelSelection.audit(svc,date,runId,cat);continue}
                if (AutoAccessibilityService.stopRequested || blocked() || TokyoDate.of() != date) break
                if (allCards) {
                    ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,ChallengeScanStatus.SCANNING)
                    ChallengeStore.setCoverage(svc, date, cat, 0)
                    check(engine.prepareFilteredCategory(cat)) { "category_filter_unreachable_" + cat }
                    var categoryEpoch = navigationEpoch
                    val snapshot = ChallengeStore.rows(svc, date)
                    val processed = (0 until snapshot.length()).map { snapshot.getJSONObject(it).getString("key") }.toMutableSet()
                    var eligible=participating.count{it.cat==cat&&it.meters!=null&&it.meters<=settings.kmOf(cat)*1000}
                    var exhausted = false
                    while (!AutoAccessibilityService.stopRequested && !blocked() && TokyoDate.of() == date && TravelSelection.enabled(svc,cat)) {
                        val card = engine.nextFilteredChallenge(cat, settings.kmOf(cat) * 1000, processed)
                        if (card == null) {
                            check(engine.observedDailyTitles>0||ui.activeLabels(pkg=pkg).any{it=="参加可能なチャレンジはありません"}){"empty_scan_without_native_official_proof"}
                            exhausted = true; break
                        }
                        eligible++
                        ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,ChallengeScanStatus.TASKS_AVAILABLE,eligible=eligible)
                        if (processChallenge(engine, runId, date, card, adBudget, pilot, true))
                            return "试运行：" + card.title + " 接取确认，Boost待验证"
                        processed.add(card.key)
                        if (!TravelSelection.enabled(svc,cat)){TravelSelection.audit(svc,date,runId,cat);break}
                        if (categoryEpoch != navigationEpoch) {
                            check(engine.prepareFilteredCategory(cat)) { "category_filter_after_sdk_recovery_unreachable_" + cat }
                            categoryEpoch = navigationEpoch
                        }
                    }
                    val complete = CategoryCompletionPolicy.mayComplete(exhausted, AutoAccessibilityService.stopRequested, TokyoDate.of() == date)
                    val untracked=participating.filter{it.cat==cat&&it.meters!=null&&it.meters<=settings.kmOf(cat)*1000}.any{card->ChallengeStore.find(svc,date,card.key)?.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
                    val status=ChallengeScanPolicy.finish(complete,true,AutoAccessibilityService.stopRequested,TokyoDate.of()==date,eligible,untracked||TravelSelection.hasSelectedPending(svc,date))
                    ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,status,both=true,eligible=eligible,reason=if(untracked)"official_joined_task_requires_detail_review" else "")
                    if (complete&&!untracked) ChallengeStore.setCoverage(svc, date, cat, settings.kmOf(cat))
                    evidence(runId, cat.name + "_filtered_category_end", "maxKm=" + settings.kmOf(cat) + " exhausted=" + exhausted + " completed=" + complete)
                } else {
                    val existing = (0 until checkedRows.length()).map { checkedRows.getJSONObject(it) }.filter { it.getString("cat") == cat.name }
                    if (existing.isNotEmpty()) continue
                    val found = engine.challengeCandidates(cat)
                    val selected = Cards.select(found, cat, settings.kmOf(cat) * 1000).matched.take(1)
                    check(selected.isNotEmpty()) { "no_joinable_card_" + cat }
                    for (card in selected) if (processChallenge(engine, runId, date, card, adBudget, pilot, false))
                        return "试运行：" + card.title + " 接取确认，Boost待验证"
                }
            }
            val scopeComplete = allCards && !AutoAccessibilityService.stopRequested && TokyoDate.of() == date && categories.filter{TravelSelection.enabled(svc,it)}.all {
                ChallengeScanStore.complete(svc,date,it,settings.kmOf(it))
            }
            return (if (!allCards) "" else if (scopeComplete) "所选分类中符合公里上限的任务已完成\n" else "出行任务已停止，部分分类尚未扫描完成\n") + ChallengeStore.summary(svc, date)
        } catch (t: Exception) {
            for(cat in TravelSelectionPolicy.queue(categories,Prefs.loadBlocking(svc))){
                if(!ChallengeScanStore.complete(svc,date,cat,settings.kmOf(cat)))ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,if(TravelSelection.hasUnresolvedAd(svc,date))ChallengeScanStatus.BLOCKED else ChallengeScanStatus.SCAN_FAILED,reason=t.message.orEmpty())
            }
            evidence(runId, "error", t.toString())
            FaultStore.capture(svc,runId,"challenge_navigation",t.message.orEmpty())
            return "挑战执行中止：${t.message}\n" + ChallengeStore.summary(svc, date)
        } finally {
            active?.let { a ->
                a.put("state", "UNCONFIRMED").put("reason", "process_or_run_interrupted")
                activeRow?.let { row -> row.put("state", "UNCONFIRMED"); ChallengeStore.save(svc, date, row) }
            }
            overlay.hide()
            if (wake.isHeld) wake.release()
        }
    }
}
