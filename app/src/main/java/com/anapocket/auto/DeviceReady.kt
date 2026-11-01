package com.anapocket.auto

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

data class DeviceReadyResult(val ready:Boolean,val reason:String="")
object DeviceReady {
    @Volatile internal var request:String=""
    @Volatile internal var expires:Long=0
    @Volatile internal var callback:String=""
    suspend fun ensure(svc:AutoAccessibilityService,run:String,forReview:Boolean=false):DeviceReadyResult {
        val km=svc.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pm=svc.getSystemService(Context.POWER_SERVICE) as PowerManager
        val initialLocked=km.isKeyguardLocked;val initialInteractive=pm.isInteractive
        var tries=0;var swipes=0
        fun result(ok:Boolean,why:String):DeviceReadyResult {
            request=""
            Handler(Looper.getMainLooper()).post{UnlockActivity.current?.finish()}
            Evidence.write(svc,"device_ready_"+run+".json",JSONObject().put("runId",run).put("date",TokyoDate.of()).put("phoneTime",TokyoDate.time()).put("initialLocked",initialLocked).put("initialInteractive",initialInteractive).put("ready",ok).put("reason",why).put("attempts",tries).put("swipes",swipes).put("callback",callback).put("secure",km.isKeyguardSecure||km.isDeviceSecure).toString(2))
            Logger.i("READY","run=$run ready=$ok initialLocked=$initialLocked attempts=$tries swipes=$swipes reason=$why")
            return DeviceReadyResult(ok,why)
        }
        while(true){
            if(!AutoAccessibilityService.isConnected())return result(false,"accessibility_disconnected")
            if(AutoAccessibilityService.stopRequested)return result(false,"user_stopped")
            if(!forReview&&AccountSafetyStore.pending(svc)!=null)return result(false,"account_safety_human_required")
            when(DeviceReadyPolicy.action(pm.isInteractive,km.isKeyguardLocked,km.isDeviceSecure||km.isKeyguardSecure,tries)){
                DeviceReadyAction.READY->return result(true,"unlocked_verified")
                DeviceReadyAction.OWNER_REQUIRED->return result(false,"secure_lock_requires_owner")
                DeviceReadyAction.EXHAUSTED->return result(false,"nonsecure_unlock_timeout")
                DeviceReadyAction.WAKE_NONSECURE->{
                    tries++;request=java.util.UUID.randomUUID().toString();expires=SystemClock.elapsedRealtime()+15_000;callback="requested"
                    val id=request
                    Handler(Looper.getMainLooper()).post{
                        runCatching{svc.startActivity(Intent(svc,UnlockActivity::class.java).putExtra("request",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))}
                            .onFailure{callback="activity_start_failed"}
                    }
                    repeat(24){if(pm.isInteractive&&!km.isKeyguardLocked)return result(true,"native_nonsecure_dismiss_verified");if(AutoAccessibilityService.stopRequested)return result(false,"user_stopped");delay(250)}
                    Handler(Looper.getMainLooper()).post{UnlockActivity.current?.finish()};delay(350)
                    val ui=Ui(svc);ui.refreshCache();val ids=ui.foregroundNodes().filter{it.isVisibleToUser}.mapNotNull{it.viewIdResourceName}.toSet()
                    if(pm.isInteractive&&DeviceReadyPolicy.canSwipe(ui.currentPackage(),ids,km.isKeyguardLocked,km.isKeyguardSecure||km.isDeviceSecure,swipes)){
                        swipes++;val w=svc.resources.displayMetrics.widthPixels;val h=svc.resources.displayMetrics.heightPixels
                        val path=android.graphics.Path().apply{moveTo(w*.5f,h*.83f);lineTo(w*.5f,h*.25f)}
                        val gesture=android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path,0,650)).build()
                        kotlinx.coroutines.withTimeoutOrNull(2500){suspendCancellableCoroutine<Boolean>{c->if(!svc.dispatchGesture(gesture,object:android.accessibilityservice.AccessibilityService.GestureResultCallback(){override fun onCompleted(g:android.accessibilityservice.GestureDescription?){if(c.isActive)c.resume(true)};override fun onCancelled(g:android.accessibilityservice.GestureDescription?){if(c.isActive)c.resume(false)}},null)&&c.isActive)c.resume(false)}}
                        delay(700)
                    }
                }
            }
        }
    }
}

/** Non-exported, short lived system keyguard request; never accepts credentials. */
class UnlockActivity:Activity(){
    companion object{@Volatile internal var current:UnlockActivity?=null}
    private var requested=false
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        if(intent.getStringExtra("request")!=DeviceReady.request||DeviceReady.request.isBlank()||SystemClock.elapsedRealtime()>DeviceReady.expires){finish();return}
        val km=getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if(km.isKeyguardSecure||km.isDeviceSecure){DeviceReady.callback="secure_lock_rejected";finish();return}
        current=this
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true)}
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply{text="Tuffy正在准备任务…";textSize=20f;gravity=android.view.Gravity.CENTER})
        Handler(Looper.getMainLooper()).postDelayed({if(!isFinishing)finish()},14_000)
    }
    override fun onWindowFocusChanged(focus:Boolean){super.onWindowFocusChanged(focus);if(focus&&!requested){requested=true
        val km=getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if(km.isKeyguardSecure||km.isDeviceSecure){DeviceReady.callback="secure_lock_rejected";finish();return}
        if(Build.VERSION.SDK_INT>=26)km.requestDismissKeyguard(this,object:KeyguardManager.KeyguardDismissCallback(){override fun onDismissSucceeded(){DeviceReady.callback="dismiss_succeeded"};override fun onDismissCancelled(){DeviceReady.callback="dismiss_cancelled"};override fun onDismissError(){DeviceReady.callback="dismiss_error"}})
    }}
    override fun onDestroy(){if(current===this)current=null;super.onDestroy()}
}
