package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/14 01:31 CAR 5km join, 720x1650: Button 「打开商店」 Rect(56,103-202,155) and Privacy Rect(8,1587-64,1643), no close. */
class UnityStoreSkipTest {
    private fun skip(activity:String?=UnityClosePolicy.ACTIVITY,cls:String="android.widget.Button",label:String="打开商店",clickable:Boolean=true,
        left:Int=56,top:Int=103,w:Int=146,h:Int=52,skips:Int=0)=
        UnityClosePolicy.storeSkip(activity,cls,label,clickable,left,top,w,h,720,1650,skips)

    @Test fun theObservedTopLeftStoreButtonIsTheLastResort(){assertTrue(skip())}
    @Test fun onlyOnTheUnityPlayable(){
        assertFalse(skip(activity="com.applovin.adview.AppLovinFullscreenActivity"))
        assertFalse(skip(activity=null))
    }
    @Test fun neverPrivacyPlayNowOrAnInstallButton(){
        assertFalse(skip(label="Privacy",left=8,top=1587,w=56,h=56))
        assertFalse(skip(label="Play Now",left=118,top=1460,w=486,h=108))
        assertFalse(skip(label="安装"))
        assertFalse(skip(left=56,top=1460))
        assertFalse(skip(left=500))
    }
    @Test fun atMostTwicePerAd(){assertTrue(skip(skips=1));assertFalse(skip(skips=UnityClosePolicy.MAX_STORE_SKIPS))}
    @Test fun notBeforeItStoodAloneForThirtySeconds(){
        assertFalse(UnityClosePolicy.storeSkipDue(29_999));assertTrue(UnityClosePolicy.storeSkipDue(30_000))
    }
    @Test fun mustBeAClickableButton(){assertFalse(skip(clickable=false));assertFalse(skip(cls="android.view.View"))}
}
