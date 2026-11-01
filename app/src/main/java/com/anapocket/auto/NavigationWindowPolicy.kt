package com.anapocket.auto

data class NavigationWindow(val index:Int,val pkg:String,val active:Boolean,val focused:Boolean,val layer:Int)
object NavigationWindowPolicy {
    fun choose(pkg:String,foregroundPkg:String?,windows:List<NavigationWindow>):Int? =
        if(foregroundPkg!=pkg)null else windows.filter{it.pkg==pkg&&(it.active||it.focused)}.maxByOrNull{it.layer}?.index
}
