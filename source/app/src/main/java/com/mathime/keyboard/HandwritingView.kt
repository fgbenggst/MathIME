package com.mathime.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class HandwritingView(context: Context) : View(context) {
    private val paths = mutableListOf<Path>()
    private var active: Path? = null
    var onStrokeFinished: (() -> Unit)? = null
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    init { setBackgroundColor(Color.WHITE) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paths.forEach { canvas.drawPath(it, pen) }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                active = Path().also { it.moveTo(event.x, event.y); paths.add(it) }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                active?.let { path ->
                    for (i in 0 until event.historySize) path.lineTo(event.getHistoricalX(i), event.getHistoricalY(i))
                    path.lineTo(event.x, event.y)
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                active?.lineTo(event.x, event.y)
                active = null
                invalidate()
                onStrokeFinished?.invoke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { active = null; invalidate(); return true }
        }
        return true
    }
    fun undo() { if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex); invalidate() }
    fun clear() { paths.clear(); invalidate() }
    fun hasInk() = paths.isNotEmpty()

    /** Cropped white-background bitmap, with padding, suitable for future model input. */
    fun exportInkBitmap(maxDimension: Int = 1024): Bitmap? {
        if (paths.isEmpty()) return null
        val bounds = RectF()
        val part = RectF()
        paths.forEachIndexed { index, path ->
            path.computeBounds(part, true)
            if (index == 0) bounds.set(part) else bounds.union(part)
        }
        val margin = 20f
        bounds.inset(-margin, -margin)
        val w = max(1f, bounds.width())
        val h = max(1f, bounds.height())
        val scale = minOf(1f, maxDimension / max(w, h))
        val bitmap = Bitmap.createBitmap(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            scale(scale, scale)
            translate(-bounds.left, -bounds.top)
            paths.forEach { drawPath(it, pen) }
        }
        return bitmap
    }
}
