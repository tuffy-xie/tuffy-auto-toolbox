package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SafetyPolicyTest {
 private fun frame(labels:List<String>,sdk:Boolean=false,ids:Set<String> = emptySet(),edit:Boolean=false,pkg:String=StartupPolicy.ANA)=RiskFrame(pkg,sdk,labels,ids,edit)
 @Test fun nativeAccountWarningsStop(){assertEquals(RiskKind.ACCOUNT_WARNING,AccountRiskPolicy.detect(frame(listOf("不正な操作を検知しました"))));assertEquals(RiskKind.ACCOUNT_WARNING,AccountRiskPolicy.detect(frame(listOf("アカウント","ご利用を制限しています"))))}
 @Test fun adsAndUnrelatedAppsDoNotTriggerAccountWarning(){assertNull(AccountRiskPolicy.detect(frame(listOf("不正な操作を検知しました"),sdk=true)));assertNull(AccountRiskPolicy.detect(frame(listOf("账号已被限制"),pkg="org.telegram.messenger")))}
 @Test fun normalAccountSettingsAreNotWarnings(){assertNull(AccountRiskPolicy.detect(frame(listOf("アカウント","削除","プライバシー","利用規約"))))}
 @Test fun captchaWidgetStopsEvenInsideSdk(){assertEquals(RiskKind.HUMAN_VERIFICATION,AccountRiskPolicy.detect(frame(listOf("Verify"),true,setOf("recaptcha-anchor"))));assertNull(AccountRiskPolicy.detect(frame(listOf("Verify"),true,setOf("verify_ad_button"))))}
 @Test fun robotPromptAndEditableCodeRequireHuman(){assertEquals(RiskKind.HUMAN_VERIFICATION,AccountRiskPolicy.detect(frame(listOf("私はロボットではありません"))));assertEquals(RiskKind.HUMAN_VERIFICATION,AccountRiskPolicy.detect(frame(listOf("認証コード"),edit=true)));assertNull(AccountRiskPolicy.detect(frame(listOf("認証コード"),sdk=true,edit=false)))}
 @Test fun clearRequiresSameIncidentAndCleanNativeScene(){assertTrue(AccountRiskPolicy.mayAcknowledge("a","a",true,null));assertFalse(AccountRiskPolicy.mayAcknowledge("a","b",true,null));assertFalse(AccountRiskPolicy.mayAcknowledge("a","a",false,null));assertFalse(AccountRiskPolicy.mayAcknowledge("a","a",true,RiskKind.HUMAN_VERIFICATION))}
 @Test fun unlockedSecurePhoneCanRunButLockedSecurePhoneCannot(){assertEquals(DeviceReadyAction.READY,DeviceReadyPolicy.action(true,false,true,0));assertEquals(DeviceReadyAction.OWNER_REQUIRED,DeviceReadyPolicy.action(false,true,true,0));assertEquals(DeviceReadyAction.OWNER_REQUIRED,DeviceReadyPolicy.action(true,true,true,0))}
 @Test fun onlyNonsecureWakeIsAttemptedAndBounded(){assertEquals(DeviceReadyAction.WAKE_NONSECURE,DeviceReadyPolicy.action(false,true,false,0));assertEquals(DeviceReadyAction.WAKE_NONSECURE,DeviceReadyPolicy.action(false,true,false,1));assertEquals(DeviceReadyAction.EXHAUSTED,DeviceReadyPolicy.action(false,true,false,2))}
 @Test fun swipeRequiresActuallyObservedUnsecuredSystemKeyguard(){val ids=setOf("com.android.systemui:id/keyguard_status_view");assertTrue(DeviceReadyPolicy.canSwipe("com.android.systemui",ids,true,false,0));assertFalse(DeviceReadyPolicy.canSwipe(StartupPolicy.ANA,ids,true,false,0));assertFalse(DeviceReadyPolicy.canSwipe("com.android.systemui",ids,false,false,0));assertFalse(DeviceReadyPolicy.canSwipe("com.android.systemui",ids,true,true,0));assertFalse(DeviceReadyPolicy.canSwipe("com.android.systemui",emptySet(),true,false,0));assertFalse(DeviceReadyPolicy.canSwipe("com.android.systemui",ids,true,false,2))}
 @Test fun privacyOverlayRequiresActualUnityControlPair(){assertTrue(UnityClosePolicy.privacyOverlay(listOf("button:privacy-back","button:privacy-request-data","Privacy")));assertFalse(UnityClosePolicy.privacyOverlay(listOf("Privacy","Close")))}
 @Test fun actualBottomSvgCloseIsRejected(){assertFalse(UnityClosePolicy.endControl(2,1617,30,30,720,1650,2f));assertFalse(UnityClosePolicy.endControl(8,1587,56,56,720,1650,2f))}
 @Test fun completeTopRightUnityEndControlIsAllowed(){assertTrue(UnityClosePolicy.endControl(628,93,48,48,720,1650,2f));assertFalse(UnityClosePolicy.endControl(710,93,48,48,720,1650,2f));assertFalse(UnityClosePolicy.endControl(30,93,48,48,720,1650,2f))}
 @Test fun manualPauseWinsOverCloseAndReward()=runBlocking{
  var clock=0L;var frames=0;var closes=0;var receipts=0
  val p=object:AdInteractionPort{override fun now()=clock;override fun stopped()=false;override suspend fun state(state:AdState){};override suspend fun frame()=if(frames++==0)AdFrame(true,false,adSurface=true) else AdFrame(true,true,watched=1,receipt=true,closeAvailable=true,manualRequired=true);override suspend fun close():Boolean{closes++;return true};override suspend fun dismissReceipt(){receipts++};override suspend fun pause(ms:Long){clock+=ms}}
  val r=AdInteraction(p).awaitReceipt(0);assertEquals(AdState.BLOCKED,r.state);assertFalse(r.completed);assertFalse(r.reward);assertEquals(0,closes);assertEquals(0,receipts)
 }
 @Test fun loginManualPauseCannotBeClaimedAsReward()=runBlocking{
  var clock=0L;var close=0
  val p=object:StartupAdPort{override fun now()=clock;override fun stopped()=false;override fun sameDate()=true;override suspend fun frame()=StartupAdFrame(ad=true,receipt=true,close=true,manualRequired=true);override suspend fun close(){close++};override suspend fun observedAd(){};override suspend fun pause(ms:Long){clock+=ms}}
  assertEquals("BLOCKED",StartupAdSequence(p).run(true).state);assertEquals(0,close)
 }
}
