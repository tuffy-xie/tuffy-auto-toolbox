package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class PangleNativeCloseTest {
    private val actual = "com.bytedance.sdk.openadsdk.activity.single.TTFullScreenExpressVideoActivity"
    @Test fun actualNativeChineseCloseDescriptionIsExplicitClose() {
        assertTrue(AdSignals.closeLabel("广告关闭")); assertFalse(AdSignals.closeLabel("一探究竟"))
        assertFalse(AdSignals.closeLabel("Install"))
    }
    @Test fun actualDefaultCloseIsAcceptedOnlyInRegisteredPangleAdWindow() {
        assertTrue(AdSignals.pangleNativeClose(actual,"default_close"))
        assertTrue(AdSignals.pangleNativeClose(actual,"广告关闭"))
        assertFalse(AdSignals.pangleNativeClose("us.ana_pocket.ANAPocket.views.home.HomeActivity","default_close"))
        assertFalse(AdSignals.pangleNativeClose(actual,"一探究竟"))
        assertFalse(AdSignals.pangleNativeClose("com.bytedance.sdk.openadsdk.activity.TTWebsiteActivity","default_close"))
    }
}
