package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class GoogleDismissPolicyTest {
    private fun allowed(pkg:String="us.ana_pocket.ANAPocket",activity:String="com.google.android.gms.ads.AdActivity",
        ids:Set<String> = setOf("app-interstitial-slot","mys-wrapper","dismiss-button"),id:String="dismiss-button",
        cls:String="android.view.View",clickable:Boolean=true,children:List<String> = listOf("NO THANKS"))=
        GoogleDismissPolicy.noThanks(pkg,activity,ids,id,cls,clickable,children)
    @Test fun actualGoogleNoThanksChildAuthorizesOnlyDismissControl(){assertTrue(allowed())}
    @Test fun foreignPackageNativeAnaDialogAndOtherSdkRemainExcluded(){assertFalse(allowed(pkg="com.android.vending"));assertFalse(allowed(activity="us.ana_pocket.ANAPocket.views.home.HomeActivity"));assertFalse(allowed(activity="com.unity3d.ads.adplayer.FullScreenWebViewDisplay"))}
    @Test fun openInstallMarketingAndUnclickableTextCannotDismiss(){assertFalse(allowed(children=listOf("開く")));assertFalse(allowed(children=listOf("NO THANKS","安装")));assertFalse(allowed(clickable=false));assertFalse(allowed(cls="android.widget.TextView"))}
    @Test fun exactObservedEndCardIdsAreRequired(){assertFalse(allowed(ids=setOf("dismiss-button")));assertFalse(allowed(id="open-button"));assertFalse(allowed(children=emptyList()))}
}
