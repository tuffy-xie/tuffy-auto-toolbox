package com.anapocket.auto

/**
 * 11/11 00:44: BIKE 500m stopped at Boost 3/5 with its ad budget used (two official no-gains), and ANA's own
 * "4回目のブーストをしますか？" prompt stayed open. Every later category then failed with
 * startup_popup_blocks_category_navigation and the night's retries ran out.
 * Once the budget is reached, the only allowed action on that prompt is ANA's own "ブーストをやめる",
 * and only when the prompt is provably this task's: next Boost == saved boost + 1 and the same offered reward.
 */
object BudgetBoostPromptPolicy {
    private const val P="us.ana_pocket.ANAPocket:id/"
    fun matches(ids:Set<String>,labels:List<String>,state:String,boost:Int,offeredReward:Int):Boolean =
        state=="AD_BUDGET_REACHED"&&boost in 1..4&&offeredReward>0&&
            listOf("title_count","current_point","button_no","button_no_text").all{P+it in ids}&&
            labels.any{it.trim()=="ブーストをやめる"}&&
            ChallengeSignals.nextBoost(labels)==boost+1&&
            (ChallengeSignals.offeredReward(labels)==offeredReward||labels.any{it.trim()==offeredReward.toString()+"pt"})
}
