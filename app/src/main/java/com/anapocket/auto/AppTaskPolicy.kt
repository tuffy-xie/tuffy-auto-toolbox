package com.anapocket.auto

import java.util.Calendar
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.UUID

enum class CheckinApp(val pkg:String,val title:String,val defaultHour:Int,val defaultMinute:Int) {
    RAKUTEN("jp.co.rakuten.pointclub.android","楽天PointClub",9,15),
    STEAK("jp.co.pepperfs.android","いきなり！ステーキ",9,30);
    companion object { fun from(id:String?)=entries.firstOrNull{it.name==id} }
}
enum class CheckinState { RUNNING,SUCCESS,ALREADY_DONE,UNCONFIRMED,FAILED,BLOCKED,NEEDS_USER_ACTION,PREEMPTED }
data class CheckinLine(val text:String,val left:Int,val top:Int,val right:Int,val bottom:Int) {
    val x get()=(left+right)/2;val y get()=(top+bottom)/2
}

/** Independent of Android, monotonic lease time, explicit wall clock for business dates. */
class UiOwnership {
    data class Token(val app:String,val run:String,val generation:Long,val expires:Long)
    private var generation=0L
    private var current:Token?=null
    @Synchronized fun owner()=current
    @Synchronized fun acquire(app:String,run:String,now:Long,expires:Long,protected:Boolean):Token? {
        if(expires<=now)return null
        val old=current
        if(old!=null&&old.expires>now) {
            if(old.app=="ANA")return null
            if(app!="ANA")return null
        }
        if(app!="ANA"&&protected)return null
        return Token(app,run,++generation,expires).also{current=it}
    }
    @Synchronized fun valid(t:Token,now:Long)=current==t&&now<t.expires
    @Synchronized fun release(t:Token):Boolean { if(current!=t)return false;current=null;generation++;return true }
    @Synchronized fun revokeOrdinary():Token? {
        val t=current?:return null;if(t.app=="ANA")return null
        current=null;generation++;return t
    }
    /** Checking and initiating a short Android operation form one critical section. */
    @Synchronized fun action(t:Token,now:Long,protected:Boolean,block:()->Unit):Boolean {
        if(!valid(t,now)||(t.app!="ANA"&&protected))return false
        block();return true
    }
}

object CheckinClock {
    private val zone get()=TimeZone.getTimeZone("Asia/Tokyo")
    fun date(now:Long)=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{timeZone=zone}.format(Date(now))
    private fun due(now:Long,h:Int,m:Int)=Calendar.getInstance(zone).apply{timeInMillis=now;set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
    fun next(now:Long,h:Int,m:Int):Long {
        val due=due(now,h,m)
        if(due.timeInMillis<=now)due.add(Calendar.DAY_OF_YEAR,1)
        return due.timeInMillis
    }
    fun protected(now:Long,h:Int,m:Int,enabled:Boolean,deliveredToday:Boolean):Boolean {
        if(!enabled)return false
        val due=due(now,h,m).timeInMillis
        if(now>=due&&!deliveredToday)return true
        return next(now,h,m)-now<=5*60_000L
    }
    fun registeredProtection(now:Long,due:Long,handled:Long,enabled:Boolean)=enabled&&due>0&&now>=due-300_000&&handled<due
}

object CheckinReceiptPolicy {
    // Actual green check icon is recognized as 「の」 by the bundled model on this phone.
    // Only exact status labels underneath their own card accept this observed glyph.
    private fun completedCardLabel(text:String)=text.replace(Regex("\\s|[✓✔○●の]"),"") in setOf("達成","達成済","達成済み","完了","クリア")
    val done=setOf(CheckinState.SUCCESS,CheckinState.ALREADY_DONE)
    fun maySubmit(state:CheckinState?,reserved:Boolean)=state !in done&&!reserved
    // A guide screenshot can contain precisely the same reward words as a real receipt.
    fun realReceipt(bound:Boolean,guide:Boolean,fresh:Boolean,text:String)=
        bound&&!guide&&fresh&&Regex("ポイント(?:を)?ゲット|ポイント獲得|獲得しました|当選結果|はずれ|ハズレ|おめでとう|クーポン.*獲得|獲得.*クーポン").containsMatchIn(text)
    fun freeDartsBanner(text:String):Boolean {
        val t=text.replace(Regex("\\s"),"")
        return t.contains("ダーツ")&&t.contains("1日1回")&&t.contains("毎日")&&
            !Regex("スクラッチ|注文|購入|おせち|求人|500円以上").containsMatchIn(t)
    }
    fun id()=UUID.randomUUID().toString()
    fun pointsEntry(lines:List<CheckinLine>):CheckinLine? {
        for(anchor in lines.filter{it.text.contains("ミッション")||Regex("1\\s*ポイント").containsMatchIn(it.text)}){
            val near=lines.filter{it.left<anchor.right&&it.right>anchor.left&&kotlin.math.abs(it.y-anchor.y)<maxOf(24,anchor.bottom-anchor.top)*4}
            val text=near.joinToString(""){it.text}.replace(Regex("\\s"),"")
            if(text.contains("1ポイント")&&text.contains("ミッション"))return anchor
        };return null
    }
    fun lotteryEntry(lines:List<CheckinLine>)=lines.firstOrNull{val t=it.text.replace(Regex("\\s"),"");t.contains("ガラガラポン")||t.contains("1日2回引ける")||t.contains("アプリ限定ラッキーくじ")}
    fun pickupEntry(lines:List<CheckinLine>)=lines.firstOrNull{it.text.replace(Regex("\\s"),"").let{t->t.equals("PICKUP",true)||t.contains("ピックアップ")}}
    fun cardDone(lines:List<CheckinLine>,anchor:CheckinLine?):Boolean {
        if(anchor==null)return false
        return lines.any{it.left<anchor.right&&it.right>anchor.left&&it.top>anchor.bottom&&it.top-anchor.bottom<maxOf(24,anchor.bottom-anchor.top)*9&&completedCardLabel(it.text)}
    }
    fun pickupRemaining(text:String)=Regex("残り\\s*([0-9]+)\\s*件").find(text)?.groupValues?.get(1)?.toIntOrNull()
    fun canAdvancePickup(remaining:Int?,submittedRemaining:Int?)=remaining!=null&&remaining in 1..100&&(submittedRemaining==null||remaining<submittedRemaining)
    fun pickupEnd(text:String):Boolean {val t=text.replace(Regex("\\s"),"");return t.contains("今日のピックアップ")&&(t.contains("明日も頑張ってピックアップするね")||t.contains("チェック完了"))}
    fun greenCompletionCandidate(text:String):Boolean {val t=text.replace(Regex("\\s"),"");return !Regex("未|不|非|無").containsMatchIn(t)&&Regex("^.{0,2}達成(?:済み?)?$").matches(t)}
    fun bonusEntry(lines:List<CheckinLine>):CheckinLine? {
        val heading=lines.firstOrNull{it.text.replace(Regex("\\s"),"").let{t->t.contains("今日")&&t.contains("くじ")}}?:return null
        return lines.firstOrNull{it.top>heading.bottom&&it.top-heading.bottom<250&&Regex("抽選|1[,，]?000").containsMatchIn(it.text)}
    }
    fun pointsDone(lines:List<CheckinLine>):Boolean {
        val anchor=pointsEntry(lines)?:return false
        return lines.any{it.left<anchor.right&&it.right>anchor.left&&it.top>anchor.bottom&&it.top-anchor.bottom<maxOf(24,anchor.bottom-anchor.top)*4&&completedCardLabel(it.text)}
    }
    fun pointsStatusReady(lines:List<CheckinLine>):Boolean {
        if(pointsDone(lines))return true
        val anchor=pointsEntry(lines)?:return false
        return lines.any{it.left<anchor.right&&it.right>anchor.left&&it.top>anchor.bottom&&it.top-anchor.bottom<maxOf(24,anchor.bottom-anchor.top)*4&&it.text.contains("未達成")}
    }
    /** Entry discovery only; the next page must independently prove free game eligibility. */
    fun dartsEntryCandidate(text:String):Boolean {
        val t=text.replace(Regex("\\s"),"")
        if(Regex("スクラッチ|注文|購入|おせち|求人|500円以上").containsMatchIn(t))return false
        return freeDartsBanner(t)||(t.contains("いきなり!ダーツで当たる")&&t.contains("DARTS",true))
    }
    /** Exact observed official game instructions, reached from its bound daily carousel entry. */
    fun boundDartsStartPage(text:String,boundDailyEntry:Boolean):Boolean {
        val t=text.replace(Regex("\\s"),"")
        return boundDailyEntry&&t.contains("ダーツの矢を引くと的に飛んでいくよ")&&t.contains("的にあてて肉マネーをGETしよう")&&t.contains("スタート")&&!Regex("有料|購入|支払い|決済|スクラッチ|注文").containsMatchIn(t)
    }
    fun rewardAmount(lines:List<CheckinLine>,unit:String):String? {
        require(unit in setOf("円","ポイント"))
        val number=Regex("(?<![-0-9.])([0-9][0-9,]*)\\s*"+unit)
        val instruction=Regex("最大|利用|単位|購入|注文|以上|未満|合計|期限|確率")
        val amounts=lines.mapNotNull{line->
            val m=number.find(line.text)?:return@mapNotNull null
            val near=lines.filter{it.left<line.right&&it.right>line.left&&kotlin.math.abs(it.y-line.y)<maxOf(24,line.bottom-line.top)*2}.joinToString(" "){it.text}
            if(instruction.containsMatchIn(near))return@mapNotNull null
            if(Regex("[0-9]+等").containsMatchIn(near)&&!Regex("当選|獲得|ゲット|当たり").containsMatchIn(near))return@mapNotNull null
            m.groupValues[1].replace(",","")
        }.distinct()
        // Missing or ambiguous amounts remain unknown; never choose the first marketing number.
        return amounts.singleOrNull()
    }
    fun alreadyDone(text:String,guide:Boolean=false):Boolean {
        if(guide)return false
        val t=text.replace(Regex("\\s"),"")
        val action="(?:参加|挑戦|プレイ|抽選|受取|受け取り|獲得|チャレンジ)"
        return Regex("(?:本日|今日).{0,20}"+action+"(?:済|.{0,3}完了)").containsMatchIn(t)||
            Regex("(?:本日|今日).{0,10}(?:既に|すでに).{0,8}"+action+"(?:して|しました|されて|されました|済)").containsMatchIn(t)
    }
}
