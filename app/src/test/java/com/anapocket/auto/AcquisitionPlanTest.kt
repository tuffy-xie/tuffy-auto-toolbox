package com.anapocket.auto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AcquisitionPlanTest {
    private fun card(title:String)=Cards.fromTitle(title)!!
    private val one=card("自転車で1km移動しよう！")
    private val three=card("自転車で3km移動しよう！")
    private val five=card("自転車で5km移動しよう！")
    @Test fun verifiedBikeTabFiveKmSnapshotExcludesOverLimitAndLazyDuplicates(){
        val cards=listOf(one,three,one,five,card("自転車で10km移動しよう！"),card("自転車で20km移動しよう！"))
        assertEquals(listOf(one.key,three.key,five.key),CategoryRunSelection.plan(cards,5000).map{it.key})
    }
    @Test fun failedOrUncertainMiddleItemIsConsumedOnceAndDoesNotPreventNextItem(){
        val plan=CategoryRunSelection.plan(listOf(one,three,five,three),5000);val attempted=mutableSetOf<String>()
        assertEquals(one,CategoryRunSelection.takeNext(plan,attempted))
        assertEquals(three,CategoryRunSelection.takeNext(plan,attempted)) // UI outcome is local failure or uncertain; never remove this key.
        assertEquals(five,CategoryRunSelection.takeNext(plan,attempted))
        assertNull(CategoryRunSelection.takeNext(plan,attempted));assertEquals(3,attempted.size)
        assertNull(CategoryRunSelection.takeNext(listOf(three),attempted)) // bounded recheck cannot redispatch it
    }
    @Test fun continuationRebuildsFromCurrentListRatherThanOldFailedCursor(){
        val oldAttempted=mutableSetOf(three.key)
        val current=CategoryRunSelection.plan(listOf(five),5000) // user handled 1/3km; disappearance is not a receipt
        assertEquals(five,CategoryRunSelection.takeNext(current,mutableSetOf()))
        assertEquals(setOf(three.key),oldAttempted)
        assertTrue(CategoryRunSelection.plan(emptyList(),5000).isEmpty())
    }
    @Test fun completeEmptyListRequiresOfficialEmptyAndNoForwardOrLoader(){
        assertFalse(ChallengeScanPolicy.verifiedJoinableEnd(5,true,false,0,true))
        assertFalse(ChallengeScanPolicy.verifiedJoinableEnd(5,false,true,0,true))
        assertFalse(ChallengeScanPolicy.verifiedJoinableEnd(5,false,false,0,false))
        assertTrue(ChallengeScanPolicy.verifiedJoinableEnd(3,false,false,0,true))
        assertTrue(ChallengeScanPolicy.verifiedJoinableEnd(3,false,false,4,false))
    }
    @Test fun disabledCategoryStillPermitsOnlyExactPendingKeyReadOnlyInspection(){
        val day="2026-11-05";val key="WALK|徒歩で500m移動しよう！"
        assertTrue(ParticipatingDayPolicy.inspect("2026/11/5まで",day,key,false,500,0,setOf(key)))
        assertFalse(ParticipatingDayPolicy.inspect("2026/11/4まで",day,key,false,500,0,setOf(key)))
        assertFalse(ParticipatingDayPolicy.inspect("2026/11/5まで",day,one.key,false,1000,0,setOf(key)))
    }
    private class Port(val challenge:RoutineStepResult):RoutinePort {
        val events=mutableListOf<String>()
        override fun stopped()=false;override fun sameDate()=true
        override suspend fun progress(stage:String,result:RoutineStepResult){events.add(stage+":"+result.state)}
        override suspend fun startup()=RoutineStepResult("NOT_PRESENT","checked")
        override suspend fun challenges()=challenge
        override suspend fun ads()=RoutineStepResult("LIMIT_REACHED","official25")
    }
    @Test fun localPartialRunsAdsButCannotBecomeWholeRoutineDoneOrRestartBlock()=runBlocking {
        val p=Port(RoutineStepResult("PARTIALLY_COMPLETED","3km local failure;5km confirmed"))
        val r=RoutineSequence(p).run()
        assertEquals("PARTIALLY_COMPLETED",r.state);assertEquals("LIMIT_REACHED",r.steps["ADS"]!!.state)
        assertTrue(p.events.contains("ADS:RUNNING"));assertNotEquals("DONE",r.state);assertNotEquals("BLOCKED",r.state)
    }
    @Test fun liveSdkOrUnisolatedBoostStillBlocksBeforeAds()=runBlocking {
        val p=Port(RoutineStepResult("BLOCKED","live_sdk_or_unisolated_boost",false))
        val r=RoutineSequence(p).run();assertEquals("BLOCKED",r.state);assertFalse(r.steps.containsKey("ADS"))
    }
}
