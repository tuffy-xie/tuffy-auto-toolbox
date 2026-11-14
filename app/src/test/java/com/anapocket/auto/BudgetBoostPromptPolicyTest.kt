package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test

class BudgetBoostPromptPolicyTest {
    private val p="us.ana_pocket.ANAPocket:id/"
    private val ids=setOf("title_count","current_bonus_label","current_point","count","point","point_unit","button_see_ad_text","button_no","button_no_text","note","text_ad_title").map{p+it}.toSet()
    /** Labels of the real 11/11 00:44 screen (BIKE 500m, Boost 3/5, 100pt). */
    private val labels=listOf("4回目のブーストをしますか？","現在のチャレンジ達成後の","100pt","1回","+25","pt","2回","+25","pt","3回","+25","pt","4回","+50","pt","5回","+50","pt",
        "動画を見て報酬をブースト","ブーストをやめる","※この画面を閉じると\n ブーストができなくなります","[広告]","毎年続く、食のよろこび")

    @Test fun realBudgetPromptForTheSameTaskMatches(){
        assertEquals(4,ChallengeSignals.nextBoost(labels))
        assertTrue(BudgetBoostPromptPolicy.matches(ids,labels,"AD_BUDGET_REACHED",3,100))
    }
    @Test fun otherStatesTasksAndScreensDoNotMatch(){
        assertFalse(BudgetBoostPromptPolicy.matches(ids,labels,"JOINED",3,100))
        assertFalse(BudgetBoostPromptPolicy.matches(ids,labels,"BOOST_MAX_CONFIRMED",3,100))
        assertFalse(BudgetBoostPromptPolicy.matches(ids,labels,"AD_BUDGET_REACHED",2,100))
        assertFalse(BudgetBoostPromptPolicy.matches(ids,labels,"AD_BUDGET_REACHED",3,75))
        assertFalse(BudgetBoostPromptPolicy.matches(ids-(p+"button_no"),labels,"AD_BUDGET_REACHED",3,100))
        assertFalse(BudgetBoostPromptPolicy.matches(ids,labels-"ブーストをやめる","AD_BUDGET_REACHED",3,100))
        assertFalse(BudgetBoostPromptPolicy.matches(ids,labels,"AD_BUDGET_REACHED",5,100))
    }
}
