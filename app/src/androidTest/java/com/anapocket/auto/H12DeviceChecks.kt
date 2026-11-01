package com.anapocket.auto
import android.content.Context
import android.content.ContextWrapper
import org.json.JSONObject
import org.json.JSONArray
object H12DeviceChecks {
 fun run(real:Context){
  val namespace="h12_fixture_"+System.currentTimeMillis();val ctx=object:ContextWrapper(real){override fun getSharedPreferences(n:String,m:Int)=real.getSharedPreferences(namespace+n,m)}
  val date=TokyoDate.of();val old="2026-10-31";val run="H12-FIXTURE";val cases=JSONArray()
  fun verify(name:String,ok:Boolean){cases.put(JSONObject().put("case",name).put("passed",ok));check(ok){name}}
  fun realSnapshot()=Prefs.loadBlocking(real).toJson()+DailyAdStore.allAttempts(real,date).map{it.toJson().toString()}+ChallengeStore.rows(real,date)+LoginBonusStore.read(real,date)+CheckinApp.entries.map{CheckinStore(real,it).config().toString()+CheckinStore(real,it).all()}
  val realBefore=realSnapshot()
  val st=Prefs.loadBlocking(real);Prefs.saveBlocking(ctx,st)
  val confirmed=JSONObject().put("key","WALK|done").put("cat","WALK").put("state","BOOST_MAX_CONFIRMED").put("ads",JSONArray().put(JSONObject().put("runId","ORIGINAL").put("startedAt",123).put("confirmed",true)))
  val unknown=JSONObject().put("key","WALK|unknown").put("cat","WALK").put("state","UNCONFIRMED").put("ads",JSONArray().put(JSONObject().put("runId","UNKNOWN").put("startedAt",456).put("confirmed",false)))
  ChallengeStore.save(ctx,date,confirmed);ChallengeStore.save(ctx,date,unknown);ChallengeStore.save(ctx,old,unknown)
  val before=ChallengeStore.rows(ctx,date).toString();val oldBefore=ChallengeStore.rows(ctx,old).toString();val settingsBefore=Prefs.loadBlocking(ctx).toJson()
  OfficialAdStore.observe(ctx,date,25,run,"FIXTURE")
  val dailyBefore=OfficialAdStore.observation(ctx,date).toString()
  ChallengeStore.setCoverage(ctx,date,Cat.WALK,100)
  verify("old_coverage_without_new_scan_never_completes",!ChallengeScanStore.complete(ctx,date,Cat.WALK,5))
  ChallengeScanStore.record(ctx,date,Cat.WALK,5,run,ChallengeScanStatus.COMPLETED,true,2)
  verify("unknown_submission_blocks_derived_completion",!ChallengeScanStore.complete(ctx,date,Cat.WALK,5))
  val attempts=ChallengeScanStore.audit(ctx,date).length();ChallengeScanStore.reset(ctx,date,run+"_reset")
  verify("reset_preserves_confirmed_and_unknown_rows",before==ChallengeStore.rows(ctx,date).toString())
  verify("reset_preserves_other_business_dates",oldBefore==ChallengeStore.rows(ctx,old).toString())
  verify("reset_preserves_official_25_observation",dailyBefore==OfficialAdStore.observation(ctx,date).toString())
  verify("reset_preserves_current_selection_km_and_schedule",settingsBefore==Prefs.loadBlocking(ctx).toJson())
  verify("reset_appends_auditable_attempt",ChallengeScanStore.audit(ctx,date).length()>attempts)
  verify("reset_returns_only_projection_to_not_scanned",ChallengeScanStore.status(ctx,date,Cat.WALK,st.kmOf(Cat.WALK))==ChallengeScanStatus.NOT_SCANNED)
  ChallengeScanStore.record(ctx,date,Cat.BIKE,5,run,ChallengeScanStatus.SCANNING)
  verify("interrupted_scan_does_not_become_complete",!ChallengeScanStore.complete(ctx,date,Cat.BIKE,5))
  verify("real_ledgers_and_configuration_unchanged",realBefore==realSnapshot())
  Evidence.write(real,"h12_android_isolation.json",JSONObject().put("fixtureOnly",true).put("cases",cases).toString(2))
 }
}
