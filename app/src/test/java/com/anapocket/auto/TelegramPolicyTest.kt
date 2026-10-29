package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class TelegramPolicyTest {
    private val token="123456789:abcdefghijklmnopqrstuvwxyz_123456789"
    @Test fun tokenShapeExcludesUrlInjection() {assertTrue(TelegramPolicy.tokenValid(token));for(t in listOf("", "abc",token+"/sendMessage",token+"?x=1",token+"\n", "https://bad"))assertFalse(TelegramPolicy.tokenValid(t))}
    @Test fun targetSupportsPrivateGroupAndChannel() {for(c in listOf("1234567","-1001234567890","@my_channel"))assertTrue(TelegramPolicy.chatValid(c));for(c in listOf("","alice","https://t.me/test","@ab", "12 34"))assertFalse(TelegramPolicy.chatValid(c))}
    @Test fun identityChecksUsernameWhenSupplied() {assertTrue(TelegramPolicy.matchesBot("@My_Test_Bot","my_test_bot"));assertTrue(TelegramPolicy.matchesBot("","actual_bot"));assertFalse(TelegramPolicy.matchesBot("@wrong_bot","actual_bot"))}
    @Test fun privateReceiverMustBeUnambiguous() {assertEquals("123",TelegramPolicy.privateChat(listOf("123","123")));assertNull(TelegramPolicy.privateChat(emptyList()));assertNull(TelegramPolicy.privateChat(listOf("123","456")))}
    @Test fun configToStringNeverExposesToken() {assertFalse(TelegramConfig(true,"name",token,"123").toString().contains(token))}
    @Test fun queuedTargetChangesOnTokenOrReceiverChange() {val a=TelegramConfig(true,"bot",token,"123");assertEquals(64,TelegramPolicy.fingerprint(a).length);assertNotEquals(TelegramPolicy.fingerprint(a),TelegramPolicy.fingerprint(a.copy(chatId="456")));assertNotEquals(TelegramPolicy.fingerprint(a),TelegramPolicy.fingerprint(a.copy(token=token+"x")))}
    @Test fun onlyExplicitTransientRejectionsRetryAtMostTwice() {for(code in listOf(429,500,502,503,504)){assertTrue(TelegramPolicy.retry(0,true,code));assertTrue(TelegramPolicy.retry(1,true,code));assertFalse(TelegramPolicy.retry(2,true,code))};for(code in listOf(0,200,400,401,403,409))assertFalse(TelegramPolicy.retry(0,true,code))}
    @Test fun lostReplyNeverAutomaticallyResendsPossiblyDeliveredMessage() {for(code in listOf(0,429,500,502))assertFalse(TelegramPolicy.retry(0,false,code))}
    @Test fun dailySummarySeparatesRunTodayAndUnknown() {val t=TelegramText.daily("2026-10-28",25,21,21,0,4,21,21,TelegramText.reason("local_started_limit"));assertTrue(t.contains("本次完成 21"));assertTrue(t.contains("今日启动 25/25"));assertTrue(t.contains("未确认 4"));assertEquals(5,t.lines().size);assertTrue(t.length<180);assertFalse(t.contains("到账"));assertFalse(t.contains("RUN_ID"))}
    @Test fun challengeSummaryKeepsObservedHistoryAndOfferedRewardDistinct() {val t=TelegramText.challenge("2026-10-28",7,6,6,5,1,"范围内任务已完成");assertTrue(t.contains("历史未确认 1"));assertTrue(t.contains("不等于积分到账"));assertTrue(t.length<180)}
    @Test fun deliveredAndInterruptedEventsAreNeverDispatchedAgain() {for(state in listOf("sent","sending","uncertain","failed"))assertFalse(TelegramPolicy.mayBeginDelivery(state));assertTrue(TelegramPolicy.mayBeginDelivery(""));assertTrue(TelegramPolicy.mayBeginDelivery("retry"))}
    @Test fun rawErrorsAndSecretsNeverBecomePushText() {val secret="https://api.telegram.org/bot"+token;assertEquals("执行中止，请查看手机记录",TelegramText.reason(secret));assertFalse(TelegramText.reason(secret).contains(token))}
}
