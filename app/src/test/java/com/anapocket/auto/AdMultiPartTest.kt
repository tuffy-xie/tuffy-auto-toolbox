package com.anapocket.auto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdMultiPartTest {
    @Test fun actualMintegralCloseDescriptionExcludesItsInstallAndPrivacyControls() {
        assertTrue(AdSignals.closeLabel("closeButton"))
        assertFalse(AdSignals.closeLabel("Click now for details"))
        assertFalse(AdSignals.closeLabel("install"))
        assertFalse(AdSignals.closeLabel("Privacy"))
        assertFalse(AdSignals.receipt(listOf("closeButton")))
    }
    @Test fun actualUnityDisplayUsesCloseAndPrivacyRatherThanActivitySuffix() {
        assertTrue(AdSignals.unityEndCard(listOf("Close", "パルシィ", "价格", "免费", "安装", "Privacy")))
        assertFalse(AdSignals.unityEndCard(listOf("Close", "今日の視聴済み回数 25/25")))
        assertFalse(AdSignals.unityEndCard(listOf("Privacy", "安装")))
    }
    @Test fun threeLongPartsAndThreePlaySheetsFinishOneWatch() = runBlocking {
        val p = object : AdInteractionPort {
            var time = 0L; var part = 1; var partStart = 0L; var inSheet = true
            var stores = 0; var adCloses = 0; var dismissed = false
            val seen = mutableListOf<AdPart>()
            override fun now() = time
            override fun stopped() = false
            override suspend fun pause(ms: Long) { time += ms }
            override suspend fun state(state: AdState) {}
            override suspend fun progress(part: AdPart) { seen += part }
            override suspend fun frame(): AdFrame = when {
                part > 3 && !dismissed -> AdFrame(true, false, receipt = true)
                part > 3 -> AdFrame(true, true, 5)
                time - partStart < 60000 -> AdFrame(true, false, adSurface = true, part = AdPart(part, 3))
                inSheet -> AdFrame(false, false, closeAvailable = true, storeOverlay = true)
                else -> AdFrame(true, false, closeAvailable = true, adSurface = true, part = AdPart(part, 3))
            }
            override suspend fun close(): Boolean {
                if (inSheet) { stores++; inSheet = false }
                else { adCloses++; part++; partStart = time; inSheet = true }
                return true
            }
            override suspend fun dismissReceipt() { dismissed = true }
        }
        val r = AdInteraction(p).awaitReceipt(4)
        assertTrue(r.completed); assertTrue(r.reward); assertEquals(5, r.after)
        assertEquals(3, p.stores); assertEquals(3, p.adCloses)
        assertEquals(listOf(AdPart(1, 3), AdPart(2, 3), AdPart(3, 3)), p.seen)
        assertTrue(p.time > 180000L); assertTrue(p.time < 300000L)
    }
    private class TimedPartPort(val mode: String) : AdInteractionPort {
        var time = 0L; var closes = 0; val states = mutableListOf<AdState>()
        override fun now() = time
        override fun stopped() = false
        override suspend fun pause(ms: Long) { time += ms }
        override suspend fun state(state: AdState) { states += state }
        override suspend fun dismissReceipt() { fail("no reward receipt exists") }
        override suspend fun close(): Boolean { closes++; return true }
        override suspend fun frame(): AdFrame = when(mode) {
            "store" -> AdFrame(false, false, closeAvailable = true, storeOverlay = true)
            "absolute" -> AdFrame(true, false, adSurface = true, part = AdPart((time / 80000).toInt() + 1, 5))
            "regress" -> AdFrame(true, false, adSurface = true, part = AdPart(if (time in 80000..89999) 2 else 1, 3))
            else -> AdFrame(true, false, adSurface = true, part = AdPart(1, 3))
        }
    }
    @Test fun repeatedPartDoesNotRenewItsDeadline() = runBlocking {
        val p = TimedPartPort("repeat"); val r = AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_part_timeout_95s", r.reason); assertFalse(r.completed)
        assertTrue(p.time in 95000..96000)
    }
    @Test fun regressingPartCannotKeepResettingTheTimer() = runBlocking {
        val p = TimedPartPort("regress"); val r = AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_part_timeout_95s", r.reason)
        assertTrue(p.time in 175000..176000)
    }
    @Test fun absoluteWatchDeadlineRemainsBounded() = runBlocking {
        val p = TimedPartPort("absolute"); val r = AdInteraction(p).awaitReceipt(0)
        assertEquals("ad_timeout_300s", r.reason); assertTrue(p.time in 300000..300800)
        assertFalse(r.completed); assertFalse(r.reward)
    }
    @Test fun playSheetCanBeDismissedButNeverProvesPlaybackOrReward() = runBlocking {
        val p = TimedPartPort("store"); val r = AdInteraction(p).awaitReceipt(0)
        assertEquals(2, p.closes); assertFalse(p.states.contains(AdState.AD_PLAYING))
        assertFalse(r.completed); assertFalse(r.reward); assertEquals("ad_timeout_95s", r.reason)
    }
    @Test fun progressParserRequiresExplicitBoundedSdkText() {
        assertEquals(AdPart(2, 3), AdSignals.part(listOf("Ad 2 of 3")))
        assertNull(AdSignals.part(listOf("Install after Ad 2 of 3")))
        assertNull(AdSignals.part(listOf("Ad 4 of 3")))
        assertNull(AdSignals.part(listOf("Ad 1 of 100")))
    }
    @Test fun playSheetRequiresTheStorePackageAndSheetMarkers() {
        val real = listOf("Google Play", "关闭工作表", "关闭", "安装")
        assertTrue(AdSignals.playSheet("com.android.vending", real))
        assertFalse(AdSignals.playSheet("us.ana_pocket.ANAPocket", real))
        assertFalse(AdSignals.playSheet("com.android.vending", listOf("Google Play", "安装")))
    }
    @Test fun actualTransparentPlaySheetMayOmitBrandFromAccessibilityTree() {
        val actual = listOf("关闭工作表", "Roblox", "安装", "关闭")
        assertTrue(AdSignals.playSheet("com.android.vending", actual))
        assertFalse(AdSignals.playSheet("us.ana_pocket.ANAPocket", actual))
        assertFalse(AdSignals.playSheet("com.android.vending", listOf("安装", "关闭")))
        assertTrue(AdSignals.playSheet("com.android.vending", listOf("关闭工作表", "不了，谢谢", "探索更多")))
        assertTrue(AdSignals.playHome("com.android.vending", listOf("游戏", "应用", "搜索", "图书", "我")))
        assertFalse(AdSignals.playHome("us.ana_pocket.ANAPocket", listOf("游戏", "应用", "搜索", "图书", "我")))
    }
}
