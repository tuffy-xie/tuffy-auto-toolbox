package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class MBridgePlaybackPolicyTest {
    private val actual=listOf("Confirm to close?","You will not be rewarded after closing the window","Close it","Continue","28s left to be rewarded")
    @Test fun actualTwentyEightSecondCountdownBlocksEarlyX(){assertEquals(28,MBridgePlaybackPolicy.remaining(actual));assertTrue(MBridgePlaybackPolicy.mustWait(actual))}
    @Test fun onlyExactObservedWarningCanContinueSameAdTwice(){assertTrue(MBridgePlaybackPolicy.mayContinue(actual,0));assertTrue(MBridgePlaybackPolicy.mayContinue(actual,1));assertFalse(MBridgePlaybackPolicy.mayContinue(actual,2));assertFalse(MBridgePlaybackPolicy.mayContinue(listOf("Continue"),0))}
    @Test fun zeroTimerIsNotARewardButDoesNotBlockRealClose(){assertFalse(MBridgePlaybackPolicy.mustWait(listOf("0s left to be rewarded")));assertNull(MBridgePlaybackPolicy.remaining(listOf("Earn rewards today")))}
    @Test fun duplicatedOrMixedCountdownNodesStayConservative(){assertTrue(MBridgePlaybackPolicy.mustWait(listOf("28s left to be rewarded","28s left to be rewarded","0s left to be rewarded")))}
    private fun control(id:String="m-playable-skip",cls:String="android.widget.TextView",clickable:Boolean=true,left:Int=630,top:Int=30,w:Int=60,h:Int=60,labels:List<String> = emptyList())=
        MBridgePlaybackPolicy.playableControl(id,cls,clickable,left,top,w,h,720,1650,2f,labels)
    @Test fun actualPlayableNextAndCloseAreSmallTopRightSdkControls(){assertTrue(control());assertTrue(control(id="m-playable-close"))}
    @Test fun endCardCloseSeenOnNov9IsAccepted(){assertTrue(control(id="close",left=630,top=78,w=60,h=62));assertFalse(control(id="close",left=630,top=78,w=60,h=62,labels=listOf("28s left to be rewarded")));assertFalse(control(id="privacyIcon",left=30,top=78,w=60,h=62))}
    @Test fun wholeInstallPageAndInfoOrUnclickableNodesAreExcluded(){assertFalse(control(id="m-playable-skip-page",left=0,top=0,w=720,h=1650));assertFalse(control(id="Install"));assertFalse(control(left=20));assertFalse(control(clickable=false));assertFalse(control(cls="android.widget.Image"))}
    @Test fun rewardCountdownAndForfeitWarningStillOverridePlayableControl(){assertFalse(control(labels=listOf("28s left to be rewarded")));assertFalse(control(labels=actual))}
}
