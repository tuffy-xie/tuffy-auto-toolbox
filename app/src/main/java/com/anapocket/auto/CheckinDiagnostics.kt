package com.anapocket.auto

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Extends the user's existing manual diagnostic export; never exports configuration or preferences. */
object CheckinDiagnostics {
    fun append(ctx:Context,add:(String,ByteArray)->Unit){
        val summary=JSONObject().put("businessDate",TokyoDate.of()).put("source","real_local_checkin_ledgers")
        var remaining=64L*1024*1024
        for(app in CheckinApp.entries){
            summary.put(app.name,JSONArray().also{a->CheckinStore(ctx,app).all().forEach{a.put(it)}})
            val base=File(ctx.filesDir,"checkins/"+app.name).canonicalFile
            val runs=base.listFiles()?.filter{it.isDirectory&&it.canonicalFile.parentFile==base&&Regex("CI_[A-Z]+_[a-zA-Z0-9_-]+").matches(it.name)}
                ?.sortedByDescending{it.lastModified()}?.take(10).orEmpty()
            for(run in runs)for(file in run.listFiles()?.filter{it.isFile}?.sortedByDescending{it.lastModified()}.orEmpty()){
                if(file.canonicalFile.parentFile!=run.canonicalFile||file.extension !in setOf("json","log","png"))continue
                val limit=if(file.extension=="png")8L*1024*1024 else 2L*1024*1024
                if(file.length()>limit||file.length()>remaining)continue
                val data=if(file.extension=="png")file.readBytes() else DiagnosticPrivacy.redact(file.readText()).toByteArray()
                if(data.size>remaining)continue
                add("checkins/"+app.name+"/"+run.name+"/"+file.name,data);remaining-=data.size
            }
        }
        add("checkins/current_state.json",DiagnosticPrivacy.redact(summary.toString(2)).toByteArray())
    }
}
