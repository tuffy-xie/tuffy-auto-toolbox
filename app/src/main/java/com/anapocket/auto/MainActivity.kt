package com.anapocket.auto

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity:AppCompatActivity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var refreshJob:Job?=null;private var current=AppSettings();private var page=0;private var applying=true;private var showResultsOnly=false
 private data class CatRow(val cat:Cat,val input:EditText,val status:TextView,val toggle:SwitchCompat)
 private data class Tile(val cat:Cat,val range:TextView,val status:TextView)
 private val rows=mutableListOf<CatRow>();private val tiles=mutableListOf<Tile>()
 private val modes=listOf(Cat.WALK,Cat.BIKE,Cat.CAR,Cat.MOV_ANY,Cat.SHINKANSEN,Cat.FLIGHT)
 private fun tv(id:Int)=findViewById<TextView>(id);private fun btn(id:Int)=findViewById<Button>(id);private fun sw(id:Int)=findViewById<SwitchCompat>(id)
 private val tvLogPath get()=tv(R.id.tvLogPath)
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);setContentView(R.layout.activity_main);Logger.init(this);Notifier.ensureChannel(this)
  tv(R.id.tvVersion).text="Tuffy的自动百宝箱 · "+BuildConfig.VERSION_NAME+(if(BuildConfig.DEBUG)" debug" else "")
  TelegramSettingsPanel(this,scope).bind();buildCats();buildHome()
  listOf(R.id.navToday,R.id.navRecords,R.id.navSettings).forEachIndexed{i,id->btn(id).setOnClickListener{showPage(i)}}
  btn(R.id.btnHomeSettings).setOnClickListener{showPage(2)};btn(R.id.btnAdjustRange).setOnClickListener{showPage(2)}
  findViewById<View>(R.id.homeAdsRow).setOnClickListener{showAdHistory()}
  findViewById<View>(R.id.homeLoginRow).setOnClickListener{AlertDialog.Builder(this).setTitle("每日签到").setMessage(LoginBonusStore.label(this,TokyoDate.of())).setPositiveButton("关闭",null).show()}
  btn(R.id.btnStartRoutine).setOnClickListener{
   if(AutoAccessibilityService.isRunning){AutoAccessibilityService.instance?.requestStop();toast("正在停止，已保存的观看不会重复启动");return@setOnClickListener}
   if(AccountSafetyStore.pending(this)!=null){showSafety();return@setOnClickListener}
   if(showResultsOnly){showPage(1);return@setOnClickListener}
   val svc=AutoAccessibilityService.instance
   if(svc==null){toast("请在设置中开启无障碍服务");showPage(2);return@setOnClickListener}
   if(AutoAccessibilityService.isRunning){toast("任务已在运行，请通过通知停止");return@setOnClickListener}
   if(saveSettings(true))svc.startDailyRoutine("manual")
  }
  btn(R.id.btnRun).setOnClickListener{if(!AutoAccessibilityService.isConnected())toast("请先开启无障碍服务") else if(!AutoAccessibilityService.isRunning&&saveSettings(true))AutoAccessibilityService.triggerDailyAds("manual",manualNew=true)}
  btn(R.id.btnChallenges).setOnClickListener{showChallenges()};tv(R.id.tvChalSummary).setOnClickListener{showChallenges()};btn(R.id.btnAdHistory).setOnClickListener{showAdHistory()}
  btn(R.id.btnSave).setOnClickListener{saveSettings()}
  btn(R.id.btnResetLimits).setOnClickListener{for(r in rows){r.input.setText(if(r.cat.defaultKm>0)r.cat.defaultKm.toString() else "");r.toggle.isChecked=!r.cat.longDistance};saveSettings(true);toast("已恢复日常范围，新干线与飞机保持关闭")}
  btn(R.id.btnTime).setOnClickListener{TimePickerDialog(this,{_,h,m->current=current.copy(hour=h,minute=m);saveSettings(true);refresh()},current.hour,current.minute,true).show()}
  sw(R.id.swRoutineSchedule).setOnCheckedChangeListener{_,_->if(!applying)saveSettings(true)};sw(R.id.swAd).setOnCheckedChangeListener{_,_->if(!applying)saveSettings(true)}
  btn(R.id.btnA11y).setOnClickListener{safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))};btn(R.id.btnNotify).setOnClickListener{requestNotify()}
  btn(R.id.btnAlarm).setOnClickListener{if(Build.VERSION.SDK_INT>=31)safeStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+packageName)))}
  btn(R.id.btnOverlay).setOnClickListener{safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+packageName)))};btn(R.id.btnAutostart).setOnClickListener{openAutostart()}
  btn(R.id.btnLockPermission).setOnClickListener{try{startActivity(Intent().setComponent(ComponentName("com.miui.securitycenter","com.miui.permcenter.permissions.PermissionsEditorActivity")).putExtra("extra_pkgname",packageName))}catch(e:Exception){safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+packageName)))}}
  btn(R.id.btnSafety).setOnClickListener{showSafety()};btn(R.id.btnWakeTest).setOnClickListener{Scheduler.scheduleReadinessTest(this);toast("90秒后唤醒并检查打开ANA，不观看广告")}
  btn(R.id.btnSelfTest).setOnClickListener{Scheduler.scheduleSelfTest(this,90_000L);toast("90秒后检查本机通知，不启动任务")}
  btn(R.id.btnToggleDiagnostics).setOnClickListener{val v=findViewById<View>(R.id.diagnosticPanel);v.visibility=if(v.visibility==View.VISIBLE)View.GONE else View.VISIBLE};btn(R.id.btnExport).setOnClickListener{exportLog()};btn(R.id.btnRefresh).setOnClickListener{refresh()}
  scope.launch{current=Prefs.load(this@MainActivity);applySettings();applying=false;Scheduler.schedule(this@MainActivity);refresh()};showPage(0)
 }
 override fun onResume(){super.onResume();refreshJob?.cancel();refreshJob=scope.launch{while(isActive){refresh();delay(1500)}}}
 override fun onPause(){refreshJob?.cancel();super.onPause()};override fun onDestroy(){scope.cancel();super.onDestroy()}
 private fun showPage(index:Int){if(page==2&&index!=2&&!applying&&!saveSettings(true))return;page=index
  findViewById<View>(R.id.todayActionBar).visibility=if(index==0)View.VISIBLE else View.GONE
  listOf(R.id.pageToday,R.id.pageRecords,R.id.pageSettings).forEachIndexed{i,id->findViewById<View>(id).visibility=if(i==index)View.VISIBLE else View.GONE}
  listOf(R.id.navToday,R.id.navRecords,R.id.navSettings).forEachIndexed{i,id->btn(id).apply{isSelected=i==index;setTextColor(color(if(i==index)R.color.brand else R.color.muted))}};refresh()
 }
 private fun icon(cat:Cat)=when(cat){Cat.WALK->R.drawable.ic_mode_walk;Cat.BIKE->R.drawable.ic_mode_bike;Cat.CAR->R.drawable.ic_mode_car;Cat.MOV_ANY->R.drawable.ic_mode_any;Cat.SHINKANSEN->R.drawable.ic_mode_shinkansen;Cat.FLIGHT->R.drawable.ic_mode_flight}
 private fun tint(cat:Cat)=android.graphics.Color.parseColor(when(cat){Cat.WALK->"#FFF0E8";Cat.BIKE->"#FFF7E2";Cat.CAR->"#EDF5FF";Cat.MOV_ANY->"#E9FAF2";Cat.SHINKANSEN->"#F3EFFF";Cat.FLIGHT->"#FFF0F1"})
 private fun rounded(fill:Int)=android.graphics.drawable.GradientDrawable().apply{setColor(fill);cornerRadius=dp(12).toFloat();setStroke(dp(1),android.graphics.Color.argb(20,100,100,140))}
 private fun buildHome(){val host=findViewById<LinearLayout>(R.id.homeCategories)
  for(group in modes.chunked(3)){val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-1,-2)}
   for(cat in group){val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=rounded(tint(cat));layoutParams=LinearLayout.LayoutParams(0,dp(88),1f).apply{setMargins(dp(3),dp(3),dp(3),dp(3))};isClickable=true;isFocusable=true;contentDescription=cat.title+"任务设置";setOnClickListener{showPage(2)}}
    card.addView(ImageView(this).apply{setImageResource(icon(cat));layoutParams=LinearLayout.LayoutParams(dp(26),dp(26))})
    card.addView(TextView(this).apply{text=cat.title;gravity=Gravity.CENTER;textSize=13f;setTextColor(color(R.color.text));setTypeface(null,Typeface.BOLD);setPadding(0,dp(3),0,0)})
    val range=TextView(this).apply{gravity=Gravity.CENTER;textSize=10f;setTextColor(color(R.color.muted));setPadding(0,dp(3),0,0)};card.addView(range)
    val status=TextView(this).apply{textSize=10f;gravity=Gravity.CENTER;setPadding(dp(6),dp(2),dp(6),dp(2));layoutParams=LinearLayout.LayoutParams(-2,-2).apply{topMargin=dp(3)}};card.addView(status);tiles.add(Tile(cat,range,status));line.addView(card)
   };host.addView(line)
  }
 }
 private fun buildCats(){val host=findViewById<LinearLayout>(R.id.llCats)
  for(cat in modes){val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(-1,dp(68))}
   line.addView(ImageView(this).apply{setImageResource(icon(cat));layoutParams=LinearLayout.LayoutParams(dp(26),dp(26)).apply{marginEnd=dp(8)}})
   val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
   labels.addView(TextView(this).apply{text=cat.title;textSize=13f;setTextColor(color(R.color.text))});val stat=TextView(this).apply{textSize=9f;setTextColor(color(R.color.muted))};labels.addView(stat);line.addView(labels)
   val input=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER;textSize=14f;gravity=Gravity.CENTER;setTextColor(color(R.color.brand));setBackgroundResource(R.drawable.bg_input);hint=if(cat.longDistance)"公里" else "";layoutParams=LinearLayout.LayoutParams(dp(62),dp(42));setSingleLine()};line.addView(input)
   line.addView(TextView(this).apply{text="km";textSize=10f;setTextColor(color(R.color.muted));setPadding(dp(4),0,dp(4),0)})
   val toggle=SwitchCompat(this).apply{layoutParams=LinearLayout.LayoutParams(-2,-2);contentDescription="启用"+cat.title;isChecked=!cat.longDistance};line.addView(toggle);host.addView(line);rows.add(CatRow(cat,input,stat,toggle))
  }
 }
 private fun applySettings(){applying=true;sw(R.id.swRoutineSchedule).isChecked=current.enabled;sw(R.id.swAd).isChecked=current.adEnabled;for(r in rows){r.input.setText(current.kmOf(r.cat).takeIf{it>0}?.toString().orEmpty());r.toggle.isChecked=current.catOn(r.cat)};applying=false}
 private fun saveSettings(quiet:Boolean=false):Boolean{
  val bad=rows.firstOrNull{it.toggle.isChecked&&CategoryDistancePolicy.parse(it.cat,it.input.text.toString())==null}
  if(bad!=null){bad.input.error="请输入1至"+bad.cat.maxKm+"的整数公里";toast("请先设置"+bad.cat.title+"公里上限");return false}
  current=current.copy(enabled=sw(R.id.swRoutineSchedule).isChecked,adEnabled=sw(R.id.swAd).isChecked,adTarget=DAILY_AD_LIMIT,dayToday=true,dayTomorrow=false,
   catEnabled=rows.associate{it.cat.name to it.toggle.isChecked},catKm=rows.associate{it.cat.name to (CategoryDistancePolicy.parse(it.cat,it.input.text.toString())?:if(it.cat.longDistance)0 else it.cat.defaultKm)})
  Prefs.saveBlocking(this,current);Scheduler.schedule(this,current.hour,current.minute);if(!quiet)toast("设置已保存");refresh();return true
 }
 private fun showSafety(){
  val row=AccountSafetyStore.pending(this)
  if(row==null){AlertDialog.Builder(this).setTitle("账号保护正常").setMessage("遇到可信账号风险提示或验证码时，会暂停任务并保留记录。人工处理后需复核；重启不会自动解除。\n\n无密码锁屏可在到点后唤醒并解锁；设置了密码则需本人解锁。").setPositiveButton("关闭",null).show();return}
  AlertDialog.Builder(this).setTitle("需要人工处理").setMessage(row.optString("title")+"\n"+row.optString("deviceTime")+"\n请先在ANA完成必要操作，再返回这里复核。复核不会自动开始任务。")
   .setPositiveButton("打开ANA手动处理"){_,_->packageManager.getLaunchIntentForPackage(StartupPolicy.ANA)?.let{safeStart(it)}}
   .setNeutralButton("处理后复核"){_,_->val svc=AutoAccessibilityService.instance;if(svc==null)toast("请先开启无障碍") else if(AutoAccessibilityService.isRunning)toast("请等待当前任务停止") else svc.startSafetyReview()}
   .setNegativeButton("保持暂停",null).show()
 }
 private fun showChallenges(){AlertDialog.Builder(this).setTitle("今日出行任务").setMessage(ChallengeStore.summary(this)).setPositiveButton("关闭",null).show()}
 private fun showAdHistory(){val date=TokyoDate.of();val official=OfficialAdStore.counter(this,date);val f=SimpleDateFormat("HH:mm:ss",Locale.US).apply{timeZone=TimeZone.getTimeZone("Asia/Tokyo")}
  val body="官方观看次数："+(official?.let{it.toString()+"/25"}?:"尚未核对")+"\n\n"+DailyAdStore.history(this,date,60).joinToString("\n\n"){"#"+it.index+" · "+f.format(Date(it.startedAt))+"\n"+(if(it.completed)"完成已确认" else "完成待核对")+" · "+(if(it.rewardConfirmed)"奖励回执已确认" else "奖励待核对")+(if(it.failureReason.isNotBlank())"\n"+TelegramText.reason(it.failureReason) else "")}
  AlertDialog.Builder(this).setTitle("今日广告记录").setMessage(body).setPositiveButton("关闭",null).show()
 }
 private fun refresh(){if(isFinishing)return;val date=TokyoDate.of();val running=AutoAccessibilityService.isRunning;val connected=AutoAccessibilityService.isConnected()
  val c=runCatching{DailyAdStore.counters(this,date)}.getOrElse{return};val official=OfficialAdStore.counter(this,date)
  val a=ChallengeStore.rows(this,date);val items=(0 until a.length()).map{a.getJSONObject(it)};val routine=RoutineStore.read(this,date);val login=LoginBonusStore.read(this,date)?.optString("state").orEmpty()
  tv(R.id.tvPhoneDate).text=date.replace('-','/');tv(R.id.tvHomeTitle).text=if(routine?.has("finishedAt")==true)"今日结果" else "今日任务"
  for(t in tiles){val enabled=current.catOn(t.cat);val km=current.kmOf(t.cat);val catRows=items.filter{it.optString("cat")==t.cat.name};val unknown=catRows.any{it.optString("state") !in setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED")}
   val covered=enabled&&km>0&&CategoryCompletionPolicy.covers(ChallengeStore.coverage(this,date,t.cat),km)&&!unknown
   t.range.text=if(km>0)"≤"+km+"km" else "未设置"
   t.status.text=when{!enabled->"未开启";covered&&catRows.isEmpty()->"无可接任务";covered->"✓ 已完成";unknown->"需核对";catRows.isNotEmpty()->"待完成";else->"待执行"}
   t.status.setTextColor(color(if(covered)R.color.accent else if(unknown)R.color.warn else R.color.muted));t.status.background=rounded(android.graphics.Color.parseColor(if(covered)"#DFF8ED" else if(unknown)"#FFF0CE" else "#EEF0F6"))
  }
  tv(R.id.tvHomeLogin).text=when(login){"REWARD_CONFIRMED"->"✓ 已领取";"UNCONFIRMED","FAILED"->"需核对";"NOT_PRESENT"->"未出现弹窗";else->"待检查"}
  tv(R.id.tvHomeAds).text=when{!current.adEnabled->"未开启";official!=null->"官方 "+official+"/25"+(if(c.unconfirmed>0)" · 待核对 "+c.unconfirmed else "");c.started>0->"本机确认 "+c.completed+"次 · 待核对官方";else->"最多25次"}
  val finished=routine?.has("finishedAt")==true;val failed=finished&&routine?.optString("state") in setOf("BLOCKED","STOPPED")
  val safety=AccountSafetyStore.pending(this)
   btn(R.id.btnSafety).text=if(safety==null)"账号保护：正常" else "账号保护：需要人工处理"
   val incomplete=finished&&(modes.filter{current.catOn(it)}.any{!CategoryCompletionPolicy.covers(ChallengeStore.coverage(this,date,it),current.kmOf(it))}||(current.adEnabled&&official!=25))
  findViewById<View>(R.id.homeNotice).visibility=if(!running&&(safety!=null||failed||incomplete))View.VISIBLE else View.GONE
  tv(R.id.tvHomeResult).text=if(safety!=null)"账号保护已暂停任务" else if(failed)"本次执行已停止" else "部分任务未完成"
  tv(R.id.tvHomeReason).text=if(safety!=null)safety.optString("title") else if(failed)TaskFailureText.short(routine?.optString("detail").orEmpty()) else if(official!=25&&current.adEnabled)"每日广告尚未完成官方核对" else "未完成项目保留，可继续处理"
  showResultsOnly=finished&&!failed&&!incomplete&&!running&&safety==null
  btn(R.id.btnStartRoutine).isEnabled=!applying;btn(R.id.btnStartRoutine).text=if(running)"■  停止任务" else if(safety!=null)"查看并处理账号保护" else if(showResultsOnly)"查看今日执行结果" else if(finished)"▶  继续未完成任务" else "▶  开始今日任务"
  btn(R.id.btnTime).text=String.format(Locale.US,"每日 %02d:%02d（手机JST）",current.hour,current.minute)
  tv(R.id.tvA11yStatus).text=if(connected)"已开启" else "未开启";tv(R.id.tvNotifyStatus).text=if(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)"已授权" else "未授权"
  tv(R.id.tvAlarmStatus).text=if(Build.VERSION.SDK_INT<31||(getSystemService(ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms())"已授权" else "需授权";tv(R.id.tvOverlayStatus).text=if(Settings.canDrawOverlays(this))"已授权" else "需授权"
  tv(R.id.tvAdStat).text="官方观看："+(official?.let{it.toString()+"/25"}?:"未核对")+"\n本机尝试 "+c.started+" · 确认完成 "+c.completed+" · 奖励回执 "+c.rewardConfirmed+"\n失败 "+c.failed+" · 待核对 "+c.unconfirmed
  tv(R.id.tvChalSummary).text=ChallengeStore.summary(this,date);for(r in rows)r.status.text=if(!current.catOn(r.cat))"未开启" else "已升满 "+items.count{it.optString("cat")==r.cat.name&&it.optInt("boost")==5}+" 项"
  val history=RoutineStore.history(this);tv(R.id.tvHistory).text=(0 until history.length()).map{history.getJSONObject(it)}.takeLast(5).reversed().joinToString("\n\n"){it.optString("date")+" · "+RoutinePolicy.stageLabel(it.optString("state"))+"\n"+it.optString("summary",it.optString("detail"))}.ifBlank{"尚无执行结果"}
  tv(R.id.tvProgress).text="下次计划："+Scheduler.describeNext(current.hour,current.minute);tv(R.id.tvTelegramStatus).text=TelegramSettingsStore(this).status();tvLogPath.text=Logger.path();tv(R.id.tvLog).text=Logger.tail(20)
 }
    private fun requestNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                return
            }
        }
        toast("通知已授权；若被系统关闭请到设置中开启")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun exportLog() {
        if(AutoAccessibilityService.isRunning){toast("请先停止任务，再导出故障包");return}
        scope.launch {
            try {
                val dst=withContext(Dispatchers.IO){FaultStore.export(this@MainActivity)}
                tvLogPath.text=dst.absolutePath
                val uri=androidx.core.content.FileProvider.getUriForFile(this@MainActivity,packageName+".files",dst)
                val send=Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(send,"分享故障包"))
            } catch(e:Exception){toast("导出失败："+e.message)}
        }
    }

    private fun openAutostart() {
        val candidates = listOf(
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.securitycenter/com.miui.powercenter.PowerSettings",
            "com.miui.securitycenter/com.miui.permcenter.permissions.PermissionsEditorActivity"
        )
        for (c in candidates) {
            try {
                val cn = ComponentName.unflattenFromString(c) ?: continue
                val i = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (_: Throwable) {
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Throwable) {
            toast("请手动进入系统设置开启自启动")
        }
    }

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (t: Throwable) {
            toast("无法打开设置: ${t.message}")
        }
    }

    private fun dp(value:Int)=(value*resources.displayMetrics.density+.5f).toInt()

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
