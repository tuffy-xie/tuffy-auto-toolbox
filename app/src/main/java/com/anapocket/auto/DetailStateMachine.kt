package com.anapocket.auto

import java.util.concurrent.atomic.AtomicLong

/** 挑战详情打开/识别的显式状态；任何终局都必须有理由。 */
enum class DetailState {
    CHALLENGE_LIST,
    DETAIL_OPENING,
    DETAIL_READY,
    DETAIL_VISIBLE_TREE_MISSING,
    WRONG_PAGE,
    UNKNOWN
}

/** 屏幕亮度探针。不保存图片时只保留这些非敏感统计量。 */
data class DetailVisual(
    val available: Boolean = false,
    val meanLuma: Double = -1.0,
    val topLuma: Double = -1.0,
    val bottomLuma: Double = -1.0,
    val dimmed: Boolean = false,
    val sheetVisible: Boolean = false,
    val error: String = ""
)

/** 单个 Accessibility 窗口的独立快照，绝不与其他窗口标签混合。 */
data class DetailWindowData(
    val packageName: String = "",
    val active: Boolean = false,
    val focused: Boolean = false,
    val layer: Int = 0,
    val type: Int = 0,
    val nodeCount: Int = 0,
    val labels: List<String> = emptyList()
)

data class DetailObservation(
    val state: DetailState,
    val reason: String,
    val foregroundPackage: String = "",
    val foregroundNodes: Int = 0,
    val foregroundLayer: Int = 0,
    val foregroundType: Int = 0,
    val anaWindowCount: Int = 0,
    val backgroundList: Boolean = false,
    val detailTitle: String = "",
    val actualCard: ChallengeCard? = null,
    val targetMatched: Boolean? = null,
    val bannerVisible: Boolean = false,
    val freeJoinNodeVisible: Boolean = false,
    val alreadyJoined: Boolean = false,
    val visual: DetailVisual = DetailVisual(),
    val windowEventAgeMs: Long = -1,
    val contentEventAgeMs: Long = -1
) {
    fun logLine(elapsedMs: Long): String {
        val v = if (visual.available) {
            "mean=%.1f/top=%.1f/bottom=%.1f/dim=${visual.dimmed}/sheet=${visual.sheetVisible}".format(
                visual.meanLuma, visual.topLuma, visual.bottomLuma
            )
        } else "unavailable(${visual.error})"
        val target = when (targetMatched) {
            true -> "MATCH"
            false -> "MISMATCH"
            null -> "N/A"
        }
        val actual = actualCard?.let { "${it.cat.name}:${it.distanceLabel}" } ?: "-"
        return "t=${elapsedMs}ms state=$state reason=$reason fg=$foregroundPackage" +
            " nodes=$foregroundNodes layer=$foregroundLayer type=$foregroundType" +
            " anaWins=$anaWindowCount bgList=$backgroundList target=$target" +
            " title='${detailTitle.take(70)}' actual=$actual visual=[$v]" +
            " banner=$bannerVisible freeJoinNode=$freeJoinNodeVisible" +
            " winEvtAge=$windowEventAgeMs contentEvtAge=$contentEventAgeMs"
    }
}

/** 事件只提供遥测，不把一次事件直接当成 READY。 */
object DetailEvents {
    private val lastWindow = AtomicLong(0)
    private val lastContent = AtomicLong(0)

    fun onWindowEvent() = lastWindow.set(System.currentTimeMillis())
    fun onContentEvent() = lastContent.set(System.currentTimeMillis())

    fun windowAge(): Long = lastWindow.get().let {
        if (it == 0L) -1 else System.currentTimeMillis() - it
    }

    fun contentAge(): Long = lastContent.get().let {
        if (it == 0L) -1 else System.currentTimeMillis() - it
    }
}

/**
 * 纯状态判定：前台窗口负责语义，后台窗口只提供上下文。
 * 该类不访问 Android API，便于对历史 R29 污染场景做单元回归。
 */
class DetailStateEvaluator(private val anaPackage: String) {

    fun evaluate(
        windows: List<DetailWindowData>,
        target: ChallengeCard? = null,
        visual: DetailVisual = DetailVisual(),
        elapsedMs: Long = 0
    ): DetailObservation {
        val ana = windows.filter { it.packageName == anaPackage }
        val activeExternal = windows.firstOrNull {
            (it.active || it.focused) && it.packageName != anaPackage &&
                it.packageName != "com.anapocket.auto" &&
                !it.packageName.startsWith("com.android.systemui")
        }
        val foreground = ana.filter { it.active || it.focused }.maxByOrNull { it.layer }
            ?: ana.maxByOrNull { it.layer }
        val base = DetailObservation(
            state = DetailState.UNKNOWN,
            reason = "insufficientEvidence",
            foregroundPackage = foreground?.packageName ?: activeExternal?.packageName.orEmpty(),
            foregroundNodes = foreground?.nodeCount ?: 0,
            foregroundLayer = foreground?.layer ?: 0,
            foregroundType = foreground?.type ?: 0,
            anaWindowCount = ana.size,
            visual = visual,
            windowEventAgeMs = DetailEvents.windowAge(),
            contentEventAgeMs = DetailEvents.contentAge()
        )
        if (foreground == null) {
            return if (activeExternal != null) {
                base.copy(
                    state = DetailState.WRONG_PAGE,
                    reason = "externalForeground:${activeExternal.packageName}",
                    foregroundPackage = activeExternal.packageName,
                    foregroundNodes = activeExternal.nodeCount
                )
            } else base.copy(reason = if (windows.isEmpty()) "noAccessibilityWindows" else "noAnaWindow")
        }

        val labels = foreground.labels.map { it.trim() }.filter { it.isNotEmpty() }
        val backgroundList = ana.filter { it !== foreground }.any { hasListTabs(it.labels) }
        val cards = labels.mapNotNull { Cards.fromTitle(it) }.distinctBy { it.key }
        val actual = target?.let { want -> cards.firstOrNull { sameCard(it, want) } }
            ?: cards.firstOrNull()
        val detailTitle = actual?.title ?: cards.firstOrNull()?.title.orEmpty()
        val hasDeadline = labels.any { it == "期限" }
        val hasReward = labels.any { it == "報酬" }
        val hasDetailStructure = hasDeadline && hasReward
        val hasDetailHeader = labels.any {
            it.contains("短距離移動チャレンジ") || it.contains("デイリーチャレンジ")
        }
        val hasList = hasListTabs(labels)
        val visualOpening = visual.available && (visual.dimmed || visual.sheetVisible)
        val common = base.copy(
            backgroundList = backgroundList,
            detailTitle = detailTitle,
            actualCard = actual,
            targetMatched = target?.let { want -> actual?.let { sameCard(it, want) } == true },
            bannerVisible = labels.any { it == "[広告]" || it.contains("広告画像") },
            freeJoinNodeVisible = labels.any { it == "に参加" || it == "に参加予約" },
            alreadyJoined = labels.any {
                it.contains("参加予約日") || it.contains("参加済み") ||
                    it.contains("エントリー済み") || it.contains("参加中")
            }
        )

        // READY 只能来自前台详情内容；后台列表没有否决权。
        if (actual != null && hasDetailStructure) {
            val matches = target == null || sameCard(actual, target)
            return common.copy(
                state = if (matches) DetailState.DETAIL_READY else DetailState.WRONG_PAGE,
                reason = if (matches) "detailStructureAndTargetConsistent" else "detailTargetMismatch"
            )
        }

        // 视觉打开信号优先于旧列表标签：动画早期焦点可能还没切到弹层。
        if (visualOpening) {
            return common.copy(
                state = if (elapsedMs >= CONTENT_MISSING_MS && visual.sheetVisible) {
                    DetailState.DETAIL_VISIBLE_TREE_MISSING
                } else DetailState.DETAIL_OPENING,
                reason = if (elapsedMs >= CONTENT_MISSING_MS && visual.sheetVisible) {
                    "visualSheetPersistedWithoutVerifiableContent"
                } else "visualOpeningSignal"
            )
        }

        // 列表页也含「🚩短距離移動チャレンジ」区块标题，必须先判 LIST；
        // 否则未打开时会把它误当成详情头部，白等 6.5 秒后误报 TREE_MISSING。
        if (hasList) {
            return common.copy(state = DetailState.CHALLENGE_LIST, reason = "foregroundListTabs")
        }

        // 有详情结构但没有可识别的每日挑战标题：先给渲染时间，
        // 超时后明确是“打开了别的详情”，不再伪装成树缺失。
        if (hasDetailStructure && actual == null) {
            return common.copy(
                state = if (elapsedMs >= CONTENT_MISSING_MS) DetailState.WRONG_PAGE else DetailState.DETAIL_OPENING,
                reason = if (elapsedMs >= CONTENT_MISSING_MS) {
                    "detailStructureWithoutDailyChallengeTarget"
                } else "detailStructureRendering"
            )
        }

        if (hasDetailHeader || (actual != null && (hasDeadline || hasReward))) {
            return common.copy(
                state = if (elapsedMs >= CONTENT_MISSING_MS) {
                    DetailState.DETAIL_VISIBLE_TREE_MISSING
                } else DetailState.DETAIL_OPENING,
                reason = if (elapsedMs >= CONTENT_MISSING_MS) {
                    "detailHeaderPersistedWithoutTargetChain"
                } else "detailContentRendering"
            )
        }

        // 新前台 ANA 窗口 + 后台列表 + 无正文：是 OPENING 的窗口证据，
        // 但没有视觉证据时，超时只能 UNKNOWN，不能谎称看见弹层。
        if (backgroundList && ana.size > 1) {
            return common.copy(
                state = if (elapsedMs >= CONTENT_MISSING_MS) {
                    DetailState.UNKNOWN
                } else DetailState.DETAIL_OPENING,
                reason = if (elapsedMs >= CONTENT_MISSING_MS) {
                    "foregroundWindowContentTimeoutWithoutVisual"
                } else "foregroundWindowPendingBackgroundList"
            )
        }

        if (labels.any { it == "所有ポイント" || it.contains("ポイント履歴") }) {
            return common.copy(state = DetailState.WRONG_PAGE, reason = "pointHistory")
        }
        return common.copy(reason = if (labels.isEmpty()) "foregroundTreeEmpty" else "unclassifiedAnaPage")
    }

    private fun hasListTabs(labels: List<String>): Boolean {
        val trimmed = labels.map { it.trim() }
        return trimmed.contains("参加可能") || trimmed.contains("参加中") || trimmed.contains("履歴")
    }

    private fun sameCard(a: ChallengeCard, b: ChallengeCard): Boolean =
        a.key == b.key && a.cat == b.cat && a.meters == b.meters

    companion object {
        const val CONTENT_MISSING_MS = 6_500L
    }
}
