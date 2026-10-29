package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class OfficialDailyMessageTest {
 @Test fun quotaIsOfficialAndNewRunCreditIsSeparate(){val t=TelegramText.officialDaily("D",25,2,4,"");assertTrue(t.contains("官方次数：25/25"));assertTrue(t.contains("本次完成：2次"));assertTrue(t.contains("待核对：4次"));assertFalse(t.contains("27/25"));assertFalse(t.contains("全部完成"))}
 @Test fun unknownOfficialCountNeverInventsRemainingOrCompletion(){val t=TelegramText.officialDaily("D",null,0,1,"需核对");assertTrue(t.contains("未核对"));assertFalse(t.contains("待完成：25"));assertFalse(t.contains("25/25"));assertTrue(t.contains('\n'));assertFalse(t.contains("\\n"))}
}
