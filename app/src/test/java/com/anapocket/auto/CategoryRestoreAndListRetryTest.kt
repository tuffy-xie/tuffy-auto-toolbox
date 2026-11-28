package com.anapocket.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRestoreAndListRetryTest {
    // 11/28 01:44: MOV_ANY icon tapped after MAX, selection not seen once; the run blocked on the boosted challenge.
    @Test fun categoryRestoreGetsOneFreshTry(){
        assertTrue(MaxBoostDialogPolicy.retryCategoryRestore(1,"category_selection_not_verified_MOV_ANY"))
        assertTrue(MaxBoostDialogPolicy.retryCategoryRestore(1,"category_icon_not_found_CAR"))
        assertTrue(MaxBoostDialogPolicy.retryCategoryRestore(1,"category_strip_missing"))
        assertFalse(MaxBoostDialogPolicy.retryCategoryRestore(2,"category_selection_not_verified_MOV_ANY"))
    }
    @Test fun categoryRestoreNeverRetriesStopsOrPopups(){
        assertFalse(MaxBoostDialogPolicy.retryCategoryRestore(1,"user_stopped"))
        assertFalse(MaxBoostDialogPolicy.retryCategoryRestore(1,"startup_popup_blocks_category_navigation"))
        assertFalse(MaxBoostDialogPolicy.retryCategoryRestore(1,"category_icon_ambiguous_WALK"))
        assertFalse(MaxBoostDialogPolicy.retryCategoryRestore(1,null))
    }
    // 11/28 02:50: the unclaimed list was not reached after an ANA restart; the rest waited for the next day.
    @Test fun unreachableListUsesTheRewardRetryBudget(){
        val r=RewardClaimPolicy.LIST_UNREACHABLE_REASON
        assertEquals(45,RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,0,2*60+50,false,true,false))
        assertEquals(90,RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,2,6*60,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,3,6*60,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL",r,0,2*60,true,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL","choice_sheet_stuck",0,2*60,false,true,false))
        assertNull(RoutineRetryPolicy.rewardNoFillDelay("PARTIALLY_COMPLETED","PARTIAL","ad_stuck",0,2*60,false,true,false))
    }
    @Test fun unreachableListText(){
        assertEquals("报酬未领取列表暂时打不开，稍后自动再领",TaskFailureText.short(RewardClaimPolicy.LIST_UNREACHABLE_REASON))
    }
}
