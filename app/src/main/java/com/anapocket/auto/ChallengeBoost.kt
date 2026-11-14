package com.anapocket.auto

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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
    fun pendingRows(ctx:Context,date:String):List<JSONObject> {
        val a=rows(ctx,date)
        return (0 until a.length()).map{a.getJSONObject(it)}.filter{it.optString("state") in setOf("AD_OPENING","UNCONFIRMED","JOINED","PILOT_JOIN_CONFIRMED","AD_BUDGET_REACHED")}
    }
    fun unconfirmedAd(row:JSONObject):Boolean {
        val a=row.optJSONArray("ads")?:return false
        return (0 until a.length()).any{val x=a.getJSONObject(it);!x.optBoolean("confirmed")&&!x.optBoolean("officialNoGain")&&!InitialRejoinPolicy.resolved(x)}
    }
    fun blocksIndependentNavigation(ctx:Context,date:String):Boolean {
        val a=rows(ctx,date)
        return (0 until a.length()).any{val r=a.getJSONObject(it);unconfirmedAd(r)&&!PendingInitialJoinStore.isolated(r,date)}
    }
    fun hasPending(ctx:Context,date:String)=pendingRows(ctx,date).isNotEmpty()
    private fun sp(ctx: Context) = ctx.getSharedPreferences("challenge_boost_v1", Context.MODE_PRIVATE)
    @Synchronized fun rows(ctx:Context,date:String):JSONArray=ReliableLedger.challenges(ctx,date)
    @Synchronized fun save(ctx:Context,date:String,row:JSONObject)=ReliableLedger.saveChallenge(ctx,date,row)
    fun find(ctx:Context,date:String,key:String):JSONObject?=ReliableLedger.challenge(ctx,date,key)
    fun summary(ctx: Context, date: String = TokyoDate.of()): String {
        val a = rows(ctx, date)
        return "任务日 $date（手机 JST）\n" + if (a.length() == 0) "尚未执行挑战接取" else (0 until a.length()).joinToString("\n") {
            val r = a.getJSONObject(it)
            val state = when (r.optString("state")) {
                "BOOST_MAX_CONFIRMED" -> "已接取并升满"
                "BOOST_MAX_OBSERVED" -> "官方详情确认升满（逐次广告回执不完整）"
                "AD_OPENING" -> if(PendingInitialJoinStore.isolated(r,date))"已隔离，原观看待核对，禁止重播" else "观看广告中"
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
        return VerifiedLoadFailurePolicy.startLimit(verifiedFailures,verifiedLoadFailures)+InitialRejoinPolicy.extraStarts(row)
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
    private fun nativeUnjoined(row:JSONObject):Boolean {
        fun field(id:String)=byId(id)?.let{ui.label(it)}
        val a=PendingInitialJoinStore.binding(row)?:return false
        val ad=row.getJSONArray("ads").getJSONObject(0)
        return !blocked()&&!AutoAccessibilityService.stopRequested&&TokyoDate.of()==row.optString("date")&&
            ui.currentPackage()==pkg&&AutoAccessibilityService.foregroundActivity=="us.ana_pocket.ANAPocket.views.home.HomeActivity"&&
            !closeDriver.isRecoverableSurface()&&!ChallengeSignals.dialog(labels())&&
            field("text_name")==row.optString("title")&&AnaCalendarDate.same(field("text_date"),a.date)&&
            byId("text_attended")==null&&byId("button_join")?.isClickable==true&&byId("button_join_boost")?.isClickable==true&&
            field("button_join_boost_point_text")==ad.optInt("expectedJoinReward").toString()+"pt"&&
            ChallengeSignals.increments(labels())==(ad.optJSONArray("initialBoostSteps")?.let{t->(0 until t.length()).map{t.getInt(it)}}?:emptyList<Int>())
    }
    private suspend fun isolateLegacyInitial(runId:String,date:String,row:JSONObject,pendingCount:Int):Boolean {
        val binding=PendingInitialJoinStore.binding(row)?:return false
        if(!PendingInitialJoinPolicy.mayInspect(binding,date,pendingCount,nativeUnjoined(row))||binding.dispatchPhase.isNotBlank())return false
        evidence(runId,"legacy_initial_isolation_before",row.toString())
        val start=android.os.SystemClock.elapsedRealtime()
        while(android.os.SystemClock.elapsedRealtime()-start<15_000){
            delay(500);ui.refreshCache()
            if(!nativeUnjoined(row)||!AccountSafety.authorize(svc,"initial_isolation"))return false
        }
        if(!PendingInitialJoinPolicy.mayIsolate(binding,date,pendingCount,nativeUnjoined(row),android.os.SystemClock.elapsedRealtime()-start))return false
        // Keep the original AD_OPENING attempt, identity, time and unknown receipt untouched.
        row.put("initialJoinIsolation",JSONObject().put("date",date).put("key",binding.key).put("runId",binding.runId)
            .put("startedAt",binding.startedAt).put("index",binding.index).put("observedAt",System.currentTimeMillis())
            .put("kind",PendingInitialJoinPolicy.ISOLATION_KIND).put("reviewRunId",runId).put("receiptState","UNCONFIRMED"))
        ChallengeStore.save(svc,date,row)
        evidence(runId,"legacy_initial_isolated_never_replay",row.toString())
        return true
    }
    private fun cleanIsolationPage():Boolean {
        val observed=nodes();val ids=observed.mapNotNull{it.viewIdResourceName}.toSet()
        return !blocked()&&!AutoAccessibilityService.stopRequested&&SubmittedUnknownIsolationPolicy.cleanNativePage(ui.currentPackage(),AutoAccessibilityService.foregroundActivity,
                ids,observed.map{ui.label(it)},closeDriver.isRecoverableSurface())
    }
    private suspend fun isolateSubmittedUnknowns(engine:AutomationEngine,run:String,date:String,rows:List<JSONObject>) {
        if(rows.isEmpty()||!cleanIsolationPage()||OfficialAdStore.attempts(svc,date).any{!it.terminal})return
        val start=android.os.SystemClock.elapsedRealtime()
        while(android.os.SystemClock.elapsedRealtime()-start<15_000){delay(500);ui.refreshCache();if(!cleanIsolationPage()||TokyoDate.of()!=date)return}
        // Read-only native participating scan, with existing exact today and category filters; never a join/Boost click.
        val official=engine.auditParticipating(run+"_unknown_read_only",rows.map{it.getString("key")}.toSet())
        if(!cleanIsolationPage())return
        for(row in rows){
            val a=SubmittedUnknownIsolation.binding(row)?:continue
            val matches=official.count{it.key==row.optString("key")&&it.meters==row.optInt("meters")&&it.title==row.optString("title")}
            val ads=row.getJSONArray("ads");val last=ads.getJSONObject(ads.length()-1)
            val absent=matches==0&&SubmittedUnknownIsolationPolicy.mayIsolateAbsent(a,date,last.optBoolean("initialJoin"),last.optString("reason"),cleanIsolationPage(),
                android.os.SystemClock.elapsedRealtime()-start,OfficialAdStore.attempts(svc,date).any{!it.terminal})
            if(absent||SubmittedUnknownIsolationPolicy.mayIsolate(a,date,matches==1,cleanIsolationPage(),
                android.os.SystemClock.elapsedRealtime()-start,OfficialAdStore.attempts(svc,date).any{!it.terminal})){
                evidence(run,"submitted_unknown_isolation_before",row.toString())
                check(SubmittedUnknownIsolation.save(svc,date,row,if(absent)SubmittedUnknownIsolationPolicy.KIND_ABSENT else SubmittedUnknownIsolationPolicy.KIND){
                    TokyoDate.of()==date&&cleanIsolationPage()&&AccountSafety.authorize(svc,"submitted_unknown_isolation_commit")&&
                        OfficialAdStore.attempts(svc,date).none{!it.terminal}
                }){"isolation_identity_or_page_changed"}
                evidence(run,"submitted_unknown_isolated_never_replay",row.toString())
            }
        }
    }
    /** Read-only participation audit + existing metadata isolation; never joins or starts a Boost. */
    suspend fun isolateForIndependentDaily(run:String,date:String):Boolean {
        if(TokyoDate.of()!=date||blocked()||AutoAccessibilityService.stopRequested||
            OfficialAdStore.attempts(svc,date).any{!it.terminal})return false
        if(ui.currentPackage()!=pkg&&!AdTaskReturn.restoreOrRelaunchIfGone(svc,date,run))return false
        val until=android.os.SystemClock.elapsedRealtime()+8_000
        while(!cleanIsolationPage()&&android.os.SystemClock.elapsedRealtime()<until&&
            TokyoDate.of()==date&&!blocked()&&!AutoAccessibilityService.stopRequested){delay(250);ui.refreshCache()}
        ui.refreshCache()
        if(!cleanIsolationPage()||!AccountSafety.authorize(svc,"independent_daily_isolation"))return false
        val pending=ChallengeStore.pendingRows(svc,date).filter{ChallengeStore.unconfirmedAd(it)&&!PendingInitialJoinStore.isolated(it,date)}
        isolateSubmittedUnknowns(AutomationEngine(svc,Prefs.loadBlocking(svc)),run,date,pending)
        ui.refreshCache()
        return TokyoDate.of()==date&&cleanIsolationPage()&&!ChallengeStore.blocksIndependentNavigation(svc,date)&&
            OfficialAdStore.attempts(svc,date).none{!it.terminal}
    }

    private fun initialBaselineProof(row: JSONObject, ad: JSONObject): Pair<Int, List<Int>>? {
        if (ad.optBoolean("adDepartureObserved") && ad.optInt("expectedJoinReward") > 0 && ad.optJSONArray("initialBoostSteps")?.length() == 5) {
            val steps = ad.getJSONArray("initialBoostSteps")
            return ad.getInt("expectedJoinReward") to (0 until steps.length()).map { steps.getInt(it) }
        }
        val prefix = "challenge_" + ad.getString("runId") + "_" + row.getString("cat") + "_" + ad.getInt("index") + "_"
        val files = EvidenceFiles.withPrefix(svc, prefix) ?: return null
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
    private fun boundNativeIdentity(row:JSONObject):Boolean {
        ui.refreshCache();val root=svc.rootInActiveWindow?:return false
        if(root.packageName?.toString()!=pkg||AdSdkWindows.contains(AutoAccessibilityService.foregroundActivity)||
            svc.windows.none{it.id==root.windowId&&(it.isActive||it.isFocused)})return false
        val observed=nodes().filter{it.windowId==root.windowId}
        fun field(id:String)=observed.filter{it.viewIdResourceName=="$pkg:id/$id"}.singleOrNull()?.let{ui.label(it)}
        return ChallengeDetailReceipt.identityMatches(row.getString("title"),row.getString("date"),
            field("text_name"),field("text_date"),field("text_attended"))
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
        Evidence.capture(svc,ui,"$name.png")
        Logger.i("CHBOOST", "run=$runId $tag $description")
    }

    /** Per-card binding is established before the first ad; anonymous dialogs are never adopted after restart. */
    private suspend fun watch(runId: String, date: String, row: JSONObject, button: AccessibilityNodeInfo?, initial: Boolean,
        recovering: JSONObject? = null, reservedContinuation:Boolean=false): Boolean {
        val ads = row.getJSONArray("ads")
        val previous = if (initial) 0 else row.getInt("boost") + 1
        val rewardBefore = if (initial) null else recovering?.getInt("offeredBefore") ?: ChallengeSignals.offeredReward(labels())
        val steps = if (initial) emptyList() else ChallengeSignals.increments(labels())
        if (!initial) check(rewardBefore != null && (recovering != null || steps.size == 5)) { "official_reward_baseline_missing" }
        val expectedReward = if (initial) null else recovering?.getInt("expectedOfferedAfter") ?: (rewardBefore!! + steps[previous - 1])
        val a = recovering ?: JSONObject().put("runId", runId).put("index", ads.length() + 1).put("initialJoin", initial)
            .put("startedAt", System.currentTimeMillis()).put("state", "AD_OPENING").put("confirmed", false).put("dispatchPhase","RESERVED")
            .put("offeredBefore", rewardBefore ?: JSONObject.NULL).put("expectedOfferedAfter", expectedReward ?: JSONObject.NULL)
        if (!initial && recovering == null) a.put("boostSteps", JSONArray(steps))
        if (initial && recovering == null) {
            val point = byId("button_join_boost_point_text")?.let { ui.label(it).removeSuffix("pt").replace(",", "").toIntOrNull() }
                ?: error("initial_ad_join_reward_baseline_missing")
            val initialSteps = ChallengeSignals.increments(labels())
            check(initialSteps.size == 5) { "initial_boost_table_missing" }
            a.put("expectedJoinReward", point).put("initialBoostSteps", JSONArray(initialSteps))
        }
        if(recovering==null){
            val official=ui.officialChallengeIdentity()?:error("official_challenge_identity_unavailable_before_dispatch")
            val observedAt=System.currentTimeMillis()
            if(initial){
                check(byId("text_name")?.let{ui.label(it)}==row.optString("title")&&
                    byId("text_date")?.let{AnaCalendarDate.same(ui.label(it),date)}==true&&byId("text_attended")==null){"official_unjoined_detail_identity_changed"}
            }else{
                val prior=ads.optJSONObject(ads.length()-1)
                val receipt=prior?.optJSONObject("officialReceiptIdentity")?:prior?.optJSONObject("officialBinding")?.takeIf{
                    !prior.optBoolean("initialJoin")&&(prior.optBoolean("confirmed")||prior.optBoolean("officialNoGain"))}
                check((receipt!=null&&receipt.optLong("missionId")==official.missionId&&receipt.optLong("participationId")==official.participationId)||
                    (receipt==null&&boundNativeIdentity(row))){"official_boost_participation_not_established"}
            }
            // Capture and durable reservation precede every UI dispatch fence; never backfill an old attempt.
            a.put("startedAt",System.currentTimeMillis()).put("officialBinding",OfficialChallengeBinding.reserve(official,date,row.getString("key"),runId,
                a.getInt("index"),a.getLong("startedAt"),AutoAccessibilityService.currentRunId?:runId,if(initial)0 else previous-1,rewardBefore,initial,observedAt).put("anaVersionCode",svc.packageManager.getPackageInfo(pkg,0).longVersionCode))
        }else if(reservedContinuation){
            val currentOfficial=ui.officialChallengeIdentity()
            check(OfficialChallengeBinding.valid(row,a)&&currentOfficial!=null&&currentOfficial.participationId==null&&
                currentOfficial.missionId==a.getJSONObject("officialBinding").getLong("missionId")){"reserved_initial_official_identity_missing_no_dispatch"}
        }
        closeDriver.beginFlow(recovering?.optBoolean("storeSheetObserved") ?: false)
        if (recovering == null) {
            val cat=Cat.ofName(row.optString("cat"))?:error("category_missing")
            val accepted=TravelSelection.reserve(svc,cat){check(ads.length()<startLimit(row)){"per_task_ad_limit"};ads.put(a);row.put("state","AD_OPENING");ChallengeStore.save(svc,date,row);true}
            if(accepted!=true){TravelSelection.audit(svc,date,runId,cat);return false}
        }
        else {
            val reserved=reservedContinuation&&PendingInitialJoinStore.binding(row)?.let{
                PendingInitialJoinPolicy.mayDispatchReserved(it,date,nativeUnjoined(row))}==true
            check(!a.getBoolean("confirmed") && a.getInt("index") == ads.length() && (reserved||closeDriver.isRecoverableSurface())) { "existing_ad_not_observed" }
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
                if(confirmedSerial==previous+1)return AdFrame(true,true,confirmedSerial,true)
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
            override suspend fun receiptObserved(frame:AdFrame,before:Int) {
                check(frame.anaForeground&&frame.receipt&&frame.watched==before+1&&TokyoDate.of()==date){"receipt_identity_not_verified"}
                val current=ChallengeStore.find(svc,date,row.getString("key"))?:error("receipt_identity_missing")
                val persisted=current.getJSONArray("ads").getJSONObject(current.getJSONArray("ads").length()-1)
                check(persisted.getString("runId")==a.getString("runId")&&persisted.getInt("index")==a.getInt("index")&&persisted.getLong("startedAt")==a.getLong("startedAt")){"receipt_attempt_changed"}
                var official=ui.officialChallengeIdentity()
                if(official==null){
                    // SDK exit can replace the native Window while the bounded diagnostic is read.
                    // Retry observation once, never retry dispatch or accept a different positive identity.
                    delay(500);ui.refreshCache()
                    check(closeDriver.isNativeChallengeReceipt()){"native_receipt_disappeared_during_identity_read"}
                    official=ui.officialChallengeIdentity()
                }
                check(OfficialChallengeBinding.matches(row,persisted,official)){"official_receipt_participation_mismatch"}
                val freshLabels=labels()
                check(!closeDriver.isAdSurface()&&(if(initial)InitialJoinReceiptPolicy.matches(a.getInt("expectedJoinReward"),
                    (0 until a.getJSONArray("initialBoostSteps").length()).map{a.getJSONArray("initialBoostSteps").getInt(it)},freshLabels)
                    else ChallengeSignals.offeredReward(freshLabels)==expectedReward&&
                        (ChallengeSignals.nextBoost(freshLabels)==previous+1||previous==5&&ChallengeSignals.nextBoost(freshLabels)==null))){"official_receipt_progress_changed_during_identity_read"}
                a.put("officialReceiptIdentity",JSONObject().put("missionId",official!!.missionId).put("participationId",official.participationId)
                    .put("observedAt",System.currentTimeMillis()).put("source",OfficialChallengeBinding.SOURCE))
                a.put("previousFailure",a.optString("reason")).put("confirmed",true).put("state",AdState.REWARD_CONFIRMED.name)
                    .put("receiptObservedAt",System.currentTimeMillis()).put("finishedAt",System.currentTimeMillis())
                    .put("afterSerial",before+1).put("offeredAfter",if(initial)a.getInt("expectedJoinReward") else expectedReward).put("reason","")
                row.put("joined",true).put("boost",if(initial)0 else previous).put("state","JOINED")
                    .put("offeredReward",a.getInt("offeredAfter")).put("rewardMeaning","offered_on_real_challenge_completion_not_account_credit")
                row.remove("error")
                ChallengeStore.save(svc,date,row) // Single FULL transaction: task and original ad confirmation commit together.
            }
            override suspend fun dismissReceipt() {} // preserve official Boost panel for the next verified action
            override suspend fun state(state: AdState) {
                if (state == AdState.AD_PLAYING) a.put("adDepartureObserved", true)
                if(!a.optBoolean("confirmed"))a.put("state", state.name)
                ChallengeStore.save(svc, date, row)
                if (state == AdState.AD_CLOSE_AVAILABLE && closeDriver.recentlyRevealed) {
                    Logger.i("CHBOOST", "run=$runId ephemeral_close_available_after_single_tap")
                } else evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_${state.name}")
            }
            override suspend fun progress(part: AdPart) {
                evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_part_${part.current}")
            }
            override suspend fun diagnostic(reason:String){FaultStore.capture(svc,runId,"challenge",reason,row.getString("cat")+"_"+a.getInt("index"))}
            override suspend fun stallProgressKey()=closeDriver.stallProgressKey()
            override suspend fun stallReviewed(quietMs:Long,key:String){
                check(!stopped()&&!blocked()&&!a.optBoolean("confirmed")){"stall_review_binding_changed"}
                a.put("stallReview",JSONObject().put("controllerRun",runId).put("quietMs",quietMs)
                    .put("finishedAt",System.currentTimeMillis()).put("frameKey",key.take(512)))
                ChallengeStore.save(svc,date,row)
            }
            override suspend fun observationBudgetChanged(ms: Long) {
                a.put("nativePangleObservationBudgetMs", ms); ChallengeStore.save(svc, date, row)
                evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_nativePangle_wait", "same attempt; native SDK close only; absolute300s observation cap")
            }
        }
        val dispatchRequired=recovering==null||reservedContinuation
        val dispatched=if(dispatchRequired&&button!=null&&!blocked()&&!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date){
            // Persist before the gesture: a process death after this fence is never treated as unsubmitted.
            AdDispatchProtocol.dispatch({phase->
                a.put("dispatchPhase",phase).put(if(phase=="ARMED")"dispatchArmedAt" else "dispatchCompletedAt",System.currentTimeMillis())
                ChallengeStore.save(svc,date,row)
            },{!blocked()&&!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date&&tap(button)})
        }else !dispatchRequired
        val result = try{if(!blocked()&&!AutoAccessibilityService.stopRequested&&TokyoDate.of()==date&&dispatched)
            AdInteraction(port).awaitReceipt(previous) else AdReceipt(AdState.UNCONFIRMED,reason="dispatch_interrupted")
        }finally{closeNode=null;closeDriver.releaseVisualCache()}
        val verified = a.optBoolean("confirmed") || result.completed && result.reward && ChallengeSignals.verifiedAdvance(previous, result.after)
        if(!a.optBoolean("confirmed"))a.put("finishedAt",System.currentTimeMillis()).put("state",result.state.name)
            .put("confirmed",verified).put("reason",result.reason).put("afterSerial",result.after)
            .put("offeredAfter",ChallengeSignals.offeredReward(labels())?:JSONObject.NULL)
        if (verified) {
            row.put("joined", true).put("boost", if (initial) 0 else previous).put("state", "JOINED")
                .put("offeredReward", if(a.optBoolean("confirmed"))a.getInt("offeredAfter") else ChallengeSignals.offeredReward(labels()) ?: JSONObject.NULL)
                .put("rewardMeaning", "offered_on_real_challenge_completion_not_account_credit")
            row.remove("error")
        } else row.put("state", "UNCONFIRMED").put("error", result.reason)
        ChallengeStore.save(svc, date, row)
        active = null;activeRow=null
        if(!verified)FaultStore.capture(svc,runId,"challenge",result.reason,row.getString("cat")+"_"+a.getInt("index"))
        evidence(runId, "${row.getString("cat")}_${a.getInt("index")}_result", row.toString())
        return verified || reconcilePangleLateAdvance(runId, date, row, a) || recoverBlankGoogle(runId,date,row,a) || recoverOpaque(runId, date, row, a)
    }

    private suspend fun recoverBlankGoogle(runId:String,date:String,row:JSONObject,ad:JSONObject):Boolean {
        if(ad.optBoolean("confirmed")||ad.optString("reason")!="ad_timeout_95s"||
            ad.getInt("index")!=row.getJSONArray("ads").length()||ad.optBoolean("blankGoogleBackRequested"))return false
        val owner=CheckinRuntime.ownership.owner();val controller=AutoAccessibilityService.currentRunId
        fun safe()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&
            owner!=null&&owner.app=="ANA"&&owner.run==controller&&AutoAccessibilityService.currentRunId==controller&&
            CheckinRuntime.ownership.valid(owner,android.os.SystemClock.elapsedRealtime())&&AccountSafety.authorize(svc,"blank_google_return")
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
        val initial=ad.optBoolean("initialJoin")
        val stepsJson=ad.optJSONArray(if(initial)"initialBoostSteps" else "boostSteps")?:return false
        val steps=(0 until stepsJson.length()).map{stepsJson.getInt(it)}
        evidence(runId,"blank_google_before_normal_back",row.toString())
        ad.put("blankGoogleBackRequested",true).put("blankGoogleBackAt",System.currentTimeMillis()).put("blankGoogleBackRunId",runId)
        ChallengeStore.save(svc,date,row)
        check(safe()&&ui.back()){"blank_google_normal_back_failed"}
        // Returning is never completion. Only the same bound official ordinal AND reward can confirm it.
        val until=android.os.SystemClock.elapsedRealtime()+45_000
        while(android.os.SystemClock.elapsedRealtime()<until){
            check(safe()){"blank_google_return_stopped"};delay(700);ui.refreshCache()
            if(initial){
                val rows=ChallengeStore.rows(svc,date)
                val pending=(0 until rows.length()).count{val r=rows.getJSONObject(it)
                    NativeChallengeReceiptPolicy.pendingForReview(r.optString("state"),PendingInitialJoinStore.isolated(r,date))}
                if(reconcileRecordedInitialReceipt(runId,date,row,ad,pending,liveBlankReturn=true))return true
                continue // No replacement ad, guessed reward, anonymous resume or ordinary Boost confirmation.
            }
            if(officialResumePrompt()!=null)return resumeOfficialInterruptedBoost(runId,date,row,ad)
            fun advanced()=OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,labels())=="ADVANCED"
            if(!advanced())continue
            delay(1500);ui.refreshCache();if(!safe()||!advanced())continue
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
        if(ad.has("officialBinding")&&!OfficialChallengeBinding.matches(row,ad,ui.officialChallengeIdentity()))return false
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
            ad.optBoolean("adDepartureObserved"), ad.getString("runId") == runId, closeDriver.isStallReviewSurface(),
            used, failures,
            row.getInt("boost"), steps)) return false
        val review=ad.optJSONObject("stallReview")
        if(!loadFailure&&(review?.optString("controllerRun")!=runId||!OpaqueRecoveryPolicy.freshStallReview(
            review.optLong("quietMs"),review.optLong("finishedAt"),System.currentTimeMillis())))return false
        check(ad.getInt("index")==ads.length()&&!ad.optBoolean("confirmed")){"native_recovery_attempt_changed"}
        val controller=AutoAccessibilityService.currentRunId;val owner=CheckinRuntime.ownership.owner()
        fun safe() = !AutoAccessibilityService.stopRequested && !blocked() && TokyoDate.of() == date&&
            owner!=null&&owner.app=="ANA"&&owner.run==controller&&AutoAccessibilityService.currentRunId==controller&&
            CheckinRuntime.ownership.valid(owner,android.os.SystemClock.elapsedRealtime())&&AccountSafety.authorize(svc,"native_recovery")
        check(safe()) { "native_recovery_stopped" }
        val snapshot = ChallengeStore.rows(svc, date)
        check((0 until snapshot.length()).count {
            val pending=snapshot.getJSONObject(it)
            pending.optString("state") in setOf("AD_OPENING", "UNCONFIRMED")&&!PendingInitialJoinStore.isolated(pending,date)
        } == 1) { "ambiguous_recovery_binding" }
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
        val current=ChallengeStore.find(svc,date,row.getString("key"))?:error("native_resume_original_missing")
        val currentAds=current.getJSONArray("ads");val original=currentAds.optJSONObject(currentAds.length()-1)
        val remaining=ChallengeStore.rows(svc,date)
        val pendingCount=(0 until remaining.length()).count{val r=remaining.getJSONObject(it)
            (ChallengeStore.unconfirmedAd(r)||r.optString("state")=="JOINED")&&!PendingInitialJoinStore.isolated(r,date)}
        val sameAttempt=original!=null&&current.getString("date")==date&&
            original.optString("runId")==ad.getString("runId")&&ad.getString("runId")==runId&&original.optInt("index")==ad.getInt("index")&&
            original.optLong("startedAt")==ad.getLong("startedAt")&&ad.getInt("index")==currentAds.length()&&
            !original.optBoolean("confirmed")&&!original.optBoolean("initialJoin")&&original.optBoolean("adDepartureObserved")&&
            original.optString("dispatchPhase")=="DISPATCHED"&&original.optString("nativeRecoveryRunId")==runId
        val navigationBound=ChallengeRecoveryPolicy.liveRestartNavigation(sameAttempt,TokyoDate.of()==date,safe(),pendingCount,
            original?.optString("nativeRecoveryStage").orEmpty(),System.currentTimeMillis()-ad.optLong("nativeRecoveryStartedAt"),
            original?.optInt("officialPromptResumeRequests")?:-1)
        val recordedBound=recordedOfficialRestart(row,ad,pendingCount)
        check(boundNativeIdentity(row)||navigationBound||recordedBound){"anonymous_resume_without_live_restart_context"}
        check(ad.optInt("officialPromptResumeRequests")==0){"official_resume_navigation_already_requested"}
        // Persist the original attempt's single UI request before dispatch; reward identity stays separate below.
        ad.put("nativeRecoveryStage", "OFFICIAL_RESUME_OBSERVED").put("officialPromptResumeRequests",1)
            .put("officialPromptResumeAt",System.currentTimeMillis()).put("officialPromptResumeRunId",runId)
        ChallengeStore.save(svc, date, row)
        check(safe()&&tap(button)){"official_resume_dispatch_failed"}
        val result=awaitResumedBoost(runId,date,row,ad,steps)
        if(!result)return false
        ad.put("nativeRecoveryFinishedAt",System.currentTimeMillis()).put("nativeRecoveryStage",
            if(ad.optBoolean("confirmed"))"ADVANCED" else if(ad.optBoolean("officialNoGain"))"NO_GAIN" else "OFFICIAL_RESUME_OBSERVED")
        ChallengeStore.save(svc,date,row)
        evidence(runId,"opaque_recovery_official_outcome_after",row.toString())
        if (loadFailure) {
            evidence(runId, "official_load_failure_cooldown", "30000ms; no ad dispatch during cooldown")
            repeat(30) { check(safe()) { "load_recovery_cooldown_stopped" }; delay(1000) }
        }
        return true
    }

    private suspend fun boost(runId: String, date: String, row: JSONObject, adBudget: Int, engine:AutomationEngine) {
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
        if(row.getString("state")=="AD_BUDGET_REACHED")leaveBudgetBoostPrompt(runId,date,row)
        // No navigation is allowed until the bound final dialog is actually gone and category/date verified.
        if(row.getInt("boost")==5)MaxBoostCoordinator.finish(svc,engine,runId,row)
    }

    /** Budget used: leave ANA's own Boost prompt with its "ブーストをやめる" button, once, only for this exact task. */
    private suspend fun leaveBudgetBoostPrompt(runId:String,date:String,row:JSONObject):Boolean {
        ui.refreshCache()
        val ns=nodes();val ids=ns.mapNotNull{it.viewIdResourceName}.toSet();val l=labels()
        if(!BudgetBoostPromptPolicy.matches(ids,l,row.optString("state"),row.optInt("boost"),row.optInt("offeredReward",-1)))return false
        val button=ns.singleOrNull{it.isEnabled&&it.viewIdResourceName==pkg+":id/button_no"}?:return false
        evidence(runId,"budget_reached_leave_official_boost_prompt",row.toString())
        row.put("budgetPromptLeftAt",System.currentTimeMillis());ChallengeStore.save(svc,date,row)
        if(!tap(button))return false
        repeat(12){delay(500);ui.refreshCache();if(ChallengeSignals.nextBoost(labels())==null){
            Logger.i("CHBOOST","run=$runId budget_prompt_left key="+row.optString("key")+" boost="+row.optInt("boost"));return true}}
        Logger.w("CHBOOST","run=$runId budget_prompt_still_visible key="+row.optString("key"))
        return false
    }
    /** A later run that finds a budget-reached task's own Boost prompt still open leaves it before navigating. */
    private suspend fun leaveLeftoverBudgetPrompt(runId:String,date:String){
        val all0=ChallengeStore.rows(svc,date)
        if((0 until all0.length()).none{all0.getJSONObject(it).optString("state")=="AD_BUDGET_REACHED"})return
        // 11/11 01:06: a manual run starts from Tuffy's own screen, so bring ANA (and its open prompt) forward first.
        ui.refreshCache()
        if(ui.currentPackage()!=pkg){if(!ui.launchApp(pkg))return}
        repeat(12){if(ui.currentPackage()==pkg&&ChallengeSignals.nextBoost(labels())!=null)return@repeat;delay(500);ui.refreshCache()}
        if(ui.currentPackage()!=pkg||ChallengeSignals.nextBoost(labels())==null)return
        val ids=nodes().mapNotNull{it.viewIdResourceName}.toSet();val l=labels()
        val all=ChallengeStore.rows(svc,date)
        val hits=(0 until all.length()).map{all.getJSONObject(it)}.filter{
            BudgetBoostPromptPolicy.matches(ids,l,it.optString("state"),it.optInt("boost"),it.optInt("offeredReward",-1))}
        if(hits.size==1)leaveBudgetBoostPrompt(runId,date,hits.single())
    }

    private fun officialResumePrompt():AccessibilityNodeInfo? {
        if(ui.currentPackage()!=pkg)return null
        val observed=nodes()
        if(observed.none{it.viewIdResourceName=="android:id/message"&&ui.label(it)=="ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？"})return null
        return observed.singleOrNull{it.viewIdResourceName=="android:id/button1"&&ui.label(it)=="再開する"&&it.isEnabled}
    }
    private fun reviewedInitialIdentity(row:JSONObject):Boolean {
        val a=PendingInitialJoinStore.binding(row)?:return false
        val ad=row.getJSONArray("ads").optJSONObject(0)?:return false
        if(ad.has("nativeRecoveryStartedAt")||ad.optInt("officialPromptResumeRequests")>0)return false
        val p=ad.optJSONObject("initialReceiptReview")?:return false
        if(p.optString("kind")!=NativeChallengeReceiptPolicy.REVIEW_KIND)return false
        val proof=InitialReceiptReview(p.optString("date"),p.optString("key"),p.optString("runId"),p.optLong("startedAt"),
            p.optInt("index"),p.optLong("reviewedAt"),p.optString("evidenceHash"),p.optLong("installedAt"))
        return NativeChallengeReceiptPolicy.reviewedIdentity(a,proof,TokyoDate.of(),System.currentTimeMillis(),
            svc.packageManager.getPackageInfo(svc.packageName,0).lastUpdateTime)
    }
    private suspend fun reconcileRecordedInitialReceipt(runId:String,date:String,row:JSONObject,ad:JSONObject,pendingCount:Int,liveBlankReturn:Boolean=false):Boolean {
        if(ad.optBoolean("initialJoin")&&!ad.optBoolean("confirmed")&&OfficialChallengeBinding.valid(row,ad)){
            val original=PendingInitialJoinStore.binding(row)?:return false
            val table=ad.optJSONArray("initialBoostSteps")?:return false
            val steps=(0 until table.length()).map{table.getInt(it)};val reward=ad.optInt("expectedJoinReward")
            val owner=CheckinRuntime.ownership.owner();val controller=AutoAccessibilityService.currentRunId
            fun safe()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&
                owner!=null&&owner.app=="ANA"&&owner.run==controller&&AutoAccessibilityService.currentRunId==controller&&
                CheckinRuntime.ownership.valid(owner,android.os.SystemClock.elapsedRealtime())&&AccountSafety.authorize(svc,"bound_initial_receipt")
            fun valid(candidate:JSONObject,official:OfficialChallengeIdentity?,pending:Int):Boolean {
                val current=PendingInitialJoinStore.binding(candidate)?:return false
                val attempt=candidate.getJSONArray("ads").getJSONObject(0)
                return safe()&&current==original&&official?.boostCount==0&&
                    NativeChallengeReceiptPolicy.boundOfficialInitial(current,pending,date,
                        OfficialChallengeBinding.matches(candidate,attempt,official),closeDriver.isNativeChallengeReceipt(),reward,steps,labels())
            }
            val first=ui.officialChallengeIdentity();if(!valid(row,first,pendingCount))return false
            delay(1500);ui.refreshCache();val second=ui.officialChallengeIdentity()
            if(first==null||second==null||!first.sameParticipation(second)||!valid(row,second,pendingCount))return false
            val saved=ReliableLedger.updateChallenge(svc,date,row.getString("key")){current->
                val rows=ReliableLedger.challenges(svc,date)
                val pending=(0 until rows.length()).count{val r=rows.getJSONObject(it)
                    NativeChallengeReceiptPolicy.pendingForReview(r.optString("state"),PendingInitialJoinStore.isolated(r,date))}
                if(!valid(current,second,pending))return@updateChallenge null
                val originalAd=current.getJSONArray("ads").getJSONObject(0);val now=System.currentTimeMillis()
                originalAd.put("officialReceiptIdentity",JSONObject().put("missionId",second.missionId).put("participationId",second.participationId)
                    .put("source",OfficialChallengeBinding.SOURCE).put("observedAt",now))
                    .put("previousFailure",originalAd.optString("reason")).put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true)
                    .put("reason","prebound_official_initial_receipt_revalidated").put("receiptObservedAt",now).put("finishedAt",now)
                    .put("afterSerial",1).put("offeredAfter",reward).put("receiptReviewRunId",runId)
                current.put("joined",true).put("boost",0).put("state","JOINED").put("offeredReward",reward)
                    .put("rewardMeaning","offered_on_real_challenge_completion_not_account_credit");current.remove("error");current
            }?:return false
            row.keys().asSequence().toList().forEach{row.remove(it)};saved.keys().forEach{key->row.put(key,saved.get(key))}
            evidence(runId,"bound_initial_receipt_after",row.toString());return true
        }
        if(!ad.optBoolean("initialJoin")||ad.optBoolean("confirmed")||ad.optString("reason")!="ad_timeout_95s")return false
        val prefix="challenge_"+ad.getString("runId")+"_"+row.getString("cat")+"_"+ad.getInt("index")+"_"
        val files=EvidenceFiles.withPrefix(svc,prefix)?:return false
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
            .firstOrNull{NativeChallengeReceiptPolicy.recordedInitialReceipt(it.readText(),reward,steps)}
        val expected=PendingInitialJoinStore.binding(row)?:return false
        val controller=AutoAccessibilityService.currentRunId;val owner=CheckinRuntime.ownership.owner()
        fun safe()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&
            owner!=null&&owner.app=="ANA"&&owner.run==controller&&AutoAccessibilityService.currentRunId==controller&&
            CheckinRuntime.ownership.valid(owner,android.os.SystemClock.elapsedRealtime())&&AccountSafety.authorize(svc,"recorded_initial_receipt")
        fun liveIdentity(candidate:JSONObject):Boolean {
            val binding=PendingInitialJoinStore.binding(candidate)?:return false
            val original=candidate.getJSONArray("ads").getJSONObject(0)
            return liveBlankReturn&&NativeChallengeReceiptPolicy.liveBlankInitial(binding,candidate.optString("runId"),runId,
                original.optString("blankGoogleBackRunId"),date,svc.packageManager.getPackageInfo(svc.packageName,0).lastUpdateTime,
                safe(),original.optBoolean("blankGoogleBackRequested"),original.has("nativeRecoveryStartedAt")||original.optInt("officialPromptResumeRequests")>0)
        }
        fun valid(candidate:JSONObject,pending:Int)=safe()&&NativeChallengeReceiptPolicy.commitInitialOnce(expected,
            PendingInitialJoinStore.binding(candidate),date,pending,startProof,receiptProof!=null,
            receiptProof==null&&boundNativeIdentity(candidate),closeDriver.isNativeChallengeReceipt(),reward,steps,labels(),operatorIdentity=reviewedInitialIdentity(candidate),liveIdentity=liveIdentity(candidate)){}
        ui.refreshCache();if(!valid(row,pendingCount))return false;delay(1500);ui.refreshCache();if(!valid(row,pendingCount))return false
        val official=if(ad.has("officialBinding"))ui.officialChallengeIdentity() else null
        if(ad.has("officialBinding")&&!OfficialChallengeBinding.matches(row,ad,official))return false
        val saved=ReliableLedger.updateChallenge(svc,date,row.getString("key")){current->
            if(!safe())return@updateChallenge null
            val rows=ReliableLedger.challenges(svc,date)
            val pending=(0 until rows.length()).count{val r=rows.getJSONObject(it)
                r.optString("state") in setOf("AD_OPENING","UNCONFIRMED","JOINED")&&!PendingInitialJoinStore.isolated(r,date)}
            val ok=NativeChallengeReceiptPolicy.commitInitialOnce(expected,PendingInitialJoinStore.binding(current),date,pending,
                startProof,receiptProof!=null,receiptProof==null&&boundNativeIdentity(current),
                closeDriver.isNativeChallengeReceipt(),reward,steps,labels(),operatorIdentity=reviewedInitialIdentity(current),liveIdentity=liveIdentity(current)){
                val original=current.getJSONArray("ads").getJSONObject(0)
                val manual=reviewedInitialIdentity(current);val direct=liveIdentity(current)
                if(official!=null)original.put("officialReceiptIdentity",JSONObject().put("missionId",official.missionId)
                    .put("participationId",official.participationId).put("source",OfficialChallengeBinding.SOURCE).put("observedAt",System.currentTimeMillis()))
                original.put("previousFailure",original.optString("reason")).put("firstUnconfirmedAt",original.optLong("finishedAt"))
                    .put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true)
                    .put("reason",if(receiptProof!=null)"original_recorded_native_initial_receipt_revalidated" else if(manual)"operator_verified_late_initial_receipt" else if(direct)"same_live_initial_receipt_after_blank_google_return" else "bound_late_native_initial_receipt_revalidated")
                    .put("afterSerial",1).put("offeredAfter",reward).put("receiptObservedAt",System.currentTimeMillis())
                    .put("receiptEvidence",receiptProof?.name?:if(manual)"operator_evidence:"+original.getJSONObject("initialReceiptReview").getString("evidenceHash") else if(direct)"original_run_direct_empty_sdk_return_and_stable_native_receipt" else "foreground_exact_task_native_initial_receipt")
                    .put("receiptReviewRunId",runId).put("finishedAt",System.currentTimeMillis())
                current.put("joined",true).put("boost",0).put("state","JOINED").put("offeredReward",reward)
                    .put("rewardMeaning","offered_on_real_challenge_completion_not_account_credit");current.remove("error")
            }
            if(ok)current else null
        }?:return false
        row.keys().asSequence().toList().forEach{row.remove(it)}
        saved.keys().forEach{key->row.put(key,saved.get(key))}
        evidence(runId,"recorded_initial_receipt_after",row.toString());return true
    }

    private fun commitReviewedBoost(date:String,row:JSONObject,ad:JSONObject,change:(JSONObject,JSONObject)->Unit):Boolean {
        val expected=PendingInitialJoinStore.binding(row)?:return false
        if(expected.runId!=ad.optString("runId")||expected.index!=ad.optInt("index")||expected.startedAt!=ad.optLong("startedAt"))return false
        val saved=ReliableLedger.updateChallenge(svc,date,row.getString("key")){current->
            if(TokyoDate.of()!=date||!NativeChallengeReceiptPolicy.samePendingBoost(expected,PendingInitialJoinStore.binding(current),date))return@updateChallenge null
            val latest=current.getJSONArray("ads").getJSONObject(expected.index-1)
            if(current.optInt("offeredReward")!=row.optInt("offeredReward")||latest.optInt("offeredBefore")!=ad.optInt("offeredBefore")||
                latest.optInt("expectedOfferedAfter")!=ad.optInt("expectedOfferedAfter")||latest.optJSONArray("boostSteps")?.toString()!=ad.optJSONArray("boostSteps")?.toString())return@updateChallenge null
            change(current,latest);current
        }?:return false
        val committed=saved.getJSONArray("ads").getJSONObject(expected.index-1)
        committed.keys().forEach{key->ad.put(key,committed.get(key))}
        saved.getJSONArray("ads").put(expected.index-1,ad) // Preserve the caller's original attempt reference.
        saved.keys().forEach{key->row.put(key,saved.get(key))}
        if(!saved.has("error"))row.remove("error")
        return true
    }

    private suspend fun reconcileBoundLateAdvance(runId:String,date:String,row:JSONObject,ad:JSONObject,pendingCount:Int):Boolean {
        val saved=ad.optJSONArray("boostSteps")?:return false
        val steps=(0 until saved.length()).map{saved.getInt(it)}
        fun valid():Boolean {
            val observed=nodes()
            val nativePanel=ui.currentPackage()==pkg&&!closeDriver.isAdSurface()&&
                listOf("title_count","current_point","boost_list").all{id->observed.any{it.viewIdResourceName=="$pkg:id/$id"}}
            return !AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&
                (!ad.has("nativeRecoveryStartedAt")||ad.has("officialBinding")||boundNativeIdentity(row))&&
                row.getString("date")==date&&row.optInt("offeredReward")==ad.optInt("offeredBefore")&&
                BoundLateBoostReceiptPolicy.matches(row.optString("state"),ad.optString("state"),ad.optString("reason"),
                    ad.optBoolean("confirmed"),ad.optBoolean("initialJoin"),ad.optInt("index"),row.getJSONArray("ads").length(),
                    pendingCount,TokyoDate.of()==date,BoundLateBoostReceiptPolicy.departed(ad.optBoolean("adDepartureObserved"),ad.has("officialBinding"),ad.optString("dispatchPhase")),row.getInt("boost"),
                    ad.optInt("offeredBefore"),ad.optInt("expectedOfferedAfter"),steps,nativePanel,labels())
        }
        ui.refreshCache();if(!valid()||!AccountSafety.authorize(svc,"bound_late_boost_receipt"))return false
        delay(1500);ui.refreshCache();if(!valid())return false
        ui.refreshCache();if(!valid())return false
        if(ad.has("officialBinding")&&!OfficialChallengeBinding.matches(row,ad,ui.officialChallengeIdentity()))return false
        if(!commitReviewedBoost(date,row,ad){current,latest->
            latest.put("previousFailure",latest.optString("reason")).put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true)
            .put("reason","same_bound_attempt_stable_native_ordinal_and_reward_advance")
            .put("afterSerial",current.getInt("boost")+2).put("offeredAfter",latest.getInt("expectedOfferedAfter"))
            .put("receiptReviewRunId",runId).put("finishedAt",System.currentTimeMillis())
            current.put("boost",current.getInt("boost")+1).put("offeredReward",latest.getInt("expectedOfferedAfter")).put("state","JOINED");current.remove("error")
        })return false
        evidence(runId,"bound_late_official_advance_after",row.toString());return true
    }
    /** Reads the outcome through the same identity and stability checks as an official resume; dispatches nothing. */
    private suspend fun settleFromOfficialPanel(runId:String,date:String,row:JSONObject,ad:JSONObject,pendingCount:Int):Boolean {
        val saved=ad.optJSONArray("boostSteps")?:return false
        val steps=(0 until saved.length()).map{saved.getInt(it)}
        val ads=row.getJSONArray("ads")
        ui.refreshCache()
        if(!ChallengeRecoveryPolicy.panelMaySettle(row.optString("state"),ad.optString("state"),ad.optString("reason"),ad.optBoolean("confirmed"),
            ad.optBoolean("initialJoin"),ad.optInt("index"),ads.length(),pendingCount,row.optString("date")==date&&TokyoDate.of()==date,
            ad.has("officialBinding"),ad.optString("dispatchPhase"),closeDriver.isRecoverableSurface(),
            (0 until ads.length()).any{ads.getJSONObject(it).optBoolean("officialNoGain")},
            OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.optInt("offeredBefore"),ad.optInt("expectedOfferedAfter"),steps,labels())))return false
        evidence(runId,"official_panel_settle_before",row.toString())
        return awaitResumedBoost(runId,date,row,ad,steps)
    }
    private suspend fun resumeOfficialInterruptedBoost(runId:String,date:String,row:JSONObject,ad:JSONObject):Boolean {
        val ads=row.getJSONArray("ads")
        val stepsJson=ad.optJSONArray("boostSteps")?:return false
        val steps=(0 until stepsJson.length()).map{stepsJson.getInt(it)}
        val pending=ChallengeStore.rows(svc,date)
        val count=(0 until pending.length()).count{val r=pending.getJSONObject(it)
            r.optString("state") in setOf("AD_OPENING","UNCONFIRMED","JOINED")&&!PendingInitialJoinStore.isolated(r,date)}
        fun safe()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&AccountSafety.authorize(svc,"official_boost_resume")
        check(safe()&&row.getString("date")==date&&row.optInt("offeredReward")==ad.optInt("offeredBefore")){"official_resume_binding_changed"}
        val restarted=count==1&&OfficialChallengeBinding.valid(row,ad)&&ad.optString("dispatchPhase")=="DISPATCHED"&&ad.optBoolean("adDepartureObserved")&&
            StalledAdRestart.relaunchedChallenge(svc,date)
        check(boundNativeIdentity(row)||recordedOfficialRestart(row,ad,count)||restarted){"anonymous_official_resume_requires_exact_task_identity"}
        check(!ad.has("officialBinding")||ad.optInt("officialPromptResumeRequests")==0){"original_official_resume_already_requested"}
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
        return awaitResumedBoost(runId,date,row,ad,steps)
    }
    private fun recordedOfficialRestart(row:JSONObject,ad:JSONObject,pending:Int):Boolean {
        val owner=CheckinRuntime.ownership.owner()?:return false
        if(owner.app!="ANA"||owner.run!=AutoAccessibilityService.currentRunId||
            !CheckinRuntime.ownership.valid(owner,android.os.SystemClock.elapsedRealtime()))return false
        val version=ad.optJSONObject("officialBinding")?.optLong("anaVersionCode")?:return false
        if(version<=0||version!=svc.packageManager.getPackageInfo(pkg,0).longVersionCode)return false
        return ChallengeRecoveryPolicy.recordedRestartNavigation(OfficialChallengeBinding.valid(row,ad),row.optString("date")==TokyoDate.of(),
            pending,ad.optString("nativeRecoveryStage"),ad.optString("nativeRecoveryRunId")==ad.optString("runId"),
            ad.optInt("officialPromptResumeRequests"),ad.optString("dispatchPhase")=="DISPATCHED"&&ad.optBoolean("adDepartureObserved"))
    }

    /** One outcome loop for both cold rebuild and an observed official resume; never dispatches a replacement ad. */
    private suspend fun awaitResumedBoost(runId:String,date:String,row:JSONObject,ad:JSONObject,steps:List<Int>):Boolean {
        fun safe()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date&&AccountSafety.authorize(svc,"official_boost_resume")
        val resumeDispatchedAt=android.os.SystemClock.elapsedRealtime()
        val until=android.os.SystemClock.elapsedRealtime()+45_000
        while(android.os.SystemClock.elapsedRealtime()<until){
            check(safe()){"official_resume_stopped"};delay(600);ui.refreshCache()
            if(closeDriver.isRecoverableSurface()){
                evidence(runId,"bound_official_resume_existing_ad",row.toString())
                return watch(runId,date,row,null,false,ad)
            }
            val proposed=OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,labels())?:continue
            if(proposed=="NO_GAIN"&&android.os.SystemClock.elapsedRealtime()-resumeDispatchedAt<15_000)continue
            val official=if(ad.has("officialBinding"))ui.officialChallengeIdentity() else null
            val identityBound=if(ad.has("officialBinding"))OfficialChallengeBinding.matches(row,ad,official) else boundNativeIdentity(row)
            val outcome=if(identityBound)OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,labels()) else null
            if(outcome==null)continue
            val loading=nodes().any{n->val b=Rect();n.getBoundsInScreen(b);ChallengeRecoveryPolicy.blockingOfficialLoading(n.viewIdResourceName,n.className.toString(),b.contains(svc.resources.displayMetrics.widthPixels/2,svc.resources.displayMetrics.heightPixels/2))}
            if(outcome=="NO_GAIN"&&ad.has("officialBinding")&&official?.boostCount!=ad.getJSONObject("officialBinding").optInt("boostBefore"))continue
            if(outcome=="NO_GAIN"&&!ChallengeRecoveryPolicy.officialNoGainReady(android.os.SystemClock.elapsedRealtime()-resumeDispatchedAt,loading))continue
            delay(1500);check(safe()){"official_resume_stopped"};ui.refreshCache()
            val stableOfficial=if(ad.has("officialBinding"))ui.officialChallengeIdentity() else null
            val stableIdentity=if(ad.has("officialBinding"))OfficialChallengeBinding.matches(row,ad,stableOfficial)&&
                (outcome!="NO_GAIN"||stableOfficial?.boostCount==ad.getJSONObject("officialBinding").optInt("boostBefore")) else boundNativeIdentity(row)
            if(closeDriver.isRecoverableSurface()||!stableIdentity||OpaqueRecoveryPolicy.outcome(row.getInt("boost"),ad.getInt("offeredBefore"),ad.getInt("expectedOfferedAfter"),steps,labels())!=outcome)continue
            if(!commitReviewedBoost(date,row,ad){current,latest->
                latest.put("previousFailure",latest.optString("reason")).put("finishedAt",System.currentTimeMillis()).put("officialPromptOutcome",outcome)
                .put("offeredAfter",ChallengeSignals.offeredReward(labels()))
            if(outcome=="ADVANCED"){
                latest.put("state",AdState.REWARD_CONFIRMED.name).put("confirmed",true).put("afterSerial",current.getInt("boost")+2)
                    .put("reason","bound_official_resume_confirms_boost_advance")
                current.put("boost",current.getInt("boost")+1).put("offeredReward",latest.getInt("expectedOfferedAfter"))
            }else{
                latest.put("state",AdState.FAILED.name).put("confirmed",false).put("officialNoGain",true)
                    .put("reason","official_resumed_session_confirms_boost_not_applied")
            }
            current.put("state","JOINED").remove("error")
            })return false
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
            boost(runId, date, row, 7,AutomationEngine(svc,Prefs.loadBlocking(svc)))
            return ChallengeStore.summary(svc, date)
        } catch (t: Exception) {
            evidence(runId, "pilot_continue_error", t.toString())
            return "试运行叠加中止：${t.message}\n" + ChallengeStore.summary(svc, date)
        }
    }

    private suspend fun processChallenge(engine: AutomationEngine, runId: String, date: String, card: ChallengeCard,
        adBudget: Int, pilot: Boolean, rejoinRow: JSONObject? = null): Boolean {
        if(!TravelSelection.enabled(svc,card.cat)){TravelSelection.audit(svc,date,runId,card.cat);return false}
        if (rejoinRow==null&&ChallengeStore.find(svc,date,card.key)?.optJSONArray("ads")?.length()?.let{it>0}==true) return false
        check(!AutoAccessibilityService.stopRequested && !blocked() && TokyoDate.of() == date) { "stopped_or_date_changed" }
        check(withTimeoutOrNull(20_000L){engine.openChallenge(card)}==true) { "target_detail_unreachable_" + card.cat }
        check(nodes().any { ui.label(it)==card.title && Dist.parse(ui.label(it))?.meters==card.meters }) { "detail_identity_mismatch" }
        check(labels().any { AnaCalendarDate.same(it, date) }) { "detail_date_mismatch" }
        val join = byId("button_join_boost") ?: error("observed_video_join_button_missing")
        val row = rejoinRow ?: JSONObject().put("key", card.key).put("date", date).put("cat", card.cat.name).put("title", card.title)
            .put("meters", card.meters).put("runId", runId).put("joined", false).put("boost", 0).put("state", "READY").put("ads", JSONArray())
        val joined=watch(runId,date,row,join,true)
        if(!joined&&!TravelSelection.enabled(svc,card.cat)){TravelSelection.audit(svc,date,runId,card.cat);return false}
        check(joined) { "join_not_confirmed_" + card.cat }
        if (pilot) { row.put("state", "PILOT_JOIN_CONFIRMED"); ChallengeStore.save(svc, date, row); return true }
        boost(runId, date, row, adBudget,engine)
        return false
    }

    suspend fun run(runId: String, categories: List<Cat>, allCards: Boolean = false, adBudget: Int = 6, pilot: Boolean = false, settingsOverride:AppSettings?=null, reuseCompleted:Boolean=false): RoutineStepResult {
        val date = TokyoDate.of()
        val settings = settingsOverride ?: Prefs.loadBlocking(svc)
        val engine = AutomationEngine(svc, settings)
        val overlay = OverlayKeeper(svc)
        val attemptedKeys=mutableSetOf<String>()
        val outcomes=JSONArray()
        val previousJournal=ReliableLedger.read(svc,"acquisition",date,"last_run"){null}?.let{JSONObject(it)}
        fun record(card:ChallengeCard,outcome:String,reason:String=""){
            outcomes.put(JSONObject().put("key",card.key).put("cat",card.cat.name).put("meters",card.meters)
                .put("outcome",outcome).put("reason",reason.take(300)).put("at",System.currentTimeMillis()))
            // Short existing FULL transaction; no UI action runs inside it, no advertisement row is written.
            ReliableLedger.put(svc,"acquisition",date,"last_run",JSONObject().put("date",date).put("runId",runId).put("outcomes",outcomes).toString())
            Logger.i("ACQUIRE","run=$runId key=${card.key} result=$outcome reason=${reason.take(160)}")
        }
        fun live()=!AutoAccessibilityService.stopRequested&&!blocked()&&TokyoDate.of()==date
        suspend fun independentPage():Boolean {
            ui.refreshCache()
            if(!live()||!cleanIsolationPage()||MaxBoostCoordinator.hasPending(svc,date)||MaxBoostCoordinator.visible(svc)||
                !AccountSafety.authorize(svc,"acquisition_continue"))return false
            val ids=nodes().mapNotNull{it.viewIdResourceName?.substringAfterLast('/')}.toSet()
            if(HomeNavigationPolicy.nativeDetail(ui.currentPackage(),labels(),ids)){
                val close=byId("close")?:return false
                if(!tap(close))return false
                delay(900);ui.refreshCache()
            }
            return live()&&cleanIsolationPage()&&!ChallengeStore.blocksIndependentNavigation(svc,date)
        }
        try {
            /* No multi-hour wake hold: native readiness and active UI ownership remain bounded. */
            overlay.show()
            val readiness=DeviceReady.ensure(svc,runId)
            check(readiness.ready){readiness.reason}
            check(AccountSafety.authorize(svc,"challenge_start")){"account_safety_human_required"}
            check(!blocked()) { "accessibility_or_keyguard" }
            MaxBoostCoordinator.recover(svc,engine,runId,date)
            // A pending ad from a killed process cannot be repeated or inferred successful.
            var prior = ChallengeStore.rows(svc, date)
            val unresolved=(0 until prior.length()).map{prior.getJSONObject(it)}.filter{
                (it.optString("state") in setOf("AD_OPENING","UNCONFIRMED","JOINED")||ChallengeStore.unconfirmedAd(it))&&!PendingInitialJoinStore.isolated(it,date)}
            if(unresolved.isNotEmpty()){
                check(ui.currentPackage()==pkg||AdTaskReturn.restoreOrRelaunchIfGone(svc,date,runId)){"original_ana_task_unavailable"}
                val pageUntil=android.os.SystemClock.elapsedRealtime()+8_000
                while(!cleanIsolationPage()&&live()&&android.os.SystemClock.elapsedRealtime()<pageUntil){delay(250);ui.refreshCache()}
                isolateSubmittedUnknowns(engine,runId,date,unresolved.filter{ChallengeStore.unconfirmedAd(it)})
                prior=ChallengeStore.rows(svc,date)
            }
            val pending = (0 until prior.length()).map { prior.getJSONObject(it) }
                .filter { it.optString("state") in setOf("AD_OPENING", "UNCONFIRMED", "JOINED")||ChallengeStore.unconfirmedAd(it) }
                .filterNot{PendingInitialJoinStore.isolated(it,date)}.filter { row->TravelSelection.enabled(svc,Cat.ofName(row.optString("cat"))!!) || row.optString("state") in setOf("AD_OPENING","UNCONFIRMED") }
            val recoverablePending=pending.filter{ChallengeStore.unconfirmedAd(it)||!cleanIsolationPage()}
            if (recoverablePending.isNotEmpty()) {
                check(recoverablePending.size == 1) { "multiple_unconfirmed_challenges_require_review" }
                val row = recoverablePending.single()
                // Existing submitted ads may finish even after their category is disabled. No new Boost is allowed.
                if(ui.currentPackage()!=pkg)ui.launchApp(pkg)
                repeat(15) { ui.refreshCache();if (!closeDriver.isRecoverableSurface() && observedJoinedMax(row) == null && !AdSignals.loadFailure(labels()) &&
                    !ChallengeSignals.dialog(labels()) && labels().none { it == "ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？" }) delay(1000) }
                evidence(runId, "manual_resume_observed_window", row.toString())
                val ads = row.getJSONArray("ads")
                val last = ads.getJSONObject(ads.length() - 1)
                val reserved=PendingInitialJoinStore.binding(row)?.let{PendingInitialJoinPolicy.mayDispatchReserved(it,date,nativeUnjoined(row))}==true
                val isolated=isolateLegacyInitial(runId,date,row,pending.size)
                if(reserved){
                    check(watch(runId,date,row,byId("button_join_boost"),true,last,reservedContinuation=true)){"reserved_initial_receipt_not_confirmed"}
                    boost(runId,date,row,adBudget,engine)
                } else if (!isolated&&!reconcileJoinedMax(runId, date, row)) {
                    if (row.optString("state") == "JOINED") {
                        if (labels().any { it == "ブーストが中断されているチャレンジが存在します。ブーストを再開しますか？" }) {
                            val resume = nodes().singleOrNull { it.viewIdResourceName == "android:id/button1" && ui.label(it) == "再開する" }
                                ?: error("known_joined_resume_button_missing")
                            evidence(runId, "known_joined_official_resume_prompt", row.toString())
                            check(boundNativeIdentity(row)){"anonymous_official_resume_requires_exact_task_identity"}
                            check(tap(resume)); delay(1800)
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
                    } else if(settleFromOfficialPanel(runId,date,row,last,pending.size)){
                        // The ad is gone and ANA's own Boost panel gave this attempt's outcome.
                    } else if(officialResumePrompt()!=null){
                        check(!last.optBoolean("initialJoin")){"初次接取回执待核对；已识别官方再开提示，尚未点击或新开广告"}
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
                    boost(runId, date, row, adBudget,engine)
                }
                prior = ChallengeStore.rows(svc, date)
            }
            if(pending.isEmpty()&&(0 until prior.length()).any{PendingInitialJoinStore.isolated(prior.getJSONObject(it),date)}){
                // A retry can start from the launcher or another app, e.g. right after a check-in.
                if(ui.currentPackage()!=pkg)ui.launchApp(pkg)
                val pageUntil=android.os.SystemClock.elapsedRealtime()+8_000
                while(!cleanIsolationPage()&&live()&&android.os.SystemClock.elapsedRealtime()<pageUntil){delay(250);ui.refreshCache()}
                check(cleanIsolationPage()&&AccountSafety.authorize(svc,"isolated_initial_navigation")){
                    "isolated_initial_requires_clean_native_window"}
            }
            check(!ChallengeStore.blocksIndependentNavigation(svc,date)){"unconfirmed_challenge_requires_evidence_review"}
            leaveLeftoverBudgetPrompt(runId,date)
            Evidence.write(svc,"challenge_"+runId+"_settings.json",settings.toJson())
            var partial=pending.isNotEmpty()
            val seenInPlans=mutableSetOf<String>()
            for(cat in categories){
                if(!TravelSelection.enabled(svc,cat)){TravelSelection.audit(svc,date,runId,cat);continue}
                if(!live())break
                // Re-evaluate after existing pending recovery; no fresh-list claim for a reused scope.
                val reusedState=if(reuseCompleted)ChallengeScanStore.resumeState(svc,date,cat,settings.kmOf(cat)) else null
                if(reuseCompleted&&RoutinePolicy.reuseChallenges(TokyoDate.of()==date,settings.dayToday&&!settings.dayTomorrow,reusedState!=null,
                    ChallengeStore.blocksIndependentNavigation(svc,date)||MaxBoostCoordinator.hasPending(svc,date)||MaxBoostCoordinator.visible(svc))){
                    partial=partial||reusedState=="PARTIALLY_COMPLETED"
                    Logger.i("ROUTINERESUME","run=$runId category=$cat skip_reused_category_ui=true after_pending_review=true state=$reusedState")
                    continue
                }
                var eligible=0;var categoryIssues=0;var scanned=false
                try{
                    ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,ChallengeScanStatus.SCANNING)
                    ChallengeStore.setCoverage(svc,date,cat,0)
                    check(engine.prepareFilteredCategory(cat,allowLoginSubmission=false)){"category_filter_unreachable_"+cat}
                    var categoryEpoch=navigationEpoch
                    // One complete joinable snapshot, and at most one bounded recheck. No persistent cursor.
                    for(pass in 0 until if(allCards)2 else 1){
                        val plan=engine.scanFilteredChallenges(cat,settings.kmOf(cat)*1000)
                        scanned=true;seenInPlans.addAll(plan.map{it.key})
                        val fresh=plan.filter{it.key !in attemptedKeys}.let{if(allCards)it else it.take(1)}
                        if(fresh.isEmpty())break
                        while(live()&&TravelSelection.enabled(svc,cat)){
                            val card=CategoryRunSelection.takeNext(fresh,attemptedKeys)?:break
                            eligible++
                            val existing=ChallengeStore.find(svc,date,card.key)
                            var rejoinRow:JSONObject?=null
                            if(existing?.optJSONArray("ads")?.length()?.let{it>0}==true){
                                // The card is in this fresh 参加可能 list; the user allows one re-join when 参加中 also proved it absent.
                                if(InitialRejoinPolicy.mayRejoin(existing,date,true)){
                                    rejoinRow=InitialRejoinPolicy.prepare(existing,runId,System.currentTimeMillis())
                                    ChallengeStore.save(svc,date,rejoinRow)
                                    evidence(runId,"initial_rejoin_official_absent_and_joinable",rejoinRow.toString())
                                    Logger.i("ACQUIRE","run=$runId key=${card.key} rejoin_once=true reason=official_not_joined")
                                }else{
                                    // A joinable-list inconsistency never grants permission to overwrite/replay an ad.
                                    categoryIssues++;partial=true;record(card,"UNCERTAIN","recorded_attempt_kept_no_rejoin");continue
                                }
                            }
                            record(card,"INSPECTING")
                            try{
                                if(categoryEpoch!=navigationEpoch){
                                    check(engine.prepareFilteredCategory(cat,allowLoginSubmission=false)){"category_filter_after_sdk_recovery_unreachable_"+cat}
                                    categoryEpoch=navigationEpoch
                                }
                                if(processChallenge(engine,runId,date,card,adBudget,pilot,rejoinRow)){
                                    record(card,"JOINED")
                                    return RoutineStepResult("PARTIALLY_COMPLETED","试运行："+card.title+" 接取确认，Boost待验证")
                                }
                                val saved=ChallengeStore.find(svc,date,card.key)
                                record(card,if(saved?.optBoolean("joined")==true)"JOINED" else "SKIPPED",if(saved==null)"selection_changed_no_dispatch" else "")
                            }catch(t:Exception){
                                if(t is kotlinx.coroutines.CancellationException)throw t
                                val row=ChallengeStore.find(svc,date,card.key)
                                val submitted= row?.let{ChallengeStore.unconfirmedAd(it)}==true
                                if(submitted&&cleanIsolationPage())isolateSubmittedUnknowns(engine,runId,date,listOf(row!!))
                                // Durable/identity errors, a live SDK, Boost modal or unisolated ad remain global.
                                val reason=t.message.orEmpty()
                                if(t is MaxBoostCompletionFailure||reason.contains("durable_ledger")||reason.contains("identity")||
                                    !independentPage())throw t
                                categoryIssues++;partial=true
                                record(card,if(submitted||reason.contains("unreachable"))"UNCERTAIN" else "LOCAL_FAILURE",reason)
                            }
                        }
                        if(!live()||!allCards||pass==1||categoryIssues==0&&categoryEpoch==navigationEpoch)break
                        check(independentPage()){"acquisition_navigation_not_safe"}
                        check(engine.prepareFilteredCategory(cat,allowLoginSubmission=false)){"category_recheck_unreachable_"+cat}
                        categoryEpoch=navigationEpoch
                    }
                    val known=ChallengeStore.rows(svc,date)
                    val boostPending=(0 until known.length()).any{val r=known.getJSONObject(it);r.optString("cat")==cat.name&&r.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
                    val complete=scanned&&live()
                    val status=ChallengeScanPolicy.finish(complete,true,AutoAccessibilityService.stopRequested,TokyoDate.of()==date,eligible,categoryIssues>0||boostPending)
                    ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,status,eligible=eligible,basis="JOINABLE",reason=if(categoryIssues>0)"local_acquisition_issues_kept_in_journal" else "")
                    if(complete)ChallengeStore.setCoverage(svc,date,cat,settings.kmOf(cat))
                    partial=partial||categoryIssues>0||boostPending
                }catch(t:Exception){
                    if(t is kotlinx.coroutines.CancellationException)throw t
                    if(t is MaxBoostCompletionFailure||t.message.orEmpty().contains("durable_ledger")||t.message.orEmpty().contains("identity")||!independentPage())throw t
                    partial=true
                    ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,ChallengeScanStatus.SCAN_FAILED,reason=t.message.orEmpty())
                    Logger.w("ACQUIRE","run=$runId category=$cat local_scan_failure_continue=true reason=${t.message}")
                }
            }
            // A previous local failure missing from a complete fresh list is skipped, never invented as joined.
            val old=previousJournal?.optJSONArray("outcomes")?:JSONArray()
            for(i in 0 until old.length()){
                val r=old.getJSONObject(i);val card=Cards.fromTitle(r.optString("key").substringAfter('|'))?:continue
                if(r.optString("outcome") in setOf("LOCAL_FAILURE","UNCERTAIN")&&card.key !in seenInPlans&&
                    TravelSelection.enabled(svc,card.cat)&&ChallengeScanStore.read(svc,date,card.cat)?.optString("runId")==runId&&
                    ChallengeScanStore.sourceComplete(ChallengeScanStore.read(svc,date,card.cat)))
                    record(card,"SKIPPED","not_in_fresh_complete_joinable_list_no_success_inferred")
            }
            check(live()){"stopped_or_date_changed"}
            check(independentPage()){"challenge_exit_not_safe"}
            val scopeComplete=categories.filter{TravelSelection.enabled(svc,it)}.all{ChallengeScanStore.sourceComplete(ChallengeScanStore.read(svc,date,it))}
            val summary=(if(scopeComplete)"可接取列表扫描结束；接取回执与Boost分别保存\n" else "部分接取扫描未完成，保留局部结果\n")+ChallengeStore.summary(svc,date)
            return RoutineStepResult(if(partial||!scopeComplete||TravelSelection.hasUnresolvedAd(svc,date))"PARTIALLY_COMPLETED" else "DONE",summary)
        } catch (t: Exception) {
            for(cat in TravelSelectionPolicy.queue(categories,Prefs.loadBlocking(svc))){
                if(!ChallengeScanStore.complete(svc,date,cat,settings.kmOf(cat)))ChallengeScanStore.record(svc,date,cat,settings.kmOf(cat),runId,if(TravelSelection.hasUnresolvedAd(svc,date))ChallengeScanStatus.BLOCKED else ChallengeScanStatus.SCAN_FAILED,reason=t.message.orEmpty())
            }
            if(t is MaxBoostCompletionFailure)Logger.e("MAXBOOST","completion halted without Back/relaunch or ad replay",t)
            else {evidence(runId,"error",t.toString());FaultStore.capture(svc,runId,"challenge_navigation",t.message.orEmpty())}
            return RoutineStepResult("BLOCKED","挑战执行中止：${t.message}\n" + ChallengeStore.summary(svc,date),false)
        } finally {
            active?.takeUnless{it.optBoolean("confirmed")}?.let { a ->
                a.put("state", "UNCONFIRMED").put("reason", "process_or_run_interrupted")
                activeRow?.let { row -> row.put("state", "UNCONFIRMED"); ChallengeStore.save(svc, date, row) }
            }
            overlay.hide()
        }
    }
}
