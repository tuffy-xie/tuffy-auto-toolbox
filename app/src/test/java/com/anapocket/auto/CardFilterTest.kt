package com.anapocket.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v10 卡片识别的回归保护。
 *
 * 这些用例全部来自 2026-10-26 真机实测文本，其中后三条是真实存在、
 * 但**不属于「当日距离挑战」**的干扰标题，必须被拒绝。
 */
class CardFilterTest {
    @Test fun actualCarCategoryUsesSharedCarTrainMonorailTitle() {
        val c = Cards.fromTitle("車、電車、モノレールで5km移動しよう！")
        assertNotNull(c)
        assertEquals(Cat.CAR, c!!.cat)
        assertEquals(5000, c.meters)
    }
    @Test fun carCategoryAdditionDoesNotAdoptRailOnlyOrWeeklyTasks() {
        assertNull(Cards.fromTitle("電車で5km移動しよう！"))
        assertNull(Cards.fromTitle("車、電車、モノレールで1週間に500km移動しよう！"))
        assertNull(Cards.fromTitle("徒歩や自転車で合計2kmのエコ移動をしよう！"))
    }

    @Test
    fun walk_with_de() {
        val c = Cards.fromTitle("徒歩で2km移動しよう！")
        assertNotNull(c)
        assertEquals(Cat.WALK, c!!.cat)
        assertEquals(2000, c.meters)
    }

    @Test
    fun walk_with_meter_unit() {
        val c = Cards.fromTitle("徒歩で500m移動しよう！")
        assertNotNull(c)
        assertEquals(Cat.WALK, c!!.cat)
        assertEquals(500, c.meters)
    }

    @Test
    fun mov_any_without_de() {
        // 真机文本：「移動手段問わず5km移動しよう！」没有「で」
        val c = Cards.fromTitle("移動手段問わず5km移動しよう！")
        assertNotNull(c)
        assertEquals(Cat.MOV_ANY, c!!.cat)
        assertEquals(5000, c.meters)
    }

    @Test
    fun bike_and_car() {
        val b = Cards.fromTitle("自転車で3km移動しよう！")
        assertEquals(Cat.BIKE, b!!.cat)
        assertEquals(3000, b.meters)

        val car = Cards.fromTitle("車で40km移動しよう！")
        assertEquals(Cat.CAR, car!!.cat)
        assertEquals(40000, car.meters)
    }

    @Test
    fun composite_eco_task_rejected() {
        // 「徒歩や自転車で合計2km…」是复合环保任务，不是徒歩挑战。
        // 用 contains 判定会被误判成 BIKE，必须拒绝。
        assertNull(Cards.fromTitle("徒歩や自転車で合計2kmのエコ移動をしよう！"))
    }

    @Test
    fun weekly_task_rejected() {
        // 周任务不是当日挑战，且距离不是当日目标
        assertNull(Cards.fromTitle("【毎週月曜日更新】移動手段問わず1週間で500km移動しよう！"))
    }

    @Test
    fun sponsored_text_rejected() {
        assertNull(Cards.fromTitle("ANA国内線に2回搭乗しよう！"))
        assertNull(Cards.fromTitle("3スポットで達成！東京・神津島スタンプラリー"))
    }

    @Test
    fun same_distance_different_title_kept() {
        val a = Cards.fromTitle("徒歩で2km移動しよう！")
        val b = Cards.fromTitle("徒歩で2km移動しよう！【別コース】")
        assertNotNull(a)
        assertNotNull(b)
        // 距离相同但标题不同 -> 必须是两条独立记录
        assertEquals(a!!.meters, b!!.meters)
        assertEquals(false, a.key == b!!.key)
    }

    @Test
    fun select_equal_to_limit_included() {
        val cards = listOf(
            Cards.fromTitle("徒歩で5km移動しよう！")!!,
            Cards.fromTitle("徒歩で10km移動しよう！")!!
        )
        val sel = Cards.select(cards, Cat.WALK, 5000)
        assertEquals(1, sel.matched.size)
        assertEquals(5000, sel.matched[0].meters)
        assertEquals(1, sel.overLimit.size)
    }

    @Test
    fun select_unknown_not_guessed() {
        // 无法解析距离的卡片必须单列 UNKNOWN，不能猜
        val cards = listOf(ChallengeCard(Cat.WALK, "徒歩で移動しよう！", null, "徒歩で移動しよう！"))
        val sel = Cards.select(cards, Cat.WALK, 5000)
        assertEquals(0, sel.matched.size)
        assertEquals(1, sel.unknown.size)
    }
}
