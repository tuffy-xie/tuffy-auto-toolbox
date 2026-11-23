package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

/** 11/23 01:07, 01:26, 01:44: three Boost ads ran to 300 s on Mintegral's native end card; its X was never found. */
class MBridgeEndCardCloseTest {
    private val pre="us.ana_pocket.ANAPocket:id/"
    // Visible ids from the 01:12:42 (BIKE_6) and 01:49:53 (CAR_6) fault trees.
    private val endCard=setOf(pre+"mbridge_reward_root_container",pre+"mbridge_temp_container",
        pre+"mbridge_video_templete_webview_parent",pre+"mbridge_video_templete_container",
        pre+"mbridge_reward_end_card_more_offer_rl",pre+"mbridge_reward_end_card_like_tv",pre+"mbridge_moreoffer_hls",
        pre+"mbridge_more_offer_ll_item",pre+"mbridge_reward_end_card_item_iv",pre+"mbridge_reward_end_card_item_title_tv")
    private val labels=listOf("hiabia","2.95万","查看","猜你喜欢","Super Billiards Real 8 Ball")
    private fun close(ids:Set<String> = endCard,id:String?=null,cls:String="android.widget.ImageView",clickable:Boolean=true,
        label:String="",children:Int=0,l:Int,t:Int,r:Int,b:Int,ls:List<String> = labels)=
        MBridgePlaybackPolicy.endCardCornerClose(ids,id,cls,clickable,label,children,l,t,r-l,b-t,720,1650,2f,ls)

    @Test fun nov23EndCardTopRightXIsTheClose() {
        assertTrue(close(l=650,t=40,r=700,b=90))
    }
    @Test fun feedbackIconAndCreativeAreNeverTheClose() {
        assertFalse(close(l=20,t=40,r=70,b=90))        // "!" feedback, top-left
        assertFalse(close(l=4,t=200,r=716,b=600))       // creative image
        assertFalse(close(l=290,t=730,r=430,b=870))     // app icon
        assertFalse(close(cls="android.widget.TextView",label="查看",l=89,t=1252,r=631,b=1376))
        assertFalse(close(id=pre+"mbridge_more_offer_ll_item",cls="android.widget.LinearLayout",l=630,t=1475,r=710,b=1625))
    }
    @Test fun onlyOnTheNativeEndCard() {
        // 01:07:47 the same corner held the playback X at (650,79); without the more-offer row this rule says nothing.
        assertFalse(close(ids=endCard-(pre+"mbridge_reward_end_card_more_offer_rl"),l=650,t=79,r=700,b=129))
        assertTrue(MBridgePlaybackPolicy.nativeEndCard(endCard))
    }
    @Test fun neverWhileACountdownOrWarningShows() {
        assertFalse(close(l=650,t=40,r=700,b=90,ls=labels+"12s left to be rewarded"))
        assertFalse(close(l=650,t=40,r=700,b=90,ls=labels+listOf("Confirm to close?",
            "You will not be rewarded after closing the window","Close it","Continue")))
    }
    @Test fun shapeAndPlacementBounds() {
        assertFalse(close(clickable=false,l=650,t=40,r=700,b=90))
        assertFalse(close(label="Close",l=650,t=40,r=700,b=90))
        assertFalse(close(children=1,l=650,t=40,r=700,b=90))
        assertFalse(close(l=650,t=200,r=700,b=250))     // below the top bar
        assertFalse(close(l=600,t=40,r=720,b=90))       // not square
    }
}
