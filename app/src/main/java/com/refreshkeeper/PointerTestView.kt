package com.refreshkeeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import kotlin.math.exp

/**
 * 1) 마우스 입력이 실제로 초당 몇 번 들어오는지 측정한다.
 * 2) 시스템 포인터를 숨기고, 120fps로 부드럽게 보정해서 그리는 "가상 포인터"를 체험하게 한다.
 */
class PointerTestView(context: Context) : View(context) {

    /** (입력 속도 Hz, 앱 프레임 fps) 를 0.5초마다 알려준다. */
    var onStats: ((Int, Int) -> Unit)? = null

    var useVirtual: Boolean = false
        set(value) {
            field = value
            pointerIcon = if (value) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL) else null
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private var targetX = 0f
    private var targetY = 0f
    private var drawX = 0f
    private var drawY = 0f
    private var hasPos = false

    private val stamps = ArrayList<Long>()
    private var lastInputHz = 0
    private var lastFrameNanos = 0L
    private var frames = 0
    private var lastStatsMs = 0L

    private val arrow = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = 16f * density
        textAlign = Paint.Align.CENTER
    }

    init {
        val s = 1.3f * density
        arrow.moveTo(0f, 0f)
        arrow.lineTo(0f, 17f * s)
        arrow.lineTo(4.5f * s, 12.8f * s)
        arrow.lineTo(7.5f * s, 20f * s)
        arrow.lineTo(10f * s, 19f * s)
        arrow.lineTo(7f * s, 11.8f * s)
        arrow.lineTo(13f * s, 11.8f * s)
        arrow.close()
        if (Build.VERSION.SDK_INT >= 35) {
            setRequestedFrameRate(120f)
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = if (lastFrameNanos == 0L) 0.008f else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameTimeNanos
            frames++

            if (hasPos) {
                val alpha = 1f - exp(-dt / 0.015f)
                drawX += (targetX - drawX) * alpha
                drawY += (targetY - drawY) * alpha
            }
            invalidate()

            val now = SystemClock.uptimeMillis()
            if (now - lastStatsMs >= 500) {
                val elapsed = (now - lastStatsMs).coerceAtLeast(1)
                val fps = (frames * 1000L / elapsed).toInt()
                frames = 0
                lastStatsMs = now
                updateInputHz(now)
                onStats?.invoke(lastInputHz, fps)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun updateInputHz(now: Long) {
        if (stamps.size < 3 || now - stamps.last() > 400) return
        var sum = 0L
        var n = 0
        for (i in 1 until stamps.size) {
            val d = stamps[i] - stamps[i - 1]
            if (d in 1..99) {
                sum += d
                n++
            }
        }
        if (n > 0) lastInputHz = (1000f * n / sum).toInt()
    }

    private fun record(e: MotionEvent) {
        for (i in 0 until e.historySize) {
            stamps.add(e.getHistoricalEventTime(i))
        }
        stamps.add(e.eventTime)
        while (stamps.size > 60) stamps.removeAt(0)

        targetX = e.x
        targetY = e.y
        if (!hasPos) {
            drawX = targetX
            drawY = targetY
            hasPos = true
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        record(event)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        record(event)
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNanos = 0L
        lastStatsMs = SystemClock.uptimeMillis()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFFF2F2F2.toInt())
        if (!hasPos) {
            canvas.drawText("여기서 마우스를 움직여 보세요", width / 2f, height / 2f, hintPaint)
            return
        }
        if (useVirtual) {
            canvas.save()
            canvas.translate(drawX, drawY)
            canvas.drawPath(arrow, fillPaint)
            canvas.drawPath(arrow, strokePaint)
            canvas.restore()
        }
    }
}
