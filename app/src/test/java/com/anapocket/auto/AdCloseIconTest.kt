package com.anapocket.auto

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdCloseIconTest {
    @Test fun actualCircularCloseRetainsCentreCross() {
        assertTrue(AdCloseIcon.isWhiteCircleCross(50, 50, fixture("mbridge-circle-close", 50)))
    }
    @Test fun informationIconAndForwardMustNotBecomeCloseCross() {
        assertFalse(AdCloseIcon.isWhiteCircleCross(50, 50, fixture("mbridge-info", 50)))
        assertFalse(AdCloseIcon.isWhiteCircleCross(36, 36, fixture("pangle-forward", 36)))
    }
    @Test fun actualFyberNativeSecondCloseIsWhiteCross() {
        assertTrue(AdCloseIcon.isWhiteCross(41, 41, fixture("fyber-native-close", 41)))
    }
    @Test fun actualFyberSvgCloseIsRecognizedWithoutColorAssumptions() {
        val close = checkNotNull(javaClass.getResourceAsStream("/fyber-close.svg")).reader().use { it.readText() }
        val rating = checkNotNull(javaClass.getResourceAsStream("/fyber-rating.svg")).reader().use { it.readText() }
        assertTrue(AdCloseIcon.isCloseSvg(close))
        assertFalse(AdCloseIcon.isCloseSvg(rating))
        assertFalse(AdCloseIcon.isCloseSvg("Install"))
    }
    private fun fixture(name: String, size: Int): IntArray {
        val b = checkNotNull(javaClass.getResourceAsStream("/$name.argb-le")).use { it.readBytes() }
        return IntArray(size * size).also { ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(it) }
    }
    @Test fun actualPangleForwardIsEndControlButNotCross() {
        val p = fixture("pangle-forward", 36)
        assertTrue(AdCloseIcon.isWhiteForward(36, 36, p))
        assertFalse(AdCloseIcon.isWhiteCross(36, 36, p))
    }
    @Test fun actualPangleSoundMustNeverBeClickedAsEndControl() {
        val p = fixture("pangle-sound", 36)
        assertFalse(AdCloseIcon.isWhiteForward(36, 36, p))
        assertFalse(AdCloseIcon.isWhiteCross(36, 36, p))
    }
    @Test fun crossAndBlankAreNotForward() {
        assertFalse(AdCloseIcon.isWhiteForward(72, 72, fixture("five-close", 72)))
        assertFalse(AdCloseIcon.isWhiteForward(36, 36, IntArray(36 * 36)))
    }
    @Test fun actualFiveSdkScreenshotContainsCloseCross() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/five-close.argb-le")).use { it.readBytes() }
        check(bytes.size == 72 * 72 * 4)
        val pixels = IntArray(72 * 72)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels)
        assertTrue("Actual accessible Five close image must match", AdCloseIcon.isWhiteCross(72, 72, pixels))
    }
    @Test fun blankAndSolidImagesAreNotCloseIcons() {
        assertFalse(AdCloseIcon.isWhiteCross(72, 72, IntArray(72 * 72) { 0xff000000.toInt() }))
        assertFalse(AdCloseIcon.isWhiteCross(72, 72, IntArray(72 * 72) { 0xffffffff.toInt() }))
    }
    @Test fun plusSymbolIsNotCloseCross() {
        val p = IntArray(72 * 72) { 0xff000000.toInt() }
        for (y in 16..55) for (x in 16..55) if (x in 32..39 || y in 32..39) p[y * 72 + x] = -1
        assertFalse(AdCloseIcon.isWhiteCross(72, 72, p))
    }
    @Test fun chevronArrowIsNotCloseCross() {
        val p = IntArray(72 * 72) { 0xff000000.toInt() }
        for (y in 16..55) for (x in 16..55) if (kotlin.math.abs(x - (52 - kotlin.math.abs(y - 36))) < 4) p[y * 72 + x] = -1
        assertFalse(AdCloseIcon.isWhiteCross(72, 72, p))
    }
    @Test fun invalidPixelBuffersFailClosed() {
        assertFalse(AdCloseIcon.isWhiteCross(1, 1, intArrayOf(-1)))
        assertFalse(AdCloseIcon.isWhiteCross(72, 72, IntArray(2)))
    }
}
