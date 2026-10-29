package com.anapocket.auto

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Visual X confirmation inside an actual accessible button. Never invents a tap position. */
object AdCloseIcon {
    /** Ignore a surrounding circular stroke while retaining the actual centre X. */
    fun isWhiteCircleCross(width: Int, height: Int, pixels: IntArray): Boolean {
        if (width < 12 || height < 12 || pixels.size != width * height) return false
        val cx = (width - 1) / 2.0; val cy = (height - 1) / 2.0
        val radius = min(width, height) * .28
        val centre = IntArray(pixels.size) { i ->
            val dx = i % width - cx; val dy = i / width - cy
            if (dx * dx + dy * dy <= radius * radius) pixels[i] else 0xff000000.toInt()
        }
        return isWhiteCross(width, height, centre)
    }
    /** Exact path exposed by Fyber's actual enabled HTML close button; never executes SVG content. */
    fun isCloseSvg(svg: String): Boolean {
        if (svg.length > 4096) return false
        val d = Regex("<path\\b[^>]*\\bd\\s*=\\s*[\"']([^\"']+)[\"']").find(svg)?.groupValues?.get(1) ?: return false
        val canonical = "M14 1.41L12.59 0L7 5.59L1.41 0L0 1.41L5.59 7L0 12.59L1.41 14L7 8.41L12.59 14L14 12.59L8.41 7L14 1.41Z"
        return d.replace(Regex("\\s+"), "") == canonical.replace(Regex("\\s+"), "")
    }
    /** Pangle's observed double-chevron advances a finished segment/end card; it is not a reward receipt. */
    fun isWhiteForward(width: Int, height: Int, pixels: IntArray): Boolean {
        if (width < 12 || height < 12 || pixels.size != width * height) return false
        fun white(p: Int): Boolean {
            val r = (p ushr 16) and 255; val g = (p ushr 8) and 255; val b = p and 255
            return min(r, min(g, b)) >= 215 && max(r, max(g, b)) - min(r, min(g, b)) <= 30
        }
        var x0 = width; var y0 = height; var x1 = -1; var y1 = -1; var count = 0
        for (y in 0 until height) for (x in 0 until width) if (white(pixels[y * width + x])) {
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y); count++
        }
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        if (w < width * .35 || h < height * .35 || w > width * .95 || h > height * .95 ||
            w.toDouble() / h !in 1.1..1.65 || count.toDouble() / (w * h) !in .2.. .65) return false
        var matched = 0; val arms = IntArray(4)
        for (y in y0..y1) for (x in x0..x1) if (white(pixels[y * width + x])) {
            val nx = (x - x0).toDouble() / (w - 1); val ny = (y - y0).toDouble() / (h - 1)
            val bend = 1 - abs(2 * ny - 1)
            if (min(abs(nx - (.08 + .35 * bend)), abs(nx - (.62 + .35 * bend))) <= .14) matched++
            arms[(if (nx > .5) 1 else 0) + (if (ny > .5) 2 else 0)]++
        }
        return matched >= count * .9 && arms.all { it >= count * .15 }
    }
    fun isWhiteCross(width: Int, height: Int, pixels: IntArray): Boolean {
        if (width < 12 || height < 12 || pixels.size != width * height) return false
        fun white(p: Int): Boolean {
            val r = (p ushr 16) and 255; val g = (p ushr 8) and 255; val b = p and 255
            return min(r, min(g, b)) >= 215 && max(r, max(g, b)) - min(r, min(g, b)) <= 30
        }
        var x0 = width; var y0 = height; var x1 = -1; var y1 = -1; var count = 0
        for (y in 0 until height) for (x in 0 until width) if (white(pixels[y * width + x])) {
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y); count++
        }
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        if (w < width * 0.25 || h < height * 0.25 || w > width * 0.9 || h > height * 0.9 ||
            w.toDouble() / h !in 0.75..1.33 || count.toDouble() / (w * h) !in 0.15.. 0.65 ||
            abs((x0 + x1 + 1) / 2.0 - width / 2.0) > width * 0.2 ||
            abs((y0 + y1 + 1) / 2.0 - height / 2.0) > height * 0.2) return false
        var diagonal = 0; val arms = IntArray(4)
        for (y in y0..y1) for (x in x0..x1) if (white(pixels[y * width + x])) {
            val nx = 2.0 * (x - x0) / (w - 1) - 1; val ny = 2.0 * (y - y0) / (h - 1) - 1
            if (min(abs(nx - ny), abs(nx + ny)) <= 0.32) diagonal++
            if (abs(nx) >= 0.4 && abs(ny) >= 0.4) arms[(if (nx > 0) 1 else 0) + (if (ny > 0) 2 else 0)]++
        }
        return diagonal >= count * 0.85 && arms.all { it >= count * 0.06 }
    }
}
