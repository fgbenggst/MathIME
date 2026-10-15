package com.mathime.keyboard

import android.graphics.PointF
import kotlin.math.*

/**
 * Separate, offline, stroke-geometry classifier for isolated symbols.
 * This is a deterministic classifier, NOT a pretrained neural model.
 * It abstains when the shape is ambiguous, leaving CoMER unchanged.
 */
class SingleGlyphRecognizer {
    data class Prediction(val latex: String, val confidence: Float, val explanation: String)

    fun predict(input: List<List<PointF>>): Prediction? {
        val strokes = input.filter { it.size >= 3 && length(it) > 8f }
        if (strokes.isEmpty() || strokes.size > 2) return null
        val all = strokes.flatten()
        val width = all.maxOf { it.x } - all.minOf { it.x }
        val height = all.maxOf { it.y } - all.minOf { it.y }
        if (width < 12f || height < 12f) return null
        val ratio = width / height
        if (ratio !in .35f..2.6f) return null

        if (strokes.size == 2) {
            val a = strokes[0]; val b = strokes[1]
            val la = lineFit(a); val lb = lineFit(b)
            // Two strokes should each be nearly straight and intersect centrally.
            if (la.second > .22f || lb.second > .22f) return null
            val ax = a.last().x - a.first().x
            val ay = a.last().y - a.first().y
            val bx = b.last().x - b.first().x
            val by = b.last().y - b.first().y
            val angle = abs(atan2(ax * by - ay * bx, ax * bx + ay * by))
            val crossAngle = min(angle, PI.toFloat() - angle)
            if (crossAngle < .55f) return null
            val horizontal = listOf(a,b).any {
                val dx=abs(it.last().x-it.first().x)
                val dy=abs(it.last().y-it.first().y)
                dx > 1.8f*dy
            }
            val vertical = listOf(a,b).any {
                val dx=abs(it.last().x-it.first().x)
                val dy=abs(it.last().y-it.first().y)
                dy > 1.8f*dx
            }
            if (horizontal && vertical) {
                return Prediction("+", .90f, "two straight, perpendicular strokes")
            }
            val diagonalA = abs(ax) > .30f * abs(ay) && abs(ay) > .30f * abs(ax)
            val diagonalB = abs(bx) > .30f * abs(by) && abs(by) > .30f * abs(bx)
            if (diagonalA && diagonalB && ratio in .50f..1.9f) {
                return Prediction("x", .86f, "two crossing diagonal strokes")
            }
            return null
        }
        // For a single continuous stroke, check an open-top curved '2' with a
        // descending middle and a rightward lower tail. Avoid guessing others.
        val stroke = strokes[0]
        val sampled = sample(stroke, 24)
        val minX = all.minOf { it.x }; val minY = all.minOf { it.y }
        val normalized = sampled.map { PointF((it.x-minX)/width, (it.y-minY)/height) }
        val first = normalized.first(); val last = normalized.last()
        val topIndex = normalized.indices.minByOrNull { normalized[it].y } ?: return null
        val bottomIndex = normalized.indices.maxByOrNull { normalized[it].y } ?: return null
        val finalTailRight = last.x > .70f && last.y > .65f
        val startUpper = first.y < .45f && first.x < .65f
        val topEarly = topIndex < 12
        val bottomLate = bottomIndex > 10
        val downSweep = normalized.slice(8..17).maxOf { it.y } > .55f
        if (startUpper && finalTailRight && topEarly && bottomLate && downSweep && ratio in .45f..1.65f) {
            return Prediction("2", .77f, "single curved stroke with lower right tail")
        }
        return null
    }

    private fun length(p: List<PointF>): Float =
        (1 until p.size).sumOf { hypot((p[it].x-p[it-1].x).toDouble(),(p[it].y-p[it-1].y).toDouble()) }.toFloat()

    private fun lineFit(p: List<PointF>): Pair<Float,Float> {
        val start=p.first(); val end=p.last()
        val dx=end.x-start.x; val dy=end.y-start.y
        val span=hypot(dx,dy).coerceAtLeast(1f)
        val error=p.sumOf { abs((it.x-start.x)*dy-(it.y-start.y)*dx).toDouble()/span }.toFloat()/p.size
        return span to error/span
    }

    private fun sample(p: List<PointF>, count:Int):List<PointF> {
        val cum=FloatArray(p.size)
        for (i in 1 until p.size) cum[i]=cum[i-1]+hypot(p[i].x-p[i-1].x,p[i].y-p[i-1].y)
        if (cum.last()<1f) return List(count){p.first()}
        var seg=1
        return (0 until count).map { i ->
            val target=cum.last()*i/(count-1)
            while(seg<cum.lastIndex && cum[seg]<target)seg++
            val t=((target-cum[seg-1])/(cum[seg]-cum[seg-1]).coerceAtLeast(.001f)).coerceIn(0f,1f)
            PointF(p[seg-1].x+t*(p[seg].x-p[seg-1].x),p[seg-1].y+t*(p[seg].y-p[seg-1].y))
        }
    }
}
