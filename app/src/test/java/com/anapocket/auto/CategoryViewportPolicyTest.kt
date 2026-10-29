package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test

class CategoryViewportPolicyTest {
    @Test fun healthyCategoryKeepsCurrentPosition() { assertEquals(CategoryViewportAction.KEEP_POSITION,CategoryViewportPolicy.action(Cat.BIKE,setOf(Cat.BIKE),0)) }
    @Test fun actualBikeToAnyResetIsRepairedBeforeAnotherAd() { assertEquals(CategoryViewportAction.RESELECT_CATEGORY,CategoryViewportPolicy.action(Cat.BIKE,setOf(Cat.MOV_ANY),0)) }
    @Test fun adOnlyViewportDoesNotResetTheCategory() { assertEquals(CategoryViewportAction.KEEP_POSITION,CategoryViewportPolicy.action(Cat.BIKE,emptySet(),0)) }
    @Test fun persistentMismatchStopsInsteadOfUnboundedReselection() { assertEquals(CategoryViewportAction.STOP,CategoryViewportPolicy.action(Cat.BIKE,setOf(Cat.MOV_ANY),1));assertEquals(CategoryViewportAction.KEEP_POSITION,CategoryViewportPolicy.action(Cat.BIKE,setOf(Cat.BIKE),1)) }
}
