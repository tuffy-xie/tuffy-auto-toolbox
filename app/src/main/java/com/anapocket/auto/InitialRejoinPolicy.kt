package com.anapocket.auto

import org.json.JSONObject

/**
 * User-approved 11/11: a first-join ad whose outcome stayed unknown may be watched once more per task per day, but only
 * when ANA itself shows the join never happened: the task was missing from a complete scan of 参加中 (isolation kind
 * ABSENT) and is back in this run's fresh 参加可能 list. Nothing else changes: a second unknown join is isolated as before
 * and there is never a third attempt.
 */
object InitialRejoinPolicy {
    fun mayRejoin(rowDate:String,today:String,joined:Boolean,adCount:Int,firstInitial:Boolean,firstConfirmed:Boolean,
        firstNoGain:Boolean,absentIsolatedToday:Boolean,inFreshJoinableList:Boolean,alreadyRejoined:Boolean):Boolean =
        rowDate==today&&!joined&&adCount==1&&firstInitial&&!firstConfirmed&&!firstNoGain&&
            absentIsolatedToday&&inFreshJoinableList&&!alreadyRejoined

    fun mayRejoin(row:JSONObject,today:String,inFreshJoinableList:Boolean):Boolean {
        val ads=row.optJSONArray("ads")?:return false
        val first=ads.optJSONObject(0)?:return false
        val iso=row.optJSONObject("submittedUnknownIsolation")
        val absent=iso!=null&&iso.optString("kind")==SubmittedUnknownIsolationPolicy.KIND_ABSENT&&SubmittedUnknownIsolation.isolated(row,today)
        return mayRejoin(row.optString("date"),today,row.optBoolean("joined"),ads.length(),first.optBoolean("initialJoin"),
            first.optBoolean("confirmed"),first.optBoolean("officialNoGain"),absent,inFreshJoinableList,row.has("rejoin"))
    }

    /** Marks the old attempt with the official evidence and opens the single re-join. Returns the row to continue with. */
    fun prepare(row:JSONObject,runId:String,now:Long):JSONObject {
        val first=row.getJSONArray("ads").getJSONObject(0)
        first.put("officialNotJoined",true).put("officialNotJoinedEvidence","absent_from_participating_scan_and_present_in_fresh_joinable_list")
            .put("officialNotJoinedAt",now)
        return row.put("rejoin",JSONObject().put("runId",runId).put("at",now).put("previousRunId",first.optString("runId")).put("count",1))
            .put("state","READY").put("runId",runId)
    }

    /** An attempt ANA proved never joined is resolved; it no longer holds the day open. */
    fun resolved(ad:JSONObject)=ad.optBoolean("officialNotJoined")
    /** The failed join attempt does not use up a Boost slot. */
    fun extraStarts(row:JSONObject)=if(row.has("rejoin"))1 else 0
}
