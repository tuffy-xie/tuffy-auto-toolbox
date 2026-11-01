package com.anapocket.auto

import android.app.Service
import android.content.*
import android.graphics.BitmapFactory
import android.os.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled Japanese recognition runs in :checkin_ocr; no network/upload and no UI privileges. */
class CheckinPerceptionUnavailable(reason:String):IllegalStateException(reason)
class CheckinOcrService:Service() {
    companion object {private val initLock=Any();@Volatile private var initialized=false}
    private var initFailure=false
    private var recognizer:com.google.mlkit.vision.text.TextRecognizer?=null
    private fun journal(event:String){runCatching{File(filesDir,"checkin_ocr_events.log").appendText(System.currentTimeMillis().toString()+" pid="+Process.myPid()+" "+event+"\n")}}
    override fun onCreate(){
        super.onCreate()
        runCatching{synchronized(initLock){if(!initialized){
            try{com.google.mlkit.common.MlKit.initialize(applicationContext)}catch(e:IllegalStateException){if(e.message?.contains("already initialized")!=true)throw e}
            initialized=true;journal("context_initialized_or_reused")
        }else journal("existing_process_context_reused")}}
            .onFailure{initFailure=true;journal("initialization_failed:"+it.javaClass.simpleName)}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_NOT_STICKY
    private fun reply(to:Messenger?,data:Bundle){runCatching{to?.send(Message.obtain().apply{this.data=data})}}
    private fun error(to:Messenger?,code:String){reply(to,Bundle().apply{putString("error",code)})}
    private val messenger=Messenger(Handler(Looper.getMainLooper()){msg->
        runCatching{handle(msg)}.onFailure{journal("request_failed:"+it.javaClass.simpleName);error(msg.replyTo,"request_failed")};true
    })
    private fun handle(msg:Message){
        val to=msg.replyTo
        if(initFailure){error(to,"initialization_failed");return}
        val file=File(msg.data.getString("path").orEmpty())
        if(!file.canonicalPath.startsWith(File(cacheDir,"checkin_ocr").canonicalPath+File.separator)){error(to,"invalid_image_path");return}
        val bmp=BitmapFactory.decodeFile(file.path)
        if(bmp==null){error(to,"image_unavailable");return}
        try {
            val client=recognizer?:TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()).also{recognizer=it}
            client.process(InputImage.fromBitmap(bmp,0)).addOnSuccessListener{result->
                runCatching{
                    val lines=JSONArray()
                    result.textBlocks.flatMap{it.lines}.take(200).forEach{line->line.boundingBox?.let{b->lines.put(JSONObject().put("t",line.text).put("l",b.left).put("y",b.top).put("r",b.right).put("b",b.bottom))}}
                    reply(to,Bundle().apply{putString("lines",lines.toString())});journal("recognition_ok lines="+lines.length())
                }.onFailure{error(to,"result_unavailable")}
            }.addOnFailureListener{error(to,"recognition_failed")}.addOnCompleteListener{runCatching{bmp.recycle()}}
        }catch(t:Throwable){runCatching{bmp.recycle()};throw t}
    }
    override fun onBind(intent:Intent?)=messenger.binder
    override fun onDestroy(){runCatching{recognizer?.close()};recognizer=null;journal("service_destroyed_context_retained");super.onDestroy()}
}

object CheckinOcr {
    data class DartPull(val arrow:CheckinLine,val endY:Int)
    /** Find the red dart BELOW the wheel using the actual vertical pull instruction as anchor. */
    fun dartPull(png:ByteArray,lines:List<CheckinLine>):DartPull? {
        val hint=lines.firstOrNull{it.text.replace(Regex("\\s"),"").let{t->t.contains("引")&&t.contains("はなす")}}?:return null
        val bmp=BitmapFactory.decodeByteArray(png,0,png.size)?:return null
        try{
            val h=hint.bottom-hint.top;if(h<40)return null
            val top=(hint.top-h).coerceAtLeast(bmp.height/2);val bottom=(hint.bottom+h/3).coerceAtMost(bmp.height-100)
            val right=(hint.left-12).coerceAtMost(bmp.width-1)
            var minX=right;var maxX=-1;var minY=bottom;var maxY=-1;var red=0
            for(y in top until bottom step 2)for(x in bmp.width/4 until right step 2){
                val c=bmp.getPixel(x,y);val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
                if(r>140&&g<135&&b<135&&r>g*1.35&&r>b*1.35){minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y);red++}
            }
            if(red<80||maxX<=minX||maxY-minY<h/2||maxX-minX>bmp.width/4)return null
            val arrow=CheckinLine("actual_red_dart",minX,minY,maxX+1,maxY+1)
            val end=(maxY+maxOf(90,(maxY-minY)/2)).coerceAtMost(bmp.height-180)
            if(end-arrow.y<80)return null
            return DartPull(arrow,end)
        }finally{bmp.recycle()}
    }
    /** OCR sometimes reads the check icon as kana. Require real green status ink before normalizing. */
    fun normalizeGreenStatus(png:ByteArray,lines:List<CheckinLine>):List<CheckinLine> {
        if(lines.none{CheckinReceiptPolicy.greenCompletionCandidate(it.text)})return lines
        val bmp=BitmapFactory.decodeByteArray(png,0,png.size)?:return lines
        try{return lines.map{line->
            if(!CheckinReceiptPolicy.greenCompletionCandidate(line.text))return@map line
            var green=0;var pixels=0
            for(y in line.top.coerceAtLeast(0) until line.bottom.coerceAtMost(bmp.height) step 2)for(x in line.left.coerceAtLeast(0) until line.right.coerceAtMost(bmp.width) step 2){
                val c=bmp.getPixel(x,y);val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
                if(g>65&&g>r*1.6&&g>b*1.6)green++;pixels++
            }
            if(green>=12&&green.toDouble()/maxOf(1,pixels)>.025)line.copy(text="達成") else line
        }}finally{bmp.recycle()}
    }
    fun warm(ctx:Context){runCatching{ctx.startService(Intent(ctx,CheckinOcrService::class.java))}}
    fun stop(ctx:Context){runCatching{ctx.stopService(Intent(ctx,CheckinOcrService::class.java))}}
    suspend fun read(ctx:Context,png:ByteArray):List<CheckinLine> {
        repeat(2){attempt->try{return readOnce(ctx,png)}catch(e:CheckinPerceptionUnavailable){
            currentCoroutineContext().ensureActive()
            if(attempt!=0||e.message!="recognizer_service_unavailable")throw e
            delay(1000) // Rebind once after a disconnected/killed helper; never repeats an official action.
        }}
        error("recognition_unavailable")
    }
    private suspend fun readOnce(ctx:Context,png:ByteArray):List<CheckinLine> {
        val file=File(File(ctx.cacheDir,"checkin_ocr").apply{mkdirs()},CheckinReceiptPolicy.id()+".png")
        withContext(Dispatchers.IO){file.writeBytes(png)}
        var connection:ServiceConnection?=null;var bound=false
        try{return withTimeout(8_000){suspendCancellableCoroutine{cont->
            val replies=Messenger(Handler(Looper.getMainLooper()){m->
                try{if(cont.isActive){val raw=m.data.getString("lines");if(raw==null)cont.resumeWithException(CheckinPerceptionUnavailable(m.data.getString("error")?:"reply_unavailable")) else {
                    val array=JSONArray(raw);cont.resume((0 until array.length()).map{val o=array.getJSONObject(it);CheckinLine(java.text.Normalizer.normalize(o.getString("t"),java.text.Normalizer.Form.NFKC),o.getInt("l"),o.getInt("y"),o.getInt("r"),o.getInt("b"))})}}
                }catch(e:Exception){if(cont.isActive)cont.resumeWithException(CheckinPerceptionUnavailable("invalid_recognizer_reply"))}
                true
            })
            val conn=object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName?,binder:IBinder?){
                    if(!cont.isActive)return
                    runCatching{Messenger(binder).send(Message.obtain().apply{replyTo=replies;data=Bundle().apply{putString("path",file.path)}})}.onFailure{if(cont.isActive)cont.resumeWithException(CheckinPerceptionUnavailable("recognizer_service_unavailable"))}
                }
                override fun onServiceDisconnected(name:ComponentName?){if(cont.isActive)cont.resumeWithException(CheckinPerceptionUnavailable("recognizer_service_unavailable"))}
                override fun onNullBinding(name:ComponentName?){if(cont.isActive)cont.resumeWithException(CheckinPerceptionUnavailable("recognizer_service_unavailable"))}
                override fun onBindingDied(name:ComponentName?){if(cont.isActive)cont.resumeWithException(CheckinPerceptionUnavailable("recognizer_service_unavailable"))}
            };connection=conn
            bound=ctx.bindService(Intent(ctx,CheckinOcrService::class.java),conn,Context.BIND_AUTO_CREATE)
            if(!bound&&cont.isActive)cont.resumeWithException(CheckinPerceptionUnavailable("recognizer_service_unavailable"))
        }}}catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive();throw CheckinPerceptionUnavailable("recognition_timeout")}finally{if(bound)connection?.let{runCatching{ctx.unbindService(it)}};file.delete()}
    }
    /** Compare the exact recognized target on a NEW screenshot before any gesture. */
    fun stable(before:ByteArray,after:ByteArray,line:CheckinLine):Boolean {
        val a=BitmapFactory.decodeByteArray(before,0,before.size)?:return false
        val b=BitmapFactory.decodeByteArray(after,0,after.size)?:run{a.recycle();return false}
        try {
            if(a.width!=b.width||a.height!=b.height)return false
            var count=0;var diff=0L
            val l=(line.left-4).coerceAtLeast(0);val r=(line.right+4).coerceAtMost(a.width-1)
            val t=(line.top-4).coerceAtLeast(0);val bottom=(line.bottom+4).coerceAtMost(a.height-1)
            if(r<=l||bottom<=t)return false
            for(y in t..bottom step 3)for(x in l..r step 3){val p=a.getPixel(x,y);val q=b.getPixel(x,y);diff+=kotlin.math.abs((p and 255)-(q and 255))+kotlin.math.abs((p shr 8 and 255)-(q shr 8 and 255))+kotlin.math.abs((p shr 16 and 255)-(q shr 16 and 255));count++}
            return count>0&&diff.toDouble()/count/765.0<0.025
        }finally{a.recycle();b.recycle()}
    }
}
