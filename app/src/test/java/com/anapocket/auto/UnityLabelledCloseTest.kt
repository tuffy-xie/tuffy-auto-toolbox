package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class UnityLabelledCloseTest {
    private val actual = listOf("Close", "パルシィ　話題の少女マンガ、女性漫画が読めるアプリ", "价格", "免费", "安装", "Privacy")
    private fun allowed(label:String="Close", cls:String="android.widget.Button", clickable:Boolean=true,
        labels:List<String> = actual, left:Int=34, top:Int=83, width:Int=118, height:Int=118) =
        UnityClosePolicy.labelledEndControl(label,cls,clickable,labels,left,top,width,height,720,1650,2f)

    @Test fun actualTopLeftExplicitCloseIsAllowed() { assertTrue(allowed()) }
    @Test fun bottomPrivacyAndAnonymousSvgRemainRejected() {
        assertFalse(allowed(label="Privacy",left=24,top=1591,width=40,height=59))
        assertFalse(allowed(label="",cls="android.view.View",left=2,top=1617,width=30,height=30))
        assertFalse(UnityClosePolicy.endControl(2,1617,30,30,720,1650,2f))
    }
    @Test fun installAndUnlabelledOrUnclickableNodesAreNotClose() {
        assertFalse(allowed(label="安装")); assertFalse(allowed(cls="android.widget.Image"))
        assertFalse(allowed(clickable=false))
    }
    @Test fun actualEndCardContextAndSmallTopBoundsRequired() {
        assertFalse(allowed(labels=listOf("Close"))); assertFalse(allowed(top=800))
        assertFalse(allowed(width=720,height=1600)); assertFalse(allowed(left=-1))
    }
}
