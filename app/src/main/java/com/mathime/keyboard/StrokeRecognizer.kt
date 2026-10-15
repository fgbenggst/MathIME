package com.mathime.keyboard

import android.graphics.PointF
import kotlin.math.*

/** Small, deterministic offline gesture recognizer. Recognizes ONE isolated glyph,
 * not mathematical layout. This is a baseline, NOT a neural formula OCR model. */
class StrokeRecognizer {
    data class Guess(val symbol: String, val score: Float)
    private val templates: List<Pair<String, List<PointF>>> = mapOf(
        "0" to "50,0 20,10 0,50 20,90 50,100 80,90 100,50 80,10 50,0",
        "1" to "20,25 55,0 55,100",
        "2" to "0,15 35,0 90,10 100,35 70,60 0,100 100,100",
        "3" to "0,0 90,0 55,45 90,50 100,80 65,100 0,95",
        "4" to "80,100 80,0 0,65 100,65",
        "5" to "100,0 10,0 0,50 65,45 100,65 80,100 0,95",
        "6" to "90,0 25,25 0,65 20,100 85,100 100,65 60,50 0,65",
        "7" to "0,0 100,0 40,100",
        "8" to "50,50 0,20 50,0 100,20 50,50 0,80 50,100 100,80 50,50",
        "9" to "90,50 30,50 0,20 30,0 90,10 100,45 70,100",
        "-" to "0,50 100,50",
        "/" to "100,0 0,100",
        "x" to "0,0 100,100",
        "∫" to "90,0 50,0 40,35 60,65 50,95 0,100",
        "∞" to "50,50 20,15 0,50 20,85 50,50 80,15 100,50 80,85 50,50"
    ).map { (s, raw) -> s to raw.split(' ').map { token ->
        val (x,y) = token.split(',').map(String::toFloat); PointF(x,y)
    } }

    fun recognize(strokes: List<List<PointF>>): Guess? {
        // Explicitly limit to single-stroke gestures. Multi-stroke math must await a proper model.
        if (strokes.size != 1 || strokes[0].size < 3) return null
        val points = normalize(resample(strokes[0], 64))
        val best = templates.map { (symbol, shape) ->
            val reference = normalize(resample(shape, 64))
            val distance = points.indices.sumOf { i ->
                hypot((points[i].x-reference[i].x).toDouble(), (points[i].y-reference[i].y).toDouble())
            }.toFloat() / points.size
            Guess(symbol, (1f - distance / 0.65f).coerceIn(0f, 1f))
        }.maxByOrNull { it.score }
        return best?.takeIf { it.score >= 0.72f }
    }

    private fun normalize(points: List<PointF>): List<PointF> {
        val minX = points.minOf { it.x }; val minY = points.minOf { it.y }
        val w = (points.maxOf { it.x }-minX).coerceAtLeast(1f)
        val h = (points.maxOf { it.y }-minY).coerceAtLeast(1f)
        return points.map { PointF((it.x-minX)/w, (it.y-minY)/h) }
    }

    private fun resample(points: List<PointF>, count: Int): List<PointF> {
        val cumulative = mutableListOf(0f)
        for (i in 1 until points.size) cumulative.add(cumulative.last() + hypot(points[i].x-points[i-1].x, points[i].y-points[i-1].y))
        val total = cumulative.last()
        if (total < 0.001f) return List(count) { PointF(points.first().x, points.first().y) }
        var segment = 1
        return (0 until count).map { index ->
            val target = total * index / (count-1)
            while (segment < cumulative.lastIndex && cumulative[segment] < target) segment++
            val span = (cumulative[segment]-cumulative[segment-1]).coerceAtLeast(0.0001f)
            val t = ((target-cumulative[segment-1])/span).coerceIn(0f,1f)
            PointF(points[segment-1].x + t*(points[segment].x-points[segment-1].x), points[segment-1].y + t*(points[segment].y-points[segment-1].y))
        }
    }
}
