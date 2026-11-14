package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class NativeDialogAdClosePolicyTest {
    private val pkg=NativeDialogAdClosePolicy.ANA
    private val act=NativeDialogAdClosePolicy.ACTIVITY
    private fun n(cls:String,l:Int,t:Int,r:Int,b:Int,click:Boolean=false,label:String="",id:String?=null)=DialogNode("android.widget.$cls",id,label,click,l,t,r,b)
    /** Visible nodes of the real 11/11 00:09 fault capture (BIKE_2 Boost ad, PeopleX creative). */
    private val real=listOf(
        n("FrameLayout",0,49,720,1650),n("LinearLayout",0,49,720,1650),n("FrameLayout",0,49,720,1650,id="android:id/content"),
        n("FrameLayout",0,49,720,1650),n("FrameLayout",0,49,720,1650),n("FrameLayout",0,49,720,1650),
        n("ImageView",0,49,720,1650),n("ImageView",648,49,720,121,click=true),
        n("FrameLayout",0,210,720,1488),n("FrameLayout",0,210,720,1488,click=true),n("ImageView",0,210,720,1488),
        n("ImageView",0,576,720,981),n("FrameLayout",0,576,720,981),n("FrameLayout",0,576,720,981),
        DialogNode("android.view.View",null,"",false,0,576,720,981),n("ImageView",0,576,720,981),n("ImageView",618,576,719,609),
        n("ImageView",0,1094,720,1487),n("TextView",22,1128,697,1173,label="書類だけで判断しない採用へ"),
        n("TextView",22,1202,539,1233,label="PeopleX"),n("TextView",22,1274,697,1364,label="対話型AIが人に代わって面接。"),
        n("ImageView",22,1375,697,1465),n("TextView",191,1402,528,1435,label="詳しくはこちら"))

    @Test fun realNativeDialogAdCornerCloseIsFound(){
        assertTrue(NativeDialogAdClosePolicy.surface(pkg,act,real,720,1650))
        val c=NativeDialogAdClosePolicy.close(pkg,act,real,720,1650,2f)
        assertNotNull(c);assertEquals(648,c!!.left);assertEquals(49,c.top)
    }
    @Test fun adBodyAndCtaAreNeverChosen(){
        val c=NativeDialogAdClosePolicy.close(pkg,act,real,720,1650,2f)!!
        assertTrue(c.right-c.left<100)
    }
    @Test fun anaOwnDialogsWithIdsAreNotAds(){
        val withId=real+n("TextView",100,500,600,560,label="400pt",id="us.ana_pocket.ANAPocket:id/title")
        assertFalse(NativeDialogAdClosePolicy.surface(pkg,act,withId,720,1650))
        assertNull(NativeDialogAdClosePolicy.close(pkg,act,withId,720,1650,2f))
    }
    @Test fun otherWindowsPackagesAndAmbiguousCornersAreRejected(){
        assertFalse(NativeDialogAdClosePolicy.surface("foreign",act,real,720,1650))
        assertFalse(NativeDialogAdClosePolicy.surface(pkg,"us.ana_pocket.ANAPocket.views.home.HomeActivity",real,720,1650))
        assertFalse(NativeDialogAdClosePolicy.surface(pkg,act,real+n("ImageView",650,60,716,126,click=true),720,1650))
        assertFalse(NativeDialogAdClosePolicy.surface(pkg,act,real.filterNot{it.cls.endsWith("TextView")},720,1650))
        assertFalse(NativeDialogAdClosePolicy.surface(pkg,act,real.filterNot{it.left==0&&it.top==49&&it.cls.endsWith("ImageView")},720,1650))
    }
    @Test fun labelledOrHugeCornerControlIsNotAClose(){
        val labelled=real.map{if(it.left==648)it.copy(label="詳しくはこちら") else it}
        assertNull(NativeDialogAdClosePolicy.close(pkg,act,labelled,720,1650,2f))
        val huge=real.map{if(it.left==648)it.copy(left=560,bottom=209) else it}
        assertNull(NativeDialogAdClosePolicy.close(pkg,act,huge,720,1650,2f))
    }
}
