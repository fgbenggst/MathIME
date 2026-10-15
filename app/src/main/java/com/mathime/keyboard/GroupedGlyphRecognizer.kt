package com.mathime.keyboard

import android.graphics.PointF
import kotlin.math.*

/**
 * Conservative offline horizontal stroke grouping.
 * Only promotes a result when every group has an accepted glyph classification.
 * Does not claim to recognize arbitrary handwritten equations.
 */
class GroupedGlyphRecognizer(private val single: SingleGlyphRecognizer) {
    data class Result(val latex: String?, val diagnostic: String)
    private data class Bounds(val l:Float,val t:Float,val r:Float,val b:Float) {
        val w get()=(r-l).coerceAtLeast(1f)
        val h get()=(b-t).coerceAtLeast(1f)
        val cx get()=(l+r)/2
        val cy get()=(t+b)/2
    }
    private fun bounds(s:List<PointF>):Bounds = Bounds(
        s.minOf{it.x},s.minOf{it.y},s.maxOf{it.x},s.maxOf{it.y})
    private fun intersect(a:Bounds,b:Bounds):Boolean =
        min(a.r,b.r)>=max(a.l,b.l) && min(a.b,b.b)>=max(a.t,b.t)

    fun analyze(input:List<List<PointF>>):Result {
        val strokes=input.filter{it.size>=3}
        val log=StringBuilder("GroupedGlyph: strokes=${strokes.size}")
        if(strokes.size !in 2..8)return Result(null,log.append("; abstain=stroke count outside 2..8").toString())
        val bb=strokes.map(::bounds)
        val all=strokes.flatten()
        val overall=bounds(all)
        // A long horizontal stroke crossing the middle of a vertically stacked
        // expression is a likely fraction bar. Do not interpret it as a glyph.
        val fractionBar=strokes.indices.any { i ->
            val b=bb[i]
            val a=strokes[i].first()
            val z=strokes[i].last()
            val dx=abs(z.x-a.x)
            val dy=abs(z.y-a.y)
            b.w>overall.w*.60f && b.h<overall.h*.16f &&
                dx>overall.w*.55f && dy<dx*.18f &&
                strokes.indices.any { j -> j!=i && bb[j].cy < b.cy-b.h } &&
                strokes.indices.any { j -> j!=i && bb[j].cy > b.cy+b.h }
        }
        if(fractionBar)return Result(null,log.append("; abstain=fraction bar / vertical layout").toString())
        val parent=IntArray(strokes.size){it}
        fun root(i:Int):Int {
            var x=i
            while(parent[x]!=x)x=parent[x]
            return x
        }
        fun union(a:Int,b:Int){val ra=root(a);val rb=root(b);if(ra!=rb)parent[rb]=ra}
        // Union overlapping ink bounding boxes only when their centers are reasonably close.
        // This keeps distinct symbols from merging due to tiny peripheral overlaps.
        for(i in strokes.indices)for(j in i+1 until strokes.size){
            val a=bb[i];val b=bb[j]
            val overlapX=(min(a.r,b.r)-max(a.l,b.l)).coerceAtLeast(0f)
            val overlapY=(min(a.b,b.b)-max(a.t,b.t)).coerceAtLeast(0f)
            val minWidth=min(a.w,b.w)
            if(intersect(a,b) && overlapX>=minWidth*.12f &&
               overlapY>=min(a.h,b.h)*.12f &&
               abs(a.cx-b.cx)<max(a.w,b.w)*.85f)union(i,j)
        }
        val groups=strokes.indices.groupBy(::root).values
            .map{indices->indices.map{strokes[it]}}
            .sortedBy{group->group.flatten().minOf{it.x}}
        log.append("; groups=").append(groups.map{it.size})
        if(groups.size !in 2..4)return Result(null,log.append("; abstain=not 2..4 spatial groups").toString())
        val glyphs=mutableListOf<String>()
        for((i,group) in groups.withIndex()){
            val shape=single.analyze(group)
            val candidate=shape.prediction ?: classifyThree(group) ?: classifyTwo(group) ?: classifyFive(group)
            log.append("; g").append(i).append("=")
                .append(candidate?.latex ?: "?")
                .append(" [").append(shape.diagnostic).append("]")
            if(candidate==null)return Result(null,log.append("; abstain=unrecognized group").toString())
            glyphs.add(candidate.latex)
        }
        val latex=glyphs.joinToString(" ")
        return Result(latex,log.append("; accepted=").append(latex).toString())
    }

    /**
     * Narrow heuristic for a single-stroke handwritten '3'.
     * Requires two rightward bulges with an inward left turn at mid-height.
     * The checks are intentionally strict to avoid silently rewriting equations.
     */
    private fun normalize(group:List<List<PointF>>):List<PointF>? {
        if(group.size!=1 || group[0].size<12)return null
        val raw=group[0]
        val b=bounds(raw)
        if(b.w<18f || b.h<25f)return null
        return raw.map { PointF((it.x-b.l)/b.w,(it.y-b.t)/b.h) }
    }
    /**
     * Two-shape classifier for a single continuous stroke.  We test the actual
     * pen trajectory (and its reverse) rather than assuming a straight tail.
     * A handwritten 2 travels from its upper-left arc to the upper-right,
     * descends to a lower-left corner, then finishes toward the lower-right.
     * We abstain if the trajectory is too ambiguous.
     */
    private fun classifyTwo(group:List<List<PointF>>):SingleGlyphRecognizer.Prediction? {
        val raw=normalize(group) ?: return null
        for (q in listOf(raw, raw.reversed())) {
            val first=q.first(); val last=q.last()
            if (first.y > .38f || first.x > .65f || last.y < .59f || last.x < .60f) continue
            // Find the lower-left corner in the latter portion of the stroke.
            // Its path position matters: it must precede the final rightward tail.
            val start=(q.size*.35f).toInt()
            val stop=(q.size*.90f).toInt().coerceAtMost(q.lastIndex)
            if (start >= stop) continue
            val cornerIndex=(start..stop).minByOrNull { i ->
                val v=q[i]
                v.x + kotlin.math.abs(v.y-.90f)*.32f
            } ?: continue
            val corner=q[cornerIndex]
            if (corner.x > .43f || corner.y < .58f) continue
            val head=q.subList(0, cornerIndex+1)
            val rightOnHead=head.filter { it.y < .65f }.maxOfOrNull { it.x } ?: 0f
            val tail=q.subList(cornerIndex, q.size)
            val rightward=last.x-corner.x
            val upward=corner.y-last.y
            // The top must bow to the right, the lower turn must be distinct,
            // and the final tail must move right (slight upward curvature allowed).
            if (rightOnHead < .64f || rightOnHead-corner.x < .20f ||
                rightward < .36f || upward < -.17f || upward > .42f) continue
            // Avoid classifying a zig-zag '3' as '2': the lower corner must
            // occur after the upper-right excursion.
            val topRightIndex=(0..cornerIndex).maxByOrNull { q[it].x } ?: continue
            if (topRightIndex >= cornerIndex || cornerIndex < q.size/3) continue
            return SingleGlyphRecognizer.Prediction(
                "2", .77f, "upper-right arc, lower-left turn and rising rightward tail")
        }
        return null
    }
    private fun classifyFive(group:List<List<PointF>>):SingleGlyphRecognizer.Prediction? {
        val p=normalize(group)?:return null
        for(q in listOf(p,p.reversed())){
            val first=q.first();val last=q.last()
            if(first.y>.25f || last.y<.65f)continue
            val top=q.filter{it.y<.23f}
            val mid=q.filter{it.y in .34f.. .65f}
            val bottom=q.filter{it.y>.70f}
            if(top.size<3 || mid.size<3 || bottom.size<3)continue
            val topWidth=top.maxOf{it.x}-top.minOf{it.x}
            val midLeft=mid.minOf{it.x}
            val lowerRight=bottom.maxOf{it.x}
            if(topWidth>.30f && midLeft<.35f && lowerRight>.65f &&
               last.x<.58f && q.any{it.x>.82f && it.y>.50f})
                return SingleGlyphRecognizer.Prediction("5",.70f,"top stroke, left spine and lower right bulge")
        }
        return null
    }

    private fun classifyThree(group:List<List<PointF>>):SingleGlyphRecognizer.Prediction? {
        if(group.size!=1)return null
        val raw=group[0]
        val minX=raw.minOf{it.x};val maxX=raw.maxOf{it.x}
        val minY=raw.minOf{it.y};val maxY=raw.maxOf{it.y}
        val w=maxX-minX;val h=maxY-minY
        if(w<18f||h<25f || w/h !in .28f..1.45f)return null
        // Use pen direction-independent ordering.
        val forward=raw.map{PointF((it.x-minX)/w,(it.y-minY)/h)}
        val sequences=listOf(forward,forward.reversed())
        for(s in sequences){
            val first=s.first();val last=s.last()
            if(first.y>.28f || last.y<.70f)continue
            val upper=s.filter{it.y in .10f.. .42f}
            val middle=s.filter{it.y in .42f.. .68f}
            val lower=s.filter{it.y in .68f.. .92f}
            if(upper.size<3 || middle.size<3 || lower.size<3)continue
            val upperRight=upper.maxOf{it.x}
            val middleLeft=middle.minOf{it.x}
            val lowerRight=lower.maxOf{it.x}
            val lowerEndLeft=last.x<.55f
            if(upperRight>.72f && middleLeft<.56f &&
               lowerRight>.60f && lowerEndLeft &&
               upperRight-middleLeft>.18f && lowerRight-middleLeft>.15f)
                return SingleGlyphRecognizer.Prediction("3",.72f,"two bulges with inward middle turn")
        }
        return null
    }
}
