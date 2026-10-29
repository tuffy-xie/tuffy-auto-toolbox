package com.anapocket.auto

/** Foreground fullscreen ad components read from installed ANA Pocket 2.3.4 manifest.
 * Excludes account/login, ad reporting/debugging, browser and store activities.
 * A matching window proves departure to an ad surface, never completion or account credit. */
object AdSdkWindows {
    private val fullScreen = setOf(
        "com.five_corp.ad.AdActivity",
        "com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.templates.renderer.fullscreen.FullscreenWebviewActivity",
        "com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.vast.VastActivity",
        "com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.staticrenderer.StaticAdActivity",
        "com.moloco.sdk.xenoss.sdkdevkit.android.adrenderer.internal.mraid.MraidActivity",
        "com.ironsource.sdk.controller.ControllerActivity",
        "com.ironsource.sdk.controller.InterstitialActivity",
        "com.google.android.gms.ads.AdActivity",
        "com.unity3d.ads.adplayer.FullScreenWebViewDisplay",
        "com.inmobi.ads.rendering.InMobiAdActivity",
        "com.bytedance.sdk.openadsdk.activity.single.TTAppOpenAdActivity",
        "com.bytedance.sdk.openadsdk.activity.single.TTAppOpenAdTransActivity",
        "com.bytedance.sdk.openadsdk.activity.single.TTRewardVideoActivity",
        "com.bytedance.sdk.openadsdk.activity.TTRewardWebActivity",
        "com.bytedance.sdk.openadsdk.activity.TTFullWebActivity",
        "com.bytedance.sdk.openadsdk.activity.single.TTRewardExpressVideoActivity",
        "com.bytedance.sdk.openadsdk.activity.single.TTFullScreenVideoActivity",
        "com.bytedance.sdk.openadsdk.activity.single.TTFullScreenExpressVideoActivity",
        "com.bytedance.sdk.openadsdk.activity.single.TTAdActivity",
        "com.applovin.adview.AppLovinFullscreenActivity",
        "com.applovin.adview.AppLovinFullscreenImmersiveActivity",
        "com.facebook.ads.AudienceNetworkActivity",
        "com.fyber.inneractive.sdk.activities.InneractiveFullscreenAdActivity",
        "com.fyber.inneractive.sdk.activities.InneractiveRichMediaVideoPlayerActivityCore",
        "com.mbridge.msdk.config.activity.MBRewardVideoActivity",
        "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
        "com.ogury.ad.interstitial.ui.InterstitialActivity",
        "com.ogury.ad.interstitial.ui.InterstitialAndroid8TransparentActivity",
        "com.ogury.ad.interstitial.ui.InterstitialAndroid8RotableActivity"
    )
    fun contains(component: String): Boolean = component in fullScreen
}
