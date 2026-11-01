package com.anapocket.auto

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import java.text.SimpleDateFormat
import java.util.*

/** Adds two cards to existing screens; no ANA configuration fields are written. */
class CheckinPanel(private val activity:MainActivity) {
    private val rows=mutableListOf<Triple<CheckinApp,TextView,Button>>()
    private val checks=mutableMapOf<CheckinApp,CheckBox>();private var refreshing=false
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
    private fun style(button:Button,primary:Boolean=false){button.isAllCaps=false;button.elevation=0f;button.textSize=12f;button.setBackgroundResource(if(primary)R.drawable.bg_primary else R.drawable.bg_secondary);button.setTextColor(if(primary)Color.WHITE else androidx.core.content.ContextCompat.getColor(activity,R.color.brand))}
    fun bind(){
        val home=(activity.findViewById<ScrollView>(R.id.pageToday).getChildAt(0) as LinearLayout)
        val settings=(activity.findViewById<ScrollView>(R.id.pageSettings).getChildAt(0) as LinearLayout)
        for(app in CheckinApp.entries){
            val card=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));background=GradientDrawable().apply{setColor(Color.parseColor("#F6F0FD"));cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.parseColor("#E5D5F4"))};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(10),0,dp(4))}}
            val check=CheckBox(activity).apply{text=app.title;textSize=16f;setTextColor(Color.parseColor("#503172"));setTypeface(null,Typeface.BOLD);contentDescription=app.title+"启用";isChecked=CheckinStore(activity,app).config().enabled
                setOnCheckedChangeListener{_,on->if(!refreshing){val s=CheckinStore(activity,app);CheckinRuntime.configurationChanged(app);s.configure(s.config().copy(enabled=on));CheckinRuntime.configurationChanged(app);CheckinScheduler.schedule(activity,app);refresh()}}}
            checks[app]=check;card.addView(check)
            val status=TextView(activity).apply{textSize=12f;setTextColor(Color.parseColor("#786A85"));setPadding(0,dp(8),0,dp(6))};card.addView(status)
            val actions=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
            val run=Button(activity).apply{text="立即执行";textSize=12f;setOnClickListener{if(CheckinRuntime.activeApp==app)CheckinRuntime.stop() else CheckinRuntime.request(activity,app)}}
            style(run,true);actions.addView(run,LinearLayout.LayoutParams(0,dp(48),1f).apply{marginEnd=dp(5)})
            actions.addView(Button(activity).apply{text="独立日程与记录";style(this);setOnClickListener{configure(app)}},LinearLayout.LayoutParams(0,dp(48),1.4f));card.addView(actions);home.addView(card);rows.add(Triple(app,status,run))
            settings.addView(Button(activity).apply{text=app.title+" · 独立开关与时间";style(this);setOnClickListener{configure(app)}})
        }
        refresh()
    }
    fun refresh(){refreshing=true;for((app,status,button)in rows){val store=CheckinStore(activity,app);val c=store.config();val time=if(store.lastAt()>0)SimpleDateFormat("MM/dd HH:mm",Locale.JAPAN).apply{timeZone=TimeZone.getTimeZone("Asia/Tokyo")}.format(Date(store.lastAt())) else "尚未执行"
        status.text=(if(c.enabled)"定时已开启" else "定时未开启")+"\n"+store.summary()+"\n最近："+time
        checks[app]?.isChecked=c.enabled;button.isEnabled=c.enabled||CheckinRuntime.activeApp==app;button.text=if(CheckinRuntime.activeApp==app)"停止此App" else "立即执行"
    };refreshing=false}
    private fun configure(app:CheckinApp){
        val store=CheckinStore(activity,app);var c=store.config();val host=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        host.addView(SwitchCompat(activity).apply{text="启用每日定时";isChecked=c.enabled;setOnCheckedChangeListener{_,on->c=c.copy(enabled=on);CheckinRuntime.configurationChanged(app);store.configure(c);CheckinRuntime.configurationChanged(app);CheckinScheduler.schedule(activity,app);refresh()}})
        val time=Button(activity).apply{text=String.format(Locale.JAPAN,"每日 %02d:%02d JST",c.hour,c.minute);style(this)}
        time.setOnClickListener{TimePickerDialog(activity,{_,h,m->c=c.copy(hour=h,minute=m);CheckinRuntime.configurationChanged(app);store.configure(c);CheckinRuntime.configurationChanged(app);CheckinScheduler.schedule(activity,app);time.text=String.format(Locale.JAPAN,"每日 %02d:%02d JST",h,m);refresh()},c.hour,c.minute,true).show()};host.addView(time)
        host.addView(TextView(activity).apply{text=store.summary()+"\n\nANA优先；进入ANA计划前5分钟时暂停此App。奖励按各App分别记录。";textSize=12f})
        if(store.hold().isNotEmpty())host.addView(Button(activity).apply{text="人工处理后解除此App暂停";setOnClickListener{AlertDialog.Builder(activity).setMessage("请先在官方App完成人工验证。解除后仍会先核对今天的记录，未确认的抽奖不会重新提交。").setPositiveButton("已处理，解除"){_,_->store.clearHold();refresh()}.setNegativeButton("取消",null).show()}})
        AlertDialog.Builder(activity).setTitle(app.title).setView(host).setPositiveButton("关闭",null).show()
    }
}
