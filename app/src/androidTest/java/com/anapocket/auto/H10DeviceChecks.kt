package com.anapocket.auto
import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object H10DeviceChecks {
    fun run(real:Context){
        val namespace="h10_fixture_"+System.currentTimeMillis()
        val ctx=object:ContextWrapper(real){
            override fun getSharedPreferences(n:String,m:Int)=real.getSharedPreferences(namespace+n,m)
            override fun getFilesDir()=File(real.filesDir,namespace).apply{mkdirs()}
        }
        val beforeSettings=Prefs.loadBlocking(real).toJson()
        val beforeDaily=DailyAdStore.allAttempts(real,TokyoDate.of()).map{it.toJson().toString()}
        val beforeCheckins=CheckinApp.entries.associateWith{CheckinStore(real,it).all().toString()+CheckinStore(real,it).config()}
        val cases=JSONArray()
        fun verify(name:String,ok:Boolean){cases.put(JSONObject().put("case",name).put("passed",ok));check(ok){name}}
        val today=TokyoDate.of();val store=CheckinStore(ctx,CheckinApp.RAKUTEN)
        store.configure(CheckinConfig(true,9,15));val generation=store.config().generation
        store.noteDispatch(today,1234,"fixture_alarm");store.waiting(today,"WAITING_FOR_ANA")
        verify("waiting_is_pending_without_task_failure",store.pendingDate()==today&&store.all().isEmpty())
        verify("waiting_has_no_failure_notice",!CheckinNoticePolicy.mayPublish(true,generation,generation,today,today,"WAITING_FOR_ANA",true))
        store.beginRun("fixture_preempt",today);store.finishRun("fixture_preempt","ANA_preempted")
        verify("ana_release_leaves_same_date_pending",store.pendingDate()==today)
        store.configure(store.config().copy(enabled=false))
        verify("off_suppresses_delayed_result",!CheckinNoticePolicy.mayPublish(false,store.config().generation,generation,today,today,"ACTUAL_BLOCKED",false))
        store.configure(store.config().copy(enabled=true))
        verify("off_on_rejects_old_callback_and_result",!CheckinDispatchPolicy.accepts(true,store.config().generation,generation)&&!CheckinNoticePolicy.mayPublish(true,store.config().generation,generation,today,today,"ACTUAL_BLOCKED",false))
        val rows=(1..5).map{i->AdAttempt("fixture_confirmed",today,i,i.toLong(),AdState.REWARD_CONFIRMED,true,true,100,officialBefore=i-1,officialAfter=i)}+
            AdAttempt("fixture_failed",today,6,6,AdState.FAILED,false,false,100,"official_error_dialog",5)+
            AdAttempt("fixture_unknown",today,7,7,AdState.UNCONFIRMED,false,false,100,"ad_timeout_95s",5)
        val prefs=ctx.getSharedPreferences("ana_official_ads_v1",0)
        prefs.edit().putString("day_"+today,JSONArray().also{a->rows.forEach{a.put(it.toJson())}}.toString())
            .putString("load_reviews_"+today,JSONArray().put(JSONObject().put("key",OfficialLoadFailurePolicy.key(rows[5])).put("date",today).put("counter",5).put("source","native_load_failure_and_fresh_unchanged_counter")).toString()).commit()
        OfficialAdStore.observe(ctx,today,5,"fixture_counter","FIXTURE")
        val before=OfficialAdStore.attempts(ctx,today).map{it.toJson().toString()}
        verify("real_shape_six_failed_seven_unknown",OfficialAdStore.attempts(ctx,today)[5].state==AdState.FAILED&&OfficialAdStore.attempts(ctx,today)[6].state==AdState.UNCONFIRMED)
        verify("fresh_counter_alone_never_resolves_unknown",!OfficialAdStore.allResolved(ctx,today)&&OfficialAdStore.reserve(ctx,today,"forbidden_new",5)==null)
        OfficialAdStore.observe(ctx,today,6,"fixture_late_counter_only","FIXTURE_NO_RECEIPT")
        verify("advance_without_receipt_still_cannot_start",OfficialAdStore.reserve(ctx,today,"forbidden_new",6)==null)
        verify("old_attempt_bytes_preserved",before==OfficialAdStore.attempts(ctx,today).map{it.toJson().toString()})
        verify("recreated_store_preserves_identity",OfficialAdStore.attempts(ctx,today).last().let{it.runId=="fixture_unknown"&&it.index==7&&it.startedAt==7L})
        verify("user_ana_settings_unchanged",beforeSettings==Prefs.loadBlocking(real).toJson())
        verify("user_ana_ledger_unchanged",beforeDaily==DailyAdStore.allAttempts(real,TokyoDate.of()).map{it.toJson().toString()})
        verify("user_p1_settings_and_history_unchanged",beforeCheckins==CheckinApp.entries.associateWith{CheckinStore(real,it).all().toString()+CheckinStore(real,it).config()})
        Evidence.write(real,"h10_isolated_"+System.currentTimeMillis()+".json",JSONObject().put("source","ISOLATED_FIXTURE_NO_OFFICIAL_REWARD").put("allPassed",true).put("cases",cases).toString(2))
    }
}
