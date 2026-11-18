package com.anapocket.auto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 2026-11-18: AppLovin WebView end cards (ArtZMiami at 00:39, MakesYouFluent at 02:16/07:54/08:11) stopped Boost runs five
 * times. The real × wrapper is Rect(618,63-698,143) / Rect(618,49-698,127) with an 18px cross in its middle.
 */
class AppLovinEndCardSmallCrossTest {
    private fun fixture(name: String, w: Int, h: Int): IntArray {
        val b = checkNotNull(javaClass.getResourceAsStream("/applovin/$name.argb-le")).use { it.readBytes() }
        check(b.size == w * h * 4)
        return IntArray(w * h).also { ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(it) }
    }

    @Test fun darkDiscEndCardCloseFailedTheWholeBoxButMatchesItsCentre() {
        val p = fixture("endcard-x-dark-80x80", 80, 80)
        assertFalse(AdCloseIcon.isWhiteCross(80, 80, p))
        assertFalse(AdCloseIcon.isWhiteCircleCross(80, 80, p))
        assertTrue(AdCloseIcon.isCentredSmallCross(80, 80, p))
    }

    @Test fun greyDiscOnWhitePageEndCardCloseMatchesItsCentre() {
        val p = fixture("endcard-x-grey-80x78", 80, 78)
        assertFalse(AdCloseIcon.isWhiteCross(80, 78, p))
        assertTrue(AdCloseIcon.isCentredSmallCross(80, 78, p))
    }

    @Test fun skipControlAndPageContentAreNotCrosses() {
        assertFalse(AdCloseIcon.isCentredSmallCross(66, 66, fixture("endcard-skip-66x66", 66, 66)))
        assertFalse(AdCloseIcon.isCentredSmallCross(80, 80, fixture("endcard-page-80x80", 80, 80)))
    }

    @Test fun blankSolidAndTinyBoxesAreRejected() {
        assertFalse(AdCloseIcon.isCentredSmallCross(80, 80, IntArray(80 * 80) { 0xff000000.toInt() }))
        assertFalse(AdCloseIcon.isCentredSmallCross(80, 80, IntArray(80 * 80) { 0xffffffff.toInt() }))
        assertFalse(AdCloseIcon.isCentredSmallCross(20, 20, IntArray(20 * 20)))
        assertFalse(AdCloseIcon.isCentredSmallCross(80, 80, IntArray(10)))
    }

    @Test fun onlyTheTopRightSingleChildWrapperIsACandidate() {
        val a = "com.applovin.adview.AppLovinFullscreenActivity"
        assertTrue(AppLovinClosePolicy.wrapper(a, "android.view.View", "", true, 1, 618, 63, 720, 1650))
        assertFalse(AppLovinClosePolicy.wrapper(a, "android.view.View", "", true, 1, 22, 63, 720, 1650))
        assertFalse(AppLovinClosePolicy.wrapper(a, "android.view.View", "", true, 1, 618, 1397, 720, 1650))
        assertFalse(AppLovinClosePolicy.wrapper(a, "android.widget.Button", "Shop Now!", true, 1, 618, 63, 720, 1650))
        assertFalse(AppLovinClosePolicy.wrapper("com.unity3d.ads.adunit.AdUnitActivity", "android.view.View", "", true, 1, 618, 63, 720, 1650))
    }
}

class CheckinOcrRetryTest {
    @Test fun aStalledOrDeadRecognizerGetsOneMoreRead() {
        // 11/18 12:30 one recognition_timeout ended the whole TikTok run with all four tasks BLOCKED.
        assertTrue(CheckinOcrRetry.retryable(0, "recognition_timeout"))
        assertTrue(CheckinOcrRetry.retryable(0, "recognizer_service_unavailable"))
        assertFalse(CheckinOcrRetry.retryable(1, "recognition_timeout"))
        assertFalse(CheckinOcrRetry.retryable(0, "invalid_image_path"))
        assertFalse(CheckinOcrRetry.retryable(0, null))
    }
}
