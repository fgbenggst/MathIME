package com.mathime.keyboard

import android.graphics.PointF
import kotlin.math.*

/** Offline stroke-geometry recognizer; abstains instead of guessing when ambiguous.
 * Scores are heuristic shape-match scores, not calibrated probabilities.
 */
class SingleGlyphRecognizer {
    data class Prediction(val latex:String, val confidence:Float, val explanation:String)
    data class Analysis(val prediction:Prediction?, val diagnostic:String)
    private data class V(val x:Float,val y:Float)
    private fun dist(a:V,b:V)=hypot(a.x-b.x,a.y-b.y)
    private fun cross(a:V,b:V)=a.x*b.y-a.y*b.x
    private fun minus(a:V,b:V)=V(a.x-b.x,a.y-b.y)
    private fun dot(a:V,b:V)=a.x*b.x+a.y*b.y
    private fun points(s:List<PointF>)=s.map{V(it.x,it.y)}
    private fun length(s:List<V>)=(1 until s.size).sumOf{dist(s[it-1],s[it]).toDouble()}.toFloat()
    private fun resample(s:List<V>, n:Int=32):List<V>{
        val cumulative=FloatArray(s.size)
        for(i in 1 until s.size)cumulative[i]=cumulative[i-1]+dist(s[i-1],s[i])
        if(cumulative.last()<1e-4f)return List(n){s.first()}
        var j=1
        return (0 until n).map{i->
            val d=cumulative.last()*i/(n-1)
            while(j<s.lastIndex && cumulative[j]<d)j++
            val t=((d-cumulative[j-1])/(cumulative[j]-cumulative[j-1]).coerceAtLeast(1e-5f)).coerceIn(0f,1f)
            V(s[j-1].x+(s[j].x-s[j-1].x)*t,s[j-1].y+(s[j].y-s[j-1].y)*t)
        }
    }
    private fun segmentIntersection(a:V,b:V,c:V,d:V):V?{
        val r=minus(b,a);val s=minus(d,c)
        val den=cross(r,s)
        if(abs(den)<1e-4f)return null
        val t=cross(minus(c,a),s)/den
        val u=cross(minus(c,a),r)/den
        if(t !in 0f..1f || u !in 0f..1f)return null
        return V(a.x+r.x*t,a.y+r.y*t)
    }
    private fun polyIntersection(a:List<V>,b:List<V>):V?{
        val hits=mutableListOf<V>()
        for(i in 1 until a.size)for(j in 1 until b.size)
            segmentIntersection(a[i-1],a[i],b[j-1],b[j])?.let{hits.add(it)}
        return hits.firstOrNull()
    }
    private fun fitDirection(s:List<V>):V {
        // End-to-end direction is robust to smooth handwriting curves.
        val a=s.first();val b=s.last();val l=dist(a,b).coerceAtLeast(1f)
        return V((b.x-a.x)/l,(b.y-a.y)/l)
    }
    fun predict(input:List<List<PointF>>)=analyze(input).prediction

    fun analyze(input:List<List<PointF>>):Analysis {
        val lines=input.map{points(it)}.filter{it.size>=2 && length(it)>8f}
        val log=StringBuilder("SingleGlyph geometry: strokes=${lines.size}; raw=${input.size}")
        fun abstain(reason:String):Analysis{
            log.append("; abstain=").append(reason)
            return Analysis(null,log.toString())
        }
        if(lines.isEmpty() || lines.size>2)return abstain("requires 1 or 2 meaningful strokes")
        val all=lines.flatten()
        val minX=all.minOf{it.x};val maxX=all.maxOf{it.x}
        val minY=all.minOf{it.y};val maxY=all.maxOf{it.y}
        val w=maxX-minX;val h=maxY-minY
        if(w<12f || h<12f)return abstain("too small")
        val ratio=w/h
        log.append("; box=${w.toInt()}x${h.toInt()} ratio=%.2f".format(java.util.Locale.US,ratio))
        if(ratio !in .3f..3f)return abstain("extreme aspect ratio")
        if(lines.size==2){
            val a=resample(lines[0]);val b=resample(lines[1])
            val center=polyIntersection(a,b)?:return abstain("no stroke intersection")
            val nx=(center.x-minX)/w;val ny=(center.y-minY)/h
            val da=fitDirection(a);val db=fitDirection(b)
            val angle=acos(abs(dot(da,db)).coerceIn(0f,1f))*180f/PI.toFloat()
            log.append("; intersection=(%.2f,%.2f) angle=%.1f".format(java.util.Locale.US,nx,ny,angle))
            if(nx !in .18f.. .82f || ny !in .16f.. .84f)return abstain("intersection outside central region")
            if(angle<32f)return abstain("strokes too parallel")
            val aHoriz=abs(da.x)>.82f;val bHoriz=abs(db.x)>.82f
            val aVert=abs(da.y)>.82f;val bVert=abs(db.y)>.82f
            val plus=(aHoriz&&bVert)||(bHoriz&&aVert)
            val diagonals=abs(da.x)>.28f&&abs(da.y)>.28f&&abs(db.x)>.28f&&abs(db.y)>.28f
            val result=when{
                plus -> Prediction("+",.85f,"crossing near-horizontal and near-vertical strokes")
                diagonals && ratio in .45f..2.1f -> Prediction("x",.83f,"two intersecting diagonal curved strokes")
                else -> return abstain("crossing geometry ambiguous (+, t, x)")
            }
            log.append("; match=${result.latex} score=${result.confidence}")
            return Analysis(result,log.toString())
        }
        // Digit 2: use shape geometry, independent of pen direction. 
        // A top arc travels toward the right, then descends to a bottom-left
        // corner, followed by a rightward tail. Requires a genuine turn.
        val p=resample(lines[0],48)
        fun norm(v:V)=V((v.x-minX)/w,(v.y-minY)/h)
        val f=p.map(::norm)
        val candidates=listOf(f,f.reversed())
        for(q in candidates){
            val first=q.first();val last=q.last()
            if(first.y>.45f || first.x>.62f || last.x<.67f || last.y<.62f)continue
            val upper=q.take(24)
            val topRight=upper.maxOf{it.x}
            val lower=q.drop(20)
            val bottomLeft=lower.minOf{it.x}
            val lowest=lower.maxOf{it.y}
            val turnIndex=q.indices.filter{it in 17..43}.minByOrNull{q[it].x+abs(q[it].y-.85f)*.4f}?:continue
            val turn=q[turnIndex]
            log.append("; digit2 arcRight=%.2f lowerLeft=%.2f bottom=%.2f turn=(%.2f,%.2f)".format(java.util.Locale.US,topRight,bottomLeft,lowest,turn.x,turn.y))
            if(topRight>.62f && lowest>.76f && turn.x<.38f && turn.y>.62f &&
               last.x-turn.x>.35f && ratio in .45f..1.8f){
                val result=Prediction("2",.79f,"top arc, lower-left turn, rightward baseline")
                return Analysis(result,log.append("; match=2 score=.79").toString())
            }
        }
        return abstain("no reliable single-stroke digit shape")
    }
}
