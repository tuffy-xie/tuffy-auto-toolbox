package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
class NativeDialogResumeTest {
    private val pkg=MaxBoostDialogPolicy.PACKAGE
    private val text=listOf("チャレンジ達成後の報酬が\n最大値に到達しました！","400pt","OK")
    private val ids=setOf("title_count","current_point","boost_list","button_no","button_no_text").map{pkg+":id/"+it}.toSet()
    @Test fun realComponentDialogClassIsAcceptedOnlyWithExactNativeWindowAndCopy(){
        assertTrue(MaxBoostDialogPolicy.nativeShape(pkg,"androidx.activity.ComponentDialog",true,ids,text))
        assertEquals(MaxBoostPage.MAX_DIALOG,MaxBoostDialogPolicy.classify(pkg,"androidx.activity.ComponentDialog",true,ids,text,emptyMap(),null))
        assertFalse(MaxBoostDialogPolicy.nativeShape(pkg,"com.google.android.gms.ads.AdActivity",true,ids,text))
        assertFalse(MaxBoostDialogPolicy.nativeShape("foreign","androidx.activity.ComponentDialog",true,ids,text))
        assertFalse(MaxBoostDialogPolicy.nativeShape(pkg,"androidx.activity.ComponentDialog",false,ids,text))
        assertFalse(MaxBoostDialogPolicy.nativeShape(pkg,"androidx.activity.ComponentDialog",true,ids,listOf("400pt","OK")))
    }
    @Test fun officialResumeNeedsTheExactMessageAndNativeButtons(){
        val ii=setOf("android:id/message","android:id/button1","android:id/button2");val ls=listOf(MaxBoostDialogPolicy.RESUME_COPY,"再開する","やめる")
        assertEquals(MaxBoostPage.RESUME_PROMPT,MaxBoostDialogPolicy.classify(pkg,"android.app.AlertDialog",true,ii,ls,emptyMap(),null))
        assertFalse(MaxBoostDialogPolicy.resumeShape(pkg,"android.app.AlertDialog",true,ii,listOf("広告を再開","再開する","やめる")))
        assertFalse(MaxBoostDialogPolicy.resumeShape(pkg,"android.app.AlertDialog",true,ii-"android:id/message",ls))
    }
    @Test fun completedSessionResumeDoesNotPermitUnknownOrUnboundOrRepeatedSubmission(){
        assertTrue(MaxBoostDialogPolicy.finalResumeAllowed(true,true,false,false,false,true))
        assertFalse(MaxBoostDialogPolicy.finalResumeAllowed(false,true,false,false,false,true))
        assertFalse(MaxBoostDialogPolicy.finalResumeAllowed(true,false,false,false,false,true))
        assertFalse(MaxBoostDialogPolicy.finalResumeAllowed(true,true,true,false,false,true))
        assertFalse(MaxBoostDialogPolicy.finalResumeAllowed(true,true,false,true,false,true))
        assertFalse(MaxBoostDialogPolicy.finalResumeAllowed(true,true,false,false,true,true))
        assertFalse(MaxBoostDialogPolicy.finalResumeAllowed(true,true,false,false,false,false))
        val b=MaxBoostBinding("2026-11-03","BIKE|自転車で1km移動しよう！","BIKE","自転車で1km移動しよう！",1000,"CH-original",6,100,400,"original")
        val fields=mapOf("text_name" to b.title,"text_date" to "2026/11/3","text_attended" to "参加日：2026/11/3")
        assertTrue(MaxBoostDialogPolicy.resumeIdentity(fields,b))
        assertFalse(MaxBoostDialogPolicy.resumeIdentity(emptyMap(),b))
        assertFalse(MaxBoostDialogPolicy.resumeIdentity(fields+("text_name" to "自転車で2km移動しよう！"),b))
        assertFalse(MaxBoostDialogPolicy.resumeIdentity(fields+("text_date" to "2026/11/2"),b))
    }
    @Test fun safeCompletedNavigationRestartIsBoundedAndCannotKillAnActiveSdk(){
        assertTrue(MaxBoostDialogPolicy.completedRestartAllowed(true,true,false,false,false,0,true))
        assertFalse(MaxBoostDialogPolicy.completedRestartAllowed(true,true,false,true,false,0,true))
        assertFalse(MaxBoostDialogPolicy.completedRestartAllowed(true,true,true,false,false,0,true))
        assertFalse(MaxBoostDialogPolicy.completedRestartAllowed(true,true,false,false,true,0,true))
        assertFalse(MaxBoostDialogPolicy.completedRestartAllowed(true,true,false,false,false,3,true))
        assertFalse(MaxBoostDialogPolicy.completedRestartAllowed(false,true,false,false,false,0,true))
    }
    @Test fun resumeMustBeBoundBeforeActionAndMustReturnTheExactDetailBeforeRouting()=runBlocking {
        val b=MaxBoostBinding("2026-11-03","BIKE|original","BIKE","original",1000,"CH-original",6,100,400,"original")
        for((identity,post,previouslyRequested) in listOf(Triple(false,MaxBoostPage.DETAIL,false),Triple(true,MaxBoostPage.HOME,false),
            Triple(true,MaxBoostPage.DETAIL,false),Triple(true,MaxBoostPage.HOME,true),Triple(true,MaxBoostPage.DETAIL,true))){
            var time=0L;var page=if(previouslyRequested)post else MaxBoostPage.RESUME_PROMPT;var clicks=0;var routed=false;var committed=false
            val p=object:MaxBoostCompletionPort {
                override fun now()=time
                override fun allowed()=true
                override suspend fun ensureDurableBinding(){committed=true}
                override fun persistedAttempts()=0
                override fun persistedResumeRequested()=previouslyRequested
                override suspend fun frame()=MaxBoostFrame(page,7,page.name,reward=400)
                override suspend fun reserveAttempt(number:Int){fail("no advertisement or OK submission expected")}
                override suspend fun click(frame:MaxBoostFrame,number:Int):MaxBoostClick=error("no advertisement or OK submission")
                override suspend fun actionResult(number:Int,result:MaxBoostClick){}
                override suspend fun resumeFinalDialog():Boolean {
                    assertTrue(committed);if(!identity)return false
                    clicks++;page=post;return true
                }
                override suspend fun dismissed(page:MaxBoostPage){assertEquals(MaxBoostPage.DETAIL,page)}
                override suspend fun restoreCategory(cat:String,page:MaxBoostPage)=true
                override suspend fun routed(){routed=true}
                override suspend fun failure(code:String,detail:String){}
                override suspend fun pause(ms:Long){time+=ms}
            }
            try{MaxBoostCompletionFlow(b,p).run();assertTrue(identity&&post==MaxBoostPage.DETAIL)}
            catch(e:MaxBoostCompletionFailure){assertFalse(identity&&post==MaxBoostPage.DETAIL)}
            assertEquals(if(identity&&!previouslyRequested)1 else 0,clicks)
            assertEquals(identity&&post==MaxBoostPage.DETAIL,routed)
        }
    }
    @Test fun restoredNativeDetailCanOverrideOnlyTheStaleControllerHostWithExactSavedIdentity(){
        val b=MaxBoostBinding("2026-11-03","BIKE|自転車で1km移動しよう！","BIKE","自転車で1km移動しよう！",1000,"CH-original",6,100,400,"original")
        val ii=setOf("design_bottom_sheet","text_name","text_date","text_attended","close").map{pkg+":id/"+it}.toSet()
        val ls=listOf(b.title,"2026/11/3","参加日：2026/11/3","ブースト5回")
        val fields=mapOf("text_name" to b.title,"text_date" to "2026/11/3","text_attended" to "参加日：2026/11/3")
        fun page(host:String="com.anapocket.auto.MainActivity",owner:String=pkg,front:Boolean=true,
            ids:Set<String> = ii,labels:List<String> = ls,f:Map<String,String> = fields,bound:MaxBoostBinding?=b)=
            MaxBoostDialogPolicy.classify(owner,host,front,ids,labels,f,bound)
        assertEquals(MaxBoostPage.DETAIL,page())
        assertEquals(MaxBoostPage.OTHER,page(owner="foreign"))
        assertEquals(MaxBoostPage.OTHER,page(front=false))
        assertEquals(MaxBoostPage.OTHER,page(bound=null))
        assertEquals(MaxBoostPage.OTHER,page(host="com.google.android.gms.ads.AdActivity"))
        assertEquals(MaxBoostPage.OTHER,page(host="android.app.Dialog"))
        assertEquals(MaxBoostPage.OTHER,page(f=fields+("text_name" to "自転車で2km移動しよう！")))
        assertEquals(MaxBoostPage.OTHER,page(f=fields+("text_date" to "2026/11/2")))
        assertEquals(MaxBoostPage.OTHER,page(f=fields-"text_attended"))
        assertEquals(MaxBoostPage.OTHER,page(ids=ii-pkg.plus(":id/close")))
        assertEquals(MaxBoostPage.OTHER,page(ids=ii+"android:id/message"))
        assertEquals(MaxBoostPage.OTHER,page(labels=ls+MaxBoostDialogPolicy.RESUME_COPY))
        assertEquals(MaxBoostPage.OTHER,page(labels=ls+text))
    }
}
