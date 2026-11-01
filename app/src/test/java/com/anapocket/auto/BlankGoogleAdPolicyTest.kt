package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class BlankGoogleAdPolicyTest {
    private val actual=listOf("android.widget.FrameLayout","android.widget.LinearLayout","android.widget.RelativeLayout","android.widget.FrameLayout")
    private fun allowed(activity:String="com.google.android.gms.ads.AdActivity",timeout:Boolean=true,elapsed:Long=96_000,used:Boolean=false,
        labels:List<String> =emptyList(),classes:List<String> =actual,controls:Int=0,black:Boolean=true)=
        BlankGoogleAdPolicy.mayReturn(activity,timeout,elapsed,used,labels,classes,controls,black)
    @Test fun actualDestroyedWebviewWindowAllowsOneNormalReturnAfterTimeout(){assertTrue(allowed())}
    @Test fun normalVideoAndRealControlsAreNeverBlankRecovery(){assertFalse(allowed(classes=actual+"android.view.SurfaceView"));assertFalse(allowed(classes=actual+"android.webkit.WebView"));assertFalse(allowed(controls=1));assertFalse(allowed(labels=listOf("動画再生中")));assertFalse(allowed(black=false))}
    @Test fun cannotReturnEarlyTwiceOrFromOtherComponents(){assertFalse(allowed(elapsed=94_999));assertFalse(allowed(timeout=false));assertFalse(allowed(used=true));assertFalse(allowed(activity="us.ana_pocket.ANAPocket.MainActivity"))}
}
