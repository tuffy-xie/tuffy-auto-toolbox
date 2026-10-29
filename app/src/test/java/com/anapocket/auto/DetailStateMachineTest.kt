package com.anapocket.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailStateMachineTest {
    private val evaluator = DetailStateEvaluator("us.ana_pocket.ANAPocket")
    private val target1 = Cards.fromTitle("徒歩で1km移動しよう！")!!
    private val target2 = Cards.fromTitle("徒歩で2km移動しよう！")!!

    private fun window(
        labels: List<String>,
        active: Boolean,
        layer: Int,
        pkg: String = "us.ana_pocket.ANAPocket"
    ) = DetailWindowData(
        packageName = pkg,
        active = active,
        focused = active,
        layer = layer,
        type = 1,
        nodeCount = labels.size,
        labels = labels
    )

    private val listLabels = listOf("参加可能", "参加中", "履歴", "スポンサードチャレンジ")
    private val detailLabels = listOf(
        "🚩短距離移動チャレンジ", "徒歩", "1", "km", "徒歩で1km移動しよう！",
        "期限", "2026/10/26", "報酬", "5", "pt", "50", "pt", "動画を見て", "〜に参加"
    )

    @Test
    fun backgroundListCannotVetoForegroundDetail() {
        val obs = evaluator.evaluate(
            listOf(window(listLabels, active = false, layer = 1), window(detailLabels, active = true, layer = 2)),
            target1
        )
        assertEquals(DetailState.DETAIL_READY, obs.state)
        assertEquals(true, obs.targetMatched)
        assertTrue(obs.backgroundList)
        assertFalse("后台列表的参加中 tab 不能触发 ALREADY", obs.alreadyJoined)
    }

    @Test
    fun splitDistanceNodesAreParsedFromFullTitle() {
        val obs = evaluator.evaluate(listOf(window(detailLabels, active = true, layer = 2)), target1)
        assertEquals(DetailState.DETAIL_READY, obs.state)
        assertEquals(Cat.WALK, obs.actualCard?.cat)
        assertEquals(1000, obs.actualCard?.meters)
    }

    @Test
    fun blankSheetIsOpeningThenTreeMissing() {
        val windows = listOf(
            window(listLabels, active = false, layer = 1),
            window(emptyList(), active = true, layer = 2)
        )
        val visual = DetailVisual(available = true, sheetVisible = true)
        val opening = evaluator.evaluate(windows, target1, visual, elapsedMs = 900)
        val missing = evaluator.evaluate(windows, target1, visual, elapsedMs = 7000)
        assertEquals(DetailState.DETAIL_OPENING, opening.state)
        assertEquals(DetailState.DETAIL_VISIBLE_TREE_MISSING, missing.state)
    }

    @Test
    fun wrongDetailTargetIsWrongPage() {
        val obs = evaluator.evaluate(listOf(window(detailLabels, active = true, layer = 2)), target2)
        assertEquals(DetailState.WRONG_PAGE, obs.state)
        assertEquals(false, obs.targetMatched)
    }

    @Test
    fun asynchronousBannerDoesNotInvalidateReady() {
        val obs = evaluator.evaluate(
            listOf(window(detailLabels + listOf("[広告]", "広告画像"), active = true, layer = 2)),
            target1
        )
        assertEquals(DetailState.DETAIL_READY, obs.state)
        assertTrue(obs.bannerVisible)
        assertFalse(obs.freeJoinNodeVisible)
    }

    @Test
    fun blankWindowWithoutVisualBecomesUnknownNotFabricatedTreeMissing() {
        val windows = listOf(
            window(listLabels, active = false, layer = 1),
            window(emptyList(), active = true, layer = 2)
        )
        assertEquals(
            DetailState.DETAIL_OPENING,
            evaluator.evaluate(windows, target1, elapsedMs = 900).state
        )
        assertEquals(
            DetailState.UNKNOWN,
            evaluator.evaluate(windows, target1, elapsedMs = 7000).state
        )
    }

    @Test
    fun normalListRemainsList() {
        val obs = evaluator.evaluate(listOf(window(listLabels, active = true, layer = 1)), target1)
        assertEquals(DetailState.CHALLENGE_LIST, obs.state)
    }

    @Test
    fun listSectionHeaderAndCardTitleAreNotMistakenForDetail() {
        val labels = listLabels + listOf(
            "🚩短距離移動チャレンジ", "徒歩で1km移動しよう！", "1", "km", "2026/10/26まで", "50", "pt"
        )
        val obs = evaluator.evaluate(listOf(window(labels, active = true, layer = 1)), target1, elapsedMs = 7000)
        assertEquals(DetailState.CHALLENGE_LIST, obs.state)
        assertEquals("foregroundListTabs", obs.reason)
    }

    @Test
    fun unknownDetailWithStructureButNoDailyTitleBecomesWrongPage() {
        val labels = listOf(
            "🌱エコチャレンジ", "徒歩", "+", "自転車", "7", "km",
            "通勤・通学の車移動を減らして徒歩や自転車移動を増やしてみよう！",
            "期限", "2026/10/30", "報酬", "150", "pt", "参加する"
        )
        val obs = evaluator.evaluate(listOf(window(labels, active = true, layer = 1)), target1, elapsedMs = 7000)
        assertEquals(DetailState.WRONG_PAGE, obs.state)
        assertEquals("detailStructureWithoutDailyChallengeTarget", obs.reason)
    }

    @Test
    fun externalForegroundIsWrongPage() {
        val obs = evaluator.evaluate(
            listOf(window(listOf("external"), active = true, layer = 9, pkg = "com.example.other")),
            target1
        )
        assertEquals(DetailState.WRONG_PAGE, obs.state)
    }
}
