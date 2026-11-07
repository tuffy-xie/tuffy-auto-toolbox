package com.anapocket.auto

import org.json.JSONObject

/** Official Fragment arguments, not accessibility node/window ids. No receipt authority by itself. */
data class OfficialChallengeIdentity(val missionId:Long,val participationId:Long?,val boostCount:Int?) {
    fun sameParticipation(other:OfficialChallengeIdentity)=missionId==other.missionId&&participationId!=null&&participationId==other.participationId
}

object OfficialChallengeBinding {
    const val SOURCE="ANA_active_fragment_arguments_v1"
    /** Reject partial/ambiguous dumps, background fragments and stale saved-state arguments. */
    fun parse(dump:String):OfficialChallengeIdentity? {
        if(dump.length>524288||dump.contains("DUMP TIMEOUT")||!dump.contains("ACTIVITY us.ana_pocket.ANAPocket/.views.home.HomeActivity ")||
            !dump.contains("mResumed=true mStopped=false mFinished=false"))return null
        val header=Regex("^( *)(ChallengeDetailBottomSheetDialogFragment|ChallengeDetailFragment|ChallengeBoostDialogFragment)\\{[a-fA-F0-9]+\\} ")
        data class Fragment(val kind:String,val indent:Int,val lines:MutableList<String>)
        val fragments=mutableListOf<Fragment>();var current:Fragment?=null
        for(line in dump.lineSequence()){
            val h=header.find(line)
            if(h!=null){current=Fragment(h.groupValues[2],h.groupValues[1].length,mutableListOf());fragments.add(current);continue}
            val c=current?:continue
            if(line.isNotBlank()&&line.takeWhile{it==' '}.length<=c.indent){current=null;continue}
            // Only the fragment's own scalar fields, not nested child manager/saved-state dumps.
            if(line.takeWhile{it==' '}.length==c.indent+4)c.lines.add(line.trim())
        }
        val active=fragments.filter{f->f.lines.any{it.startsWith("mState=7 ")}&&
            f.lines.any{it.startsWith("mAdded=true mRemoving=false ")}&&f.lines.any{it.startsWith("mHidden=false mDetached=false ")}}
        val detail=active.singleOrNull{it.kind=="ChallengeDetailFragment"}?:return null
        val parent=active.singleOrNull{it.kind=="ChallengeDetailBottomSheetDialogFragment"}?:return null
        fun args(f:Fragment)=f.lines.singleOrNull{it.startsWith("mArguments=Bundle[{")}
        fun value(a:String,key:String):String?=Regex("(?:[,{ ]|\\[)"+Regex.escape(key)+"=([0-9]+|null)(?=[,}])").find(a)?.groupValues?.get(1)
        fun identity(f:Fragment,upper:Boolean=false):OfficialChallengeIdentity? {
            val a=args(f)?:return null
            val m=value(a,if(upper)"MISSION_ID_KEY" else "mission_id")?.toLongOrNull()?.takeIf{it>0}?:return null
            val userKey=if(upper)"USER_MISSION_ID_KEY" else "user_mission_id"
            // Before JOIN the parent has no participation argument at all; the detail explicitly has null.
            // A Boost must still provide its own positive participation id, never infer it from a title.
            val u=value(a,userKey)?:if(!upper&&!a.contains(userKey+"="))"null" else return null
            val participation=if(u=="null"||u=="0")null else u.toLongOrNull()?.takeIf{it>0}?:return null
            val count=if(upper)value(a,"ADDITIONAL_BOOST_COUNT_KEY")?.toIntOrNull()?.takeIf{it in 0..5}?:return null else null
            return OfficialChallengeIdentity(m,participation,count)
        }
        val d=identity(detail)?:return null;val a=identity(parent)?:return null
        if(d.missionId!=a.missionId||d.participationId!=null&&a.participationId!=null&&d.participationId!=a.participationId)return null
        val boosts=active.filter{it.kind=="ChallengeBoostDialogFragment"}
        if(boosts.isEmpty())return d.copy(participationId=d.participationId?:a.participationId)
        val b=identity(boosts.singleOrNull()?:return null,true)?:return null
        return b.takeIf{d.missionId==it.missionId&&it.participationId!=null&&
            (d.participationId==null||d.participationId==it.participationId)&&(a.participationId==null||a.participationId==it.participationId)}
    }
    fun reserve(identity:OfficialChallengeIdentity,date:String,key:String,run:String,index:Int,startedAt:Long,
        controller:String,boost:Int,before:Int?,initial:Boolean,observedAt:Long):JSONObject {
        check(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))&&key.isNotBlank()&&run.isNotBlank()&&controller.isNotBlank()&&index>0&&startedAt>0&&observedAt<=startedAt&&startedAt-observedAt<=10000){"official_binding_context_invalid"}
        check(if(initial)identity.participationId==null&&boost==0 else identity.participationId!=null&&boost in 0..4&&identity.boostCount==boost&&before!=null){"official_binding_progress_invalid"}
        return JSONObject().put("source",SOURCE).put("date",date).put("key",key).put("runId",run).put("index",index).put("startedAt",startedAt)
            .put("controllerRun",controller).put("missionId",identity.missionId).put("participationId",identity.participationId?:JSONObject.NULL)
            .put("boostBefore",boost).put("offeredBefore",before?:JSONObject.NULL).put("initialJoin",initial).put("observedAt",observedAt)
    }
    fun valid(row:JSONObject,ad:JSONObject):Boolean {
        val b=ad.optJSONObject("officialBinding")?:return false
        return b.optString("source")==SOURCE&&b.optString("date")==row.optString("date")&&b.optString("key")==row.optString("key")&&
            b.optString("runId")==ad.optString("runId")&&b.optInt("index")==ad.optInt("index")&&b.optLong("startedAt")==ad.optLong("startedAt")&&
            b.optString("controllerRun").isNotBlank()&&b.optLong("missionId")>0&&b.optLong("observedAt")>0&&
            b.optLong("observedAt")<=ad.optLong("startedAt")&&ad.optLong("startedAt")-b.optLong("observedAt")<=10000&&
            b.optBoolean("initialJoin")==ad.optBoolean("initialJoin")&&
            (if(ad.optBoolean("initialJoin"))b.isNull("participationId") else b.optLong("participationId")>0&&b.optInt("boostBefore") in 0..4&&b.optInt("offeredBefore")==ad.optInt("offeredBefore"))
    }
    fun matches(row:JSONObject,ad:JSONObject,observed:OfficialChallengeIdentity?):Boolean {
        if(!valid(row,ad)||observed==null)return false
        val b=ad.getJSONObject("officialBinding")
        return observed.missionId==b.getLong("missionId")&&observed.participationId!=null&&
            (ad.optBoolean("initialJoin")||observed.participationId==b.optLong("participationId"))
    }
    /** The original absent binding is absent forever; a late observation cannot upgrade old UNKNOWN. */
    fun unchanged(old:JSONObject,next:JSONObject):Boolean = listOf("officialBinding","officialReceiptIdentity").all{key->
        if(!old.has(key))key!="officialBinding"||!next.has(key)
        else old.optJSONObject(key)?.toString()==next.optJSONObject(key)?.toString()
    }
}
