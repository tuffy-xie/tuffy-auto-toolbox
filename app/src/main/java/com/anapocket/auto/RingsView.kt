package com.anapocket.auto

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

/** Three concentric progress rings (travel, ads, check-in). Pure canvas drawing: no bitmaps are allocated. */
class RingsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val colors = intArrayOf(R.color.ring_travel, R.color.ring_ads, R.color.ring_login).map { ContextCompat.getColor(context, it) }
    private val tracks = intArrayOf(R.color.ring_track_travel, R.color.ring_track_ads, R.color.ring_track_login).map { ContextCompat.getColor(context, it) }
    private val stroke = resources.displayMetrics.density * 11f
    private val gap = resources.displayMetrics.density * 2.5f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = stroke }
    private val oval = RectF()
    private val target = FloatArray(3)
    private val shown = FloatArray(3)
    private var animator: ValueAnimator? = null

    /** Values are clamped to 0..1. Only a real change animates, so a periodic refresh costs nothing. */
    fun setProgress(travel: Float, ads: Float, login: Float) {
        val next = floatArrayOf(travel, ads, login).map { it.coerceIn(0f, 1f) }
        if (next.indices.all { next[it] == target[it] }) return
        val from = shown.copyOf()
        next.forEachIndexed { i, v -> target[i] = v }
        animator?.cancel()
        if (!isAttachedToWindow || !isShown) { target.copyInto(shown); invalidate(); return }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650; interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { a -> val f = a.animatedFraction; for (i in 0..2) shown[i] = from[i] + (target[i] - from[i]) * f; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { animator?.cancel(); target.copyInto(shown); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        for (i in 0..2) {
            val r = size / 2f - stroke / 2f - i * (stroke + gap)
            if (r <= stroke / 2f) break
            oval.set(cx - r, cy - r, cx + r, cy + r)
            paint.color = tracks[i]; canvas.drawArc(oval, 0f, 360f, false, paint)
            val sweep = shown[i] * 360f
            if (sweep > 0.5f) { paint.color = colors[i]; canvas.drawArc(oval, -90f, sweep.coerceAtMost(359.9f), false, paint) }
        }
    }
}
