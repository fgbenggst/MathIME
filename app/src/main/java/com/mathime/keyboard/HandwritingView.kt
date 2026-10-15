package com.mathime.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

class HandwritingView(context: Context) : View(context) {
    private val paths = mutableListOf<Path>()
    private var active: Path? = null
    var onStrokeFinished: (() -> Unit)? = null
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 7f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    init { setBackgroundColor(Color.WHITE) }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); paths.forEach { canvas.drawPath(it, pen) } }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { active = Path().also { it.moveTo(event.x, event.y); paths.add(it) }; invalidate(); return true }
            MotionEvent.ACTION_MOVE -> { active?.lineTo(event.x, event.y); invalidate(); return true }
            MotionEvent.ACTION_UP -> { active?.lineTo(event.x, event.y); active = null; invalidate(); onStrokeFinished?.invoke(); return true }
            MotionEvent.ACTION_CANCEL -> { active = null; return true }
        }
        return true
    }
    fun undo() { if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex); invalidate() }
    fun clear() { paths.clear(); invalidate() }
    fun hasInk() = paths.isNotEmpty()
}
