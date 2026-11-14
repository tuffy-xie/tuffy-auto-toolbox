package com.anapocket.auto

data class DialogNode(val cls:String,val id:String?,val label:String,val clickable:Boolean,val left:Int,val top:Int,val right:Int,val bottom:Int)

/**
 * 11/11 00:07: a Boost ad was a native ad rendered in ANA's own androidx ComponentDialog (PeopleX creative):
 * no resource ids anywhere, a full-screen backdrop image, ad copy, and one unlabelled clickable ImageView in the
 * top-right corner. It sat 95 s unrecognised and then blocked every retry. ANA's own dialogs always carry ids,
 * so this exact shape is the only thing accepted here.
 */
object NativeDialogAdClosePolicy {
    const val ANA="us.ana_pocket.ANAPocket"
    const val ACTIVITY="androidx.activity.ComponentDialog"
    private val allowedIds=setOf("android:id/content")
    fun surface(pkg:String?,activity:String,nodes:List<DialogNode>,width:Int,height:Int):Boolean {
        if(pkg!=ANA||activity!=ACTIVITY||width<=0||height<=0||nodes.isEmpty())return false
        if(nodes.any{it.id!=null&&it.id !in allowedIds})return false
        val backdrop=nodes.any{it.cls=="android.widget.ImageView"&&it.right-it.left>=width*.95&&it.bottom-it.top>=height*.85}
        val copy=nodes.count{it.cls=="android.widget.TextView"&&it.label.isNotBlank()}>=2
        return backdrop&&copy&&closeCandidates(nodes,width,height,0f).size==1
    }
    /** density 0 skips the dp size check (used only by [surface], which then re-checks via [close]). */
    private fun closeCandidates(nodes:List<DialogNode>,width:Int,height:Int,density:Float)=nodes.filter{
        val w=it.right-it.left;val h=it.bottom-it.top
        it.cls=="android.widget.ImageView"&&it.clickable&&it.label.isBlank()&&it.id==null&&
            it.left>=width*.85&&it.right<=width&&it.top<=height*.1&&w>0&&h>0&&kotlin.math.abs(w-h)<=8&&
            (density<=0f||w/density in 20f..56f)
    }
    fun close(pkg:String?,activity:String,nodes:List<DialogNode>,width:Int,height:Int,density:Float):DialogNode? =
        if(!surface(pkg,activity,nodes,width,height))null else closeCandidates(nodes,width,height,density).singleOrNull()
}
