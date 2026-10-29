package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class AdSdkWindowsTest {
    @Test fun observedSdkWindowsDoNotRequireAdActivitySubstring() {
        assertTrue(AdSdkWindows.contains("com.unity3d.ads.adplayer.FullScreenWebViewDisplay"))
        assertTrue(AdSdkWindows.contains("com.mbridge.msdk.config.activity.MBRewardVideoActivity"))
        assertTrue(AdSdkWindows.contains("com.bytedance.sdk.openadsdk.activity.TTFullWebActivity"))
    }
    @Test fun manifestFullscreenSdkActivitiesAreRecognized() {
        assertTrue(AdSdkWindows.contains("com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.vast.VastActivity"))
        assertTrue(AdSdkWindows.contains("com.applovin.adview.AppLovinFullscreenActivity"))
    }
    @Test fun hostLoginStoreAndSdkDebugWindowsDoNotProveAdPlayback() {
        for (n in listOf("us.ana_pocket.ANAPocket.views.home.HomeActivity", "android.widget.FrameLayout",
            "com.google.android.play.core.hsdp.service.HsdpShimActivity", "com.amplifyframework.auth.cognito.activities.CustomTabsManagerActivity",
            "com.applovin.mediation.MaxDebuggerActivity", "com.five_corp.ad.AdReportDialogActivity")) assertFalse(n, AdSdkWindows.contains(n))
    }
}
