package com.anapocket.auto
import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
object R47SelectionProbe {
 suspend fun run(ctx:Context){
  val before=Prefs.loadBlocking(ctx).toJson();val id="r47_fixture_"+System.currentTimeMillis()
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
  val ds=PreferenceDataStoreFactory.create(scope=scope,produceFile={File(ctx.cacheDir,id+".preferences_pb")})
  val cases=JSONArray();fun verify(name:String,ok:Boolean){cases.put(JSONObject().put("case",name).put("passed",ok))}
  val initial=Prefs.loadFromStore(ds)
  for(cat in Cat.values()){
   Prefs.setCategoryInStore(ds,cat,true);verify(cat.name+"_selected_persisted",Prefs.loadFromStore(ds).catOn(cat))
   Prefs.setCategoryInStore(ds,cat,false);verify(cat.name+"_unselected_persisted",!Prefs.loadFromStore(ds).catOn(cat))
   verify(cat.name+"_distance_unchanged",Prefs.loadFromStore(ds).kmOf(cat)==initial.kmOf(cat))
  }
  verify("all_off_actual_queue_empty",TravelSelectionPolicy.queue(Cat.values().toList(),Prefs.loadFromStore(ds)).isEmpty())
  verify("all_off_ads_unchanged",Prefs.loadFromStore(ds).adEnabled==initial.adEnabled)
  Prefs.setCategoryInStore(ds,Cat.WALK,true);Prefs.setCategoryInStore(ds,Cat.CAR,true)
  verify("actual_partial_queue",TravelSelectionPolicy.queue(Cat.values().toList(),Prefs.loadFromStore(ds))==listOf(Cat.WALK,Cat.CAR))
  val walkBefore=Prefs.loadFromStore(ds).catOn(Cat.WALK)
  coroutineScope{repeat(20){launch{Prefs.toggleCategoryInStore(ds,Cat.WALK)}}}
  verify("rapid_even_toggles_are_not_lost",Prefs.loadFromStore(ds).catOn(Cat.WALK)==walkBefore)
  Prefs.toggleCategoryInStore(ds,Cat.WALK)
  verify("odd_toggle_changes_persisted_selection",Prefs.loadFromStore(ds).catOn(Cat.WALK)!=walkBefore)
  Prefs.setCategoryInStore(ds,Cat.WALK,true)
  var submitted=0;TravelSelection.reserveIf({Prefs.loadFromStore(ds).catOn(Cat.WALK)}){submitted++}
  Prefs.setCategoryInStore(ds,Cat.WALK,false)
  TravelSelection.reserveIf({Prefs.loadFromStore(ds).catOn(Cat.WALK)}){submitted++}
  verify("disable_fence_prevents_next_submission",submitted==1)
  val fixture=object:ContextWrapper(ctx){override fun getSharedPreferences(n:String,m:Int)=super.getSharedPreferences(id+n,m);override fun getFilesDir()=File(super.getFilesDir(),id).apply{mkdirs()}}
  for(app in CheckinApp.entries){val s=CheckinStore(fixture,app);val old=s.config();s.configure(old.copy(enabled=true));val on=s.config();s.configure(on.copy(enabled=false));val off=s.config();s.configure(off.copy(enabled=true));val again=s.config()
   verify(app.name+"_old_alarm_after_off_on_rejected",!CheckinDispatchPolicy.accepts(again.enabled,again.generation,on.generation))
   verify(app.name+"_disabled_callback_rejected",!CheckinDispatchPolicy.accepts(off.enabled,off.generation,off.generation))
   verify(app.name+"_time_preserved",old.hour==again.hour&&old.minute==again.minute)
   verify(app.name+"_recreated_store_retains_generation",CheckinStore(fixture,app).config()==again)
   verify(app.name+"_old_dispatch_not_committed",!s.acceptDispatch(TokyoDate.of(),0,"fixture",on.generation))
   s.beginRun("old",TokyoDate.of());s.pending(TokyoDate.of());s.configure(again.copy(enabled=false));s.configure(again.copy(enabled=true))
   verify(app.name+"_pending_old_generation_hidden",s.pendingDate()==null)
   s.finishRun("old","ANA_preempted");verify(app.name+"_old_cancel_cannot_resume_new_generation",s.pendingDate()==null)
  }
  verify("old_business_date_rejected",!CheckinDispatchPolicy.accepts(true,3,3,false))
  verify("production_ana_config_unchanged",before==Prefs.loadBlocking(ctx).toJson())
  scope.cancel();val pass=(0 until cases.length()).all{cases.getJSONObject(it).getBoolean("passed")}
  Evidence.write(ctx,"r47_selection_fixture_"+System.currentTimeMillis()+".json",JSONObject().put("source","ISOLATED_DATASTORE_AND_PREFERENCES_NO_OFFICIAL_UI_OR_REWARD").put("allPassed",pass).put("cases",cases).toString(2));check(pass)
 }
}
