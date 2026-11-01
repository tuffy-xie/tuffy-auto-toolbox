package com.anapocket.auto

/** Actual ANA 2.3.4 category icon palette, only used inside observed native category Compose bounds. */
object CategoryChipVision {
    private fun color(cat: Cat): Int = when (cat) {
        Cat.WALK -> 0xff6969
        Cat.BIKE -> 0xffae2c
        Cat.CAR -> 0x00aff0
        Cat.MOV_ANY -> 0x5e70c1
        Cat.SHINKANSEN -> 0x1c8adb
        Cat.FLIGHT -> 0xfa78be
    }
    private fun near(pixel: Int, rgb: Int): Boolean =
        kotlin.math.abs((pixel shr 16 and 255) - (rgb shr 16 and 255)) <= 18 &&
        kotlin.math.abs((pixel shr 8 and 255) - (rgb shr 8 and 255)) <= 18 &&
        kotlin.math.abs((pixel and 255) - (rgb and 255)) <= 18
    fun matches(cat: Cat, width: Int, height: Int, pixels: IntArray): Boolean {
        if (width <= 0 || height <= 0 || pixels.size != width * height || width.toDouble() / height !in .8..1.3) return false
        val n = pixels.count { near(it, color(cat)) }
        // A glyph occupies a minority of the white tile. Selected cyan backgrounds are not car icons.
        return n >= maxOf(20, pixels.size / 200) && n < pixels.size / 3
    }
    fun selectedBackground(cat: Cat, pixels: IntArray): Boolean = pixels.isNotEmpty() &&
        pixels.count { near(it, color(cat)) } > pixels.size * .55
    /** Darkened tiles behind an observed popup are not actionable icon evidence. */
    fun unobscured(pixels:IntArray):Boolean=pixels.isNotEmpty()&&(pixels.count{p->
        val r=p shr 16 and 255;val g=p shr 8 and 255;val b=p and 255
        minOf(r,g,b)>=220&&maxOf(r,g,b)-minOf(r,g,b)<=20
    }>pixels.size*.5||Cat.values().any{selectedBackground(it,pixels)})
}

object CategoryRunSelection {
    fun next(cards: List<ChallengeCard>, cat: Cat, maxMeters: Int, processed: Set<String>): ChallengeCard? =
        Cards.select(Cards.dedupe(cards), cat, maxMeters).matched.firstOrNull { it.key !in processed }
}

object DistanceLimitPolicy {
    fun parse(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in 1..200 }
}

object CategoryCompletionPolicy {
    fun mayComplete(exhausted: Boolean, stopped: Boolean, samePhoneDate: Boolean): Boolean = exhausted && !stopped && samePhoneDate
    fun covers(previousMaxKm: Int, currentMaxKm: Int): Boolean = currentMaxKm in 1..20000 && previousMaxKm >= currentMaxKm
}

object InitialJoinReceiptPolicy {
    fun matches(expectedPoints: Int, expectedSteps: List<Int>, labels: List<String>): Boolean =
        expectedPoints > 0 && expectedSteps.size == 5 && expectedSteps.all { it > 0 } &&
            ChallengeSignals.joined(labels) && ChallengeSignals.nextBoost(labels) == 1 &&
            ChallengeSignals.offeredReward(labels) == expectedPoints && ChallengeSignals.increments(labels) == expectedSteps
}

object CategoryDistancePolicy {fun parse(cat:Cat,raw:String):Int? = raw.trim().toIntOrNull()?.takeIf{it in 1..cat.maxKm}}
