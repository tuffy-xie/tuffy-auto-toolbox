package com.anapocket.auto

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import java.text.SimpleDateFormat
import java.util.*

/** Adds one grouped card per app to the home page and a row per app to settings; no ANA configuration fields are written. */
class CheckinPanel(private val activity:MainActivity) {
    private val rows=mutableListOf<Triple<CheckinApp,TextView,Button>>()
    private val checks=mutableMapOf<CheckinApp,CompoundButton>();private var refreshing=false
    private val stamp=SimpleDateFormat("MM/dd HH:mm",Locale.JAPAN).apply{timeZone=TimeZone.getTimeZone("Asia/Tokyo")}
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density+.5f).toInt()
    private fun color(id:Int)=androidx.core.content.ContextCompat.getColor(activity,id)
    private fun row(text:String,style:Int)=Button(activity,null,0,style).apply{this.text=text;isAllCaps=false}
    private fun divider(inset:Int=16)=android.view.View(activity).apply{setBackgroundColor(color(R.color.line));layoutParams=LinearLayout.LayoutParams(-1,activity.resources.getDimensionPixelSize(R.dimen.hairline).coerceAtLeast(1)).apply{marginStart=dp(inset)}}
    private fun style(button:Button){button.isAllCaps=false;button.setBackgroundResource(R.drawable.bg_row);button.setTextColor(color(R.color.brand))}
    fun bind(){
        val home=activity.findViewById<LinearLayout>(R.id.homeCheckins)
        val settings=activity.findViewById<LinearLayout>(R.id.settingsCheckins)
        for((i,app) in CheckinApp.entries.withIndex()){
            val card=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setBackgroundResource(R.drawable.bg_card);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{if(i>0)topMargin=dp(12)}}
            // Top-aligned so the icon and switch stay beside the title when the task summary grows to several lines.
            val head=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP;setPadding(dp(16),dp(12),dp(16),dp(12))}
            head.addView(ImageView(activity).apply{setImageResource(R.drawable.ic_glyph_gift);setPadding(dp(6),dp(6),dp(6),dp(6));background=GradientDrawable().apply{setColor(color(R.color.icon_checkin));cornerRadius=dp(8).toFloat()};importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO;layoutParams=LinearLayout.LayoutParams(dp(30),dp(30))})
            val texts=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(12);marginEnd=dp(8)}}
            texts.addView(TextView(activity).apply{text=app.title;textSize=16f;setTextColor(color(R.color.text));setTypeface(null,Typeface.BOLD)})
            val status=TextView(activity).apply{textSize=13f;setTextColor(color(R.color.muted));setLineSpacing(dp(2).toFloat(),1f);setPadding(0,dp(3),0,0)};texts.addView(status);head.addView(texts)
            val check=SwitchCompat(activity).apply{contentDescription=app.title+"每日定时";isChecked=CheckinStore(activity,app).config().enabled
                setOnCheckedChangeListener{_,on->if(!refreshing){val s=CheckinStore(activity,app);CheckinRuntime.configurationChanged(app);s.configure(s.config().copy(enabled=on));CheckinRuntime.configurationChanged(app);CheckinScheduler.schedule(activity,app);refresh()}}}
            checks[app]=check;head.addView(check);card.addView(head);card.addView(divider())
            val actions=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            val run=row("立即执行",R.style.RowButton).apply{gravity=Gravity.CENTER;setOnClickListener{if(CheckinRuntime.activeApp==app)CheckinRuntime.stop() else CheckinRuntime.request(activity,app)}}
            actions.addView(run,LinearLayout.LayoutParams(0,dp(46),1f))
            actions.addView(android.view.View(activity).apply{setBackgroundColor(color(R.color.line))},LinearLayout.LayoutParams(activity.resources.getDimensionPixelSize(R.dimen.hairline).coerceAtLeast(1),dp(18)))
            actions.addView(row("日程与记录",R.style.RowButton).apply{gravity=Gravity.CENTER;setOnClickListener{configure(app)}},LinearLayout.LayoutParams(0,dp(46),1f))
            card.addView(actions);home.addView(card);rows.add(Triple(app,status,run))
            if(i>0)settings.addView(divider())
            settings.addView(row(app.title+" · 独立开关与时间",R.style.RowNav).apply{setOnClickListener{configure(app)}},LinearLayout.LayoutParams(-1,dp(48)))
        }
        refresh()
    }
    fun refresh(){refreshing=true;for((app,status,button)in rows){val store=CheckinStore(activity,app);val c=store.config();val time=if(store.lastAt()>0)stamp.format(Date(store.lastAt())) else "尚未执行"
        status.text=(if(c.enabled)String.format(Locale.JAPAN,"每日 %02d:%02d",c.hour,c.minute) else "定时未开启")+" · 最近 "+time+"\n"+store.summary()
        checks[app]?.isChecked=c.enabled;button.isEnabled=c.enabled||CheckinRuntime.activeApp==app;button.text=if(CheckinRuntime.activeApp==app)"停止此App" else "立即执行"
    };refreshing=false}
    private fun configure(app:CheckinApp){
        val store=CheckinStore(activity,app);var c=store.config();val host=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        host.addView(SwitchCompat(activity).apply{text="启用每日定时";isChecked=c.enabled;setOnCheckedChangeListener{_,on->c=c.copy(enabled=on);CheckinRuntime.configurationChanged(app);store.configure(c);CheckinRuntime.configurationChanged(app);CheckinScheduler.schedule(activity,app);refresh()}})
        val time=Button(activity).apply{text=String.format(Locale.JAPAN,"每日 %02d:%02d JST",c.hour,c.minute);style(this)}
        time.setOnClickListener{TimePickerDialog(activity,{_,h,m->c=c.copy(hour=h,minute=m);CheckinRuntime.configurationChanged(app);store.configure(c);CheckinRuntime.configurationChanged(app);CheckinScheduler.schedule(activity,app);time.text=String.format(Locale.JAPAN,"每日 %02d:%02d JST",h,m);refresh()},c.hour,c.minute,true).show()};host.addView(time)
        host.addView(TextView(activity).apply{text=store.summary()+"\n\nANA优先；进入ANA计划前5分钟时暂停此App。奖励按各App分别记录。";textSize=13f;setTextColor(color(R.color.muted));setLineSpacing(dp(2).toFloat(),1f);setPadding(dp(4),dp(10),dp(4),dp(4))})
        if(store.hold().isNotEmpty())host.addView(Button(activity).apply{text="人工处理后解除此App暂停";style(this);setTextColor(color(R.color.danger));setOnClickListener{AlertDialog.Builder(activity).setMessage("请先在官方App完成人工验证。解除后仍会先核对今天的记录，未确认的抽奖不会重新提交。").setPositiveButton("已处理，解除"){_,_->store.clearHold();refresh()}.setNegativeButton("取消",null).show()}})
        AlertDialog.Builder(activity).setTitle(app.title).setView(host).setPositiveButton("关闭",null).show()
    }
}
