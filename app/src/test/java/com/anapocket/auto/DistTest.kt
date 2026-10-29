package com.anapocket.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DistTest {

    @Test
    fun km_integer() {
        assertEquals(5000, Dist.parse("徒歩で5km移動しよう！")!!.meters)
        assertEquals(1000, Dist.parse("徒歩で1km移動しよう！")!!.meters)
        assertEquals(40000, Dist.parse("車で40km移動しよう！")!!.meters)
    }

    @Test
    fun km_decimal() {
        assertEquals(500, Dist.parse("徒歩で0.5km移動しよう！")!!.meters)
        assertEquals(2500, Dist.parse("自転車で2.5km移動しよう！")!!.meters)
    }

    @Test
    fun meter_unit() {
        assertEquals(500, Dist.parse("徒歩で500m移動しよう！")!!.meters)
        assertEquals(300, Dist.parse("徒歩で300メートル")!!.meters)
    }

    @Test
    fun full_width_digits() {
        assertEquals(1000, Dist.parse("徒歩で１ｋｍ移動しよう！")!!.meters)
        assertEquals(5000, Dist.parse("徒歩で５ｋｍ移動しよう！")!!.meters)
    }

    @Test
    fun mov_any_and_bike() {
        assertEquals(5000, Dist.parse("移動手段問わず5km移動しよう！")!!.meters)
        assertEquals(1000, Dist.parse("自転車で1km移動しよう！")!!.meters)
        assertEquals(20000, Dist.parse("移動手段問わず20km移動しよう！")!!.meters)
    }

    @Test
    fun no_distance_returns_null() {
        assertNull(Dist.parse("報酬 P25pt"))
        assertNull(Dist.parse("2026/10/26"))
        assertNull(Dist.parse("広告ブーストで最大200pt"))
        assertNull(Dist.parse(""))
        assertNull(Dist.parse(null))
    }

    @Test
    fun label() {
        assertEquals("500m", Dist.label(500))
        assertEquals("5km", Dist.label(5000))
        assertEquals("5.5km", Dist.label(5500))
    }
}

class CardsTest {

    @Test
    fun build_card_from_title() {
        val c = Cards.fromTitle("徒歩で5km移動しよう！")
        assertEquals(Cat.WALK, c!!.cat)
        assertEquals(5000, c.meters)
    }

    @Test
    fun build_mov_any() {
        val c = Cards.fromTitle("移動手段問わず5km移動しよう！")
        assertEquals(Cat.MOV_ANY, c!!.cat)
        assertEquals(5000, c.meters)
    }

    @Test
    fun bike_not_confused_with_car() {
        // "自転車" 含 "車"，必须判成 BIKE 而不是 CAR
        val c = Cards.fromTitle("自転車で5km移動しよう！")
        assertEquals(Cat.BIKE, c!!.cat)
    }

    @Test
    fun non_challenge_text_rejected() {
        assertNull(Cards.fromTitle("ＡＮＡウインドサーフィンワールドカップ開催記念！"))
        assertNull(Cards.fromTitle("報酬 10pt"))
        assertNull(Cards.fromTitle(""))
    }

    @Test
    fun dedupe_same_title() {
        val a = Cards.fromTitle("徒歩で5km移動しよう！")!!
        val b = Cards.fromTitle("徒歩で5km移動しよう！")!!
        val out = Cards.dedupe(listOf(a, b))
        assertEquals(1, out.size)
    }

    @Test
    fun same_distance_different_title_not_merged() {
        val a = Cards.fromTitle("徒歩で5km移動しよう！")!!
        val b = Cards.fromTitle("徒歩で5kmのエコ移動をしよう！")!!
        val out = Cards.dedupe(listOf(a, b))
        assertEquals(2, out.size)
        assertEquals(5000, out[0].meters)
        assertEquals(5000, out[1].meters)
    }

    @Test
    fun select_respects_limit() {
        val list = listOf(
            Cards.fromTitle("徒歩で1km移動しよう！")!!,
            Cards.fromTitle("徒歩で2km移動しよう！")!!,
            Cards.fromTitle("徒歩で3km移動しよう！")!!,
            Cards.fromTitle("徒歩で5km移動しよう！")!!,
            Cards.fromTitle("徒歩で10km移動しよう！")!!
        )
        val sel = Cards.select(list, Cat.WALK, 5000)
        assertEquals(4, sel.matched.size)      // 1,2,3,5 全部入选（含等于上限）
        assertEquals(1, sel.overLimit.size)    // 10km 超出
        assertEquals(0, sel.unknown.size)
        assertEquals(5, sel.discovered)
    }

    @Test
    fun select_marks_unknown() {
        val ok = Cards.fromTitle("徒歩で5km移動しよう！")!!
        val unk = ChallengeCard(Cat.WALK, "徒歩で???移動しよう！", null, "徒歩で???移動しよう！")
        val sel = Cards.select(listOf(ok, unk), Cat.WALK, 5000)
        assertEquals(1, sel.matched.size)
        assertEquals(1, sel.unknown.size)
    }

    @Test
    fun select_ignores_other_categories() {
        val list = listOf(
            Cards.fromTitle("徒歩で5km移動しよう！")!!,
            Cards.fromTitle("自転車で5km移動しよう！")!!,
            Cards.fromTitle("車で5km移動しよう！")!!
        )
        val sel = Cards.select(list, Cat.WALK, 5000)
        assertEquals(1, sel.matched.size)
        assertEquals(Cat.WALK, sel.matched[0].cat)
    }

    @Test
    fun matched_sorted_ascending() {
        val list = listOf(
            Cards.fromTitle("徒歩で5km移動しよう！")!!,
            Cards.fromTitle("徒歩で1km移動しよう！")!!,
            Cards.fromTitle("徒歩で3km移動しよう！")!!
        )
        val sel = Cards.select(list, Cat.WALK, 5000)
        assertTrue(sel.matched[0].meters!! <= sel.matched[1].meters!!)
        assertTrue(sel.matched[1].meters!! <= sel.matched[2].meters!!)
    }
}
