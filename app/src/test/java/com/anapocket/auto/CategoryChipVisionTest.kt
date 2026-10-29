package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class CategoryChipVisionTest {
    private fun image(name: String): Triple<Int, Int, IntArray> {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/category/" + name + ".argb-le")).use { it.readBytes() }
        val pixels = IntArray(96 * 96)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels)
        return Triple(96, 96, pixels)
    }
    @Test fun realObservedFourCategoryIconsMatchOnlyTheirOwnCategory() {
        for ((name, expected) in listOf("walk" to Cat.WALK, "bike" to Cat.BIKE, "car" to Cat.CAR, "any" to Cat.MOV_ANY)) {
            val (w,h,p) = image(name)
            for (cat in Cat.values()) assertEquals(name + " vs " + cat, cat == expected, CategoryChipVision.matches(cat,w,h,p))
        }
    }
    @Test fun actualRailIconsAreNotAnyOfTheFourTargetIcons() {
        for (name in listOf("train", "bullet")) {
            val (w,h,p)=image(name)
            for (cat in listOf(Cat.WALK,Cat.BIKE,Cat.CAR,Cat.MOV_ANY)) assertFalse(name + " vs " + cat, CategoryChipVision.matches(cat,w,h,p))
        }
    }
    @Test fun realShinkansenAndFlightIconsMatchTheirNewCategories(){for((name,expected)in listOf("bullet" to Cat.SHINKANSEN,"flight" to Cat.FLIGHT)){val(w,h,p)=image(name);for(cat in Cat.values())assertEquals(name+" vs "+cat,cat==expected,CategoryChipVision.matches(cat,w,h,p))}}
    @Test fun clippedPartialTilesAreNotGuessedAsCategoryTargets() {
        val (w,h,p)=image("walk")
        assertFalse(CategoryChipVision.matches(Cat.WALK,37,h,p.take(37*h).toIntArray()))
        assertFalse(CategoryChipVision.matches(Cat.WALK,w,h,IntArray(1)))
    }
    @Test fun actualSelectedBackgroundsUseTheirOwnColorAndCannotBecomeUnselectedIcons() {
        for ((name,expected) in listOf("selected-car" to Cat.CAR, "selected-walk" to Cat.WALK, "selected-bike" to Cat.BIKE, "selected-any" to Cat.MOV_ANY)) {
            val (w,h,p)=image(name)
            for (cat in Cat.values()) {
                assertEquals(name + " vs " + cat, cat == expected, CategoryChipVision.selectedBackground(cat,p))
                assertFalse(CategoryChipVision.matches(cat,w,h,p))
            }
        }
    }
}
