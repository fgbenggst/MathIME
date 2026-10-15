package com.mathime.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.hypot

class HandwritingView(context: Context) : View(context) {
    private val paths = mutableListOf<Path>()
    private val strokes = mutableListOf<MutableList<PointF>>()
    private var active: Path? = null
    var eraserMode: Boolean = false
        set(value) { field = value; active = null; invalidate() }
    private val eraserRadius = 22f
    private var lastErase: PointF? = null
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
                if (eraserMode) {
                    lastErase = PointF(event.x, event.y)
                    eraseAt(event.x, event.y)
                    return true
                }
                active = Path().also { it.moveTo(event.x, event.y); paths.add(it) }
                strokes.add(mutableListOf(PointF(event.x, event.y)))
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (eraserMode) {
                    for (i in 0 until event.historySize) eraseBetween(event.getHistoricalX(i), event.getHistoricalY(i))
                    eraseBetween(event.x, event.y)
                    return true
                }
                active?.let { path ->
                    for (i in 0 until event.historySize) {
                        val x = event.getHistoricalX(i); val y = event.getHistoricalY(i)
                        path.lineTo(x, y); strokes.lastOrNull()?.add(PointF(x, y))
                    }
                    path.lineTo(event.x, event.y)
                    strokes.lastOrNull()?.add(PointF(event.x, event.y))
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                if (eraserMode) {
                    eraseBetween(event.x, event.y)
                    lastErase = null
                    onStrokeFinished?.invoke()
                    return true
                }
                active?.lineTo(event.x, event.y)
                strokes.lastOrNull()?.add(PointF(event.x, event.y))
                active = null
                invalidate()
                onStrokeFinished?.invoke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { active = null; invalidate(); return true }
        }
        return true
    }
    private fun eraseBetween(x: Float, y: Float) {
        val prev = lastErase
        if (prev != null) {
            val distance = hypot(x - prev.x, y - prev.y)
            val steps = (distance / (eraserRadius * 0.45f)).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                eraseAt(prev.x + (x - prev.x) * t, prev.y + (y - prev.y) * t)
            }
        } else eraseAt(x, y)
        lastErase = PointF(x, y)
    }

    /** Erase actual stroke geometry rather than painting white pixels.
     * This keeps the exported model image and stroke data consistent.
     */
    private fun eraseAt(x: Float, y: Float) {
        val rebuilt = mutableListOf<MutableList<PointF>>()
        for (stroke in strokes) {
            var piece = mutableListOf<PointF>()
            for (point in stroke) {
                val erased = hypot(point.x - x, point.y - y) <= eraserRadius
                if (erased) {
                    if (piece.isNotEmpty()) rebuilt.add(piece)
                    piece = mutableListOf()
                } else piece.add(PointF(point.x, point.y))
            }
            if (piece.isNotEmpty()) rebuilt.add(piece)
        }
        strokes.clear()
        strokes.addAll(rebuilt)
        rebuildPaths()
        invalidate()
    }

    private fun rebuildPaths() {
        paths.clear()
        strokes.forEach { stroke ->
            if (stroke.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    if (stroke.size == 1) lineTo(stroke[0].x + .1f, stroke[0].y)
                    else for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                }
                paths.add(path)
            }
        }
    }

    fun undo() { if (paths.isNotEmpty()) { paths.removeAt(paths.lastIndex); strokes.removeAt(strokes.lastIndex) }; invalidate() }
    fun clear() { paths.clear(); strokes.clear(); invalidate() }
    fun getStrokes(): List<List<PointF>> = strokes.map { stroke -> stroke.map { PointF(it.x, it.y) } }
    fun hasInk() = paths.isNotEmpty()

    /** Three conservative input variants. Never infer a glyph identity or replace symbols.
     * ORIGINAL is the untouched path; SMOOTH and SHAPE use recorded touch coordinates.
     */
    fun exportVariants(): List<Pair<String, Bitmap>> {
        val original = exportInkBitmap() ?: return emptyList()
        val snapshot = getStrokes()
        return listOf(
            "原始筆跡" to original,
            "輕度平滑" to renderStrokes(snapshot, 0.22f, 7f),
            "保守字形修正" to renderStrokes(snapshot, 0.40f, 6f)
        )
    }

    private fun renderStrokes(source: List<List<PointF>>, smoothing: Float, width: Float): Bitmap {
        val all = source.flatten()
        if (all.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val left = all.minOf { it.x }; val right = all.maxOf { it.x }
        val top = all.minOf { it.y }; val bottom = all.maxOf { it.y }
        val margin = 24f
        val naturalW = max(1f, right - left + 2 * margin)
        val naturalH = max(1f, bottom - top + 2 * margin)
        val scale = minOf(1f, 1024f / max(naturalW, naturalH))
        val bmp = Bitmap.createBitmap(max(1, (naturalW * scale).toInt()), max(1, (naturalH * scale).toInt()), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; strokeWidth = width; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        Canvas(bmp).apply {
            drawColor(Color.WHITE)
            scale(scale, scale)
            translate(-left + margin, -top + margin)
            source.forEach { stroke ->
                if (stroke.isEmpty()) return@forEach
                val path = Path()
                val points = stroke.mapIndexed { index, point ->
                    if (index == 0 || index == stroke.lastIndex || stroke.size < 3) point
                    else {
                        val before = stroke[index - 1]; val after = stroke[index + 1]
                        // Preserve sharp corners: smoothing only when direction is consistent.
                        val ax = point.x - before.x; val ay = point.y - before.y
                        val bx = after.x - point.x; val by = after.y - point.y
                        val dot = ax * bx + ay * by
                        if (dot <= 0f) point else PointF(
                            point.x * (1f - smoothing) + (before.x + after.x) * smoothing / 2f,
                            point.y * (1f - smoothing) + (before.y + after.y) * smoothing / 2f
                        )
                    }
                }
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
                if (points.size == 1) path.lineTo(points[0].x + .1f, points[0].y)
                drawPath(path, paint)
            }
        }
        return bmp
    }

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
