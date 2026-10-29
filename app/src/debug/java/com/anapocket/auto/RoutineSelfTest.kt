package com.anapocket.auto
import android.content.Context
import android.content.ContextWrapper
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

object RoutineSelfTest{
 suspend fun run(real:Context){
  val ctx=object:ContextWrapper(real){override fun getSharedPreferences(name:String,mode:Int)=real.getSharedPreferences("r42_fixture_"+name,mode)}
  fun hashes():Map<String,String> = listOf("ana_daily_ads","ana_official_ads_v1","challenge_boost_v1","ana_telegram_v1").associateWith{name->val f=File(real.filesDir.parentFile,"shared_prefs/$name.xml");if(f.exists())MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString(""){"%02x".format(it)} else "absent"}
  val before=hashes();val checks=JSONArray();var failure=""
  fun verify(name:String,value:Boolean){checks.put(JSONObject().put("check",name).put("pass",value));check(value){name}}
  try{
   val date=TokyoDate.of();val run="FIXTURE_R42"
   ctx.getSharedPreferences("ana_login_bonus_v1",0).edit().clear().commit();ctx.getSharedPreferences("ana_daily_routine_v1",0).edit().clear().commit()
   val missing=JSONObject().put("date",date).put("state","NOT_PRESENT").put("completed",false).put("rewardConfirmed",false)
   LoginBonusStore.save(ctx,date,missing);verify("missing_popup_not_claimed",!LoginBonusStore.read(ctx,date)!!.optBoolean("completed"))
   val row=LoginBonusStore.reserve(ctx,date,run)!!;verify("login_reservation_durable",LoginBonusStore.read(ctx,date)!!.getString("state")=="AD_OPENING")
   verify("duplicate_reservation_rejected",LoginBonusStore.reserve(ctx,date,run+"_again")==null)
   row.put("state","UNCONFIRMED").put("reason","controlled_receipt_missing");LoginBonusStore.save(ctx,date,row)
   verify("unknown_not_reissued",LoginBonusStore.reserve(ctx,date,run+"_third")==null)
   row.put("state","REWARD_CONFIRMED").put("completed",true).put("rewardConfirmed",true);LoginBonusStore.save(ctx,date,row)
   verify("confirmed_not_reissued",LoginBonusStore.reserve(ctx,date,run+"_fourth")==null)
   var time=0L;var frame=0;var closes=0
   val port=object:StartupAdPort{
    override fun now()=time;override fun stopped()=false;override fun sameDate()=true
    override suspend fun frame()=when(frame++){0->StartupAdFrame(ad=true);1->StartupAdFrame(ad=true,close=true);else->StartupAdFrame(receipt=true)}
    override suspend fun close(){closes++};override suspend fun observedAd(){};override suspend fun pause(ms:Long){time+=ms}
   }
   verify("controlled_ad_close_then_official_fixture_receipt",StartupAdSequence(port).run(true).state=="REWARD_CONFIRMED"&&closes==1)
   val calls=mutableListOf<String>()
   val routine=object:RoutinePort{
    override fun stopped()=false;override fun sameDate()=true
    override suspend fun progress(stage:String,result:RoutineStepResult){RoutineStore.update(ctx,date,run,stage,result.state,result.detail)}
    override suspend fun startup():RoutineStepResult{calls.add("startup");return RoutineStepResult("NOT_PRESENT","not_claimed")}
    override suspend fun challenges():RoutineStepResult{calls.add("challenges");return RoutineStepResult("DONE","fixture_four_classes")}
    override suspend fun ads():RoutineStepResult{calls.add("ads");return RoutineStepResult("LIMIT_REACHED","fixture_limit_no_new_viewings")}
   }
   val outcome=RoutineSequence(routine).run();RoutineStore.finish(ctx,date,run,outcome,"controlled_summary_no_external_message")
   verify("serial_order_and_no_parallel_launch",calls==listOf("startup","challenges","ads"))
   val saved=RoutineStore.read(ctx,date)!!
   verify("routine_progress_roundtrip",saved.getString("state")=="DONE"&&saved.getJSONObject("steps").getJSONObject("STARTUP").getString("state")=="NOT_PRESENT")
   verify("routine_history_saved",RoutineStore.history(ctx).length()==1)
   verify("real_day_new_login_bucket_policy",StartupPolicy.canReserveLogin("2026-10-29","2026-10-28","REWARD_CONFIRMED"))
   verify("production_ad_challenge_and_encrypted_telegram_bytes_unchanged",before==hashes())
  }catch(e:Exception){failure=e.javaClass.simpleName}
  finally{ctx.getSharedPreferences("ana_login_bonus_v1",0).edit().clear().commit();ctx.getSharedPreferences("ana_daily_routine_v1",0).edit().clear().commit()}
  val report=JSONObject().put("environment","controlled_android_isolated_preferences_and_injected_frames_no_real_ads_or_messages").put("pass",failure.isBlank()).put("failure",failure).put("checks",checks).put("productionUnchanged",before==hashes())
  Evidence.write(real,"routine_controlled_"+System.currentTimeMillis()+".json",report.toString(2))
  Logger.i("ROUTINE_TEST","controlled checks="+checks.length()+" pass="+failure.isBlank())
 }
}
