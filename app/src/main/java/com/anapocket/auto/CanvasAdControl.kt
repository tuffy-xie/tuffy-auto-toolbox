package com.anapocket.auto

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}
data class CanvasControl(val bounds: PixelRect, val kind: String)

/** Finds white triangle/bar geometry or X. Triangle/bar is NOT trusted skip semantics; CanvasControlPolicy authorizes actions. */
object CanvasAdControl {
    private data class Component(val b: PixelRect, val count: Int)
    private fun white(p: Int): Boolean {
        val r=p ushr 16 and 255; val g=p ushr 8 and 255; val b=p and 255
        return min(r,min(g,b)) >= 210 && max(r,max(g,b))-min(r,min(g,b)) <= 30
    }
    fun find(width: Int, height: Int, pixels: IntArray): CanvasControl? {
        if (width <= 0 || height <= 0 || pixels.size != width*height) return null
        val seen=BooleanArray(pixels.size); val queue=IntArray(pixels.size); val components=ArrayList<Component>()
        for (i in pixels.indices) {
            if (seen[i] || !white(pixels[i])) continue
            var read=0;var written=1;queue[0]=i;seen[i]=true
            var x0=i%width;var y0=i/width;var x1=x0;var y1=y0
            while(read<written) {
                val at=queue[read++];val x=at%width;val y=at/width
                x0=min(x0,x);x1=max(x1,x);y0=min(y0,y);y1=max(y1,y)
                for(dy in -1..1)for(dx in -1..1) {
                    val nx=x+dx;val ny=y+dy
                    if(nx !in 0 until width || ny !in 0 until height)continue
                    val next=ny*width+nx
                    if(!seen[next] && white(pixels[next])){seen[next]=true;queue[written++]=next}
                }
            }
            if(x1-x0+1 in 2..56 && y1-y0+1 in 6..56) components.add(Component(PixelRect(x0,y0,x1+1,y1+1),written))
        }
        fun darkPlate(b: PixelRect, pad: Int): Boolean {
            val l=max(0,b.left-pad);val t=max(0,b.top-pad);val r=min(width,b.right+pad);val d=min(height,b.bottom+pad)
            var dark=0;var count=0
            for(y in t until d)for(x in l until r){val p=pixels[y*width+x];count++;if(max(p ushr 16 and 255,max(p ushr 8 and 255,p and 255))<90)dark++}
            return count>0 && dark>count*.7
        }
        fun triangle(c: Component): Boolean {
            val b=c.b
            if(b.width !in 5..36 || b.height !in 8..42 || b.width.toDouble()/b.height !in .4..1.2 || c.count.toDouble()/(b.width*b.height) !in .35.. .8)return false
            var fit=0
            for(y in b.top until b.bottom)for(x in b.left until b.right)if(white(pixels[y*width+x])){
                val nx=(x-b.left).toDouble()/max(1,b.width-1);val ny=(y-b.top).toDouble()/max(1,b.height-1)
                if(nx <= 1-abs(2*ny-1)+.2)fit++
            }
            return fit>=c.count*.88
        }
        val matches=ArrayList<CanvasControl>()
        for(c in components){
            val b=c.b
            if(b.width in 10..48 && b.height in 10..48 && b.width.toDouble()/b.height in .75..1.33 && darkPlate(b,max(b.width,b.height)/2)){
                val pad=max(b.width,b.height)/2
                val crop=PixelRect(max(0,b.left-pad),max(0,b.top-pad),min(width,b.right+pad),min(height,b.bottom+pad))
                val argb=IntArray(crop.width*crop.height){j->pixels[(crop.top+j/crop.width)*width+crop.left+j%crop.width]}
                if(AdCloseIcon.isWhiteCross(crop.width,crop.height,argb))matches.add(CanvasControl(b,"CLOSE"))
            }
            if(!triangle(c))continue
            for(bar in components){
                val a=bar.b
                if(a.left<=b.right || a.left-b.right>b.height*.8 || a.width.toDouble()/a.height>.3 ||
                    a.height.toDouble()/b.height !in .75..1.6 || bar.count.toDouble()/(a.width*a.height)<.6 ||
                    abs((a.top+a.bottom)-(b.top+b.bottom))>max(a.height,b.height)*.5)continue
                val combined=PixelRect(b.left,min(b.top,a.top),a.right,max(b.bottom,a.bottom))
                if(combined.width.toDouble()/combined.height in .75..1.8 && darkPlate(combined,max(b.height,a.height)))matches.add(CanvasControl(combined,"SKIP"))
            }
        }
        return matches.distinct().singleOrNull()
    }
}

/** Only an unambiguous cross may be tapped; the triangle/bar is an untrusted visual hint. */
object CanvasControlPolicy {
    fun canTap(kind: String): Boolean = kind == "CLOSE"
}
