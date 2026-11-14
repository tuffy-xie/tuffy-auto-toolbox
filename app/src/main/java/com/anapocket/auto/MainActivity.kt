package com.anapocket.auto

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
 private var refreshJob:Job?=null;private var snapJob:Job?=null;private var current=AppSettings();private var page=0;private var applying=true;private var showResultsOnly=false;private var primaryState:HomeStatusPolicy.Primary?=null
 private data class CatRow(val cat:Cat,val input:EditText,val status:TextView,val toggle:SwitchCompat)
 private data class Tile(val cat:Cat,val range:TextView,val status:TextView,val card:View,val check:ImageView)
 private var checkinPanel:CheckinPanel?=null
 private val rows=mutableListOf<CatRow>();private val tiles=mutableListOf<Tile>()
 private val modes=listOf(Cat.WALK,Cat.BIKE,Cat.CAR,Cat.MOV_ANY,Cat.SHINKANSEN,Cat.FLIGHT)
 private fun tv(id:Int)=findViewById<TextView>(id);private fun btn(id:Int)=findViewById<Button>(id);private fun sw(id:Int)=findViewById<SwitchCompat>(id)
 private val tvLogPath get()=tv(R.id.tvLogPath)
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);setContentView(R.layout.activity_main);Logger.init(this);Notifier.ensureChannel(this)
  tv(R.id.tvVersion).text="Tuffy的自动百宝箱 · "+BuildConfig.VERSION_NAME+(if(BuildConfig.DEBUG)" debug" else "")
  findViewById<ImageView>(R.id.ivLoginIcon).background=squircle(color(R.color.icon_login))
  findViewById<ImageView>(R.id.ivAdsIcon).background=squircle(color(R.color.icon_ads))
  TelegramSettingsPanel(this,scope).bind();buildCats();buildHome();checkinPanel=CheckinPanel(this).also{it.bind()}
  listOf(R.id.navToday,R.id.navRecords,R.id.navSettings).forEachIndexed{i,id->btn(id).setOnClickListener{showPage(i)}}
  findViewById<View>(R.id.btnHomeSettings).setOnClickListener{showPage(2)};btn(R.id.btnAdjustRange).setOnClickListener{showPage(2)}
  findViewById<View>(R.id.homeAdsRow).setOnClickListener{showAdHistory()}
  findViewById<View>(R.id.homeLoginRow).setOnClickListener{AlertDialog.Builder(this).setTitle("每日签到").setMessage(LoginBonusStore.label(this,TokyoDate.of())).setPositiveButton("关闭",null).show()}
  btn(R.id.btnStartRoutine).setOnClickListener{
   if(AutoAccessibilityService.isRunning){AutoAccessibilityService.instance?.requestStop();toast("正在停止，已保存的观看不会重复启动");return@setOnClickListener}
   if(AccountSafetyStore.pending(this)!=null){showSafety();return@setOnClickListener}
   // Everything today is confirmed: the button shows that instead of offering a run that has nothing left to do.
   if(showResultsOnly){showPage(1);return@setOnClickListener}
   val svc=AutoAccessibilityService.instance
   if(svc==null){toast("请在设置中开启无障碍服务");showPage(2);return@setOnClickListener}
   if(saveSettings(true))svc.startDailyRoutine("manual")
  }
  btn(R.id.btnRescanChallenges).setOnClickListener{requestChallengeScan(false)}
  btn(R.id.btnTodayResults).setOnClickListener{showPage(1)}
  btn(R.id.btnResetChallengeScan).setOnClickListener{requestChallengeScan(true)}
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
  btn(R.id.btnToggleDiagnostics).setOnLongClickListener{
   android.app.AlertDialog.Builder(this).setTitle("临时截图诊断")
    .setMessage("正式运行默认不保存截图。主动开启后有效10分钟，最多12张、总计32MiB；必要视觉识别仍按需执行。历史证据不会删除。")
    .setPositiveButton("开启10分钟"){_,_->ScreenshotDiagnostics.start(this)}
    .setNegativeButton("关闭截图"){_,_->ScreenshotDiagnostics.stop(this)}.setNeutralButton("取消",null).show();true
  }
  btn(R.id.btnToggleDiagnostics).setOnClickListener{val v=findViewById<View>(R.id.diagnosticPanel);v.visibility=if(v.visibility==View.VISIBLE)View.GONE else View.VISIBLE;refresh()};btn(R.id.btnExport).setOnClickListener{exportLog()};btn(R.id.btnRefresh).setOnClickListener{refresh()}
  scope.launch{current=Prefs.load(this@MainActivity);applySettings();applying=false;Scheduler.schedule(this@MainActivity);refresh()};showPage(0)
 }
 // The hero artwork is only needed while the screen is visible; dropping it lets a long background run reclaim the bitmap.
 override fun onStart(){super.onStart();findViewById<ImageView>(R.id.heroImage).setImageResource(R.drawable.tuffy_hero)}
 override fun onStop(){findViewById<ImageView>(R.id.heroImage).setImageDrawable(null);super.onStop()}
 override fun onResume(){super.onResume();refreshJob?.cancel();refreshJob=scope.launch{while(isActive){refresh();delay(if(AutoAccessibilityService.isRunning)1500 else 4000)}}}
 override fun onPause(){refreshJob?.cancel();super.onPause()};override fun onDestroy(){scope.cancel();super.onDestroy()}
 private fun showPage(index:Int){if(page==2&&index!=2&&!applying&&!saveSettings(true))return;page=index
  findViewById<View>(R.id.todayActionBar).visibility=if(index==0)View.VISIBLE else View.GONE
  listOf(R.id.pageToday,R.id.pageRecords,R.id.pageSettings).forEachIndexed{i,id->findViewById<View>(id).visibility=if(i==index)View.VISIBLE else View.GONE}
  listOf(R.id.navToday,R.id.navRecords,R.id.navSettings).forEachIndexed{i,id->btn(id).isSelected=i==index};refresh()
 }
 private fun icon(cat:Cat)=when(cat){Cat.WALK->R.drawable.ic_mode_walk;Cat.BIKE->R.drawable.ic_mode_bike;Cat.CAR->R.drawable.ic_mode_car;Cat.MOV_ANY->R.drawable.ic_mode_any;Cat.SHINKANSEN->R.drawable.ic_mode_shinkansen;Cat.FLIGHT->R.drawable.ic_mode_flight}
 private fun modeColor(cat:Cat)=color(when(cat){Cat.WALK->R.color.mode_walk;Cat.BIKE->R.color.mode_bike;Cat.CAR->R.color.mode_car;Cat.MOV_ANY->R.color.mode_any;Cat.SHINKANSEN->R.color.mode_shinkansen;Cat.FLIGHT->R.color.mode_flight})
 private fun squircle(fill:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(8).toFloat()}
 /** White glyph on a coloured rounded square, like an iOS settings icon. */
 private fun badge(cat:Cat,size:Int=30)=ImageView(this).apply{setImageResource(icon(cat));imageTintList=ColorStateList.valueOf(android.graphics.Color.WHITE);background=squircle(modeColor(cat));setPadding(dp(5),dp(5),dp(5),dp(5));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO;layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))}
 private fun hairline()=resources.getDimensionPixelSize(R.dimen.hairline).coerceAtLeast(1)
 private fun buildHome(){val host=findViewById<LinearLayout>(R.id.homeCategories)
  for(group in modes.chunked(3)){val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-1,-2)}
   for(cat in group){val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundResource(R.drawable.bg_tile);setPadding(dp(12),dp(12),dp(12),dp(10));minimumHeight=dp(112);layoutParams=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f).apply{setMargins(dp(5),dp(5),dp(5),dp(5))};isClickable=true;isFocusable=true;contentDescription=cat.jp;setOnClickListener{scope.launch{current=Prefs.toggleCategory(this@MainActivity,cat);applySettings();refresh();if(current.catOn(cat)&&current.kmOf(cat)==0)toast("已选中，请设置"+cat.title+"公里上限")}}}
    val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};top.addView(badge(cat))
    top.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
    val check=ImageView(this).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO};top.addView(check,LinearLayout.LayoutParams(dp(22),dp(22)));card.addView(top)
    card.addView(TextView(this).apply{text=cat.title;textSize=15f;setTextColor(color(R.color.text));setTypeface(null,Typeface.BOLD);setPadding(0,dp(9),0,0);maxLines=1})
    val range=TextView(this).apply{textSize=12f;setTextColor(color(R.color.muted));maxLines=1};card.addView(range)
    val status=TextView(this).apply{textSize=12f;setTypeface(null,Typeface.BOLD);maxLines=1;setPadding(0,dp(1),0,0)};card.addView(status)
    androidx.core.view.ViewCompat.setAccessibilityDelegate(card,object:androidx.core.view.AccessibilityDelegateCompat(){override fun onInitializeAccessibilityNodeInfo(v:View,n:androidx.core.view.accessibility.AccessibilityNodeInfoCompat){super.onInitializeAccessibilityNodeInfo(v,n);n.isCheckable=true;n.isChecked=v.isSelected}})
    tiles.add(Tile(cat,range,status,card,check));line.addView(card)
   };host.addView(line)
  }
 }
 private fun buildCats(){val host=findViewById<LinearLayout>(R.id.llCats)
  for((i,cat) in modes.withIndex()){
   if(i>0)host.addView(View(this).apply{setBackgroundColor(color(R.color.line))},LinearLayout.LayoutParams(-1,hairline()).apply{marginStart=dp(58)})
   val line=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,dp(16),0);layoutParams=LinearLayout.LayoutParams(-1,dp(62))}
   line.addView(badge(cat))
   val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(12)}}
   labels.addView(TextView(this).apply{text=cat.title;textSize=16f;setTextColor(color(R.color.text))});val stat=TextView(this).apply{textSize=12f;setTextColor(color(R.color.muted))};labels.addView(stat);line.addView(labels)
   val input=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER;textSize=15f;gravity=Gravity.CENTER;setTextColor(color(R.color.brand));setHintTextColor(color(R.color.muted));setBackgroundResource(R.drawable.bg_input);setPadding(dp(4),0,dp(4),0);hint=if(cat.longDistance)"公里" else "";contentDescription=cat.title+"公里上限";layoutParams=LinearLayout.LayoutParams(dp(60),dp(34));setSingleLine()};line.addView(input)
   line.addView(TextView(this).apply{text="km";textSize=13f;setTextColor(color(R.color.muted));setPadding(dp(5),0,dp(10),0)})
   val toggle=SwitchCompat(this).apply{layoutParams=LinearLayout.LayoutParams(-2,-2);contentDescription="启用"+cat.title;isChecked=!cat.longDistance};line.addView(toggle);host.addView(line);rows.add(CatRow(cat,input,stat,toggle))
  }
 }
 private fun applySettings(){applying=true;sw(R.id.swRoutineSchedule).isChecked=current.enabled;sw(R.id.swAd).isChecked=current.adEnabled;for(r in rows){r.input.setText(current.kmOf(r.cat).takeIf{it>0}?.toString().orEmpty());r.toggle.isChecked=current.catOn(r.cat)};applying=false}
 private fun saveSettings(quiet:Boolean=false):Boolean{
  val bad=rows.firstOrNull{it.toggle.isChecked&&CategoryDistancePolicy.parse(it.cat,it.input.text.toString())==null}
  if(bad!=null){bad.input.error="请输入1至"+bad.cat.maxKm+"的整数公里";toast("请先设置"+bad.cat.title+"公里上限");return false}
  current=current.copy(enabled=sw(R.id.swRoutineSchedule).isChecked,adEnabled=sw(R.id.swAd).isChecked,adTarget=DAILY_AD_LIMIT,dayToday=true,dayTomorrow=false,
   catEnabled=rows.associate{it.cat.name to it.toggle.isChecked},catKm=rows.associate{it.cat.name to (CategoryDistancePolicy.parse(it.cat,it.input.text.toString())?:if(it.cat.longDistance)0 else it.cat.defaultKm)})
  Prefs.saveBlocking(this,current);Scheduler.schedule(this,current.hour,current.minute);CheckinRuntime.updateAnaWindow(this);CheckinScheduler.protection(this);if(!quiet)toast("设置已保存");refresh();return true
 }

 private fun requestChallengeScan(resetOnly:Boolean){
  val preserving=!resetOnly&&ChallengeScanStore.pendingReviewAllowed(this)
  val reason=if(preserving)null else ChallengeScanStore.resetBlock(this)
  if(reason!=null){toast(reason);return}
  if(!AutoAccessibilityService.isConnected()){toast("请先开启无障碍服务，扫描状态未改变");return}
  AlertDialog.Builder(this).setTitle(if(preserving)"核对未确认观看（仅出行）" else if(resetOnly)"修复／重置出行扫描状态" else "重新扫描出行任务")
   .setMessage(if(preserving)"保留原账本与扫描状态，先核对旧观看和真实页面。只有安全隔离条件全部满足才继续独立出行项目；不会重播未确认广告，也不运行每日广告。无法核实时暂停并保留记录。" else if(resetOnly)"只重置当天所选分类的扫描缓存及派生完成状态，保留历史、广告回执、签到和所有已提交记录。重置后可重新扫描。" else "读取当前所选分类和公里上限，重新核对官方参加中及参加可能任务，仅接取真正遗漏的项目。已完成的每日广告不会重看。")
   .setPositiveButton(if(preserving)"保留记录并核对" else if(resetOnly)"重置扫描状态" else "重新扫描并继续"){_,_->
    val blocked=if(preserving&&ChallengeScanStore.pendingReviewAllowed(this))null else ChallengeScanStore.resetBlock(this)
    if(blocked!=null){toast(blocked);return@setPositiveButton}
    val svc=AutoAccessibilityService.instance
    if(svc==null){toast("无障碍未连接，未重置");return@setPositiveButton}
    val audit="SCANRESET"+java.util.UUID.randomUUID().toString()
    scope.launch{
     FaultStore.capture(svc,audit,"challenge_scan_reset","manual_reset_only="+resetOnly)
     if(preserving){
      if(!ChallengeScanStore.pendingReviewAllowed(this@MainActivity)){toast("页面或任务状态已变化，未启动核对");return@launch}
      svc.startDailyRoutine("manual-challenge-rescan");return@launch
     }
     if(ChallengeScanStore.resetBlock(this@MainActivity)!=null){toast("任务状态已变化，请稍后重试");return@launch}
     ChallengeScanStore.reset(this@MainActivity,TokyoDate.of(),audit);refresh()
     if(resetOnly)toast("扫描状态已重置，历史记录全部保留") else svc.startDailyRoutine("manual-challenge-rescan")
    }
   }.setNegativeButton("取消",null).show()
 }

 private fun showSafety(){
  val row=AccountSafetyStore.pending(this)
  if(row==null){AlertDialog.Builder(this).setTitle("账号保护正常").setMessage("遇到可信账号风险提示或验证码时，会暂停任务并保留记录。人工处理后需复核；重启不会自动解除。\n\n无密码锁屏可在到点后唤醒并解锁；设置了密码则需本人解锁。").setPositiveButton("关闭",null).show();return}
  AlertDialog.Builder(this).setTitle("需要人工处理").setMessage(row.optString("title")+"\n"+row.optString("deviceTime")+"\n请先在ANA完成必要操作，再返回这里复核。复核不会自动开始任务。")
   .setPositiveButton("打开ANA手动处理"){_,_->packageManager.getLaunchIntentForPackage(StartupPolicy.ANA)?.let{safeStart(it)}}
   .setNeutralButton("处理后复核"){_,_->val svc=AutoAccessibilityService.instance;if(svc==null)toast("请先开启无障碍") else if(AutoAccessibilityService.isRunning)toast("请等待当前任务停止") else svc.startSafetyReview()}
   .setNegativeButton("保持暂停",null).show()
 }
 private fun showChallenges(){
  val date=TokyoDate.of();val rows=ChallengeStore.rows(this,date)
  val pending=(0 until rows.length()).map{rows.getJSONObject(it)}.filter{NativeChallengeReceiptPolicy.pendingForReview(it.optString("state"),PendingInitialJoinStore.isolated(it,date))}
  val row=pending.singleOrNull();val binding=row?.let{PendingInitialJoinStore.binding(it)}
  val builder=AlertDialog.Builder(this).setTitle("今日出行任务").setMessage(ChallengeStore.summary(this)).setPositiveButton("关闭",null)
  if(!AutoAccessibilityService.isRunning&&CheckinRuntime.activeApp==null&&binding!=null&&NativeChallengeReceiptPolicy.reviewableInitial(binding,date))
   builder.setNeutralButton("核对原接取回执"){_,_->reviewInitialReceipt(binding)}
  builder.show()
 }
 private fun reviewInitialReceipt(expected:PendingInitialJoin){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),0)}
  box.addView(TextView(this).apply{text=expected.key.substringAfter('|')+"\n任务日 "+expected.date+"\n\n仅用于已人工核实的原广告连续返回。金额、次数和最近任务不能证明身份。这里只保存复核证据，继续任务时仍会校验官方成功页面。"})
  val checks=listOf("已核对原派发任务、日期及观看身份","原广告直接返回，ANA未重开且未观看其他广告","已观察官方接取成功及首次Boost面板").map{text->CheckBox(this).apply{this.text=text;box.addView(this)}}
  val hash=EditText(this).apply{hint="核对证据SHA256（64位）";setSingleLine();inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;box.addView(this)}
  val dialog=AlertDialog.Builder(this).setTitle("原接取回执人工复核").setView(box).setNegativeButton("取消",null).setPositiveButton("保存复核证据",null).create()
  dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   val digest=hash.text.toString().trim().lowercase(java.util.Locale.ROOT)
   if(checks.any{!it.isChecked}||!digest.matches(Regex("[0-9a-f]{64}"))){toast("请完成逐项核对并填写真实证据SHA256");return@setOnClickListener}
   try{
    check(!AutoAccessibilityService.isRunning&&CheckinRuntime.activeApp==null&&TokyoDate.of()==expected.date){"当前有任务运行或日期已变更"}
    val now=System.currentTimeMillis();val installed=packageManager.getPackageInfo(packageName,0).lastUpdateTime
    val saved=ReliableLedger.updateChallenge(this,expected.date,expected.key){row->
     val current=PendingInitialJoinStore.binding(row)?:return@updateChallenge null
     val all=ReliableLedger.challenges(this,expected.date)
     if(current!=expected||!NativeChallengeReceiptPolicy.reviewableInitial(current,TokyoDate.of())||
       (0 until all.length()).count{val r=all.getJSONObject(it);NativeChallengeReceiptPolicy.pendingForReview(r.optString("state"),PendingInitialJoinStore.isolated(r,expected.date))}!=1)return@updateChallenge null
     val ad=row.getJSONArray("ads").getJSONObject(0)
     if(ad.has("nativeRecoveryStartedAt")||ad.optInt("officialPromptResumeRequests")>0||ad.has("initialReceiptReview"))return@updateChallenge null
     ad.put("initialReceiptReview",JSONObject().put("kind",NativeChallengeReceiptPolicy.REVIEW_KIND)
      .put("date",current.date).put("key",current.key).put("runId",current.runId).put("startedAt",current.startedAt).put("index",current.index)
      .put("reviewedAt",now).put("installedAt",installed).put("evidenceHash",digest))
     row
    }
    check(saved!=null){"原记录已变化或已保存复核，请重新核对"}
    dialog.dismiss();toast("已保存复核证据；请继续未完成任务核对官方页面");refresh()
   }catch(e:Exception){toast("复核未保存："+e.message)}
  }};dialog.show()
 }

 private fun showAdHistory(){val date=TokyoDate.of();val official=OfficialAdStore.counter(this,date);val f=SimpleDateFormat("HH:mm:ss",Locale.US).apply{timeZone=TimeZone.getTimeZone("Asia/Tokyo")}
  val body="官方观看次数："+(official?.let{it.toString()+"/25"}?:"尚未核对")+"\n\n"+DailyAdStore.history(this,date,60).joinToString("\n\n"){"#"+it.index+" · "+f.format(Date(it.startedAt))+"\n"+(if(it.completed)"完成已确认" else "完成待核对")+" · "+(if(it.rewardConfirmed)"奖励回执已确认" else "奖励待核对")+(if(it.failureReason.isNotBlank())"\n"+TelegramText.reason(it.failureReason) else "")}
  AlertDialog.Builder(this).setTitle("今日广告记录").setMessage(body).setPositiveButton("关闭",null).show()
 }

 /** Everything read from the ledgers and stores. Built off the main thread, applied on it. */
 private class TileSnap(val enabled:Boolean,val km:Int,val covered:Boolean,val unknown:Boolean,val total:Int,val done:Int,val scanLabel:String,val maxed:Int)
 private class Snap(val date:String,val running:Boolean,val c:AdCounters,val official:Int?,val tiles:Map<Cat,TileSnap>,val login:String,
  val routine:JSONObject?,val safety:JSONObject?,val recovery:String?,val incomplete:Boolean,val hasTodayWork:Boolean,
  val chalSummary:String?,val history:String?,val nextPlan:String?,val telegram:String?)
 private fun snapshot(s:AppSettings,records:Boolean,settings:Boolean,diagnostics:Boolean):Snap{
  val date=TokyoDate.of();val running=AutoAccessibilityService.isRunning
  val c=DailyAdStore.counters(this,date);val official=OfficialAdStore.counter(this,date)
  val a=ChallengeStore.rows(this,date);val items=(0 until a.length()).map{a.getJSONObject(it)};val routine=RoutineStore.read(this,date);val login=LoginBonusStore.read(this,date)?.optString("state").orEmpty()
  val tiles=modes.associateWith{cat->val enabled=s.catOn(cat);val km=s.kmOf(cat);val catRows=items.filter{it.optString("cat")==cat.name}
   val maxedStates=setOf("BOOST_MAX_CONFIRMED","BOOST_MAX_OBSERVED");val unknown=catRows.any{it.optString("state") !in maxedStates}
   val complete=enabled&&km>0&&ChallengeScanStore.complete(this,date,cat,km)
   TileSnap(enabled,km,complete&&!unknown,unknown,catRows.size,catRows.count{it.optString("state") in maxedStates},
    if(enabled&&km>0&&catRows.isEmpty()&&!complete)ChallengeScanPolicy.label(ChallengeScanStore.status(this,date,cat,km)) else "",catRows.count{it.optInt("boost")==5})}
  val hasTodayWork=routine!=null||items.isNotEmpty()||c.started>0||login in setOf("UNCONFIRMED","FAILED","REWARD_CONFIRMED")
  val incomplete=hasTodayWork&&(modes.filter{s.catOn(it)}.any{!ChallengeScanStore.complete(this,date,it,s.kmOf(it))}||(s.adEnabled&&official!=25)||login in setOf("UNCONFIRMED","FAILED"))
  val history=if(records)RoutineStore.history(this).let{h->(0 until h.length()).map{h.getJSONObject(it)}.takeLast(5).reversed().joinToString("\n\n"){it.optString("date")+" · "+RoutinePolicy.stageLabel(it.optString("state"))+"\n"+it.optString("summary",it.optString("detail"))}.ifBlank{"尚无执行结果"}} else null
  return Snap(date,running,c,official,tiles,login,routine,AccountSafetyStore.pending(this),AnaProcessRecovery.describe(this),incomplete,hasTodayWork,
   if(records)ChallengeStore.summary(this,date) else null,history,if(diagnostics)"下次计划："+Scheduler.describeNext(s.hour,s.minute) else null,if(settings)TelegramSettingsStore(this).status() else null)
 }
 private fun refresh(){
  if(isFinishing)return
  checkinPanel?.refresh()
  val s=current;val records=page==1;val settings=page==2;val diagnostics=records&&findViewById<View>(R.id.diagnosticPanel).visibility==View.VISIBLE
  snapJob?.cancel()
  snapJob=scope.launch{
   val snap=try{withContext(Dispatchers.IO){snapshot(s,records,settings,diagnostics)}}catch(e:CancellationException){throw e}catch(e:Exception){ledgerUnavailable(e);return@launch}
   if(!isFinishing&&!isDestroyed)try{applySnap(snap,diagnostics)}catch(e:Exception){ledgerUnavailable(e)}
  }
 }
 private fun ledgerUnavailable(e:Exception){
  Logger.e("LEDGER","cannot read durable state; business controls disabled",e)
  findViewById<View>(R.id.homeNotice).visibility=View.VISIBLE
  tv(R.id.tvHomeResult).text="任务账本暂不可用"
  tv(R.id.tvHomeReason).text="未提交新广告。请保留现场并重新打开应用；如仍失败，请导出诊断核对存储状态。"
  btn(R.id.btnStartRoutine).isEnabled=false;btn(R.id.btnRescanChallenges).isEnabled=false;btn(R.id.btnResetChallengeScan).isEnabled=false
 }
 private fun applySnap(s:Snap,diagnostics:Boolean){val connected=AutoAccessibilityService.isConnected();val running=s.running;val c=s.c;val official=s.official;val routine=s.routine;val login=s.login;val safety=s.safety
  tv(R.id.tvPhoneDate).text=phoneDate(s.date);val finished=routine?.has("finishedAt")==true;tv(R.id.tvHomeTitle).text=if(finished)"今日结果" else "今日任务"
  var travelTotal=0;var travelDone=0;var travelCats=0;var travelCovered=0
  for(t in tiles){val ts=s.tiles.getValue(t.cat)
   t.card.isSelected=ts.enabled;t.card.alpha=if(ts.enabled)1f else .5f
   t.check.setImageResource(if(ts.enabled)R.drawable.ic_check_on else R.drawable.ic_check_off)
   t.card.contentDescription=t.cat.jp+if(ts.enabled)"，已选中，再点取消" else "，未选中，点击选择"
   if(Build.VERSION.SDK_INT>=30)t.card.stateDescription=if(ts.enabled)"已选中" else "未选中"
   t.range.text=if(ts.km>0)"≤ "+ts.km+" km" else "未设置"
   val (label,tone)=HomeStatusPolicy.tile(ts.enabled,ts.km,ts.covered,ts.unknown,ts.total,ts.done,running,ts.scanLabel)
   t.status.text=label;t.status.setTextColor(color(when(tone){HomeStatusPolicy.Tone.DONE->R.color.accent;HomeStatusPolicy.Tone.WARN->R.color.warn;HomeStatusPolicy.Tone.ACTIVE->R.color.brand;HomeStatusPolicy.Tone.MUTED->R.color.muted}))
   if(ts.enabled&&ts.km>0){travelCats++;if(ts.covered)travelCovered++;travelTotal+=ts.total;travelDone+=ts.done}
  }
  val travel=HomeStatusPolicy.travelProgress(travelCats,travelCovered,travelTotal,travelDone)
  val adsOn=current.adEnabled;val adsCount=official?:c.completed
  val loginDone=login in setOf("REWARD_CONFIRMED","NOT_PRESENT")
  findViewById<RingsView>(R.id.rings).setProgress(travel,if(adsOn)adsCount/DAILY_AD_LIMIT.toFloat() else 0f,if(loginDone)1f else 0f)
  tv(R.id.tvRingTravel).text=if(travelTotal>0)"$travelDone/$travelTotal" else if(travelCats>0&&travelCovered==travelCats)"完成" else "—"
  tv(R.id.tvRingAds).text=if(!adsOn)"关闭" else "$adsCount/$DAILY_AD_LIMIT"
  val loginText=when(login){"REWARD_CONFIRMED"->"已领取";"UNCONFIRMED","FAILED"->"需核对";"NOT_PRESENT"->"无弹窗";else->"待检查"}
  tv(R.id.tvRingLogin).text=loginText
  findViewById<View>(R.id.ringLegend).contentDescription="出行 "+tv(R.id.tvRingTravel).text+"，广告 "+tv(R.id.tvRingAds).text+"，签到 "+loginText
  tv(R.id.tvHomeLogin).text=when(login){"REWARD_CONFIRMED"->"已领取";"UNCONFIRMED","FAILED"->"需核对";"NOT_PRESENT"->"未出现弹窗";else->"待检查"}
  tv(R.id.tvHomeAds).text=when{!adsOn->"未开启";official!=null->"官方 "+official+"/25"+(if(c.unconfirmed>0)" · 待核对 "+c.unconfirmed else "");c.started>0->"本机确认 "+c.completed+" 次";else->"最多 25 次"}
  val failed=!running&&routine?.optString("state") in setOf("BLOCKED","STOPPED")
  btn(R.id.btnSafety).text=if(safety==null)"账号保护：正常" else "账号保护：需要人工处理"
  findViewById<View>(R.id.homeNotice).visibility=if(!running&&(safety!=null||failed||s.incomplete))View.VISIBLE else View.GONE
  tv(R.id.tvHomeResult).text=if(safety!=null)"账号保护已暂停任务" else if(failed&&routine?.optString("state")=="BLOCKED")"任务受阻（BLOCKED）" else if(failed)"本次执行已停止" else "部分任务未完成"
  tv(R.id.tvHomeReason).text=if(safety!=null)safety.optString("title") else if(failed)TaskFailureText.short(routine?.optString("detail").orEmpty()) else s.recovery?:if(official!=25&&adsOn)"每日广告尚未完成官方核对" else "未完成项目保留，可继续处理"
  showResultsOnly=HomeStatusPolicy.resultsOnly(finished,failed,s.incomplete,running,safety!=null)
  val hero=tv(R.id.tvHeroStatus);val heroText=when{running->"运行中";safety!=null->"已暂停";showResultsOnly->"全部完成";failed->"已停止";else->""}
  hero.text=heroText;hero.visibility=if(heroText.isEmpty())View.GONE else View.VISIBLE
  hero.setTextColor(color(when{running->R.color.brand;showResultsOnly->R.color.accent;else->R.color.warn}))
  val start=btn(R.id.btnStartRoutine);start.isEnabled=!applying
  val state=HomeStatusPolicy.primary(running,safety!=null,showResultsOnly,s.hasTodayWork)
  if(state!=primaryState){primaryState=state
   start.text=when(state){HomeStatusPolicy.Primary.STOP->"停止任务";HomeStatusPolicy.Primary.SAFETY->"查看并处理账号保护";HomeStatusPolicy.Primary.DONE->"✓  今日任务已全部完成";HomeStatusPolicy.Primary.CONTINUE->"继续未完成任务";HomeStatusPolicy.Primary.START->"开始今日任务"}
   start.setBackgroundResource(when(state){HomeStatusPolicy.Primary.STOP->R.drawable.bg_stop;HomeStatusPolicy.Primary.DONE->R.drawable.bg_done;else->R.drawable.bg_primary})
   start.setTextColor(if(state==HomeStatusPolicy.Primary.DONE)color(R.color.accent) else android.graphics.Color.WHITE)
  }
  btn(R.id.btnRescanChallenges).isEnabled=!running&&!applying;btn(R.id.btnRescanChallenges).alpha=if(running)0.4f else 1f
  btn(R.id.btnResetChallengeScan).isEnabled=!running&&!applying
  btn(R.id.btnTime).text=String.format(Locale.US,"每日 %02d:%02d（手机 JST）",current.hour,current.minute)
  if(page==2){
   fun status(view:Int,ok:Boolean,okText:String,badText:String){tv(view).text=if(ok)okText else badText;tv(view).setTextColor(color(if(ok)R.color.muted else R.color.warn))}
   status(R.id.tvA11yStatus,connected,"已开启","未开启")
   status(R.id.tvNotifyStatus,Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED,"已授权","未授权")
   status(R.id.tvAlarmStatus,Build.VERSION.SDK_INT<31||(getSystemService(ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms(),"已授权","需授权")
   status(R.id.tvOverlayStatus,Settings.canDrawOverlays(this),"已授权","需授权")
   for(r in rows){val ts=s.tiles.getValue(r.cat);r.status.text=if(!current.catOn(r.cat))"未开启" else "今日已升满 "+ts.maxed+" 项"}
  }
  s.telegram?.let{tv(R.id.tvTelegramStatus).text=it}
  tv(R.id.tvAdStat).text="官方观看："+(official?.let{it.toString()+"/25"}?:"未核对")+"\n本机尝试 "+c.started+" · 确认完成 "+c.completed+" · 奖励回执 "+c.rewardConfirmed+"\n失败 "+c.failed+" · 待核对 "+c.unconfirmed
  s.chalSummary?.let{tv(R.id.tvChalSummary).text=it};s.history?.let{tv(R.id.tvHistory).text=it}
  if(diagnostics){s.nextPlan?.let{tv(R.id.tvProgress).text=it};tvLogPath.text=Logger.path();tv(R.id.tvLog).text=Logger.tail(20)}
 }
 private fun phoneDate(date:String):String{
  val p=date.split('-');if(p.size!=3)return date
  val week=runCatching{val cal=java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"),Locale.JAPAN);cal.set(p[0].toInt(),p[1].toInt()-1,p[2].toInt());"日一二三四五六"[cal.get(java.util.Calendar.DAY_OF_WEEK)-1]}.getOrNull()
  return p[1].trimStart('0')+"月"+p[2].trimStart('0')+"日"+(week?.let{" 星期$it"}?:"")+" · JST"
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
        if(AutoAccessibilityService.isRunning||CheckinRuntime.activeApp!=null){toast("请先停止任务，再导出故障包");return}
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
