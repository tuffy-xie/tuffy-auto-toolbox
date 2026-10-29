package com.anapocket.auto
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
class CanvasAdControlTest {
    private fun pixels(name: String,w: Int,h: Int): IntArray {
        val bytes=checkNotNull(javaClass.getResourceAsStream(name)).use { it.readBytes() }
        return IntArray(w*h).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(it) }
    }
    @Test fun actualSingleTapRevealFramesLocateThePaintedSkipInsideTheObservedButton() {
        for(name in listOf("revealed1","revealed2")) {
            val result=checkNotNull(CanvasAdControl.find(720,352,pixels("/canvas/"+name+".argb-le",720,352)))
            assertEquals("SKIP",result.kind)
            assertTrue(result.bounds.left>=32 && result.bounds.right<=92)
            assertTrue(result.bounds.top>=24 && result.bounds.bottom<=84)
        }
    }
    @Test fun actualHiddenControlsGameFrameCannotInventAClosePosition() {
        assertNull(CanvasAdControl.find(720,352,pixels("/canvas/hidden.argb-le",720,352)))
    }
    @Test fun aPlayTriangleWithoutTheObservedVerticalBarCannotBecomeSkip() {
        val p=pixels("/canvas/revealed1.argb-le",720,352)
        for(y in 42..64)for(x in 66..72)p[y*720+x]=0xff000000.toInt()
        assertNull(CanvasAdControl.find(720,352,p))
    }
    @Test fun actualInformationAndDoubleChevronAreNotCanvasSkipOrClose() {
        assertNull(CanvasAdControl.find(50,50,pixels("/mbridge-info.argb-le",50,50)))
        assertNull(CanvasAdControl.find(36,36,pixels("/pangle-forward.argb-le",36,36)))
    }
    @Test fun actualWhiteCrossGeometryCanBeLocatedWithoutGuessingItsPosition() {
        val result=checkNotNull(CanvasAdControl.find(72,72,pixels("/five-close.argb-le",72,72)))
        assertEquals("CLOSE",result.kind)
    }
    @Test fun twoVisibleSkipControlsAreAmbiguousAndMustNotBeGuessed() {
        val p=pixels("/canvas/revealed1.argb-le",720,352);val original=p.clone()
        for(y in 0 until 60)for(x in 0 until 60)p[(25+y)*720+500+x]=original[(24+y)*720+32+x]
        assertNull(CanvasAdControl.find(720,352,p))
    }
}
