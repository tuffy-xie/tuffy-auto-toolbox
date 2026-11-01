package com.anapocket.auto

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

object TravelSelectionPolicy {
    val categoryOrder=listOf(Cat.WALK,Cat.BIKE,Cat.CAR,Cat.MOV_ANY,Cat.SHINKANSEN,Cat.FLIGHT)
    fun decision(selected:Boolean,inSubmittedAd:Boolean)=if(inSubmittedAd)"FINISH_EXISTING_ONLY" else if(selected)"ENABLED" else "DISABLED_BY_USER"
    fun queue(requested:List<Cat>,settings:AppSettings)=requested.filter{settings.catOn(it)&&settings.kmOf(it)>0}
}
object TravelSelection {
    val mutex=Mutex()
    fun enabled(ctx:Context,cat:Cat)=Prefs.loadBlocking(ctx).let{it.catOn(cat)&&it.kmOf(cat)>0}
    fun audit(ctx:Context,date:String,run:String,cat:Cat){
        val body=JSONObject().put("runId",run).put("date",date).put("cat",cat.name).put("state","DISABLED_BY_USER").put("at",System.currentTimeMillis())
        ctx.getSharedPreferences("ana_category_selection_audit_v1",0).edit().putString(date+":"+cat.name,body.toString()).commit()
        Logger.i("SELECTION","run=$run cat=$cat DISABLED_BY_USER no_new_ad")
    }
    suspend fun <T> reserve(ctx:Context,cat:Cat,block:()->T):T?=reserveIf({enabled(ctx,cat)},block)
    internal suspend fun <T> reserveIf(selected:suspend()->Boolean,block:()->T):T?=mutex.withLock{if(!selected())null else block()}
    fun hasUnresolvedAd(ctx:Context,date:String):Boolean {val rows=ChallengeStore.rows(ctx,date);return (0 until rows.length()).any{val ads=rows.getJSONObject(it).optJSONArray("ads");val last=ads?.optJSONObject(ads.length()-1);last!=null&&!last.optBoolean("confirmed")&&!last.optBoolean("officialNoGain")&&last.optString("state") in setOf("AD_OPENING","AD_PLAYING","AD_CLOSE_AVAILABLE","UNCONFIRMED")}}
    fun hasSelectedPending(ctx:Context,date:String):Boolean {val rows=ChallengeStore.rows(ctx,date);return (0 until rows.length()).any{val r=rows.getJSONObject(it);Cat.ofName(r.optString("cat"))?.let{enabled(ctx,it)}==true&&r.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}}
}
