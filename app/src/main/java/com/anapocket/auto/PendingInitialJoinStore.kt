package com.anapocket.auto

import android.content.Context
import org.json.JSONObject

object PendingInitialJoinStore {
    fun binding(row:JSONObject):PendingInitialJoin? {
        val ads=row.optJSONArray("ads")?:return null
        val ad=ads.optJSONObject(ads.length()-1)?:return null
        return PendingInitialJoin(row.optString("date"),row.optString("key"),ad.optString("runId"),
            ad.optLong("startedAt"),ad.optInt("index"),ads.length(),ad.optBoolean("initialJoin"),
            row.optBoolean("joined"),row.optInt("boost"),row.optString("state"),ad.optString("state"),
            ad.optBoolean("confirmed"),ad.optBoolean("adDepartureObserved"),ad.optString("reason"),ad.optString("dispatchPhase"))
    }
    fun isolated(row:JSONObject,date:String):Boolean {
        if(SubmittedUnknownIsolation.isolated(row,date))return true
        val a=binding(row)?:return false
        val p=row.optJSONObject("initialJoinIsolation")?:return false
        return PendingInitialJoinPolicy.isolated(a,InitialJoinIsolationProof(p.optString("date"),p.optString("key"),
            p.optString("runId"),p.optLong("startedAt"),p.optInt("index"),p.optLong("observedAt"),p.optString("kind")),date)
    }
    fun onlyIsolatedPending(c:Context,date:String):Boolean = PendingInitialJoinPolicy.independentDailyAllowed(
        ChallengeStore.pendingRows(c,date).map{isolated(it,date)},TokyoDate.of()==date)

}
