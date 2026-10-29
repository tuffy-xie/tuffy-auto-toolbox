package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class CategoryRunSelectionTest {
    private fun cards(vararg titles: String) = titles.map { Cards.fromTitle(it)!! }
    private fun processAll(list: List<ChallengeCard>, cat: Cat, max: Int): List<Int> {
        val done=mutableSetOf<String>(); val result=mutableListOf<Int>()
        while (true) { val next=CategoryRunSelection.next(list,cat,max,done) ?: break; done.add(next.key); result.add(next.meters!!) }
        return result
    }
    @Test fun tenKilometresIncludesEverySmallerTaskAndExactBoundary() {
        val a=cards("自転車で500m移動しよう！", "自転車で10km移動しよう！", "自転車で15km移動しよう！", "自転車で3km移動しよう！")
        assertEquals(listOf(500,3000,10000), processAll(a,Cat.BIKE,10000))
    }
    @Test fun fortyKilometresIncludesAllDailyTasksThroughForty() {
        val a=cards("車、電車、モノレールで5km移動しよう！", "車、電車、モノレールで40km移動しよう！", "車、電車、モノレールで50km移動しよう！")
        assertEquals(listOf(5000,40000),processAll(a,Cat.CAR,40000))
    }
    @Test fun firstOverLimitCardDoesNotHideALaterEligibleCard() {
        val a=cards("徒歩で15km移動しよう！", "徒歩で2km移動しよう！")
        assertEquals(2000,CategoryRunSelection.next(a,Cat.WALK,10000,emptySet())!!.meters)
    }
    @Test fun duplicateAndAlreadyProcessedAndOtherModesAreNeverSubmittedAgain() {
        val a=cards("徒歩で1km移動しよう！", "徒歩で1km移動しよう！", "自転車で1km移動しよう！")
        assertEquals(listOf(1000),processAll(a,Cat.WALK,10000))
        assertNull(CategoryRunSelection.next(a,Cat.WALK,10000,setOf(a.first().key)))
    }
}
