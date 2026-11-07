package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class StartupPolicyTest{
 private val ids=setOf("button_get_point","bonus_point","recycler","button_ok")
 private val labels=listOf("ログインボーナス","連続ログインでボーナスポイントUP","動画を見てさらに50ptをGET")
 @Test fun installedLayoutRecognizesSpecificLoginOffer(){
  assertTrue(StartupPolicy.loginOffer(StartupPolicy.ANA,labels,ids))
  val actual=listOf("ログインボーナス","連続ログインでボーナスポイントUP","1日目","2日目","動画を見てさらに\n100ptをGET","閉じる")
  assertTrue(StartupPolicy.loginOffer(StartupPolicy.ANA,actual,setOf("bonus_point","button_ok")))
  assertEquals("bonus_point",StartupPolicy.loginClaimId(StartupPolicy.ANA,actual,setOf("bonus_point","button_ok")))
  assertTrue(StartupPolicy.loginOffer(StartupPolicy.ANA,labels,ids-"bonus_point"))
 }
 @Test fun genericClaimButtonOrChallengeNeverMatches(){assertFalse(StartupPolicy.loginOffer(StartupPolicy.ANA,listOf("報酬","GET"),ids));assertFalse(StartupPolicy.loginOffer(StartupPolicy.ANA,listOf("動画を見て報酬をブースト"),ids))}
 @Test fun foreignAppCannotOfferANAReward(){assertFalse(StartupPolicy.loginOffer("third.party",labels,ids))}
 @Test fun incompleteStructureNeverStartsLoginAd(){
  assertFalse(StartupPolicy.loginOffer(StartupPolicy.ANA,labels,ids-"button_ok"))
  assertFalse(StartupPolicy.loginOffer(StartupPolicy.ANA,labels,ids-setOf("button_get_point","bonus_point")))
  assertFalse(StartupPolicy.loginOffer(StartupPolicy.ANA,labels,ids-"recycler"))
  assertFalse(StartupPolicy.loginOffer(StartupPolicy.ANA,listOf("ログインボーナス","連続ログインでボーナスポイントUP","GET"),ids))
 }
 @Test fun receiptRequiresNativeForegroundStructure(){assertTrue(StartupPolicy.rewardReceipt(StartupPolicy.ANA,listOf("報酬をゲット！"),setOf("button_ok"),false));assertFalse(StartupPolicy.rewardReceipt(StartupPolicy.ANA,listOf("報酬をゲット！"),emptySet(),false));assertFalse(StartupPolicy.rewardReceipt("sdk.app",listOf("報酬をゲット！"),setOf("button_ok"),false))}
 @Test fun sdkSurfaceReceiptLookalikeDoesNotConfirm(){assertFalse(StartupPolicy.rewardReceipt(StartupPolicy.ANA,listOf("報酬をゲット！"),setOf("button_ok"),true))}
 @Test fun closeAndReturnAreNotRewardProof(){assertFalse(StartupPolicy.loginConfirmed(true,true,false,true));assertFalse(StartupPolicy.loginConfirmed(true,false,true,true));assertFalse(StartupPolicy.loginConfirmed(false,true,true,true));assertFalse(StartupPolicy.loginConfirmed(true,true,true,false));assertTrue(StartupPolicy.loginConfirmed(true,true,true,true))}
 @Test fun nativePopupDoesNotCloseLoginOrReceipt(){
  val x=setOf("image_close","content");assertTrue(StartupPolicy.nativePopup(StartupPolicy.ANA,x,false,false,false))
  assertTrue(StartupPolicy.loginContext(StartupPolicy.ANA,labels));assertFalse(StartupPolicy.loginOffer(StartupPolicy.ANA,labels,x))
  assertFalse(StartupPolicy.nativePopup(StartupPolicy.ANA,x,StartupPolicy.loginContext(StartupPolicy.ANA,labels),false,false))
  assertFalse(StartupPolicy.nativePopup(StartupPolicy.ANA,x,false,true,false))
 }
 @Test fun sdkAndForeignPopupNeverUseNativeClose(){val x=setOf("image_close","content");assertFalse(StartupPolicy.nativePopup("other",x,false,false,false));assertFalse(StartupPolicy.nativePopup(StartupPolicy.ANA,x,false,false,true))}
 @Test fun duplicateAndUnknownLoginNeverReserveAgainSameDay(){for(s in listOf("AD_OPENING","AD_PLAYING","UNCONFIRMED","REWARD_CONFIRMED","FAILED"))assertFalse(StartupPolicy.canReserveLogin("2026-10-28","2026-10-28",s))}
 @Test fun missingPopupMayBeCheckedAgainWithoutClaimingCompletion(){assertTrue(StartupPolicy.canReserveLogin("2026-10-28","2026-10-28","NOT_PRESENT"));assertTrue(StartupPolicy.canReserveLogin("2026-10-28",null,null))}
 @Test fun actualNextDateUsesNewLoginState(){assertTrue(StartupPolicy.canReserveLogin("2026-10-29","2026-10-28","REWARD_CONFIRMED"));assertTrue(StartupPolicy.canReserveLogin("2026-10-29","2026-10-28","UNCONFIRMED"))}
}
