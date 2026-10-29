package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
class CategoryDragPlanTest {
 @Test fun observedStripDragStaysInsideStripAndAwayFromScreenEdge(){val p=CategoryDragPlan.points(152,720,true)!!;assertTrue(p.first<672);assertTrue(p.second>152);assertTrue(p.first>p.second)}
 @Test fun resetDirectionIsTheOppositeWithinTheSameObservedBounds(){val p=CategoryDragPlan.points(152,720,true)!!;val q=CategoryDragPlan.points(152,720,false)!!;assertEquals(p.first,q.second);assertEquals(p.second,q.first)}
 @Test fun unobservedOrTinyBoundsDoNotProduceGuessedGestures(){assertNull(CategoryDragPlan.points(0,0,true));assertNull(CategoryDragPlan.points(700,720,true))}
 @Test fun overlapMakesTheActualAnyTileVisibleAfterNativePageFramesSkipIt(){
  fun visible(offset:Int)=662-offset>=152&&758-offset<=720
  assertFalse(visible(0));assertFalse(visible(568));assertFalse(visible(782))
  val drag=CategoryDragPlan.points(152,720,false)!!
  assertTrue(visible(782-(drag.second-drag.first)))
 }
}
