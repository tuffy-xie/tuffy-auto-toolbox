package com.anapocket.auto

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

/** Small private rollback journal. FULL synchronous transactions acknowledge durable writes before UI actions. */
object ReliableLedger {
    private class Db(c:Context):SQLiteOpenHelper(c.applicationContext,"tuffy_ledger_v14.db",null,1){
        override fun onConfigure(db:SQLiteDatabase){db.execSQL("PRAGMA synchronous=FULL")}
        override fun onCreate(db:SQLiteDatabase){
            db.execSQL("CREATE TABLE records (area TEXT NOT NULL, scope TEXT NOT NULL, identity TEXT NOT NULL, ordinal INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(area,scope,identity))")
            db.execSQL("CREATE TABLE imports (area TEXT NOT NULL, scope TEXT NOT NULL, PRIMARY KEY(area,scope))")
        }
        override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){error("unsupported_ledger_schema")}
    }
    @Volatile private var helper:Db?=null
    @Volatile private var failed=false
    private fun db(c:Context):SQLiteDatabase {
        check(!failed){"durable_ledger_unavailable_restart_required"}
        return try{(helper?:synchronized(this){helper?:Db(c).also{helper=it}}).writableDatabase}
        catch(t:Exception){failed=true;throw IllegalStateException("durable_ledger_open_failed",t)}
    }
    private inline fun <T> transaction(c:Context,body:(SQLiteDatabase)->T):T {
        val d=db(c)
        try{d.beginTransaction();try{val value=body(d);d.setTransactionSuccessful();return value}finally{d.endTransaction()}}
        catch(t:Exception){failed=true;throw IllegalStateException("durable_ledger_commit_failed",t)}
    }
    @Synchronized private fun importChallenges(c:Context,date:String){
        val d=db(c)
        val imported=d.rawQuery("SELECT 1 FROM imports WHERE area='challenge' AND scope=?",arrayOf(date)).use{it.moveToFirst()}
        if(imported)return
        val legacy=JSONArray(c.getSharedPreferences("challenge_boost_v1",0).getString("day_"+date,"[]"))
        val keys=mutableSetOf<String>()
        transaction(c){sql->
            for(i in 0 until legacy.length()){
                val row=legacy.getJSONObject(i);check(row.getString("date")==date&&keys.add(row.getString("key"))){"legacy_challenge_identity_corrupt"}
                val values=ContentValues().apply{put("area","challenge");put("scope",date);put("identity",row.getString("key"));put("ordinal",i);put("payload",row.toString())}
                sql.insertOrThrow("records",null,values)
            }
            sql.execSQL("INSERT INTO imports(area,scope) VALUES('challenge',?)",arrayOf(date))
        }
        // The old preferences are retained byte-for-byte; no deletion or in-place schema rewrite.
    }
    @Synchronized fun challenges(c:Context,date:String):JSONArray {
        importChallenges(c,date);val out=JSONArray()
        db(c).rawQuery("SELECT payload FROM records WHERE area='challenge' AND scope=? ORDER BY ordinal",arrayOf(date)).use{cursor->
            while(cursor.moveToNext())out.put(JSONObject(cursor.getString(0)))
        };return out
    }
    @Synchronized fun challenge(c:Context,date:String,key:String):JSONObject? {
        importChallenges(c,date)
        return db(c).rawQuery("SELECT payload FROM records WHERE area='challenge' AND scope=? AND identity=?",arrayOf(date,key)).use{if(it.moveToFirst())JSONObject(it.getString(0)) else null}
    }
    @Synchronized fun saveChallenge(c:Context,date:String,row:JSONObject){
        importChallenges(c,date);check(row.getString("date")==date){"challenge_date_mismatch"}
        val key=row.getString("key");val payload=row.toString();val previous=challenge(c,date,key)
        if(previous!=null){
            val old=previous.getJSONArray("ads");val next=row.getJSONArray("ads");check(next.length()>=old.length()){"ad_history_truncated"}
            for(i in 0 until old.length()){
                val a=old.getJSONObject(i);val n=next.getJSONObject(i)
                check(a.getString("runId")==n.getString("runId")&&a.getInt("index")==n.getInt("index")&&a.getLong("startedAt")==n.getLong("startedAt")){"ad_identity_replaced"}
                check(!a.optBoolean("confirmed")||n.optBoolean("confirmed")){"confirmed_receipt_regressed"}
                check(OfficialChallengeBinding.unchanged(a,n)){"official_ad_binding_replaced_or_added_late"}
            }
        }
        transaction(c){sql->
            val ordinal=sql.rawQuery("SELECT ordinal FROM records WHERE area='challenge' AND scope=? AND identity=?",arrayOf(date,key)).use{if(it.moveToFirst())it.getInt(0) else -1}
            if(ordinal>=0)sql.execSQL("UPDATE records SET payload=? WHERE area='challenge' AND scope=? AND identity=?",arrayOf(payload,date,key))
            else {val order=sql.rawQuery("SELECT COALESCE(MAX(ordinal),-1)+1 FROM records WHERE area='challenge' AND scope=?",arrayOf(date)).use{it.moveToFirst();it.getInt(0)}
                sql.execSQL("INSERT INTO records(area,scope,identity,ordinal,payload) VALUES('challenge',?,?,?,?)",arrayOf(date,key,order,payload))}
        }
    }
    /** Serialize review against the latest row; reuse original FULL save/identity guards, never insert an attempt. */
    @Synchronized fun updateChallenge(c:Context,date:String,key:String,change:(JSONObject)->JSONObject?):JSONObject? {
        val previous=challenge(c,date,key)?:return null
        val next=change(JSONObject(previous.toString()))?:return null
        check(next.getString("date")==date&&next.getString("key")==key){"challenge_review_identity_changed"}
        if(next.toString()!=previous.toString())saveChallenge(c,date,next)
        return next
    }
    @Synchronized fun read(c:Context,area:String,scope:String,identity:String,legacy:()->String?):String? {
        val value=db(c).rawQuery("SELECT payload FROM records WHERE area=? AND scope=? AND identity=?",arrayOf(area,scope,identity)).use{if(it.moveToFirst())it.getString(0) else null}
        if(value!=null)return value
        val old=legacy()?:return null
        put(c,area,scope,identity,old);return old
    }
    @Synchronized fun put(c:Context,area:String,scope:String,identity:String,payload:String){
        transaction(c){sql->sql.execSQL("INSERT OR REPLACE INTO records(area,scope,identity,ordinal,payload) VALUES(?,?,?,0,?)",arrayOf(area,scope,identity,payload))}
    }
    @Synchronized fun mutate(c:Context,area:String,scope:String,identity:String,change:(JSONObject?)->JSONObject?):JSONObject? = transaction(c){sql->
        val old=sql.rawQuery("SELECT payload FROM records WHERE area=? AND scope=? AND identity=?",arrayOf(area,scope,identity)).use{if(it.moveToFirst())JSONObject(it.getString(0)) else null}
        val next=change(old)
        if(next!=null)sql.execSQL("INSERT OR REPLACE INTO records(area,scope,identity,ordinal,payload) VALUES(?,?,?,0,?)",arrayOf(area,scope,identity,next.toString()))
        next
    }
    @Synchronized fun exportSnapshot(c:Context):JSONObject {
        check(!AutoAccessibilityService.isRunning&&CheckinRuntime.activeApp==null){"export_requires_idle"}
        val sql=db(c)
        val integrity=sql.rawQuery("PRAGMA quick_check",null).use{check(it.moveToFirst());it.getString(0)}
        check(integrity=="ok"){"ledger_snapshot_integrity_failed"}
        val records=JSONArray();val imports=JSONArray()
        sql.rawQuery("SELECT area,scope,identity,ordinal,payload FROM records ORDER BY area,scope,ordinal,identity",null).use{cur->
            while(cur.moveToNext())records.put(JSONObject().put("area",cur.getString(0)).put("scope",cur.getString(1))
                .put("identity",cur.getString(2)).put("ordinal",cur.getInt(3)).put("payload",cur.getString(4)))
        }
        sql.rawQuery("SELECT area,scope FROM imports ORDER BY area,scope",null).use{cur->
            while(cur.moveToNext())imports.put(JSONObject().put("area",cur.getString(0)).put("scope",cur.getString(1)))
        }
        val legacy=JSONObject()
        // Read-only copies include retained pre-SQLite business days; no lazy import or history mutation.
        for(name in listOf("challenge_boost_v1","ana_daily_ads","ana_official_ads_v1","ana_login_bonus_v1")){
            val values=JSONObject()
            c.getSharedPreferences(name,Context.MODE_PRIVATE).all.forEach{(key,value)->values.put(key,value)}
            legacy.put(name,values)
        }
        return JSONObject().put("legacyBusinessPreferences",legacy).put("format","tuffy_sqlite_logical_snapshot_v1").put("integrity",integrity)
            .put("records",records).put("imports",imports).put("schemaVersion",1)
    }
    @Synchronized fun scopes(c:Context,area:String):List<String> = db(c).rawQuery("SELECT DISTINCT scope FROM records WHERE area=?",arrayOf(area)).use{cur->buildList{while(cur.moveToNext())add(cur.getString(0))}}
}
