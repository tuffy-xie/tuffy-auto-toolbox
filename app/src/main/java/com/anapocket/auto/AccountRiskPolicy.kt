package com.anapocket.auto

data class RiskFrame(val pkg:String?,val sdk:Boolean,val labels:List<String>,val ids:Set<String>,val editable:Boolean=false)
enum class RiskKind(val title:String){ACCOUNT_WARNING("账号异常或使用受限"),HUMAN_VERIFICATION("验证码或身份确认需人工处理"),STORAGE_ERROR("保护记录无法读取")}
object AccountRiskPolicy {
    fun detect(f:RiskFrame):RiskKind? {
        if(f.pkg !in setOf(StartupPolicy.ANA,"com.android.chrome","com.google.android.webview"))return null
        val labels=f.labels.map{it.trim()};val text=labels.joinToString("\n")
        val captchaId=f.ids.any{Regex("(^|[/:_.-])(recaptcha|hcaptcha|g-recaptcha|captcha_input|captcha_container|recaptcha-anchor)([/:_.-]|$)",RegexOption.IGNORE_CASE).containsMatchIn(it)}
        val robot=labels.any{it in setOf("私はロボットではありません","I'm not a robot","I’m not a robot","我不是机器人","请完成安全验证")}
        val inputVerification=f.editable&&labels.any{it in setOf("認証コード","確認コード","验证码","Verification code","画像認証")}
        if(captchaId||robot||inputVerification)return RiskKind.HUMAN_VERIFICATION
        if(f.pkg!=StartupPolicy.ANA||f.sdk)return null
        val direct=labels.any{it.contains("不正な操作を検知")||it.contains("不正な利用を検知")||it.contains("不正利用の疑い")||it.contains("アカウントの利用を停止")||it.contains("アカウントの利用が制限")||it.contains("アカウントが停止")||it.contains("アカウントが制限")||it.contains("检测到异常操作")||it.contains("账号已被限制")||it.equals("Your account has been suspended",true)}
        val account=Regex("アカウント|会員|账号|account",RegexOption.IGNORE_CASE).containsMatchIn(text)
        val restriction=Regex("利用制限|ご利用を制限|利用停止|异常行为|使用受限|suspicious activity",RegexOption.IGNORE_CASE).containsMatchIn(text)
        return if(direct||account&&restriction)RiskKind.ACCOUNT_WARNING else null
    }
    fun mayAcknowledge(expected:String,current:String?,cleanNative:Boolean,detected:RiskKind?)=
        expected==current&&cleanNative&&detected==null
}

enum class DeviceReadyAction { READY, WAKE_NONSECURE, OWNER_REQUIRED, EXHAUSTED }
object DeviceReadyPolicy {
    fun action(interactive:Boolean,locked:Boolean,secure:Boolean,attempts:Int):DeviceReadyAction=when{
        interactive&&!locked->DeviceReadyAction.READY
        secure->DeviceReadyAction.OWNER_REQUIRED
        attempts>=2->DeviceReadyAction.EXHAUSTED
        else->DeviceReadyAction.WAKE_NONSECURE
    }
    fun canSwipe(pkg:String?,ids:Set<String>,locked:Boolean,secure:Boolean,attempts:Int)=
        locked&&!secure&&attempts<2&&pkg=="com.android.systemui"&&ids.any{it.contains("keyguard")||it.contains("lockscreen")}
}
