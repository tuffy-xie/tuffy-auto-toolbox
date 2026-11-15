package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class OguryClosePolicyTest {
 private val ids=setOf("root","format","optout","close-btn");private val labels=listOf("ogury_close_button")
 private fun match(pkg:String=StartupPolicy.ANA,activity:String="com.ogury.ad.interstitial.ui.InterstitialActivity",id:String="close-btn",label:String="ogury_close_button",clazz:String="android.widget.Button",w:Float=25f,h:Float=25f)=OguryClosePolicy.control(pkg,activity,ids,labels,id,label,clazz,w,h)
 @Test fun actualObservedVendorButtonIsAccepted(){assertTrue(match())}
 @Test fun restartedServiceCanRecognizeTheExactObservedSdkDocument(){assertTrue(match(activity=""));assertFalse(OguryClosePolicy.surface(StartupPolicy.ANA,"",setOf("root"),labels))}
 @Test fun browserAndPrivacyControlsAreNeverTapped(){assertFalse(match(pkg="com.android.chrome"));assertFalse(match(id="optout"));assertFalse(match(label="Privacy"))}
 @Test fun wholeAdOrUnlabelledImagesCannotBecomeAClosingTarget(){assertFalse(match(w=360f,h=750f));assertFalse(match(clazz="android.widget.ImageView"));assertFalse(match(label=""))}
 // 11/16 BIKE_2 end screen: 「继续」 Button id=continue_button, 102x76 px at density 2.
 private val endIds=setOf("root","format","video_fs_container","end_screen","end_screen_image","optout","continue_button")
 private fun end(id:String="continue_button",ids:Set<String> =endIds,activity:String="com.ogury.ad.interstitial.ui.InterstitialActivity")=
  OguryClosePolicy.control(StartupPolicy.ANA,activity,ids,labels,id,"ogury_close_button","android.widget.Button",51f,38f)
 @Test fun nov16EndScreenContinueIsTheVendorClose(){assertTrue(end());assertTrue(end(activity=""))}
 @Test fun continueBeforeTheEndScreenOrOptoutIsRefused(){assertFalse(end(ids=endIds-"end_screen"));assertFalse(end(id="optout"))}
 @Test fun unknownActivityWithoutExactDocumentCannotAuthorizeClose(){assertFalse(OguryClosePolicy.control(StartupPolicy.ANA,"com.unknown.Activity",emptySet(),emptyList(),"close-btn","ogury_close_button","android.widget.Button",25f,25f))}
}
