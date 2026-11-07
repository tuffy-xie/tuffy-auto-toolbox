import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import dalvik.system.PathClassLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Development-only isolated ART probe. Never obtains the real application's Context, preferences or database. */
public final class H14LedgerProbe {
    static final String DAY="2099-01-01";
    static ClassLoader loader; static Class<?> ledger; static Object instance; static ProbeContext context;
    static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static JSONObject legacyRow(String key,long start)throws Exception{
        JSONObject ad=new JSONObject().put("runId","CH-PROBE-"+start).put("index",1).put("startedAt",start)
            .put("state","AD_OPENING").put("confirmed",false).put("dispatchPhase","DISPATCHED").put("adDepartureObserved",true);
        return new JSONObject().put("date",DAY).put("key",key).put("title","H14 synthetic record")
            .put("cat","WALK").put("meters",1000).put("joined",false).put("boost",0).put("state","AD_OPENING").put("ads",new JSONArray().put(ad));
    }
    static final class ProbeContext extends ContextWrapper {
        final File root; final String legacy;
        ProbeContext(File root)throws Exception{super(null);this.root=root;
            require(root.getCanonicalPath().startsWith("/data/local/tmp/tuffy_h14_probe_20261102_"),"isolated_root_required");
            require(root.isDirectory(),"isolated_root_missing");
            legacy=new JSONArray().put(legacyRow("WALK|synthetic",1234)).put(legacyRow("BIKE|synthetic_unknown",2345)).toString();
        }
        @Override public Context getApplicationContext(){return this;}
        @Override public String getPackageName(){return "h14.isolated.probe";}
        @Override public File getFilesDir(){return root;}
        @Override public File getCacheDir(){return root;}
        @Override public File getNoBackupFilesDir(){return root;}
        @Override public Resources getResources(){return Resources.getSystem();}
        @Override public ApplicationInfo getApplicationInfo(){ApplicationInfo a=new ApplicationInfo();a.packageName=getPackageName();a.dataDir=root.getPath();a.targetSdkVersion=34;a.uid=android.os.Process.myUid();return a;}
        @Override public File getDatabasePath(String name){require(name.equals("tuffy_ledger_v14.db"),"unexpected_database");return new File(root,name);}
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return openOrCreateDatabase(name,mode,factory,null);}
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){
            SQLiteDatabase d=SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);
            if((mode&Context.MODE_ENABLE_WRITE_AHEAD_LOGGING)!=0)d.enableWriteAheadLogging();return d;
        }
        @Override public SharedPreferences getSharedPreferences(String name,int mode){
            return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
                if(m.getName().equals("getString"))return name.equals("challenge_boost_v1")&&a[0].equals("day_"+DAY)?legacy:a[1];
                if(m.getName().equals("getAll"))return Collections.emptyMap();
                if(m.getName().equals("contains"))return false;
                if(m.getName().startsWith("get"))return a[1];
                throw new AssertionError("probe_preferences_are_read_only");
            });
        }
    }
    static Object call(String name,Class<?>[] types,Object...args)throws Exception{return ledger.getMethod(name,types).invoke(instance,args);}
    static Object lambda(String name,InvocationHandler body)throws Exception{return Proxy.newProxyInstance(loader,new Class[]{Class.forName("kotlin.jvm.functions."+name,true,loader)},body);}
    static void write(String value)throws Exception{
        call("put",new Class[]{Context.class,String.class,String.class,String.class,String.class},context,"probe",DAY,"record",new JSONObject().put("stage",value).put("runId","CH-SYNTHETIC").put("startedAt",9876).toString());
    }
    static JSONObject read()throws Exception{
        Object fallback=lambda("Function0",(p,m,a)->null);
        String value=(String)call("read",new Class[]{Context.class,String.class,String.class,String.class,fallback.getClass().getInterfaces()[0]},context,"probe",DAY,"record",fallback);
        return value==null?null:new JSONObject(value);
    }
    static SQLiteDatabase database()throws Exception{
        Field f=ledger.getDeclaredField("helper");f.setAccessible(true);return ((SQLiteOpenHelper)f.get(null)).getWritableDatabase();
    }
    static void selfKill(String phase){
        System.out.println("H14_PROBE_SELF_KILL_ONLY "+phase+" pid="+android.os.Process.myPid());System.out.flush();
        android.os.Process.killProcess(android.os.Process.myPid());throw new AssertionError("self_kill_did_not_terminate_probe");
    }
    @SuppressWarnings({"unchecked","rawtypes"}) static void serialization()throws Exception{
        Class<?> type=Class.forName("com.anapocket.auto.AdAttempt",true,loader),state=Class.forName("com.anapocket.auto.AdState",true,loader);
        Constructor<?> ctor=Arrays.stream(type.getConstructors()).filter(c->c.getParameterCount()==17).findFirst().get();
        Object[] args={"AD-SYNTHETIC",DAY,1,1234L,Enum.valueOf((Class)state,"AD_PLAYING"),false,false,0L,"",3,-1,"","OFFICIAL_QUOTA_RESERVED","DISPATCHED",1200L,1234L,true};
        Object attempt=ctor.newInstance(args);JSONObject data=(JSONObject)type.getMethod("toJson").invoke(attempt);
        Object companion=type.getField("Companion").get(null);Object restored=companion.getClass().getMethod("fromJson",JSONObject.class).invoke(companion,data);
        require((Boolean)type.getMethod("getAdDepartureObserved").invoke(restored),"departure_not_round_tripped");
        require(type.getMethod("getRunId").invoke(restored).equals("AD-SYNTHETIC"),"run_changed");
        require((Long)type.getMethod("getStartedAt").invoke(restored)==1234L,"start_changed");
        args[13]="";args[16]=false;
        JSONObject old=(JSONObject)type.getMethod("toJson").invoke(ctor.newInstance(args));
        require(!old.has("adDepartureObserved")&&!old.has("dispatchPhase"),"legacy_optional_fields_changed");
    }
    static void migration()throws Exception{
        JSONArray rows=(JSONArray)call("challenges",new Class[]{Context.class,String.class},context,DAY);
        require(rows.length()==2,"legacy_import_count");JSONObject unknown=rows.getJSONObject(1);String before=unknown.toString();
        JSONObject row=rows.getJSONObject(0);row.getJSONArray("ads").getJSONObject(0).put("confirmed",true).put("state","REWARD_CONFIRMED");row.put("joined",true).put("state","JOINED");
        for(int i=0;i<2;i++)call("saveChallenge",new Class[]{Context.class,String.class,JSONObject.class},context,DAY,row);
        JSONArray saved=(JSONArray)call("challenges",new Class[]{Context.class,String.class},context,DAY);
        require(saved.length()==2&&saved.getJSONObject(0).getJSONArray("ads").length()==1,"idempotence_failed");
        require(saved.getJSONObject(1).toString().equals(before),"unrelated_unknown_changed");
        row.getJSONArray("ads").getJSONObject(0).put("confirmed",false);
        try{call("saveChallenge",new Class[]{Context.class,String.class,JSONObject.class},context,DAY,row);throw new AssertionError("confirmed_downgrade_accepted");}
        catch(InvocationTargetException expected){require(expected.getCause().getMessage().contains("confirmed_receipt_regressed"),"wrong_rejection");}
        require(((JSONArray)call("challenges",new Class[]{Context.class,String.class},context,DAY)).getJSONObject(0).getJSONArray("ads").getJSONObject(0).getBoolean("confirmed"),"confirmed_lost_after_rejected_write");
    }
    public static void main(String[] args)throws Exception{
        require(args.length>=3,"arguments");File apk=new File(args[0]),root=new File(args[1]);
        require(apk.getCanonicalPath().startsWith("/data/local/tmp/tuffy_h14_probe_20261102_"),"probe_apk_only");
        context=new ProbeContext(root);loader=new PathClassLoader(apk.getPath(),H14LedgerProbe.class.getClassLoader());
        ledger=Class.forName("com.anapocket.auto.ReliableLedger",true,loader);instance=ledger.getField("INSTANCE").get(null);
        String mode=args[2],value=args.length>3?args[3]:"BASE";
        if(mode.equals("init")){serialization();migration();write(value);}
        else if(mode.equals("write"))write(value);
        else if(mode.equals("read")){
            JSONObject record=read();require(record.getString("stage").equals(value),"unexpected_committed_value");
            require(record.getString("runId").equals("CH-SYNTHETIC")&&record.getLong("startedAt")==9876,"persistent_identity_changed");
            JSONArray rows=(JSONArray)call("challenges",new Class[]{Context.class,String.class},context,DAY);
            require(rows.length()==2&&rows.getJSONObject(0).getJSONArray("ads").getJSONObject(0).getBoolean("confirmed"),"confirmed_history_changed");
            JSONObject unknown=rows.getJSONObject(1).getJSONArray("ads").getJSONObject(0);
            require(!unknown.getBoolean("confirmed")&&unknown.getString("runId").equals("CH-PROBE-2345")&&unknown.getLong("startedAt")==2345&&unknown.getString("dispatchPhase").equals("DISPATCHED"),"unrelated_unknown_changed_after_restart");
        }
        else if(mode.equals("commit_kill")){write(value);selfKill("AFTER_DURABLE_COMMIT");}
        else if(mode.equals("dirty_kill")||mode.equals("abort")){
            Object change=lambda("Function1",(p,m,a)->{
                if(!m.getName().equals("invoke"))return null;
                JSONObject dirty=(JSONObject)a[0];dirty.put("stage",value);
                database().execSQL("UPDATE records SET payload=? WHERE area='probe' AND scope=? AND identity='record'",new Object[]{dirty.toString(),DAY});
                if(mode.equals("dirty_kill"))selfKill("BEFORE_TRANSACTION_COMMIT");
                throw new IllegalStateException("SYNTHETIC_TRANSACTION_ABORT");
            });
            try{call("mutate",new Class[]{Context.class,String.class,String.class,String.class,change.getClass().getInterfaces()[0]},context,"probe",DAY,"record",change);throw new AssertionError("abort_not_reported");}
            catch(InvocationTargetException e){require(mode.equals("abort")&&e.getCause().getMessage().equals("durable_ledger_commit_failed"),"wrong_transaction_failure");}
            try{write("MUST_NOT_WRITE");throw new AssertionError("failed_store_allowed_next_action");}
            catch(InvocationTargetException e){require(e.getCause().getMessage().contains("durable_ledger_unavailable"),"write_failure_not_latched");}
        }else throw new AssertionError("unknown_mode");
        JSONObject result=new JSONObject().put("mode",mode).put("expected",value).put("result","PASS").put("testDataOnly",true).put("root",root.getCanonicalPath());
        try(Cursor c=database().rawQuery("PRAGMA synchronous",null)){c.moveToFirst();require(c.getInt(0)==2,"FULL_synchronous_required");result.put("synchronous",c.getInt(0));}
        try(Cursor c=database().rawQuery("PRAGMA journal_mode",null)){c.moveToFirst();result.put("journalMode",c.getString(0));}
        System.out.println(result);
    }
}