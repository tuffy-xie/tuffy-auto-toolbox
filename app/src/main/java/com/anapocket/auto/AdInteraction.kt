package com.anapocket.auto

import kotlinx.coroutines.delay

/** Only foreground ANA receipt text and its explicitly labelled watched counter are trusted. */
object AdSignals {
    fun closeLabel(value: String): Boolean = value.trim().lowercase(java.util.Locale.ROOT) in setOf(
        "閉じる", "広告を閉じる", "close", "close ad", "关闭广告", "广告关闭", "×", "✕", "✖", "閉じるボタン", "closebutton", "close_button", "close-button")
    fun pangleNativeClose(activity: String, label: String): Boolean =
        AdSdkWindows.contains(activity) && activity.startsWith("com.bytedance.sdk.openadsdk.activity.") &&
            label.trim() in setOf("default_close", "广告关闭")
    fun unityEndCard(labels: List<String>): Boolean = labels.any { it.trim() == "Close" } && labels.any { it.trim() == "Privacy" }
    fun watched(labels: List<String>): Int? {
        val text = labels.joinToString(" ")
        val m = Regex("今日の視聴済み回数\\s*[:：]?\\s*(\\d+)\\s*/\\s*25").find(text) ?: return null
        return m.groupValues[1].toIntOrNull()?.takeIf { it in 0..25 }
    }
    fun part(labels: List<String>): AdPart? {
        val m = Regex("^Ad\\s+(\\d+)\\s+of\\s+(\\d+)$", RegexOption.IGNORE_CASE)
        return labels.asSequence().mapNotNull { m.matchEntire(it.trim()) }.mapNotNull {
            val current = it.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val total = it.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            if (total in 1..5 && current in 1..total) AdPart(current, total) else null
        }.firstOrNull()
    }
    fun playSheet(packageName: String?, labels: List<String>): Boolean = packageName == "com.android.vending" &&
        labels.any {
            it.trim() in setOf("关闭工作表", "Close sheet", "シートを閉じる")
        }
    fun playHome(packageName: String?, labels: List<String>): Boolean = packageName == "com.android.vending" &&
        labels.map { it.trim() }.distinct().count { it in setOf("游戏", "应用", "搜索", "图书", "我") } >= 3
    fun receipt(labels: List<String>): Boolean = labels.any {
        it.trim() in setOf("報酬をゲット！", "報酬をゲット!", "報酬を受け取りました", "ポイントを獲得しました")
    }
    fun limit(labels: List<String>): Boolean = watched(labels) == 25 || labels.any {
        it.contains("本日の視聴回数の上限") || it.contains("本日の視聴上限") ||
            it.contains("本日は終了") || it.contains("本日の広告視聴は終了")
    }
    fun loadFailure(labels: List<String>): Boolean = labels.any { it.trim().startsWith("広告表示に失敗しました") }
    fun error(labels: List<String>): Boolean = loadFailure(labels) || labels.any {
        it.contains("広告を読み込めません") || it.contains("広告の読み込みに失敗") ||
            it.contains("通信エラー") || it.contains("ネットワークエラー") || it == "エラー"
    }
}

data class AdPart(val current: Int, val total: Int)

data class AdFrame(
    val anaForeground: Boolean,
    val entry: Boolean,
    val watched: Int? = null,
    val receipt: Boolean = false,
    val closeAvailable: Boolean = false,
    val limit: Boolean = false,
    val error: Boolean = false,
    val blocked: Boolean = false,
    val adSurface: Boolean = false,
    val part: AdPart? = null,
    val storeOverlay: Boolean = false,
    val nativePangleRender: Boolean = false,
    val manualRequired:Boolean=false,
    val advanceControl:Boolean=false
)

data class AdReceipt(
    val state: AdState, val completed: Boolean = false, val reward: Boolean = false,
    val after: Int = -1, val reason: String = ""
)

/** Transport is injected for controlled integration tests; production uses Accessibility only. */
interface AdInteractionPort {
    fun now(): Long
    fun stopped(): Boolean
    fun stopReason(): String = "user_stopped"
    suspend fun frame(): AdFrame
    suspend fun close(): Boolean
    suspend fun dismissReceipt()
    suspend fun pause(ms: Long) { delay(ms) }
    suspend fun state(state: AdState)
    suspend fun progress(part: AdPart) {}
    suspend fun observationBudgetChanged(ms: Long) {}
    suspend fun diagnostic(reason:String) {}
}

class AdInteraction(private val port: AdInteractionPort) {
    suspend fun awaitReceipt(before: Int, previousDepartureObserved:Boolean=false): AdReceipt {
        val start = port.now()
        val diagnostic = DiagnosticCapturePolicy()
        var leftEntry = previousDepartureObserved
        var reward = false
        var returnedAt: Long? = null
        var completed = false
        var after = -1
        var lastState = AdState.AD_OPENING
        var closeTries = 0
        var advanceTries = 0
        var storeCloseTries = 0
        var partStartedAt = start
        var observedPart: AdPart? = null
        var nativePangleWait = false
        var lastClose = Long.MIN_VALUE / 2
        suspend fun emit(s: AdState) {
            if (lastState != s) { lastState = s; port.state(s) }
        }
        while (port.now() - start < 300_000L) {
            if (port.stopped()) return AdReceipt(AdState.UNCONFIRMED, reward = reward, reason = port.stopReason())
            val f = port.frame()
            if(f.manualRequired)return AdReceipt(AdState.BLOCKED,reward=reward,reason="account_safety_human_required")
            if(diagnostic.shouldCapture(port.now()-start,f.adSurface,!f.entry&&!f.receipt&&!f.closeAvailable)) port.diagnostic("slow_or_unrecognized_ad")
            val progress = f.part
            if (f.anaForeground && f.adSurface && progress != null && progress != observedPart) {
                val previous = observedPart
                if (previous == null || progress.total == previous.total && progress.current > previous.current) {
                    if (previous != null) { partStartedAt = port.now(); closeTries = 0; advanceTries = 0; storeCloseTries = 0 }
                    observedPart = progress
                    port.progress(progress)
                }
            }
            if (!nativePangleWait && observedPart == null && f.anaForeground && f.adSurface && f.nativePangleRender) {
                nativePangleWait = true
                port.observationBudgetChanged(300_000L)
            }
            val segmentBudget = if (nativePangleWait && observedPart == null) 300_000L else 95_000L
            if (port.now() - partStartedAt >= segmentBudget) return AdReceipt(AdState.UNCONFIRMED, reward = reward,
                reason = if (observedPart == null || observedPart!!.total == 1) "ad_timeout_95s" else "ad_part_timeout_95s")
            if (f.blocked) return AdReceipt(AdState.BLOCKED, reward = reward, reason = "accessibility_or_keyguard")
            if (f.error && f.anaForeground) return AdReceipt(AdState.FAILED, reward = reward, reason = "official_error_dialog")
            if (f.adSurface && !f.entry && !f.receipt) {
                leftEntry = true
                emit(AdState.AD_PLAYING)
            }
            // A new foreground receipt must follow a departure from the entry page.
            if (leftEntry && f.anaForeground && f.receipt && !reward) {
                reward = true
                emit(AdState.REWARD_CONFIRMED)
                port.dismissReceipt()
            }
            if (leftEntry && f.anaForeground && f.entry) {
                emit(AdState.AD_RETURNED)
                if (returnedAt == null) returnedAt = port.now()
                if (f.watched != null && f.watched == before + 1) {
                    emit(AdState.AD_FINISHED)
                    completed = true
                    after = f.watched
                    if (reward || port.now() - returnedAt!! >= 8000L) return AdReceipt(if (reward) AdState.REWARD_CONFIRMED else AdState.UNCONFIRMED,
                        completed = true, reward = reward, after = f.watched,
                        reason = if (reward) "" else "reward_receipt_missing")
                }
                if (f.watched != null && f.watched > before + 1) {
                    return AdReceipt(AdState.UNCONFIRMED, reward = reward, after = f.watched, reason = "counter_jump_ambiguous")
                }
                if (port.now() - returnedAt!! >= 8_000L) {
                    return AdReceipt(AdState.UNCONFIRMED, completed = completed, reward = reward, after = after, reason = if (completed) "reward_receipt_missing" else "completion_not_confirmed")
                }
            }
            if (f.anaForeground && f.limit && !leftEntry) return AdReceipt(AdState.LIMIT_REACHED, reason = "official_limit")
            // A timer never creates a close button. Only a visible enabled labelled node can be tapped.
            val closeBudget = if (f.storeOverlay) storeCloseTries < 2 else if(f.advanceControl) advanceTries < 3 else closeTries < 3
            if ((leftEntry || f.storeOverlay) && !f.entry && !f.receipt && f.closeAvailable && closeBudget && port.now() - lastClose >= 2000L) {
                emit(AdState.AD_CLOSE_AVAILABLE)
                lastClose = port.now()
                if (f.storeOverlay) storeCloseTries++ else if(f.advanceControl) advanceTries++ else closeTries++
                port.close()
            }
            port.pause(800L)
        }
        return AdReceipt(AdState.UNCONFIRMED, reward = reward, reason = "ad_timeout_300s")
    }
}
