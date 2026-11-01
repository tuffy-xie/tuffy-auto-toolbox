package com.anapocket.auto

/** Observed Google Play notification introduction, not a notification-permission grant. */
object PlayStoreReturnPolicy {
    fun notificationIntro(pkg:String?,labels:List<String>):Boolean {
        if(pkg!="com.android.vending")return false
        val text=labels.joinToString("").replace(Regex("\\s"),"")
        return text.contains("想要随时了解最新动态")&&labels.any{it.trim()=="以后再说"}&&labels.any{it.trim()=="立即启用"}
    }
    fun mayBackFromIntro(pkg:String?,labels:List<String>,observedAdStoreSheet:Boolean,backs:Int)=
        observedAdStoreSheet&&backs in 0..1&&notificationIntro(pkg,labels)
}
