package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** The Nov 9 login ad: AppLovin end card whose ✕ stayed on screen for five minutes without being recognised. */
class AppLovinCloseTest {
    private val activity="com.applovin.adview.AppLovinFullscreenActivity"
    private val frame="android.widget.FrameLayout"
    private fun wrapper(act:String=activity,cls:String=frame,label:String="",clickable:Boolean=true,children:Int=1,left:Int=626,top:Int=133)=
        AppLovinClosePolicy.wrapper(act,cls,label,clickable,children,left,top,720,1650)

    @Test fun acceptsTheObservedWrapper(){assertTrue(wrapper())}
    @Test fun rejectsOtherShapes(){
        assertFalse(wrapper(act="com.five_corp.ad.AdActivity"))
        assertFalse(wrapper(cls="android.widget.ImageView"))
        assertFalse(wrapper(label="求人詳細"))
        assertFalse(wrapper(clickable=false))
        assertFalse(wrapper(children=2))
        assertFalse(wrapper(left=10))
        assertFalse(wrapper(top=1200))
    }
    private fun skip(act:String=activity,cls:String="android.view.View",label:String="",clickable:Boolean=true,left:Int=22,top:Int=63,w:Int=66,h:Int=66)=
        AppLovinClosePolicy.skip(act,cls,label,clickable,left,top,w,h,720,1650,2f)
    @Test fun acceptsTheObservedVideoSkip(){assertTrue(skip())}
    @Test fun skipRejectsOtherControls(){
        assertFalse(skip(act="com.five_corp.ad.AdActivity"))
        assertFalse(skip(cls="android.widget.Button"))
        assertFalse(skip(label="Shop Now"))
        assertFalse(skip(clickable=false))
        assertFalse("the end card's close sits top right",skip(left=628,top=73,w=60,h=60))
        assertFalse("the info icon is bottom left",skip(left=24,top=1497,w=32,h=34))
        assertFalse("the full page",skip(left=0,top=49,w=720,h=1506))
    }
    @Test fun recognisesTheVideoEndCardCross(){
        val rows=javaClass.getResourceAsStream("/applovin/video_end_close_60.argb")!!.bufferedReader().readLines().filter{it.isNotBlank()}
        val pixels=rows.flatMap{it.trim().split(' ')}.map{java.lang.Long.parseLong(it,16).toInt()}.toIntArray()
        assertEquals(60*60,pixels.size)
        assertTrue(AdCloseIcon.isWhiteCross(60,60,pixels))
    }
    @Test fun recognisesThePaintedCrossFromTheRealScreen(){
        val rows=javaClass.getResourceAsStream("/applovin/endcard_close_54.argb")!!.bufferedReader().readLines().filter{it.isNotBlank()}
        val pixels=rows.flatMap{it.trim().split(' ')}.map{java.lang.Long.parseLong(it,16).toInt()}.toIntArray()
        assertEquals(54*54,pixels.size)
        assertTrue(AdCloseIcon.isWhiteCircleCross(54,54,pixels))
    }
}
