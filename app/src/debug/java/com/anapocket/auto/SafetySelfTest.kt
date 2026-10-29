package com.anapocket.auto
import android.content.Context
import android.content.ContextWrapper
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
object SafetySelfTest {
 fun run(real:Context){
  val ctx=object:ContextWrapper(real){override fun getSharedPreferences(name:String,mode:Int)=real.getSharedPreferences("r44_fixture_"+name,mode)}
  fun hashes()=listOf("ana_account_safety_v1","ana_daily_ads","ana_official_ads_v1","challenge_boost_v1","ana_login_bonus_v1","ana_telegram_v1").associateWith{n->val f=File(real.filesDir.parentFile,"shared_prefs/"+n+".xml");if(f.exists())MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString(""){"%02x".format(it)} else "absent"}
  val before=hashes();val checks=JSONArray();var failure=""
  fun verify(name:String,yes:Boolean){checks.put(JSONObject().put("name",name).put("pass",yes));check(yes){name}}
  ctx.getSharedPreferences("ana_account_safety_v1",0).edit().clear().commit()
  try{
   val(first,created)=AccountSafetyStore.latch(ctx,RiskKind.HUMAN_VERIFICATION,"FIXTURE_R44","isolated_injected_frame")
   verify("durable_manual_latch",created&&AccountSafetyStore.pending(ctx)!!.getString("id")==first.getString("id"))
   val(second,again)=AccountSafetyStore.latch(ctx,RiskKind.ACCOUNT_WARNING,"FIXTURE_R44_2","fixture")
   verify("restart_does_not_replace_or_clear_incident",!again&&second.getString("id")==first.getString("id"))
   verify("own_app_is_not_review_proof",!AccountSafetyStore.acknowledge(ctx,first.getString("id"),false,null))
   verify("warning_cannot_be_acknowledged",!AccountSafetyStore.acknowledge(ctx,first.getString("id"),true,RiskKind.ACCOUNT_WARNING))
   verify("stale_incident_cannot_clear_latch",!AccountSafetyStore.acknowledge(ctx,"other",true,null))
   verify("verified_manual_review_clears",AccountSafetyStore.acknowledge(ctx,first.getString("id"),true,null)&&AccountSafetyStore.pending(ctx)==null)
   verify("production_journals_and_encrypted_telegram_unchanged",before==hashes())
  }catch(e:Exception){failure=e.message.orEmpty()}
  finally{ctx.getSharedPreferences("ana_account_safety_v1",0).edit().clear().commit()}
  val report=JSONObject().put("environment","isolated_preferences_injected_safety_only_no_real_account_warning_or_captcha").put("pass",failure.isBlank()).put("failure",failure).put("checks",checks).put("productionUnchanged",before==hashes())
  Evidence.write(real,"r44_safety_controlled_"+System.currentTimeMillis()+".json",report.toString(2));Logger.i("SAFETY_TEST","pass="+failure.isBlank()+" checks="+checks.length())
 }
}
