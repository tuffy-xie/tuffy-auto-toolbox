package com.anapocket.auto
object CategoryDragPlan {
 fun points(left:Int,right:Int,forward:Boolean):Pair<Int,Int>? {
  val width=right-left;if(width<96)return null
  val a=left+(width*.18).toInt();val b=left+(width*.82).toInt()
  return if(forward)b to a else a to b
 }
}
