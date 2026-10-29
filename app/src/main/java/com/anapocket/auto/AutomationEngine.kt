package com.anapocket.auto

import android.content.Context
import android.graphics.Rect
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/** 广告单次观看的结果。严格区分，避免把「页面返回」当成「奖励到账」。 */
enum class AdOutcome {
    REWARD_CONFIRMED, // 积分/计数增长，奖励已独立确认
    WATCH_COMPLETED,  // 观察到结束卡或播放超过阈值，界面已返回
    PAGE_RETURNED,    // 仅界面返回，未观察到完整播放
    TIMEOUT,          // 超时逃生
    NOT_STARTED       // 广告未启动（无库存/已达上限）
}

private enum class ClaimResult { SUCCESS, ALREADY, UNCONFIRMED, FAIL }

private data class CardHit(val cat: Cat, val km: Int, val node: AccessibilityNodeInfo)

/**
 * v9 自动化引擎：消费任务队列，逐条更新状态。
 *
 * 设计原则（针对 v8 验收意见）：
 *  1. 挑战卡片必须先读到文本，或（可选的）位置猜测后在详情页强校验类别+公里数，才允许点击领取；
 *  2. 只有出现「参加予約ができました」等确认文案才记为 SUCCESS；
 *  3. 广告只有积分/计数增长才记为 REWARD_CONFIRMED，否则是 UNCONFIRMED；
 *  4. 任何一步失败都写入 task.error，不用「整体 OK」掩盖。
 */
class AutomationEngine(
    private val svc: AutoAccessibilityService,
    private val st: AppSettings
) {
    private val ui = Ui(svc)
    private val pkg = "us.ana_pocket.ANAPocket"
    private val overlay = OverlayKeeper(svc)
    private val detailEvaluator = DetailStateEvaluator(pkg)
    private var lastOpenError = "card_open_failed"
    private var lastDetailObservation: DetailObservation? = null

    private val cardRe = Regex("(移動手段問わず|自転車|徒歩|車)\\s*で?\\s*(\\d+)\\s*km")
    private val detailRe = Regex("(移動手段問わず|自転車|徒歩|車)\\s*で?\\s*(\\d+)\\s*km")

    private val stop: Boolean get() = AutoAccessibilityService.stopRequested

    suspend fun execute(runId: String, queue: MutableList<AutoTask>): RunOutcome {
        Logger.i("ENGINE", "run=$runId tasks=${queue.size} adTarget=${st.adTarget} today=${st.dayToday} tomorrow=${st.dayTomorrow}")
        overlay.show()
        try {
            wakeScreen()
            ensureForeground()
            recoverToEarnTab()

            val adTasks = queue.filter { it.kind == TaskKind.AD }
            if (adTasks.isEmpty()) {
                Logger.i("ENGINE", "ad section skipped (not selected or target reached at build time)")
            } else {
                runAds(adTasks)
            }

            for (off in listOf(0, 1)) {
                if (stop) break
                val group = queue.filter { it.kind == TaskKind.CHALLENGE && it.dayOffset == off }.toMutableList()
                if (group.isEmpty()) continue
                val produced = ArrayList<AutoTask>()
                runChallengeDayV10(off, group, produced)
                // 队列粒度：用「日期 × 分类 × 实际卡片」替换 scope 占位项
                queue.removeAll(group)
                queue.addAll(produced)
            }
        } catch (t: Throwable) {
            Logger.e("ENGINE", "execute crashed: ${t.message}", t)
        } finally {
            overlay.hide()
        }

        val success = queue.count { it.state == TaskState.SUCCESS }
        val failed = queue.count { it.state == TaskState.FAILED }
        val unconfirmed = queue.count { it.state == TaskState.UNCONFIRMED }
        val already = queue.count { it.state == TaskState.ALREADY_DONE }
        val skipped = queue.count { it.state == TaskState.SKIPPED || it.state == TaskState.PENDING }

        val detail = queue.joinToString("; ") { t ->
            "${t.id}=${t.state}" + (if (t.error.isNotEmpty()) "(${t.error})" else "")
        }
        Logger.i("ENGINE", "queue result: $detail")
        return RunOutcome(runId, success, failed, unconfirmed, already, skipped, stop, detail)
    }


    /** R39 reuses only R30/R32 discovery and target-validated opening; legacy ad/claim code is not called. */
    suspend fun challengeCandidates(cat: Cat): List<ChallengeCard> {
        wakeScreen()
        ensureForeground()
        if (!ensureListPage() || !ensureChallengeTab() || !ensureJoinableTab() || !selectDate(0)) return emptyList()
        check(scrollToTop()) { "challenge_list_top_unavailable" }
        val todayNode = ui.activeNodes(pkg = pkg).firstOrNull { ui.label(it) == "今日" } ?: return emptyList()
        val dateBounds = Rect(); todayNode.getBoundsInScreen(dateBounds)
        val (width, _) = screen()
        repeat(4) { ui.swipe((width * .35).toInt(), dateBounds.centerY(), (width * .92).toInt(), dateBounds.centerY(), 450); delay(400) }
        val all = ui.activeNodes(pkg = pkg).firstOrNull { ui.label(it) == "すべて" } ?: error("all_category_chip_not_observed")
        val allBounds = Rect(); all.getBoundsInScreen(allBounds)
        ui.tap(allBounds.centerX(), allBounds.centerY()); delay(1300)
        return discoverAllCards().map { it.card }.filter { it.cat == cat }.distinctBy { it.key }
    }
    suspend fun openChallenge(card: ChallengeCard): Boolean {
        val m = card.meters ?: return false
        return openCardFromTop(card.cat, m, card.title, applyFilter = false)
    }

    private var activeFilteredCategory: Cat? = null
    private fun categoryRow(): AccessibilityNodeInfo? {
        ui.refreshCache()
        return ui.activeNodes(pkg = pkg).firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "$pkg:id/category_filter_compose_view" }
    }
    private fun nativeCardFields(title: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        var parent = title.parent
        repeat(3) {
            val p = parent ?: return emptyList()
            val fields = ArrayList<AccessibilityNodeInfo>()
            fun visit(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > 8) return
                fields.add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let { visit(it, depth + 1) }
            }
            visit(p, 0)
            if (fields.any { it.viewIdResourceName == "$pkg:id/text_period" }) return fields.takeIf {
                it.count { n -> n.viewIdResourceName == "$pkg:id/text_period" } == 1 &&
                it.count { n -> n.viewIdResourceName == "$pkg:id/text_name" } == 1
            } ?: emptyList()
            parent = p.parent
        }
        return emptyList()
    }
    private fun visibleDailyCards(): List<ChallengeCard> {
        ui.refreshCache()
        val (_, h) = screen()
        val today = ui.activeNodes(pkg = pkg).filter { it.isVisibleToUser && topOf(it) in 400..(h * .86).toInt() &&
            it.viewIdResourceName == "$pkg:id/text_name" }.map { it to nativeCardFields(it) }.filter { (_, fields) ->
            DailyCardDeadline.matches(fields.singleOrNull { it.viewIdResourceName == "$pkg:id/text_period" }?.let { ui.label(it) }, TokyoDate.of())
        }
        val unknown = today.filter { (title, fields) -> UnknownDailyCardPolicy.mustReview(ui.label(title),
            fields.any { it.viewIdResourceName == "$pkg:id/unit" && ui.label(it) in setOf("km", "m") },
            fields.count { it.viewIdResourceName == "$pkg:id/icon" }) }
        check(unknown.isEmpty()) { "unsupported_same_day_distance_card:" + unknown.joinToString { ui.label(it.first) } }
        return Cards.dedupe(today.mapNotNull { Cards.fromTitle(ui.label(it.first)) })
    }
    private fun dailyPositionSignature(): String {
        ui.refreshCache()
        return ui.activeNodes(pkg = pkg).filter { it.isVisibleToUser }
            .filter { it.viewIdResourceName == "$pkg:id/text_name" }
            .joinToString("|") { ui.label(it) + "@" + topOf(it) }
    }
    private suspend fun categoryBitmap(): android.graphics.Bitmap? {
        repeat(3) {
            val png = ui.captureVisual(includePng = true).png
            if (png != null) return android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)
            delay(350)
        }
        return null
    }
    private fun tilePixels(bitmap: android.graphics.Bitmap, b: Rect): IntArray? {
        if (b.width() <= 0 || b.height() <= 0 || b.left < 0 || b.top < 0 || b.right > bitmap.width || b.bottom > bitmap.height) return null
        return IntArray(b.width() * b.height()).also { bitmap.getPixels(it, 0, b.width(), b.left, b.top, b.width(), b.height()) }
    }
    private fun categoryStripSignature():String {
        val strip=Rect();(categoryRow()?:return "missing").getBoundsInScreen(strip)
        return ui.activeNodes(pkg=pkg).filter{val b=Rect();it.getBoundsInScreen(b);it.isVisibleToUser&&strip.contains(b)&&it.isClickable}.joinToString("|"){val b=Rect();it.getBoundsInScreen(b);ui.label(it)+"@"+b}
    }
    /** Scroll the observed Compose strip itself; never accept a parent-pager gesture as progress. */
    private suspend fun scrollCategoryStrip(forward:Boolean):Boolean {
        val strip=Rect();(categoryRow()?:return false).getBoundsInScreen(strip)
        val before=categoryStripSignature()
        val direct=if(forward)AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
        val generic=if(forward)AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val nodes=ui.activeNodes(pkg=pkg).filter{n->val b=Rect();n.getBoundsInScreen(b);n.isVisibleToUser&&strip.contains(b)&&b.width()>=strip.width()/2&&(n.isScrollable||n.actionList.any{it.id==direct||it.id==generic})}
        Evidence.write(svc,"category_strip_actions_"+System.currentTimeMillis()+".txt",nodes.joinToString("\n"){n->val b=Rect();n.getBoundsInScreen(b);n.className.toString()+" bounds="+b+" scrollable="+n.isScrollable+" actions="+n.actionList.map{it.id}})
        for(n in nodes){
            val action=if(n.actionList.any{it.id==direct})direct else generic
            if(n.performAction(action)){delay(700);ui.refreshCache();if(categoryStripSignature()!=before&&categoryStripSignature()!="missing"){Logger.i("CHSTRIP","native action moved forward="+forward);return true}}
        }
        val plan=CategoryDragPlan.points(strip.left,strip.right,forward)?:return false
        ui.swipeSlow(plan.first,strip.centerY(),plan.second,strip.centerY(),1400);delay(650);ui.refreshCache()
        val after=categoryStripSignature();val moved=after!="missing"&&after!=before
        Logger.i("CHSTRIP","slow drag forward="+forward+" moved="+moved)
        return moved
    }
    /** R40: actual icon selection inside the native strip; never fall back to all-category scanning. */
    suspend fun prepareFilteredCategory(cat: Cat): Boolean {
        wakeScreen(); ensureForeground()
        check(StartupFlow(svc).prepare("NAV"+System.currentTimeMillis(),launch=false).ok) { "startup_popup_blocks_category_navigation" }
        check(ensureListPage() && ensureChallengeTab()) { "filtered_list_unreachable" }
        if (categoryRow() == null) {
            val participating = ui.activeNodes(pkg = pkg).firstOrNull { it.isVisibleToUser && ui.label(it) == "参加中" }
                ?: error("native_participating_tab_missing_for_category_reset")
            val b = Rect(); participating.getBoundsInScreen(b)
            check(ui.tap(b.centerX(), b.centerY())) { "native_participating_tab_dispatch_failed" }
            for (i in 0 until 12) {
                ui.refreshCache()
                if (ui.activeLabels(pkg = pkg).any { it.startsWith("進行中") || it.startsWith("報酬未受取") }) break
                delay(300)
            }
            val joinable = ui.activeNodes(pkg = pkg).firstOrNull { it.isVisibleToUser && ui.label(it) == "参加可能" }
                ?: error("native_joinable_tab_missing_for_category_reset")
            val j = Rect(); joinable.getBoundsInScreen(j)
            check(ui.tap(j.centerX(), j.centerY())) { "native_joinable_tab_dispatch_failed" }
        }
        repeat(12) { if (categoryRow() == null) delay(400) }
        check(categoryRow() != null && ensureJoinableTab() && selectDate(0)) { "native_category_strip_not_observed" }
        for(reset in 0 until 4){
            val r=Rect();(categoryRow()?:error("category_strip_disappeared")).getBoundsInScreen(r)
            val allVisible=ui.activeNodes(pkg=pkg).any{n->val b=Rect();n.getBoundsInScreen(b);n.isVisibleToUser&&ui.label(n)=="すべて"&&r.contains(b)&&b.width()>r.width()/5}
            if(allVisible)break
            if(!scrollCategoryStrip(false))break
        }
        ui.refreshCache()
        val all = ui.activeNodes(pkg = pkg).firstOrNull { it.isVisibleToUser && ui.label(it) == "すべて" }
            ?: error("category_all_reset_not_observed")
        val allBounds = Rect(); all.getBoundsInScreen(allBounds)
        ui.tap(allBounds.centerX(), allBounds.centerY()); delay(800)
        var overlapTried=false
        repeat(8) { scan ->
            check(!stop) { "user_stopped" }
            val strip = Rect(); (categoryRow() ?: error("category_strip_missing")).getBoundsInScreen(strip)
            val candidates = ui.activeNodes(pkg = pkg).filter { n ->
                val b = Rect(); n.getBoundsInScreen(b)
                n.isVisibleToUser && n.isEnabled && n.isClickable && n.childCount == 0 && ui.label(n).isBlank() &&
                    strip.contains(b) && b.width() > 0 && b.height() >= strip.height() * .6 && b.width().toDouble() / b.height() in .8..1.3
            }
            val bitmap = categoryBitmap() ?: error("category_icon_screenshot_unavailable")
            val matches = try { candidates.filter { n ->
                val b = Rect(); n.getBoundsInScreen(b)
                tilePixels(bitmap, b)?.let { CategoryChipVision.matches(cat, b.width(), b.height(), it) } == true
            } } finally { bitmap.recycle() }
            check(matches.size <= 1) { "category_icon_ambiguous_" + cat }
            if (matches.size == 1) {
                val b = Rect(); matches.single().getBoundsInScreen(b)
                dumpUi("category_" + cat.name + "_observed_before")
                Logger.i("CHQUEUE", "category=" + cat + " actualIconBounds=" + b + " strip=" + strip + " scan=" + scan)
                ui.tap(b.centerX(), b.centerY()); delay(900)
                repeat(10) {
                    val selected = categoryBitmap() ?: error("category_selected_screenshot_unavailable")
                    val freshStrip = Rect(); (categoryRow() ?: error("category_strip_missing_after_selection")).getBoundsInScreen(freshStrip)
                    val isSelected = try { ui.activeNodes(pkg = pkg).any { n ->
                        val currentBounds = Rect(); n.getBoundsInScreen(currentBounds)
                        n.isVisibleToUser && n.isClickable && n.childCount == 0 && ui.label(n).isBlank() &&
                            freshStrip.contains(currentBounds) && tilePixels(selected, currentBounds)?.let {
                                CategoryChipVision.selectedBackground(cat, it)
                            } == true
                    } } finally { selected.recycle() }
                    val cards = visibleDailyCards()
                    if (isSelected && cards.all { it.cat == cat }) {
                        activeFilteredCategory = cat
                        dumpUi("category_" + cat.name + "_verified")
                        Logger.i("CHQUEUE", "category=" + cat + " iconSelected=true visibleDaily=" + cards.map { it.title })
                        return true
                    }
                    delay(400)
                }
                error("category_selection_not_verified_" + cat)
            }
            if(!scrollCategoryStrip(true)){
                check(!overlapTried){"category_strip_no_movement_"+cat};overlapTried=true
                val r=Rect();(categoryRow()?:error("category_strip_missing")).getBoundsInScreen(r)
                val before=categoryStripSignature();val plan=CategoryDragPlan.points(r.left,r.right,false)?:error("category_overlap_bounds")
                ui.swipeSlow(plan.first,r.centerY(),plan.second,r.centerY(),1400);delay(750);ui.refreshCache()
                check(categoryStripSignature()!=before){"category_overlap_did_not_move_"+cat}
                Logger.i("CHSTRIP","category="+cat+" bounded_overlap_scan=true;full_icon_required")
            }
        }
        error("category_icon_not_found_" + cat)
    }
    /** Next eligible card from the current category viewport, without resetting to the first page. */
    suspend fun nextFilteredChallenge(cat: Cat, maxMeters: Int, processed: Set<String>): ChallengeCard? {
        check(activeFilteredCategory == cat && ensureListPage()) { "filtered_category_context_lost" }
        var stable = 0
        var previous = ""
        var repairs = 0
        repeat(60) { page ->
            check(!stop) { "user_stopped" }
            val cards = visibleDailyCards()
            when (CategoryViewportPolicy.action(cat, cards.map { it.cat }.toSet(), repairs)) {
                CategoryViewportAction.KEEP_POSITION -> Unit
                CategoryViewportAction.RESELECT_CATEGORY -> {
                    dumpUi("category_" + cat.name + "_lost_after_sdk_return")
                    ui.captureVisual(includePng = true).png?.let { png -> Evidence.writeBytes(svc,
                        "ui_category_" + cat.name + "_lost_" + System.currentTimeMillis() + ".png", png) }
                    Logger.i("CHQUEUE", "category=" + cat + " contextLostObserved=" + cards.map { it.cat }.distinct() + " repairOnce=true")
                    repairs++
                    check(prepareFilteredCategory(cat)) { "category_context_reselect_failed_" + cat }
                    previous = ""; stable = 0
                    return@repeat
                }
                CategoryViewportAction.STOP -> error("category_context_changed_again_after_reselect_" + cat)
            }
            CategoryRunSelection.next(cards, cat, maxMeters, processed)?.let {
                Logger.i("CHQUEUE", "category=" + cat + " maxMeters=" + maxMeters + " next=" + it.title + " currentViewport=true page=" + page)
                return it
            }
            val list = ui.activeNodes(pkg = pkg).firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "$pkg:id/list" }
                ?: error("filtered_native_list_unreadable")
            val signature = dailyPositionSignature()
            if (signature == previous) stable++ else stable = 0
            previous = signature
            val canForward = list.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
            if (stable >= 3 && !canForward) { Logger.i("CHQUEUE", "category=" + cat + " exhausted=true maxMeters=" + maxMeters); return null }
            val moved = list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            delay(650)
            if (!moved || dailyPositionSignature() == signature) {
                val (w, h) = screen(); swipeListWithFallback(w, h, towardTop = false)
            }
            if (stable >= 5 && dailyPositionSignature() == signature) { Logger.i("CHQUEUE", "category=" + cat + " exhaustedStable=true"); return null }
        }
        error("filtered_scan_budget_exhausted_" + cat)
    }
    suspend fun openVisibleChallenge(card: ChallengeCard): Boolean {
        val meters = card.meters ?: return false
        return openCardFromTop(card.cat, meters, card.title, applyFilter = false, resetToTop = false)
    }

    // ------------------------------------------------------------ 基础能力

    private fun screen(): Pair<Int, Int> = ui.screenSize()

    /** 把引擎当前看到的无障碍树写入 evidence，便于事后定位（release 版也可用）。 */
    private fun dumpUi(tag: String) {
        try {
            val (w, h) = screen()
            val pkgNow = ui.currentPackage() ?: "?"
            val body = ui.dump(30)
            val nRoots = ui.roots().size
            val nNodes = ui.snapshot().size
            Evidence.write(
                svc,
                "ui_${tag}_${System.currentTimeMillis()}.txt",
                "pkg=$pkgNow size=${w}x$h windows=$nRoots nodes=$nNodes\n$body"
            )
        } catch (t: Throwable) {
            Logger.w("ENGINE", "dumpUi($tag) failed: ${t.message}")
        }
    }

    private fun topOf(n: AccessibilityNodeInfo): Int {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r.top
    }

    /**
     * App 的奖励到账弹窗。
     *
     * 注意匹配必须收紧：真机里「参加中」列表有一条常驻提示
     * 「11/14まで報酬を受け取りましょう！」，早期用 contains("報酬を受け取")
     * 会把它当成奖励弹窗，导致「点了视频按钮 0.08 秒就报 reward confirmed」
     * 的荒谬结果（R6 实测）。这里排除「ましょう」这类提示语。
     */
    private fun hasRewardPopup(labels: List<String>): Boolean =
        labels.any {
            val t = it.trim()
            (t.contains("報酬をゲット") && !t.contains("報酬をゲットしよう")) ||
                t.contains("報酬が確定") ||
                t.contains("ポイントを獲得") ||
                t.contains("報酬を受け取りました") ||
                (t.contains("報酬を受け取") && !t.contains("ましょう"))
        }

    private fun tabsVisible(): Boolean {
        val labels = ui.activeLabels(pkg = pkg)
        return labels.contains("広告視聴") && labels.contains("チャレンジ")
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = svc.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) {
                Logger.i("ENGINE", "screen already interactive")
                return
            }
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ana:screen"
            )
            wl.acquire(90_000L)
            Logger.i("ENGINE", "screen wake requested")
        } catch (t: Throwable) {
            Logger.w("ENGINE", "wake failed: ${t.message}")
        }
    }

    private suspend fun ensureForeground() {
        if (ui.currentPackage() == pkg) {
            Logger.i("ENGINE", "app already foreground")
            return
        }
        ui.launchApp(pkg)
        delay(3000)
        if (ui.currentPackage() != pkg) {
            Logger.w("ENGINE", "launch retry")
            ui.launchApp(pkg)
            delay(3200)
        }
        Logger.i("ENGINE", "foreground=${ui.currentPackage()}")
    }

    private suspend fun goEarnTab() {
        val (w, h) = screen()
        val tab = ui.find(Sel.BOTTOM_EARN, minTop = (h * 0.82).toInt()) ?: ui.find(Sel.BOTTOM_EARN)
        if (tab != null) {val b=Rect();tab.getBoundsInScreen(b);Logger.i("CHAL","native earn tab bounds="+b);ui.tap(b.centerX(),b.centerY())} else ui.tap((w * 0.30).toInt(), (h * 0.93).toInt())
        delay(1500)
    }

    private suspend fun recoverToEarnTab() {
        // 先处理奖励弹窗：它会盖住顶部标签栏，导致 tabsVisible=false
        repeat(2) {
            val popup = ui.find(listOf("報酬をゲット", "報酬を受け取", "ポイントをゲット", "報酬が確定"))
            if (popup == null) return@repeat
            Logger.i("ENGINE", "dismiss reward popup")
            ui.find(listOf("OK", "受け取る", "閉じる"))?.let { ui.click(it) }
            delay(1200)
        }
        repeat(3) {
            if (tabsVisible()) return
            ui.back()
            delay(800)
        }
        goEarnTab()
        if (!tabsVisible()) {
            Logger.w("ENGINE", "recover: relaunch app")
            ui.launchApp(pkg)
            delay(6500)
            goEarnTab()
        }
        Logger.i("ENGINE", "recover done tabsVisible=${tabsVisible()}")
    }

    /** 读取顶部积分，优先带千分位的数字。 */
    private fun readPoints(): Int? {
        var best: Int? = null
        var bestScore = -1
        for (n in ui.snapshot()) {
            val t = n.text?.toString()?.trim() ?: continue
            if (t.isEmpty() || topOf(n) > 320) continue
            val digits = t.replace(",", "")
            if (!digits.matches(Regex("\\d+"))) continue
            val v = digits.toIntOrNull() ?: continue
            val score = if (t.contains(",")) 1_000_000 + v else v
            if (score > bestScore) {
                bestScore = score
                best = v
            }
        }
        return best
    }

    /** 读取「今日の視聴済み回数 x/y」。读不到返回 null，由调用方决定降级策略。 */
    private fun readAdDone(): Int? {
        var fallback: Int? = null
        for (n in ui.snapshot()) {
            val l = ui.label(n)
            if (l.isEmpty()) continue
            val m = Regex("(\\d+)\\s*/\\s*(\\d+)").find(l) ?: continue
            val a = m.groupValues[1].toIntOrNull() ?: continue
            val b = m.groupValues[2].toIntOrNull() ?: continue
            if (b in 1..100) {
                if (b == 25) return a
                if (fallback == null) fallback = a
            }
        }
        return fallback
    }

    // ------------------------------------------------------------ 广告任务

    private suspend fun openAdsTab(): Boolean {
        val (w, h) = screen()
        val tab = ui.find(Sel.TAB_ADS, maxTop = 300)
        if (tab != null) ui.click(tab) else ui.tap((w * 0.583).toInt(), (h * 0.145).toInt())
        delay(2200)
        val ok = tabsVisible()
        Logger.i("AD", "ads tab open=$ok")
        return ok
    }

    private suspend fun startOneAd(): Boolean {
        val (w, h) = screen()
        val btn = ui.find(Sel.WATCH_AD, minTop = 300, maxTop = (h * 0.88).toInt())
        if (btn != null) {
            Logger.i("AD", "tap node '${ui.label(btn)}'")
            ui.click(btn)
        } else {
            val x = w / 2
            val y = (h * 0.6406).toInt()
            Logger.i("AD", "no readable node, tap ($x,$y)")
            ui.tap(x, y)
        }
        delay(3500)
        if (tabsVisible()) {
            Logger.w("AD", "still on ads page -> not started")
            return false
        }
        Logger.i("AD", "ad activity detected")
        return true
    }

    private suspend fun runAds(tasks: List<AutoTask>) {
        if (!openAdsTab()) {
            for (t in tasks) {
                t.state = TaskState.FAILED
                t.error = "ads_tab_unreachable"
                t.finishedAt = System.currentTimeMillis()
            }
            return
        }

        val appDone = readAdDone()
        val localDone = RunStore.adDone(svc, Dates.today())
        val base = appDone ?: localDone
        val toRun = (st.adTarget - base).coerceAtLeast(0)
        Logger.i("AD", "done_app=${appDone ?: "unreadable"} done_local=$localDone base=$base target=${st.adTarget} toRun=$toRun")

        if (toRun == 0) {
            for (t in tasks) {
                t.state = TaskState.SKIPPED
                t.note = "目标已完成（base=$base target=${st.adTarget}）"
                t.finishedAt = System.currentTimeMillis()
            }
            Logger.i("AD", "target already satisfied, nothing to do")
            return
        }

        var pointsBefore = readPoints()
        var ran = 0
        var fails = 0

        for (t in tasks) {
            if (stop) {
                t.state = TaskState.SKIPPED
                t.note = "用户停止"
                continue
            }
            if (ran >= toRun) {
                t.state = TaskState.SKIPPED
                t.note = "已达当日目标"
                continue
            }
            ran++
            t.startedAt = System.currentTimeMillis()
            t.state = TaskState.RUNNING
            if (!tabsVisible()) {
                Logger.w("AD", "not on app tabs before ad -> recover")
                recoverToEarnTab()
                if (!openAdsTab()) {
                    t.state = TaskState.FAILED
                    t.error = "ads_tab_lost"
                    t.finishedAt = System.currentTimeMillis()
                    break
                }
            }

            if (!startOneAd()) {
                t.state = TaskState.FAILED
                t.error = "ad_not_started"
                t.finishedAt = System.currentTimeMillis()
                fails++
                if (fails >= 2) {
                    Logger.w("AD", "two consecutive start failures, abort ad section")
                    break
                }
                continue
            }

            val r = waitAdFinish()
            val pts = readPoints()
            val grew = pointsBefore != null && pts != null && pts > pointsBefore
            t.state = when {
                grew -> TaskState.SUCCESS
                r == AdOutcome.REWARD_CONFIRMED -> TaskState.SUCCESS // App 自己弹了「報酬をゲット！」
                r == AdOutcome.WATCH_COMPLETED -> TaskState.UNCONFIRMED
                r == AdOutcome.PAGE_RETURNED -> TaskState.UNCONFIRMED
                else -> TaskState.FAILED
            }
            t.note = "outcome=$r points=${pointsBefore ?: "?"}->${pts ?: "?"}"
            t.error = when (t.state) {
                TaskState.FAILED -> "outcome_$r"
                TaskState.UNCONFIRMED -> "reward_unconfirmed"
                else -> ""
            }
            t.finishedAt = System.currentTimeMillis()
            Logger.i("AD", "task=${t.id} state=${t.state} ${t.note}")

            if (grew) {
                pointsBefore = pts
                RunStore.bumpAdDone(svc, Dates.today())
                fails = 0
            } else if (r == AdOutcome.WATCH_COMPLETED || r == AdOutcome.PAGE_RETURNED) {
                // App 已返回广告页，视为一次观看被记账（奖励另行确认）
                RunStore.bumpAdDone(svc, Dates.today())
                fails = 0
            } else {
                fails++
            }

            delay(1200)
            if (!tabsVisible() && !openAdsTab()) break
        }
        Logger.i("AD", "section done ran=$ran confirmed=${tasks.count { it.state == TaskState.SUCCESS }}")
    }

    private suspend fun waitAdFinish(): AdOutcome {
        val (w, h) = screen()
        val start = System.currentTimeMillis()
        var sawEndCard = false
        var sawReward = false
        var rewardDismissTries = 0
        var closeAttempts = 0

        while (System.currentTimeMillis() - start < 90_000) {
            if (stop) {
                Logger.w("AD", "stop requested during ad")
                return AdOutcome.PAGE_RETURNED
            }
            val elapsed = System.currentTimeMillis() - start
            val pkgNow = ui.currentPackage()

            if (pkgNow != null && pkgNow != pkg) {
                if (pkgNow == "com.android.vending") {
                    // 广告 CTA 可能把 Play 商店整个拉起：先返回，仍在则点顶部关闭工作表
                    Logger.i("AD", "play store -> back")
                    ui.back()
                    delay(1600)
                    if (ui.currentPackage() == "com.android.vending") {
                        Logger.i("AD", "still vending -> close sheet")
                        ui.tap(w / 2, (h * 0.12).toInt())
                    }
                } else {
                    Logger.i("AD", "external app $pkgNow -> back")
                    ui.back()
                }
                delay(2000)
                continue
            }

            val labels = ui.snapshot().map { ui.label(it) }.filter { it.isNotEmpty() }

            if (labels.any { it.contains("看下一个") }) {
                sawEndCard = true
                Logger.i("AD", "tap 看下一个")
                ui.find(listOf("看下一个"))?.let { ui.click(it) }
                delay(3200)
                continue
            }
            if (labels.any { it.contains("关闭工作表") }) {
                Logger.i("AD", "close sheet node")
                ui.tap(w / 2, (h * 0.12).toInt())
                delay(1800)
                continue
            }
            if (labels.any { it.contains("一探究竟") }) sawEndCard = true

            if (hasBoostDialog(labels)) {
                Logger.i("AD", "boost dialog visible -> join confirmed")
                sawReward = true
                return AdOutcome.REWARD_CONFIRMED
            }

            if (hasRewardPopup(labels)) {
                if (!sawReward) Logger.i("AD", "reward popup detected -> reward confirmed by app UI")
                sawReward = true
                // 该弹窗在 WebView 里，节点不可点；最多试 2 次后直接用返回键退出，
                // 否则会一直命中同一个弹窗空耗到 90s 超时。
                if (rewardDismissTries < 2) {
                    rewardDismissTries++
                    val btn = ui.find(listOf("OK", "受け取る", "閉じる"))
                    if (btn != null) ui.click(btn) else ui.tap(w / 2, (h * 0.60).toInt())
                    delay(1200)
                } else {
                    Logger.i("AD", "reward popup persists -> back")
                    ui.back()
                    delay(1200)
                    if (hasRewardPopup(ui.snapshot().map { ui.label(it) })) {
                        ui.back()
                        delay(1000)
                    }
                    return AdOutcome.REWARD_CONFIRMED
                }
                continue
            }

            if (tabsVisible()) {
                val dur = System.currentTimeMillis() - start
                // 广告刚关的瞬间 Boost 弹窗还没渲染出来，先等一拍再判断，
                // 否则会把「参加确认弹窗」漏掉（R9 实测 rounds=1 joined=false）。
                delay(2200)
                if (hasBoostDialog(ui.snapshot().map { ui.label(it) })) {
                    Logger.i("AD", "boost dialog appeared after ad -> join confirmed")
                    return AdOutcome.REWARD_CONFIRMED
                }
                Logger.i("AD", "back on ads page after ${dur}ms endCard=$sawEndCard reward=$sawReward")
                return when {
                    sawReward -> AdOutcome.REWARD_CONFIRMED
                    sawEndCard || dur >= 12_000 -> AdOutcome.WATCH_COMPLETED
                    else -> AdOutcome.PAGE_RETURNED
                }
            }

            if (elapsed > 40_000 && closeAttempts < 3) {
                Logger.i("AD", "fallback close ${closeAttempts + 1} at ${elapsed}ms")
                tapTopRightTwice()
                closeAttempts++
                delay(2200)
                if (!tabsVisible()) {
                    ui.back()
                    delay(1000)
                }
                continue
            }
            delay(1800)
        }

        if (hasRewardPopup(ui.snapshot().map { ui.label(it) })) {
            Logger.i("AD", "reward popup at timeout -> confirmed")
            ui.find(listOf("OK", "受け取る"))?.let { ui.click(it) }
            delay(1200)
            return AdOutcome.REWARD_CONFIRMED
        }
        Logger.w("AD", "ad timeout 90s, escape")
        ui.back()
        delay(700)
        ui.back()
        return AdOutcome.TIMEOUT
    }

    /**
     * 关闭广告。
     *
     * 各家广告的关闭按钮都在右上角但位置有差异：
     * 五行广告实测 × 在 (0.94w, 0.051h)，Pangle 是「转圈→×」两步。
     * 单点一个坐标经常点不中，导致广告关不掉、也回不到详情页触发 Boost 弹窗。
     */
    private suspend fun tapTopRightTwice() {
        val (w, h) = screen()
        val candidates = listOf(
            (w * 0.94).toInt() to (h * 0.051).toInt(),
            (w * 0.917).toInt() to (h * 0.076).toInt(),
            (w * 0.88).toInt() to (h * 0.10).toInt()
        )
        for ((x, y) in candidates) {
            ui.tap(x, y)
            delay(1100)
            val pkgNow = ui.currentPackage().orEmpty()
            // 已经离开广告页就不用继续试
            if (pkgNow.isNotEmpty() && !pkgNow.contains("ad") && pkgNow != "com.android.chrome") {
                if (pkgNow == pkg) return
            }
        }
        // 两步关闭（转圈变 × 的场景）
        ui.tap(candidates[0].first, candidates[0].second)
        delay(900)
    }

    // -------------------------------------------------------- 挑战任务

    /**
     * 切到「チャレンジ」标签并等列表真正出现。
     *
     * 原来点完只等 1.7s 就断言，广告返回后页面重载较慢时会连续失败
     * （真机实测 14:08 那轮 4 张卡全部 challenge_tab_unreachable）。
     */
    private suspend fun ensureChallengeTab(): Boolean {
        val (w, h) = screen()
        fun listReady(): Boolean {
            val l = ui.activeLabels(pkg = pkg)
            return l.contains("参加可能") || l.contains("参加中") || l.contains("履歴")
        }

        if (listReady()) {
            Logger.i("CHAL", "challenge tab ok=true (already)")
            return true
        }

        // 冷启动后 App 停在「ホーム」页，顶部没有 チャレンジ tab，
        // 必须先点底部「ためる」（R18 实测：直接点顶部会 challenge_tab_unreachable）。
        if (!tabsVisible()) {
            Logger.i("CHAL", "challenge tab: not on earn page -> go earn first")
            goEarnTab()
            delay(1200)
        }

        ui.find(listOf("チャレンジ"), maxTop = 320)?.let { ui.click(it) } ?: ui.tap(
            (w * 0.083).toInt(),
            (h * 0.145).toInt()
        )
        repeat(8) {
            delay(1000)
            if (listReady()) {
                Logger.i("CHAL", "challenge tab ok=true")
                return true
            }
        }
        // 再点一次（可能第一次落在动画中）
        ui.tap((w * 0.083).toInt(), (h * 0.145).toInt())
        repeat(4) {
            delay(1000)
            if (listReady()) {
                Logger.i("CHAL", "challenge tab ok=true (retry)")
                return true
            }
        }
        Logger.i("CHAL", "challenge tab ok=false")
        return false
    }

    /**
     * 当前日期标签。列表滚动后「今日」可能移出 380..620 的原始位置，
     * 所以不锁死 y 范围，只要求是列表页内最靠上的「今日/明日」。
     */
    private fun dateLabel(): String? {
        val n = ui.activeNodes(pkg = pkg).filter {
            val l = ui.label(it)
            l == "今日" || l == "明日"
        }.minByOrNull { topOf(it) } ?: return null
        return ui.label(n)
    }

    private suspend fun selectDate(dayOffset: Int): Boolean {
        val target = if (dayOffset == 0) "今日" else "明日"
        val (w, h) = screen()
        if (dateLabel() == target) {
            Logger.i("CHAL", "date already $target")
            return true
        }
        val node = ui.activeNodes(pkg = pkg).firstOrNull {
            val l = ui.label(it)
            (l == "今日" || l == "明日") && topOf(it) in 300..700
        }
        if (node != null) ui.click(node) else ui.tap((w * 0.083).toInt(), (h * 0.282).toInt())
        delay(1300)

        val opt = ui.activeNodes(pkg = pkg).firstOrNull { ui.label(it) == target && topOf(it) > 560 }
        if (opt != null) {
            ui.click(opt)
        } else {
            // 弹层位置兜底：选完后必须回读校验
            ui.tap((w * 0.278).toInt(), (h * 0.42).toInt())
        }
        delay(1700)

        val now = dateLabel()
        Logger.i("CHAL", "date select want=$target got=${now ?: "null"}")
        return now == target
    }

    /** 切到「参加可能」子标签，并确认切换成功（日期行出现）。 */
    private suspend fun ensureJoinableTab(): Boolean {
        repeat(2) {
            // 必须先用前台标签确认在列表页，否则可能点到后台窗口的同名节点
            val labels = ui.activeLabels(pkg = pkg)
            if (!(labels.contains("参加可能") || labels.contains("参加中") || labels.contains("履歴"))) {
                return false
            }
            if (labels.contains("今日") || labels.contains("明日")) return true

            val n = ui.find(listOf("参加可能"), minTop = 200, maxTop = 560) ?: return false
            ui.click(n)
            delay(1300)
            val after = ui.activeLabels(pkg = pkg)
            if (after.contains("今日") || after.contains("明日")) return true
        }
        return false
    }

    /** 明日列表首次需要看一条视频解锁；解锁失败则记录并继续（后续扫描会自然失败）。 */
    private suspend fun dismissUnlock() {
        val a = ui.find(listOf("解除する"))
        val b = ui.find(listOf("動画を見てロックを解除"))
        if (a == null && b == null) return
        Logger.i("CHAL", "unlock overlay present")
        a?.let { ui.click(it); delay(1600) }
        ui.find(listOf("動画を見てロックを解除"))?.let { ui.click(it); delay(2600) }
        val r = waitAdFinish()
        Logger.i("CHAL", "unlock ad outcome=$r")
        delay(1500)
        ui.find(listOf("閉じる"))?.let { ui.click(it); delay(800) }
    }

    private fun readCards(): List<CardHit> {
        val out = ArrayList<CardHit>()
        val (_, h) = screen()
        for (n in ui.snapshot()) {
            val l = ui.label(n)
            if (l.isEmpty()) continue
            val m = cardRe.find(l) ?: continue
            val cat = Cat.ofJp(m.groupValues[1]) ?: continue
            val km = m.groupValues[2].toIntOrNull() ?: continue
            val top = topOf(n)
            // 排除顶部标签栏与底部广告条区域，避免点到广告
            if (top < 300 || top > (h * 0.86).toInt()) continue
            out.add(CardHit(cat, km, n))
        }
        if (out.isEmpty()) dumpUi("nocards")
        return out
    }

    private fun detailMatch(): Pair<Cat, Int>? {
        val (_, h) = screen()
        val cands = ui.snapshot().mapNotNull { n ->
            val l = ui.label(n)
            if (l.isEmpty()) return@mapNotNull null
            val m = detailRe.find(l) ?: return@mapNotNull null
            val cat = Cat.ofJp(m.groupValues[1]) ?: return@mapNotNull null
            val km = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            Triple(cat, km, topOf(n))
        }.filter { it.third in 0..(h * 0.55).toInt() }
        val first = cands.minByOrNull { it.third } ?: return null
        return first.first to first.second
    }

    private suspend fun dismissOk() {
        val (_, h) = screen()
        ui.find(listOf("OK", "閉じる"), minTop = (h * 0.45).toInt())?.let {
            ui.click(it)
            delay(800)
        }
    }

    /**
     * 打开卡片并领取。只有详情页类别+公里数校验通过才允许点击「10pt に参加予約」，
     * 只有出现预约成功文案才返回 SUCCESS。
     */
    private suspend fun claimCard(hit: CardHit): ClaimResult {
        Logger.i("CHAL", "open ${hit.cat.jp} ${hit.km}km")
        ui.click(hit.node)
        delay(2400)

        val d = detailMatch()
        if (d == null) {
            Logger.w("CHAL", "detail unverified -> abort (no blind claim)")
            ui.back()
            delay(1200)
            return ClaimResult.FAIL
        }
        if (d.first != hit.cat || d.second != hit.km) {
            Logger.w("CHAL", "detail mismatch want=${hit.cat.jp}${hit.km} got=${d.first.jp}${d.second}")
            ui.back()
            delay(1200)
            return ClaimResult.FAIL
        }
        Logger.i("CHAL", "detail verified ${d.first.jp} ${d.second}km")

        val labels = ui.snapshot().map { ui.label(it) }
        if (labels.any { it.contains("参加予約日") || it.contains("参加済み") || it.contains("エントリー済み") || it.contains("参加中") }) {
            Logger.i("CHAL", "already joined/reserved")
            ui.back()
            delay(1200)
            return ClaimResult.ALREADY
        }

        val (w, h) = screen()
        ui.swipe(w / 2, (h * 0.78).toInt(), w / 2, (h * 0.45).toInt(), 300)
        delay(900)

        var ten = findFreeJoinButton()
        if (ten == null) {
            // 免费入口可能在广告条下方：把弹层内容向上滚一屏再找
            Logger.i("CHAL", "free join not visible -> scroll up inside sheet")
            ui.swipe(w / 2, (h * 0.72).toInt(), w / 2, (h * 0.42).toInt(), 420)
            delay(1100)
            ten = findFreeJoinButton()
        }
        if (ten == null) {
            Logger.w("CHAL", "free join button not found")
            dumpUi("no10pt")
            ui.back()
            delay(1200)
            return ClaimResult.FAIL
        }
        dumpUi("detail")
        val label = ui.label(ten)
        val (_, sh) = screen()
        val tr = Rect()
        ten.getBoundsInScreen(tr)
        val tx = tr.centerX()
        val ty = tr.centerY()
        Logger.i("CHAL", "tap10pt label='$label' at ($tx,$ty) screenH=$sh")
        // 坐标点击优先：WebView 虚拟节点的 ACTION_CLICK 可能上溯到卡片容器，触发广告
        ui.tap(tx, ty)

        var ok = false
        var dup = false
        var confirm = false
        var intercepted = ""
        for (i in 0 until 8) {
            delay(900)
            val pkgNow = ui.currentPackage()
            if (pkgNow != null && pkgNow != pkg) {
                intercepted = pkgNow
                Logger.w("CHAL", "tap10pt intercepted by $pkgNow")
                break
            }
            val after = ui.snapshot().map { ui.label(it) }
            if (after.any {
                    it.contains("参加予約ができました") || it.contains("参加が完了") ||
                        it.contains("予約が完了") || it.contains("エントリーが完了") ||
                        it.contains("参加登録が完了")
                }) {
                ok = true
                break
            }
            if (after.any { it.contains("既に参加") || it.contains("参加済み") }) {
                dup = true
                break
            }
            if (!confirm) {
                val btn = ui.find(listOf("参加する", "予約する", "エントリーする"), minTop = (sh * 0.40).toInt())
                if (btn != null) {
                    Logger.i("CHAL", "confirm dialog -> '${ui.label(btn)}'")
                    ui.click(btn)
                    confirm = true
                }
            }
        }
        if (intercepted.isNotEmpty()) {
            dumpUi("intercepted")
            ui.back()
            delay(1200)
            return ClaimResult.FAIL
        }
        if (ok) dumpUi("success")
        dismissOk()
        ui.back()
        delay(1300)
        return when {
            ok -> ClaimResult.SUCCESS
            dup -> ClaimResult.ALREADY
            else -> ClaimResult.FAIL
        }
    }

    /**
     * 找「免费参加」入口（页面上的「または Npt に参加」）。
     *
     * 真机实测（2026-10-26，720x1650）：
     *   1) 详情页底部常驻 [広告] 横幅，会盖住「10pt に参加」的可视区域；
     *   2) 该弹层 WebView 节点极多，自建 snapshot 会被上限截断，
     *      「または / 10pt」根本不在结果里 —— 必须用系统文本搜索。
     */
    private fun findFreeJoinButton(): AccessibilityNodeInfo? {
        val (_, h) = screen()

        // 双路取节点：系统文本搜索 + 自建遍历。二者在不同页面各有盲区，
        // 真机实测 findByText 在部分 WebView 快照里返回空，必须互补。
        fun nodesWith(text: String): List<AccessibilityNodeInfo> {
            val a = ui.findByText(text)
            if (a.isNotEmpty()) return a
            return ui.snapshot().filter { ui.label(it).contains(text) }
        }

        // 取最靠下的「または」（报酬区也有一个，那在上半屏）
        val orNode = nodesWith("または")
            .filter { topOf(it) > (h * 0.45).toInt() }
            .maxByOrNull { topOf(it) }

        if (orNode != null) {
            val oy = topOf(orNode)
            val cands = nodesWith("pt").filter { n ->
                val l = ui.label(n)
                val t = topOf(n)
                l.isNotEmpty() && !l.contains("動画") && !l.contains("広告") &&
                    t > oy && t < oy + 320
            }
            cands.minByOrNull { topOf(it) }?.let {
                Logger.i("CHAL", "free join via または: '${ui.label(it)}' @${topOf(it)}")
                return it
            }
        }

        // 兜底：直接找下半屏的「に参加」
        return nodesWith("に参加")
            .filter { n ->
                val l = ui.label(n)
                l.isNotEmpty() && !l.contains("動画") && !l.contains("広告") &&
                    topOf(n) > (h * 0.45).toInt()
            }
            .minByOrNull { topOf(it) }
    }

    /** 参加后的 Boost 询问弹窗：「チャレンジをはじめました！N回目のブーストをしますか？」 */
    private fun hasBoostDialog(labels: List<String>): Boolean =
        labels.any { it.contains("チャレンジをはじめました") } ||
            labels.any { it.contains("ブーストをしますか") }

    /**
     * 处理「参加成功 + Boost 询问」弹窗。
     *
     * 真机实测完整链路（2026-10-26 手动验证）：
     *   详情页点「動画を見て Npt〜に参加」→ 播五行广告（约 40-45s，右上角 × 关闭）
     *   → 弹「チャレンジをはじめました！1回目のブーストをしますか？」
     *   → 点「ブーストしない」→ 弹「達成目指してがんばりましょう」+ 横幅广告 → 点 OK
     *   → 卡片从「参加可能」列表消失（参加成功的唯一硬证据）
     *
     * @param doBoost 是否继续看广告刷 Boost（默认 false，符合「不做未授权广告」原则）
     * @return true 表示看到「チャレンジをはじめました」，即参加已被 App 确认
     */
    private suspend fun handleBoostDialog(doBoost: Boolean): Boolean {
        val (w, h) = screen()
        var joined = false
        var rounds = 0
        var idle = 0

        // 广告关闭后 Boost 弹窗有 1-3 秒延迟，必须等它出现再判断，
        // 否则会像 R9 那样「rounds=1 joined=false」直接错过确认弹窗。
        delay(2500)

        while (rounds < 12) {
            rounds++
            val labels = ui.snapshot().map { ui.label(it) }
            if (labels.any { it.contains("チャレンジをはじめました") }) joined = true

            if (!hasBoostDialog(labels)) {
                val okBtn = ui.find(listOf("OK"), exact = true)
                if (okBtn == null) {
                    idle++
                    if (idle >= 3 && joined) break
                    if (idle >= 5) break
                    delay(1200)
                    continue
                }
            }
            idle = 0

            if (hasBoostDialog(labels)) {
                if (doBoost) {
                    val boostBtn = ui.find(listOf("動画を見て報酬をブースト"))
                    if (boostBtn != null) {
                        Logger.i("CHAL", "boost: watch ad to boost")
                        ui.click(boostBtn)
                        delay(2500)
                        val o = waitAdFinish()
                        Logger.i("CHAL", "boost ad outcome=$o")
                        continue
                    }
                }
                val skip = ui.find(listOf("ブーストしない"))
                if (skip != null) {
                    Logger.i("CHAL", "boost: skip")
                    ui.click(skip)
                } else {
                    Logger.w("CHAL", "boost: skip button missing -> tap fallback")
                    ui.tap(w / 2, (h * 0.46).toInt())
                }
                delay(2200)
                continue
            }

            // 「達成目指してがんばりましょう」这类确认弹窗
            val ok = ui.find(listOf("OK"), exact = true)
            if (ok != null) {
                ui.click(ok)
                delay(1400)
                continue
            }
            break
        }
        Logger.i("CHAL", "boost dialog handled rounds=$rounds joined=$joined")
        return joined
    }

    /**
     * 点视频参加按钮「動画を見て Npt〜に参加」。
     *
     * 真机像素实测：蓝色主按钮在 y≈1225-1320（0.74h-0.80h），
     * 位于底部广告横幅（y≈1420-1515）上方，不会被遮挡。
     * 该按钮同时是「最高 Boost 档位」的入口（看广告参加高档位）。
     */
    private suspend fun tapVideoJoin(): Boolean {
        val (w, h) = screen()
        val n = ui.find(
            listOf("動画を見て"),
            minTop = (h * 0.60).toInt(),
            maxTop = (h * 0.90).toInt()
        )
        if (n != null) {
            val r = Rect()
            n.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) {
                Logger.i("CHAL", "tapVideoJoin via node '${ui.label(n)}' at (${r.centerX()},${r.centerY()})")
                ui.tap(r.centerX(), r.centerY())
                return true
            }
        }
        // 兜底：固定比例（真机实测蓝色按钮中心 y≈0.771h）
        val x = (w * 0.52).toInt()
        val y = (h * 0.771).toInt()
        Logger.i("CHAL", "tapVideoJoin via fixed ($x,$y)")
        ui.tap(x, y)
        return true
    }

    private fun bottomOf(n: AccessibilityNodeInfo): Int {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r.bottom
    }

    private suspend fun scrollTop() {
        val (w, h) = screen()
        repeat(3) {
            ui.swipe(w / 2, (h * 0.30).toInt(), w / 2, (h * 0.85).toInt(), 220)
            delay(400)
        }
        delay(400)
    }

    private suspend fun runChallengeDay(dayOffset: Int, tasks: List<AutoTask>) {
        val label = if (dayOffset == 0) "今日" else "明日"
        Logger.i("CHAL", "=== day=$label tasks=${tasks.map { "${it.cat?.name}<=${it.km}" }} ===")

        if (!tabsVisible()) {
            Logger.w("CHAL", "not on app tabs -> recover before challenge")
            recoverToEarnTab()
        }
        if (!ensureChallengeTab()) {
            failAll(tasks, "challenge_tab_unreachable")
            return
        }
        if (!selectDate(dayOffset)) {
            failAll(tasks, "date_select_failed")
            return
        }
        ensureJoinableTab()
        dismissUnlock()

        val attempted = HashSet<String>()
        val claimed = HashMap<String, Int>()
        val errors = HashMap<String, String>()

        var pass = 0
        while (pass < 2 && !stop) {
            pass++
            scrollTop()
            var screens = 0
            while (screens < 12 && !stop) {
                val pend = tasks.filter { (claimed[it.id] ?: 0) == 0 && it.state == TaskState.PENDING }
                if (pend.isEmpty()) break

                val cards = readCards()
                val hit = cards.firstOrNull { c ->
                    "${c.cat.name}|${c.km}" !in attempted &&
                        pend.any { it.cat == c.cat && c.km <= (it.km ?: 0) }
                }
                if (hit == null) {
                    val (w, h) = screen()
                    ui.swipe(w / 2, (h * 0.80).toInt(), w / 2, (h * 0.32).toInt(), 320)
                    delay(900)
                    screens++
                    continue
                }

                attempted.add("${hit.cat.name}|${hit.km}")
                val owner = tasks.first { it.cat == hit.cat && hit.km <= (it.km ?: 0) }
                val res = claimCard(hit)
                when (res) {
                    ClaimResult.SUCCESS -> claimed[owner.id] = (claimed[owner.id] ?: 0) + 1
                    ClaimResult.UNCONFIRMED -> errors[owner.id] = "unconfirmed_${hit.km}km"
                    ClaimResult.ALREADY -> {
                        owner.state = TaskState.ALREADY_DONE
                        owner.note = "已参加/已预约 ${hit.cat.jp} ${hit.km}km"
                    }
                    ClaimResult.FAIL -> errors[owner.id] = "claim_failed_${hit.km}km"
                }
                Logger.i("CHAL", "card ${hit.cat.jp}${hit.km}km -> $res")
            }
            if (tasks.none { (claimed[it.id] ?: 0) == 0 && it.state == TaskState.PENDING }) break
        }

        for (t in tasks) {
            val c = claimed[t.id] ?: 0
            when {
                c > 0 -> {
                    t.state = TaskState.SUCCESS
                    t.note = "claimed=$c"
                }
                t.state == TaskState.ALREADY_DONE -> Unit
                else -> {
                    t.state = TaskState.FAILED
                    t.error = errors[t.id] ?: "no_matching_card"
                }
            }
            t.finishedAt = System.currentTimeMillis()
            Logger.i("CHAL", "task=${t.id} state=${t.state} note=${t.note} err=${t.error}")
        }
    }

    private fun failAll(tasks: List<AutoTask>, reason: String) {
        for (t in tasks) {
            t.state = TaskState.FAILED
            t.error = reason
            t.finishedAt = System.currentTimeMillis()
            Logger.w("CHAL", "task=${t.id} failed: $reason")
        }
    }

    // ================================================== v10 卡片级发现与领取

    private data class FoundCard(val card: ChallengeCard, val screen: Int)

    /** 从列表顶部逐屏扫描到末尾，收集所有可读挑战卡片（含所在屏序号）。 */
    private suspend fun discoverAllCards(): List<FoundCard> {
        val (w, h) = screen()
        val out = ArrayList<FoundCard>()
        val seen = HashSet<String>()
        var screenIdx = 0
        var emptyStreak = 0
        var sameStreak = 0
        var prevSig = ""

        if (!ensureListPage()) {
            Logger.w("CHAL", "discover: cannot reach list page")
            return out
        }
        // 无障碍服务刚重连（例如刚装完 APK）时 rootInActiveWindow 可能为 null，
        // snapshot 会返回空列表，扫描会在 8 秒内空转退出（R10 实测 discovered=0）。
        var treeReady = false
        for (i in 0 until 12) {
            if (ui.activeNodes(pkg = pkg).isNotEmpty()) { treeReady = true; break }
            delay(600)
        }
        if (!treeReady) {
            Logger.w("CHAL", "discover: accessibility tree empty after 7s")
            return out
        }
        scrollToTop()
        // 回顶后列表可能还在 overscroll 回弹，立刻滑动会被回弹吃掉（R13 实测滚动全失效）
        delay(1300)

        while (screenIdx < 40 && !stop) {
            // 只用前台窗口的节点：snapshot() 会把后台残留页面的节点也带进来
            var nodes = ui.activeNodes(pkg = pkg)

            var added = 0
            for (n in nodes) {
                val label = ui.label(n)
                if (label.isEmpty()) continue
                val top = topOf(n)
                if (top < 300 || top > (h * 0.88).toInt()) continue
                val card = Cards.fromTitle(label) ?: continue
                if (seen.add(card.key)) {
                    out.add(FoundCard(card, screenIdx))
                    added++
                }
            }
            val sig = nodes.joinToString("|") { ui.label(it) }
            Logger.i(
                "CHAL",
                "scan[$screenIdx] nodes=${nodes.size} labels=${nodes.count { ui.label(it).isNotEmpty() }} " +
                    "added=$added emptyStreak=$emptyStreak sigLen=${sig.length} same=${sig == prevSig}"
            )
            if (screenIdx == 0) {
                // 首屏把 ANA 窗口里的文本打出来，便于判断到底停在哪
                val sample = ui.activeLabels(pkg = pkg).take(24)
                Logger.i("CHAL", "screen0 labels: " + sample.joinToString(" | "))
            }
            if (added == 0) emptyStreak++ else emptyStreak = 0
            val same = sig == prevSig
            prevSig = sig

            // 到底判定不能只看「一屏没变化」：dispatchGesture 的滑动本身就不稳定，
            // 实测会出现「1 次生效 1 次不生效」交替（R16: sigLen 1714→1714→1739→1739），
            // 如果按 same 就 break，会停在第 3 屏而卡片其实在第 7-8 屏。
            if (same) {
                sameStreak++
            } else {
                sameStreak = 0
            }
            if (sameStreak >= 2) {
                // 滚不动了：先确认是不是页面被切走了（而不是真的到底）
                val pt = pageType()
                if (pt != "CHALLENGE_LIST") {
                    Logger.w("CHAL", "scan[$screenIdx]: stuck on $pt -> recover")
                    if (!ensureListPage()) break
                    ensureJoinableTab()
                    sameStreak = 0
                    delay(900)
                    continue
                }
            }
            if (sameStreak >= 4) {
                Logger.i("CHAL", "scan: no movement for $sameStreak screens -> end")
                break
            }

            // 滚动策略（真机实测 2026-10-26）：
            //   - 短 duration 的 swipe（≈340ms）走右边距最可靠，WebView 会当成 fling；
            //   - 700ms 的慢速拖动 WebView 完全不响应（bounds 一点不变）；
            //   - scrollForward 返回 true 也可能没真滚，只作兜底。
            val sx = (w * 0.97).toInt()
            var moved = ui.swipe(sx, (h * 0.72).toInt(), sx, (h * 0.44).toInt(), 340)
            if (!moved) {
                delay(1200)
                moved = ui.scrollForward(pkg = pkg)
            }
            delay(900)
            screenIdx++

            // 滑动偶尔会被 WebView 当成点击而打开详情页：发现就退回来
            if (onDetailPage()) {
                Logger.w("CHAL", "discover: stray detail page -> back")
                ui.back()
                delay(1300)
            }
        }
        dumpUi("list_end")
        return out
    }

    private fun cardsJson(list: List<ChallengeCard>): String {
        val arr = org.json.JSONArray()
        for (c in list) {
            val o = org.json.JSONObject()
            o.put("cat", c.cat.name)
            o.put("title", c.title)
            o.put("meters", c.meters ?: org.json.JSONObject.NULL)
            o.put("distance", c.distanceLabel)
            arr.put(o)
        }
        return arr.toString(2)
    }

    private fun observeDetail(
        target: ChallengeCard? = null,
        visual: DetailVisual = DetailVisual(),
        elapsedMs: Long = 0
    ): DetailObservation {
        return detailEvaluator.evaluate(ui.detailWindows(), target, visual, elapsedMs)
    }

    /**
     * 详情距离来自前台完整标题，而非假设「徒歩/1/km」在同一个节点。
     * 真机已证明三者是独立 TextView，旧算法因此稳定返回 null。
     */
    private fun detailMeters(): Pair<Cat, Int>? {
        val obs = observeDetail()
        if (obs.state != DetailState.DETAIL_READY) return null
        val card = obs.actualCard ?: return null
        val meters = card.meters ?: return null
        return card.cat to meters
    }

    /** 扫描/返回阶段只读窗口语义，不额外截图；OPENING 也视为详情仍在打开。 */
    private fun onDetailPage(): Boolean {
        return when (observeDetail().state) {
            DetailState.DETAIL_OPENING,
            DetailState.DETAIL_READY,
            DetailState.DETAIL_VISIBLE_TREE_MISSING -> true
            else -> false
        }
    }

    /** 点击后的有界分阶段轮询，READY 需连续两次一致。 */
    private suspend fun awaitDetailReady(
        target: ChallengeCard,
        baseline: DetailVisual
    ): DetailObservation {
        val start = System.currentTimeMillis()
        var readyStreak = 0
        var last = observeDetail(target, baseline, 0)
        while (System.currentTimeMillis() - start < 12_000L && !stop) {
            val elapsed = System.currentTimeMillis() - start
            val visual = ui.captureVisual(baseline).visual
            val obs = observeDetail(target, visual, elapsed)
            last = obs
            lastDetailObservation = obs
            Logger.i("DETAIL", obs.logLine(elapsed))
            when (obs.state) {
                DetailState.DETAIL_READY -> {
                    readyStreak++
                    if (readyStreak >= 2) return obs
                }
                DetailState.WRONG_PAGE,
                DetailState.DETAIL_VISIBLE_TREE_MISSING -> {
                    dumpDetailEvidence("detail_${obs.state.name.lowercase()}", obs)
                    return obs
                }
                DetailState.CHALLENGE_LIST -> {
                    readyStreak = 0
                    if (elapsed >= 3_500L) {
                        val noResponse = obs.copy(reason = "noOpenResponseWithin3500ms")
                        lastDetailObservation = noResponse
                        dumpDetailEvidence("detail_no_open_response", noResponse)
                        return noResponse
                    }
                }
                else -> readyStreak = 0
            }
            delay(300)
        }
        val timeout = last.copy(
            state = if (last.state == DetailState.DETAIL_VISIBLE_TREE_MISSING) {
                DetailState.DETAIL_VISIBLE_TREE_MISSING
            } else DetailState.UNKNOWN,
            reason = "detailOpenTotalTimeout:last=${last.state}:${last.reason}"
        )
        lastDetailObservation = timeout
        dumpDetailEvidence("detail_open_timeout", timeout)
        return timeout
    }

    /** 保存逐窗口文本和截图，失败现场不再只剩一句 card_open_failed。 */
    private suspend fun dumpDetailEvidence(tag: String, obs: DetailObservation): String {
        var screenshot = ""
        try {
            val now = System.currentTimeMillis()
            val capture = ui.captureVisual(includePng = true)
            capture.png?.let {
                screenshot = "${tag}_${now}.png"
                Evidence.writeBytes(svc, screenshot, it)
            }
            val windows = ui.detailWindows()
            val body = StringBuilder(obs.logLine(0)).append('\n')
            for ((i, w) in windows.withIndex()) {
                body.append("window[$i] pkg=${w.packageName} active=${w.active} focused=${w.focused}")
                    .append(" layer=${w.layer} type=${w.type} nodes=${w.nodeCount}\n")
                body.append("  labels=").append(w.labels.take(70).joinToString(" | ")).append('\n')
            }
            Evidence.write(svc, "${tag}_${now}.txt", body.toString())
        } catch (t: Throwable) {
            Logger.w("DETAIL", "dump evidence failed: ${t.message}")
        }
        return screenshot
    }

    /**
     * 列表页特征：顶部有 参加可能 / 参加中 / 履歴 三个 tab。
     * 三者任一存在都算「在列表页」——只认「参加可能」会误判为不在列表，
     * 因为上一次操作可能把 tab 停在了「参加中」。
     */
    private fun hasJoinableTab(): Boolean {
        val labels = ui.activeLabels(pkg = pkg)
        return labels.contains("参加可能") || labels.contains("参加中") || labels.contains("履歴")
    }

    /**
     * 前台页面类型。
     *
     * v10 需求要求「准确区分 ANA 主页面 / チャレンジ列表 / 详情 / 广告 / 外部页面」，
     * 否则一次误触跳转（例如点进「所有ポイント」明细）就会让整轮扫描读到 0 张卡片。
     */
    private fun pageType(): String {
        when (observeDetail().state) {
            DetailState.DETAIL_OPENING,
            DetailState.DETAIL_READY,
            DetailState.DETAIL_VISIBLE_TREE_MISSING -> return "CHALLENGE_DETAIL"
            else -> Unit
        }
        val labels = ui.activeLabels(pkg = pkg)
        if (labels.contains("参加可能")) return "CHALLENGE_LIST"
        if (labels.any { it.contains("チャレンジをはじめました") || it.contains("ブーストをしますか") }) {
            return "BOOST_DIALOG"
        }
        if (labels.any { it == "参加する" || it == "詳細" || it.startsWith("報酬") }) {
            return "CHALLENGE_DETAIL"
        }
        if (labels.any { it == "所有ポイント" || it.contains("ポイント履歴") }) return "POINT_HISTORY"
        if (labels.contains("広告視聴") && labels.contains("チャレンジ")) return "APP_HOME"
        if (HomeNavigationPolicy.nativeHome(ui.currentPackage(),labels)) return "ANA_HOME"
        if (labels.any { it.contains("お使いの端末に必要な機能") } && labels.isEmpty()) return "UNKNOWN"
        return "OTHER"
    }

    /**
     * 确保停在挑战列表页。
     *
     * 注意：如果已经在 ANA 主界面（有 チャレンジ/広告視聴 标签）只是不在列表，
     * 按返回键会把 App 退出，所以必须先判断再决定 back 还是切 tab。
     */
    private suspend fun ensureListPage(): Boolean {
        repeat(4) {
            val t = pageType()
            Logger.i("CHAL", "ensureListPage: page=$t")
            when (t) {
                "CHALLENGE_LIST" -> return true
                "APP_HOME", "ANA_HOME" -> {
                    ensureChallengeTab()
                    if (hasJoinableTab()) return true
                }
                "CHALLENGE_DETAIL", "POINT_HISTORY", "OTHER" -> {
                    // 误触进入的子页面（如「所有ポイント」明细）：返回一级
                    ui.back()
                    delay(1300)
                }
                "BOOST_DIALOG" -> {
                    // 参加确认弹窗还在，先处理掉
                    handleBoostDialog(doBoost = false)
                }
                else -> {
                    if (ui.snapshot().isEmpty()) {
                        delay(900)
                    } else {
                        ui.back()
                        delay(1300)
                    }
                }
            }
        }
        if (!tabsVisible()) recoverToEarnTab()
        if (ensureChallengeTab() && hasJoinableTab()) {
            Logger.i("CHAL", "ensureListPage final=true")
            return true
        }
        // 最后一招：重启目标 App 再进列表
        Logger.w("CHAL", "ensureListPage: relaunch target app")
        ui.launchApp(pkg)
        delay(4000)
        ensureChallengeTab()
        val ok = hasJoinableTab()
        Logger.i("CHAL", "ensureListPage final=$ok")
        return ok
    }

    /**
     * 按用户实际操作方式选择分类图标。
     *
     * 真机布局：分类行位于「今日」同一 y；图标无 text/desc。
     * 归一化手势后（行回到最左，再左滑一次）实测坐标：
     *   徒歩≈0.30w，自転車≈0.46w，車≈0.78w。
     * 选择后必须用列表内容验证，失败返回 false 由调用方回退全列表扫描。
     */
    private suspend fun selectCategoryFilter(cat: Cat): Boolean {
        val (w, _) = screen()
        if (!scrollToTop()) {
            Logger.w("CHAL", "category: scrollToTop failed")
            return false
        }
        delay(600)
        val today = ui.activeNodes(pkg = pkg).firstOrNull { ui.label(it) == "今日" } ?: run {
            Logger.w("CHAL", "category: 今日 row not found")
            return false
        }
        val r = Rect()
        today.getBoundsInScreen(r)
        val rowY = r.centerY()
        // 行回到最左，并清除当前分类筛选。
        repeat(2) {
            ui.swipe((w * 0.20).toInt(), rowY, (w * 0.92).toInt(), rowY, 500)
            delay(450)
        }
        ui.tap((w * 0.35).toInt(), rowY) // 「すべて」
        delay(1400)

        val x = when (cat) {
            Cat.WALK -> (w * 0.30).toInt()
            Cat.BIKE -> (w * 0.46).toInt()
            Cat.CAR -> (w * 0.78).toInt()
            else -> {
                Logger.w("CHAL", "category: ${cat.name} chip mapping unsupported -> all-list fallback")
                return false
            }
        }
        ui.swipe((w * 0.92).toInt(), rowY, (w * 0.20).toInt(), rowY, 500)
        delay(700)
        ui.tap(x, rowY)
        delay(1800)

        val labels = ui.activeLabels(pkg = pkg)
        val ok = when (cat) {
            Cat.WALK -> labels.any { it.contains("徒歩") || it.contains("歩こう") }
            Cat.BIKE -> labels.any { it.contains("自転車") }
            Cat.CAR -> labels.any { it.contains("車、電車") || it.contains("車で") }
            else -> false
        }
        Logger.i("CHAL", "category ${cat.name} select=$ok rowY=$rowY x=$x labels=${labels.take(18)}")
        return ok
    }

    /**
     * 列表滚动：右边缘手势失效时用中心手势兜底，并用内容签名验证是否真的移动。
     *
     * R31 实测：ANA 搭乘/赞助区域对 x≈0.97w 的滑动无响应，
     * 中心 (350,1200)->(350,500) 立即生效；旧逻辑只看“手势已派发”，
     * 会把一次无效滑动误判成到顶或卡死。
     */
    private suspend fun swipeListWithFallback(w: Int, h: Int, towardTop: Boolean): Boolean {
        val before = ui.activeLabels(pkg = pkg).joinToString("|")
        val xr = (w * 0.97).toInt()
        if (towardTop) {
            ui.swipe(xr, (h * 0.34).toInt(), xr, (h * 0.78).toInt(), 300)
        } else {
            ui.swipe(xr, (h * 0.72).toInt(), xr, (h * 0.44).toInt(), 320)
        }
        delay(520)
        var after = ui.activeLabels(pkg = pkg).joinToString("|")
        if (after.isNotEmpty() && after != before) return true

        val xc = w / 2
        if (towardTop) {
            ui.swipe(xc, (h * 0.30).toInt(), xc, (h * 0.80).toInt(), 600)
        } else {
            ui.swipe(xc, (h * 0.72).toInt(), xc, (h * 0.32).toInt(), 600)
        }
        delay(650)
        after = ui.activeLabels(pkg = pkg).joinToString("|")
        val moved = after.isNotEmpty() && after != before
        Logger.i("CHAL", "swipeList towardTop=$towardTop rightEdgeNoMove centerMoved=$moved")
        return moved
    }

    /** 回到列表顶部：内容连续两次不变才算到底，避免单次无效滑动误判。 */
    private suspend fun scrollToTop(): Boolean {
        val (w, h) = screen()
        var prev = ""
        var sameStreak = 0
        // 挑战列表很长（实测 40+ 屏），16 次上滑远不够回到顶部
        repeat(50) {
            val labels = ui.activeLabels(pkg = pkg)
            if (labels.isEmpty()) { delay(500); return@repeat }
            val sig = labels.joinToString("|")
            if (sig == prev) sameStreak++ else sameStreak = 0
            prev = sig
            if (sameStreak >= 2) return true
            val moved = swipeListWithFallback(w, h, towardTop = true)
            if (moved) sameStreak = 0 else sameStreak++
            if (sameStreak >= 2) return true
            delay(350)
        }
        return false
    }

    /**
     * 从列表顶部逐屏下滚，按「分类 + 距离 + 标题」三重校验定位卡片后打开。
     *
     * 为什么不用节点 ACTION_CLICK：WebView 虚拟节点的点击可能上溯到错误容器，
     * v9 就出现过点「10pt」却拉起广告。这里一律用坐标点击，并在点击后校验
     * 是否真的进入了详情页。
     */
    private suspend fun openCardFromTop(cat: Cat, meters: Int, title: String, applyFilter: Boolean = true, resetToTop: Boolean = true): Boolean {
        val (w, h) = screen()
        lastOpenError = "card_open_failed"
        lastDetailObservation = null
        if (!ensureListPage()) return false
        // 按用户实际操作先选分类图标，避免在 ANA 搭乘/赞助等无关区块反复滚动。
        val filtered = if (applyFilter) selectCategoryFilter(cat) else activeFilteredCategory == cat
        Logger.i("CHAL", "category filter ${cat.name} applied=$filtered")
        if (resetToTop) scrollToTop()
        // 回顶后列表仍有 overscroll 回弹；立即读到的 bounds 会随后移动
        // （R30 实测 1km 标题从 y=1077 回弹到 y=989，旧坐标点击未打开）。
        delay(1200)
        var tapAttempts = 0

        repeat(45) {
            if (stop) return false
            val nodes = ui.activeNodes(pkg = pkg)
            // 查找范围放宽到 0.90h（卡片经常落在 y≈1300-1450），
            // 但底部 y≈1342-1410 有常驻提示条，直接点会打开提示。
            // 所以「找到但太靠下」时先把卡片滚进安全区再点。
            val hit = nodes.firstOrNull { n ->
                val l = ui.label(n)
                if (l.isEmpty()) return@firstOrNull false
                val t = topOf(n)
                if (t < 300 || t > (h * 0.90).toInt()) return@firstOrNull false
                val c = Cards.fromTitle(l) ?: return@firstOrNull false
                val sameTitle = l == title || l.contains(title) || title.contains(l)
                c.cat == cat && c.meters == meters && sameTitle
            }
            if (hit != null) {
                delay(320)
                var r = Rect()
                hit.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0) {
                    // 太靠下 → 小幅上滑把它推到安全区
                    if (r.centerY() > (h * 0.74).toInt()) {
                        Logger.i("CHAL", "card too low (${r.centerY()}) -> nudge up")
                        ui.swipe(w / 2, (h * 0.70).toInt(), w / 2, (h * 0.52).toInt(), 500)
                        delay(1000)
                        val again = ui.activeNodes(pkg = pkg).firstOrNull { n ->
                            val l = ui.label(n)
                            if (l.isEmpty()) return@firstOrNull false
                            val t = topOf(n)
                            if (t < 300 || t > (h * 0.90).toInt()) return@firstOrNull false
                            val c = Cards.fromTitle(l) ?: return@firstOrNull false
                            c.cat == cat && c.meters == meters &&
                                (l == title || l.contains(title) || title.contains(l))
                        }
                        if (again != null) {
                            r = Rect()
                            again.getBoundsInScreen(r)
                        }
                    }
                    if (r.centerY() <= (h * 0.74).toInt()) {
                        if (tapAttempts >= 2) {
                            lastOpenError = "card_open_response_exhausted"
                            return false
                        }
                        // 点击前重新取一次节点与坐标，禁止使用滚动/回弹前的旧 bounds。
                        val fresh = ui.activeNodes(pkg = pkg).firstOrNull { n ->
                            val l = ui.label(n)
                            val c = Cards.fromTitle(l)
                            l.isNotEmpty() && c != null && c.cat == cat && c.meters == meters &&
                                (l == title || l.contains(title) || title.contains(l))
                        }
                        if (fresh != null) {
                            val fr = Rect()
                            fresh.getBoundsInScreen(fr)
                            if (fr.width() > 0 && fr.height() > 0) r = fr
                        }
                        if (r.centerY() > (h * 0.74).toInt()) {
                            Logger.w("CHAL", "card moved too low before tap (${r.centerY()}) -> scroll more")
                            ui.swipe((w * 0.97).toInt(), (h * 0.70).toInt(), (w * 0.97).toInt(), (h * 0.44).toInt(), 320)
                            delay(900)
                            return@repeat
                        }
                        tapAttempts++
                        Logger.i("CHAL", "open '$title' at (${r.centerX()},${r.centerY()}) attempt=$tapAttempts")
                        val baseline = ui.captureVisual().visual
                        val semantic = ui.clickSemantic(fresh ?: hit)
                        val dispatched = semantic || ui.tap(r.centerX(), r.centerY())
                        Logger.i("DETAIL", "tap dispatched=$dispatched semantic=$semantic attempt=$tapAttempts")
                        val target = ChallengeCard(cat, title, meters, title)
                        val obs = awaitDetailReady(target, baseline)
                        if (obs.state == DetailState.DETAIL_READY) return true
                        lastOpenError = when (obs.state) {
                            DetailState.DETAIL_VISIBLE_TREE_MISSING -> "detail_visible_tree_missing"
                            DetailState.WRONG_PAGE -> "detail_wrong_page"
                            DetailState.UNKNOWN -> "detail_open_unknown"
                            DetailState.CHALLENGE_LIST -> "card_tap_no_response"
                            else -> "detail_open_timeout"
                        }
                        Logger.w("CHAL", "open not ready state=${obs.state} reason=${obs.reason} error=$lastOpenError")
                        if (obs.state != DetailState.CHALLENGE_LIST) return false
                        if (!ensureListPage()) return false
                        scrollToTop()
                    } else {
                        Logger.w("CHAL", "card still too low (${r.centerY()}) -> scroll more")
                        swipeListWithFallback(w, h, towardTop = false)
                        delay(900)
                    }
                }
            } else {
                val moved = swipeListWithFallback(w, h, towardTop = false)
                if (!moved) {
                    Logger.w("CHAL", "list swipe did not move -> scrollForward fallback")
                    ui.scrollForward(pkg = pkg)
                }
                delay(750)
            }
        }
        dumpUi("card_not_found")
        return false
    }

    private var lastClaimError: String = ""

    /** 已经打开详情页后的领取动作（含校验、点击、三结局判定）。 */
    private suspend fun claimOpenedDetail(cat: Cat, meters: Int): ClaimResult {
        lastClaimError = ""
        val d = detailMeters()
        if (d == null) {
            lastClaimError = "detail_unverified"
            Logger.w("CHAL", "detail unverified -> abort")
            ui.back(); delay(1200)
            return ClaimResult.FAIL
        }
        if (d.first != cat || d.second != meters) {
            lastClaimError = "detail_mismatch_${d.first.name}_${d.second}m"
            Logger.w("CHAL", "detail mismatch want=${cat.name}${meters}m got=${d.first.name}${d.second}m")
            ui.back(); delay(1200)
            return ClaimResult.FAIL
        }
        Logger.i("CHAL", "detail verified ${cat.name} ${Dist.label(meters)}")

        val currentDetail = observeDetail()
        if (currentDetail.alreadyJoined) {
            Logger.i("CHAL", "already joined/reserved")
            ui.back(); delay(1200)
            return ClaimResult.ALREADY
        }

        val (w, h) = screen()
        ui.swipe(w / 2, (h * 0.78).toInt(), w / 2, (h * 0.45).toInt(), 300)
        delay(900)

        // 免费入口「または Npt に参加」在真机上被底部广告横幅（实测 y≈1420-1515）
        // 几乎完全覆盖，没有安全可点区域。先尝试把弹层向上滚，让它移出广告区。
        var ten = findFreeJoinButton()
        var tries = 0
        while (ten == null && tries < 3) {
            tries++
            val ok = ui.swipeSlow(w / 2, (h * 0.66).toInt(), w / 2, (h * 0.40).toInt(), 1400)
            Logger.i("CHAL", "free join hidden -> swipeSlow=$ok ($tries)")
            if (!ok) ui.scrollForward(pkg = pkg)
            delay(1200)
            ten = findFreeJoinButton()
        }

        // 仍找不到 → 走视频入口（蓝色主按钮，未被遮挡）。
        // 这条路线同时是「最高 Boost 档位」的正确入口：看广告参加 100pt 档。
        if (ten == null) {
            Logger.w("CHAL", "free join unavailable -> fall back to video join")
            if (!tapVideoJoin()) {
                lastClaimError = "video_join_button_not_found"
                dumpUi("novideojoin")
                ui.back(); delay(1200)
                return ClaimResult.FAIL
            }
            val adOutcome = waitAdFinish()
            Logger.i("CHAL", "video join ad outcome=$adOutcome")

            // 广告关闭后会出现「チャレンジをはじめました！＋Boost 询问」，
            // 这一步才是 App 对参加的正式确认（真机手动验证过）。
            var joined = handleBoostDialog(doBoost = false)
            if (!joined) {
                for (i in 0 until 5) {
                    delay(900)
                    val labels = ui.snapshot().map { ui.label(it) }
                    if (labels.any {
                            it.contains("参加予約ができました") || it.contains("参加が完了") ||
                                it.contains("予約が完了") || it.contains("エントリーが完了")
                        }) { joined = true; break }
                }
            }
            dismissOk()
            // 重要：广告奖励确认 ≠ 挑战参加成功。
            // 真机实测（2026-10-26）看到「報酬をゲット」后判 SUCCESS，
            // 但卡片仍留在「参加可能」列表里 —— 说明只是看完了广告，没完成参加。
            // 因此这里最多给 UNCONFIRMED，由一轮结束后的列表比对来定论。
            return when {
                joined -> ClaimResult.SUCCESS          // 页面明确回显「参加予約ができました」
                adOutcome == AdOutcome.REWARD_CONFIRMED ||
                    adOutcome == AdOutcome.WATCH_COMPLETED -> {
                    lastClaimError = "join_unverified_after_ad_$adOutcome"
                    ClaimResult.UNCONFIRMED
                }
                else -> { lastClaimError = "video_join_failed_$adOutcome"; ClaimResult.FAIL }
            }
        }
        if (ten == null) {
            lastClaimError = "free_join_not_found"
            Logger.w("CHAL", "free join button not found")
            dumpUi("nojoin")
            ui.back(); delay(1200)
            return ClaimResult.FAIL
        }
        dumpUi("detail")
        val label = ui.label(ten)
        val tr = Rect(); ten.getBoundsInScreen(tr)
        val tx = tr.centerX(); val ty = tr.centerY()
        Logger.i("CHAL", "tapJoin label='$label' at ($tx,$ty) h=${h}")

        // 优先语义点击：免费入口常被底部广告横幅盖住（真机实测坐标点击会打开广告），
        // performAction(ACTION_CLICK) 不经过屏幕坐标，可以绕过遮挡。
        val semOk = ui.clickSemantic(ten)
        Logger.i("CHAL", "tapJoin semantic=$semOk")
        if (!semOk) {
            Logger.w("CHAL", "semantic click failed -> coordinate tap")
            ui.tap(tx, ty)
        }

        var ok = false; var dup = false; var confirm = false; var intercepted = ""
        for (i in 0 until 8) {
            delay(900)
            val pkgNow = ui.currentPackage()
            if (pkgNow != null && pkgNow != pkg) { intercepted = pkgNow; break }
            val after = ui.snapshot().map { ui.label(it) }
            if (after.any {
                    it.contains("参加予約ができました") || it.contains("参加が完了") ||
                        it.contains("予約が完了") || it.contains("エントリーが完了") ||
                        it.contains("参加登録が完了")
                }) { ok = true; break }
            if (after.any { it.contains("既に参加") || it.contains("参加済み") }) { dup = true; break }
            if (!confirm) {
                val btn = ui.find(listOf("参加する", "予約する", "エントリーする"), minTop = (h * 0.40).toInt())
                if (btn != null) { ui.click(btn); confirm = true }
            }
        }
        if (intercepted.isNotEmpty()) {
            lastClaimError = "intercepted_by_$intercepted"
            Logger.w("CHAL", "join tap intercepted by $intercepted")
            dumpUi("intercepted")
            ui.back(); delay(1200)
            return ClaimResult.FAIL
        }
        if (ok) dumpUi("success")
        dismissOk()
        ui.back(); delay(1300)
        return when {
            ok -> ClaimResult.SUCCESS
            dup -> ClaimResult.ALREADY
            else -> { lastClaimError = "no_confirm"; ClaimResult.FAIL }
        }
    }

    /**
     * 用「卡片是否仍出现在参加可能列表」做独立验证。
     *
     * 为什么必须有这一步：2026-10-26 真机实测，走视频入口看完广告后
     * App 弹了「報酬をゲット！」，引擎据此判 SUCCESS，
     * 但卡片仍留在「参加可能」列表里 —— 实际并没有完成参加。
     * 因此「点击成功 / 广告播完 / 广告奖励弹窗」都不能作为参加成功的证据，
     * 唯一可信的判据是：参加后卡片从「参加可能」列表消失。
     */
    private suspend fun verifyByListGone(dayOffset: Int, out: MutableList<AutoTask>) {
        val targets = out.filter {
            it.state == TaskState.UNCONFIRMED || it.state == TaskState.SUCCESS
        }
        if (targets.isEmpty()) return
        Logger.i("CHAL", "=== verify by list-gone: ${targets.size} tasks ===")

        if (!ensureListPage()) {
            Logger.w("CHAL", "verify: list unreachable, keep UNCONFIRMED")
            for (t in targets) if (t.state == TaskState.SUCCESS) {
                t.state = TaskState.UNCONFIRMED
                t.error = "verify_list_unreachable"
            }
            return
        }
        if (!ensureListPage()) {
            Logger.w("CHAL", "verify: list page unreachable after scan")
        }
        if (!ensureJoinableTab()) {
            Logger.w("CHAL", "verify: joinable tab unreachable")
        }
        val want = if (dayOffset == 0) "今日" else "明日"
        val dl = dateLabel()
        if (dl != null && dl != want) selectDate(dayOffset)

        val (w, h) = screen()
        val remaining = HashSet<String>()
        scrollToTop()
        var guard = 0
        while (guard < 24 && !stop) {
            for (n in ui.activeNodes(pkg = pkg)) {
                val l = ui.label(n)
                if (l.isEmpty()) continue
                val c = Cards.fromTitle(l) ?: continue
                remaining.add(c.title)
            }
            ui.swipe((w * 0.97).toInt(), (h * 0.78).toInt(), (w * 0.97).toInt(), (h * 0.36).toInt(), 320)
            delay(900)
            guard++
            if (onDetailPage()) {
                ui.back()
                delay(1200)
            }
        }
        Logger.i("CHAL", "verify: remaining cards=${remaining.size}")

        for (t in targets) {
            val still = remaining.contains(t.title)
            if (still) {
                if (t.state == TaskState.SUCCESS) {
                    t.state = TaskState.UNCONFIRMED
                    Logger.w("CHAL", "${t.id} DOWNGRADED: card still in list")
                } else {
                    Logger.w("CHAL", "${t.id} unconfirmed: card still in list")
                }
                t.error = "card_still_available"
            } else {
                t.state = TaskState.SUCCESS
                t.error = ""
                Logger.i("CHAL", "${t.id} CONFIRMED: card gone from list")
            }
        }
        Evidence.write(
            svc,
            "verify_${System.currentTimeMillis()}.json",
            cardsJson(targets.map { ChallengeCard(it.cat ?: Cat.WALK, it.title, it.meters, it.title) })
        )
    }

    private suspend fun failScopes(scopes: List<AutoTask>, reason: String, out: MutableList<AutoTask>) {
        for (s in scopes) {
            out.add(
                s.copy(
                    id = s.id + "-x",
                    state = TaskState.FAILED,
                    error = reason,
                    finishedAt = System.currentTimeMillis()
                )
            )
            Logger.w("CHAL", "scope ${s.cat?.name} failed: $reason")
        }
    }

    /**
     * v10：按 scope（日期 × 分类）发现全部符合距离上限的卡片，
     * 为每张卡片生成独立任务并逐个领取。
     */
    private suspend fun runChallengeDayV10(dayOffset: Int, scopes: List<AutoTask>, out: MutableList<AutoTask>) {
        val label = if (dayOffset == 0) "今日" else "明日"
        Logger.i("CHAL", "=== day=$label scopes=${scopes.map { "${it.cat?.name}<=${it.maxMeters}m" }} ===")
        if (!tabsVisible()) recoverToEarnTab()
        if (!ensureChallengeTab()) { failScopes(scopes, "challenge_tab_unreachable", out); return }
        // 顺序很重要：日期行只在「参加可能」子标签下才有，
        // 若停在「参加中/履歴」会读不到「今日」→ date_select_failed（R7 实测）。
        if (!ensureJoinableTab()) { failScopes(scopes, "joinable_tab_unreachable", out); return }
        if (!selectDate(dayOffset)) { failScopes(scopes, "date_select_failed", out); return }
        dismissUnlock()

        val found = discoverAllCards()
        Logger.i("CHAL", "discovered=${found.size} :: " + found.joinToString { "${it.card.cat.name}:${it.card.distanceLabel}" })
        Evidence.write(svc, "cards_${label}_${System.currentTimeMillis()}.json", cardsJson(found.map { it.card }))
        if (found.isEmpty()) { failScopes(scopes, "list_unreadable", out); return }

        val allCards = found.map { it.card }
        for (scope in scopes) {
            if (stop) break
            val cat = scope.cat ?: continue
            val sel = Cards.select(allCards, cat, scope.maxMeters)
            val note = "discovered=${sel.discovered} matched=${sel.matched.size} over=${sel.overLimit.size} unknown=${sel.unknown.size}"
            Logger.i("CHAL", "${cat.name} $note")
            if (sel.matched.isEmpty()) {
                out.add(
                    scope.copy(
                        id = "CH-$label-${cat.name}",
                        state = TaskState.FAILED,
                        error = if (sel.discovered == 0) "no_card_found" else "no_card_within_limit",
                        note = note,
                        finishedAt = System.currentTimeMillis()
                    )
                )
                continue
            }
            var seq = 0
            for (card in sel.matched) {
                if (stop) break
                seq++
                val task = AutoTask(
                    id = "CH-$label-${cat.name}-%02d".format(seq),
                    kind = TaskKind.CHALLENGE,
                    dayOffset = dayOffset,
                    date = Dates.of(dayOffset),
                    cat = cat,
                    km = (card.meters ?: 0) / 1000,
                    title = card.title,
                    meters = card.meters ?: 0,
                    maxMeters = scope.maxMeters,
                    startedAt = System.currentTimeMillis()
                )
                task.state = TaskState.RUNNING

                // 每张卡片前重新确认页面状态（可能被广告/弹窗打断）
                if (!ensureListPage()) {
                    if (!tabsVisible()) recoverToEarnTab()
                    if (!ensureChallengeTab()) {
                        task.state = TaskState.FAILED
                        task.error = "challenge_tab_unreachable"
                        task.finishedAt = System.currentTimeMillis()
                        out.add(task)
                        continue
                    }
                }
                ensureJoinableTab()
                val dl = dateLabel()
                if (dl != null && dl != label) selectDate(dayOffset)

                if (!openCardFromTop(cat, card.meters ?: 0, card.title)) {
                    task.state = TaskState.FAILED
                    task.error = lastOpenError
                } else {
                    when (claimOpenedDetail(cat, card.meters ?: 0)) {
                        ClaimResult.SUCCESS -> task.state = TaskState.SUCCESS
                        ClaimResult.ALREADY -> task.state = TaskState.ALREADY_DONE
                        ClaimResult.UNCONFIRMED -> {
                            task.state = TaskState.UNCONFIRMED
                            task.error = lastClaimError
                        }
                        ClaimResult.FAIL -> { task.state = TaskState.FAILED; task.error = lastClaimError }
                    }
                }
                task.finishedAt = System.currentTimeMillis()
                out.add(task)
                Logger.i("CHAL", "${task.id} ${card.distanceLabel} -> ${task.state} ${task.error}")
            }
        }

        // 一轮结束：用列表比对做独立验证，把 UNCONFIRMED 收敛成 SUCCESS / 保持未确认
        verifyByListGone(dayOffset, out)
    }

    // ================================================== 独立详情诊断（绝不领取）

    suspend fun runDetailDiagnostic(runId: String, observeOnly: Boolean): String {
        return if (observeOnly) observeDetailTransitions(runId) else runControlledDetailTrials(runId)
    }

    private fun detailJson(
        runId: String,
        elapsedMs: Long,
        obs: DetailObservation,
        screenshot: String,
        target: ChallengeCard? = null,
        success: Boolean? = null
    ): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("runId", runId)
            put("elapsedMs", elapsedMs)
            put("state", obs.state.name)
            put("reason", obs.reason)
            put("targetTask", target?.title ?: "")
            put("actualTitle", obs.detailTitle)
            put("actualCat", obs.actualCard?.cat?.name ?: "")
            put("actualMeters", obs.actualCard?.meters ?: org.json.JSONObject.NULL)
            put("targetMatched", obs.targetMatched)
            put("foregroundPackage", obs.foregroundPackage)
            put("foregroundNodes", obs.foregroundNodes)
            put("foregroundLayer", obs.foregroundLayer)
            put("foregroundType", obs.foregroundType)
            put("anaWindowCount", obs.anaWindowCount)
            put("backgroundList", obs.backgroundList)
            put("bannerVisible", obs.bannerVisible)
            put("freeJoinNodeVisible", obs.freeJoinNodeVisible)
            put("visualAvailable", obs.visual.available)
            put("meanLuma", obs.visual.meanLuma)
            put("topLuma", obs.visual.topLuma)
            put("bottomLuma", obs.visual.bottomLuma)
            put("dimmed", obs.visual.dimmed)
            put("sheetVisible", obs.visual.sheetVisible)
            put("windowEventAgeMs", obs.windowEventAgeMs)
            put("contentEventAgeMs", obs.contentEventAgeMs)
            put("screenshot", screenshot)
            put("success", success)
        }
    }

    /** 阶段一至四：程序不点击，人工操作，状态迁移逐条留证。 */
    private suspend fun observeDetailTransitions(runId: String): String {
        val start = System.currentTimeMillis()
        val transitions = org.json.JSONArray()
        var lastSignature = ""
        var sequence = 0
        var ready = 0
        var list = 0
        while (System.currentTimeMillis() - start < 60_000L && !stop) {
            val elapsed = System.currentTimeMillis() - start
            val visual = ui.captureVisual().visual
            val obs = observeDetail(visual = visual, elapsedMs = elapsed)
            Logger.i("DIAG", obs.logLine(elapsed))
            val signature = "${obs.state}|${obs.detailTitle}|${obs.targetMatched}|${obs.reason}"
            if (signature != lastSignature) {
                lastSignature = signature
                sequence++
                val shot = dumpDetailEvidence("detail_${runId}_${sequence}_${obs.state.name}", obs)
                transitions.put(detailJson(runId, elapsed, obs, shot))
                Evidence.write(svc, "detail_${runId}_observations.json", transitions.toString(2))
                if (obs.state == DetailState.DETAIL_READY) ready++
                if (obs.state == DetailState.CHALLENGE_LIST) list++
            }
            delay(300)
        }
        return "observe transitions=$sequence ready=$ready list=$list"
    }

    private suspend fun awaitListForDiagnostic(runId: String): DetailObservation {
        val start = System.currentTimeMillis()
        var last = observeDetail()
        while (System.currentTimeMillis() - start < 8_000L) {
            val elapsed = System.currentTimeMillis() - start
            last = observeDetail(elapsedMs = elapsed)
            Logger.i("DIAG", "closeWait ${last.logLine(elapsed)}")
            if (last.state == DetailState.CHALLENGE_LIST) return last
            if (last.state == DetailState.WRONG_PAGE) return last
            delay(300)
        }
        return last.copy(state = DetailState.UNKNOWN, reason = "closeToListTimeout:last=${last.state}")
    }

    /** 阶段五：固定断言为“目标准确 READY + 关闭返回 LIST”，失败即停止。 */
    private suspend fun runControlledDetailTrials(runId: String): String {
        wakeScreen()
        ensureForeground()
        recoverToEarnTab()
        if (!ensureChallengeTab() || !ensureJoinableTab()) return "controlled blocked=listUnreachable"
        val distances = listOf(1, 1, 1, 2, 2, 2, 3, 3, 4, 1)
        val results = org.json.JSONArray()
        var consecutive = 0
        for ((index, km) in distances.withIndex()) {
            if (stop) break
            val cycle = index + 1
            val title = "徒歩で${km}km移動しよう！"
            val target = Cards.fromTitle(title) ?: return "controlled badTarget=$title"
            val start = System.currentTimeMillis()
            Logger.i("DIAG", "trial=$cycle target=$title begin")
            val opened = openCardFromTop(target.cat, target.meters ?: 0, target.title)
            val readyObs = lastDetailObservation ?: observeDetail(target)
            val readyShot = dumpDetailEvidence("detail_${runId}_trial${cycle}_ready", readyObs)
            var closeObs: DetailObservation? = null
            var closeShot = ""
            if (opened && readyObs.state == DetailState.DETAIL_READY) {
                ui.back()
                closeObs = awaitListForDiagnostic(runId)
                closeShot = dumpDetailEvidence("detail_${runId}_trial${cycle}_closed", closeObs)
            }
            val elapsed = System.currentTimeMillis() - start
            val success = opened && readyObs.state == DetailState.DETAIL_READY &&
                readyObs.targetMatched == true && closeObs?.state == DetailState.CHALLENGE_LIST
            val item = detailJson(runId, elapsed, readyObs, readyShot, target, success).apply {
                put("trial", cycle)
                put("closeState", closeObs?.state?.name ?: "NOT_REACHED")
                put("closeReason", closeObs?.reason ?: "openFailed")
                put("closeScreenshot", closeShot)
                put("consecutiveBefore", consecutive)
            }
            results.put(item)
            Evidence.write(svc, "detail_${runId}_trials.json", results.toString(2))
            Logger.i("DIAG", "trial=$cycle success=$success elapsed=$elapsed close=${closeObs?.state}")
            if (!success) {
                return "controlled FAIL trial=$cycle consecutive=$consecutive state=${readyObs.state} close=${closeObs?.state}"
            }
            consecutive++
        }
        return "controlled PASS consecutive=$consecutive"
    }
}
