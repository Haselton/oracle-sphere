package com.haseltonmedia.oraclesphere

import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import kotlin.math.*
import java.text.SimpleDateFormat
import java.util.*

class OracleView(context: Context): View(context), Choreographer.FrameCallback {
    var mu=1.15f; var rho=1.0f; var dieMass=1.0f; var hapticGain=1.0f
    val history = mutableListOf<String>()
    private val answers = arrayOf("IT FAVORS YOU","THE PATH IS OPEN","PROCEED","LIKELY","THE MIST HAS NOT LIFTED","ASK WHEN THE HOUR TURNS","TWO TRUTHS CONTEND","NOT YET WRITTEN","TURN AWAY","THE DOOR STAYS SHUT","UNLIKELY","LET IT REST")
    private val phi=(1f+sqrt(5f))/2f
    private val normals=arrayOf(
        floatArrayOf(0f,1f,phi),floatArrayOf(0f,-1f,phi),floatArrayOf(0f,1f,-phi),floatArrayOf(0f,-1f,-phi),
        floatArrayOf(1f,phi,0f),floatArrayOf(-1f,phi,0f),floatArrayOf(1f,-phi,0f),floatArrayOf(-1f,-phi,0f),
        floatArrayOf(phi,0f,1f),floatArrayOf(phi,0f,-1f),floatArrayOf(-phi,0f,1f),floatArrayOf(-phi,0f,-1f)
    ).map { v-> val l=sqrt(v.sumOf{(it*it).toDouble()}).toFloat(); floatArrayOf(v[0]/l,v[1]/l,v[2]/l) }
    private val particles=List(220){ P(Math.random().toFloat(),Math.random().toFloat(),Math.random().toFloat(),Math.random().toFloat()) }
    private val vertices: List<FloatArray>
    private val faces: List<IntArray>
    private data class P(var a:Float,var r:Float,var z:Float,var phase:Float)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val thin=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f}
    private var ax=.18f; private var ay=-.36f; private var az=.08f
    private var shellRollX=0f; private var shellRollY=0f; private var galaxyAngle=0f
    private var wx=0f; private var wy=0f; private var wz=0f
    private var fluidX=0f; private var fluidY=0f; private var fluidZ=0f
    private var shellX=0f; private var shellY=0f; private var shellZ=0f
    private var slosh=0f; private var sloshPhase=0f; private var impact=0f
    private var lastNs=0L; private var lastTouchX=0f; private var lastTouchY=0f; private var downNs=0L
    private var committed=-1; private var stillTime=0f; private var inputAge=99f; private var settleGlow=0f
    private val vibrator = if(Build.VERSION.SDK_INT>=31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
    private var hapticClock=0f
    private var dragging=false; private var dragDistance=0f
    private var sphereCx=0f; private var sphereCy=0f; private var sphereRadius=1f

    init {
        isFocusable=true
        val q=1f/phi
        vertices=buildList {
            for(x in listOf(-1f,1f))for(y in listOf(-1f,1f))for(z in listOf(-1f,1f))add(floatArrayOf(x,y,z))
            for(a in listOf(-q,q))for(b in listOf(-phi,phi)){add(floatArrayOf(0f,a,b));add(floatArrayOf(a,b,0f));add(floatArrayOf(b,0f,a))}
        }
        faces=normals.map { n ->
            val chosen=vertices.indices.sortedByDescending { dot(vertices[it],n) }.take(5)
            val center=floatArrayOf(chosen.map{vertices[it][0]}.average().toFloat(),chosen.map{vertices[it][1]}.average().toFloat(),chosen.map{vertices[it][2]}.average().toFloat())
            val ref=if(abs(n[2])<.8f)floatArrayOf(0f,0f,1f)else floatArrayOf(0f,1f,0f)
            val ux=normalize(cross(ref,n));val uy=cross(n,ux)
            chosen.sortedBy { atan2(dot(sub(vertices[it],center),uy),dot(sub(vertices[it],center),ux)) }.toIntArray()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
    private fun dot(a:FloatArray,b:FloatArray)=a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
    private fun sub(a:FloatArray,b:FloatArray)=floatArrayOf(a[0]-b[0],a[1]-b[1],a[2]-b[2])
    private fun cross(a:FloatArray,b:FloatArray)=floatArrayOf(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
    private fun normalize(a:FloatArray):FloatArray{val l=sqrt(dot(a,a));return floatArrayOf(a[0]/l,a[1]/l,a[2]/l)}
    fun resetTuning(){mu=1.15f;rho=1f;dieMass=1f;hapticGain=1f}
    fun imuImpulse(x:Float,y:Float,z:Float,strength:Float){ excite(-y*.055f,x*.055f,z*.025f,(strength/12f).coerceIn(.18f,1.25f)) }
    fun kick(strength:Float){ excite((Math.random()-.5).toFloat()*strength,(Math.random()-.5).toFloat()*strength,(Math.random()-.5).toFloat()*strength,strength) }
    private fun excite(ix:Float,iy:Float,iz:Float,s:Float){
        val wasQuiet=inputAge>.28f
        shellX+=ix*18f; shellY+=iy*18f; shellZ+=iz*13f; slosh=(slosh+s*1.25f).coerceAtMost(2.8f); inputAge=0f; committed=-1; stillTime=0f
        if(wasQuiet && s>.20f) pulse((40+18*s).toLong(),(35+42*s*hapticGain).toInt().coerceIn(1,135))
    }
    private fun pulse(ms:Long,amp:Int){ if(hapticGain<=0f)return; if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(ms,amp)) else @Suppress("DEPRECATION") vibrator.vibrate(ms) }

    override fun doFrame(now:Long){
        if(lastNs==0L)lastNs=now
        val dt=((now-lastNs)/1e9f).coerceIn(.001f,.033f); lastNs=now; step(dt); invalidate(); Choreographer.getInstance().postFrameCallback(this)
    }
    private fun step(dt:Float){
        inputAge+=dt
        val tau=(1.28f*rho/mu).coerceIn(.42f,3.2f)
        val follow=1f-exp(-dt/(tau*.22f))
        fluidX+=(shellX-fluidX)*follow; fluidY+=(shellY-fluidY)*follow; fluidZ+=(shellZ-fluidZ)*follow
        shellX*=exp(-dt*9f); shellY*=exp(-dt*9f); shellZ*=exp(-dt*9f)
        val drag=exp(-dt*(2.55f/tau)/dieMass.coerceAtLeast(.3f))
        wx=(wx+(fluidX-wx)*dt*2.1f/dieMass)*drag; wy=(wy+(fluidY-wy)*dt*2.1f/dieMass)*drag; wz=(wz+(fluidZ-wz)*dt*2.1f/dieMass)*drag
        ax+=wx*dt; ay+=wy*dt; az+=wz*dt
        fluidX*=exp(-dt/tau); fluidY*=exp(-dt/tau); fluidZ*=exp(-dt/tau)
        val fluidSpeed=sqrt(fluidX*fluidX+fluidY*fluidY+fluidZ*fluidZ)
        sloshPhase+=dt*(.15f+fluidSpeed*1.8f+slosh*1.3f)
        galaxyAngle+=dt*(fluidZ*.42f+(fluidX-fluidY)*.08f)
        shellRollX+=shellX*dt*.26f;shellRollY+=shellY*dt*.26f
        slosh*=exp(-dt/tau); impact*=exp(-dt*12f); settleGlow*=exp(-dt*1.7f)
        val energy=sqrt(wx*wx+wy*wy+wz*wz)+sqrt(fluidX*fluidX+fluidY*fluidY+fluidZ*fluidZ)+slosh*.32f
        if(energy<.105f && inputAge>.45f){ stillTime+=dt; creepToFace(dt); if(stillTime>.38f && committed<0)commitFace() } else stillTime=0f
        hapticClock+=dt
        if(inputAge>.16f && energy>.30f && hapticClock>.16f){
            val diss=(mu*(abs(fluidX-wx)+abs(fluidY-wy)+abs(fluidZ-wz))*.10f + slosh*.035f)*hapticGain
            if(diss>.075f){pulse(28,(10+diss*58).toInt().coerceIn(8,58));hapticClock=0f}
        }
    }
    private fun creepToFace(dt:Float){
        val i=facingFace(); val n=rotate(normals[i]); val gain=(1f-exp(-dt*2.8f))*0.55f
        // Small axis-angle correction aligns the physical face normal to the viewing axis.
        ax += n[1]*gain; ay -= n[0]*gain
        wx*=.86f;wy*=.86f;wz*=.82f
    }
    private fun commitFace(){
        committed=facingFace(); settleGlow=1f; pulse(34,(82*hapticGain).toInt().coerceIn(1,180))
        val stamp=SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date())
        history.add(0,"$stamp  —  ${answers[committed].lowercase().replaceFirstChar{it.uppercase()}}")
        while(history.size>8)history.removeLast()
    }
    private fun rotate(v:FloatArray):FloatArray{
        val cx=cos(ax);val sx=sin(ax);val cy=cos(ay);val sy=sin(ay);val cz=cos(az);val sz=sin(az)
        val x1=v[0];val y1=v[1]*cx-v[2]*sx;val z1=v[1]*sx+v[2]*cx
        val x2=x1*cy+z1*sy;val y2=y1;val z2=-x1*sy+z1*cy
        return floatArrayOf(x2*cz-y2*sz,x2*sz+y2*cz,z2)
    }
    private fun facingFace():Int { var best=0;var z=-9f; normals.forEachIndexed{i,n->val q=rotate(n)[2];if(q>z){z=q;best=i}};return best }

    override fun onDraw(c:Canvas){
        super.onDraw(c); val w=width.toFloat();val h=height.toFloat();val cx=w/2;val cy=h*.47f;val radius=min(w*.43f,h*.33f);sphereCx=cx;sphereCy=cy;sphereRadius=radius
        paint.shader=LinearGradient(0f,0f,0f,h,Color.rgb(3,10,16),Color.rgb(1,3,7),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null
        // cinematic shafts
        paint.shader=LinearGradient(cx,0f,cx,cy,0x443CB7B0,0x00104042,Shader.TileMode.CLAMP); val beam=Path().apply{moveTo(cx-w*.17f,0f);lineTo(cx+radius*.45f,cy);lineTo(cx-radius*.45f,cy);close()};c.drawPath(beam,paint);paint.shader=null
        drawTitle(c,w)
        // Ball-return cradle: rear trough and side rollers suspend the sphere.
        paint.shader=LinearGradient(cx-radius,cy+radius*.45f,cx+radius,cy+radius*1.2f,0xFF3B4042.toInt(),0xFF050708.toInt(),Shader.TileMode.CLAMP)
        c.drawRoundRect(cx-radius*.96f,cy+radius*.67f,cx+radius*.96f,cy+radius*1.25f,radius*.34f,radius*.34f,paint);paint.shader=null
        thin.color=0xFF353B3D.toInt();thin.strokeWidth=radius*.12f;c.drawArc(cx-radius*1.05f,cy-radius*.16f,cx-radius*.43f,cy+radius*1.05f,70f,118f,false,thin);c.drawArc(cx+radius*.43f,cy-radius*.16f,cx+radius*1.05f,cy+radius*1.05f,-8f,118f,false,thin)
        c.save();c.clipPath(Path().apply{addCircle(cx,cy,radius,Path.Direction.CW)})
        paint.shader=RadialGradient(cx-radius*.28f,cy-radius*.32f,radius*1.45f,intArrayOf(0xFF12343C.toInt(),0xFF06141D.toInt(),0xFF010407.toInt()),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP);c.drawCircle(cx,cy,radius,paint);paint.shader=null
        drawNebula(c,cx,cy,radius); drawParticles(c,cx,cy,radius); drawDie(c,cx,cy,radius);drawShellRunes(c,cx,cy,radius)
        c.restore()
        // glass reflections and sapphire edge
        thin.strokeWidth=radius*.018f;thin.shader=SweepGradient(cx,cy,intArrayOf(0xFF133944.toInt(),0xAAE4C77D.toInt(),0x333DCFD0,0xFF081219.toInt(),0xFF133944.toInt()),null);c.drawCircle(cx,cy,radius*.985f,thin);thin.shader=null
        paint.shader=LinearGradient(cx-radius*.6f,cy-radius,cx,cy-radius*.1f,0xAAFFFFFF.toInt(),0x00FFFFFF,Shader.TileMode.CLAMP);c.drawArc(cx-radius*.75f,cy-radius*.86f,cx+radius*.35f,cy+radius*.15f,198f,77f,false,paint);paint.shader=null
        paint.shader=LinearGradient(cx,cy+radius*.72f,cx,cy+radius*1.21f,0xCC15191A.toInt(),0xFF020304.toInt(),Shader.TileMode.CLAMP);c.drawArc(cx-radius*.93f,cy+radius*.60f,cx+radius*.93f,cy+radius*1.24f,180f,180f,true,paint);paint.shader=null
        if(committed<0){paint.color=0xB8B7CBCB.toInt();paint.textSize=13f*resources.displayMetrics.scaledDensity;paint.textAlign=Paint.Align.CENTER;paint.letterSpacing=.15f;c.drawText(if(inputAge<2f)"THE VEIL IS MOVING" else "SHAKE · FLICK · TAP",cx,cy+radius*1.47f,paint);paint.letterSpacing=0f}
    }
    private fun drawTitle(c:Canvas,w:Float){paint.textAlign=Paint.Align.CENTER;paint.color=0xFFE5C77B.toInt();paint.typeface=Typeface.create("serif",Typeface.NORMAL);paint.textSize=22f*resources.displayMetrics.scaledDensity;paint.letterSpacing=.22f;c.drawText("ORACLE SPHERE",w/2,48f*resources.displayMetrics.density,paint);paint.letterSpacing=0f;paint.typeface=null}
    private fun drawNebula(c:Canvas,cx:Float,cy:Float,r:Float){
        val lag=galaxyAngle+atan2(fluidY,fluidX)*.18f
        // Three participating-media glows give the fluid luminous volume.
        repeat(3){i->
            val a=lag+i*2.09f;val gx=cx+cos(a)*r*(.18f+i*.06f);val gy=cy+sin(a)*r*(.13f+i*.035f)
            paint.shader=RadialGradient(gx,gy,r*(.38f+i*.05f),if(i==1)0x55F0B65D else 0x5532D5D0,0x00101820,Shader.TileMode.CLAMP);c.drawCircle(gx,gy,r*.52f,paint);paint.shader=null
        }
        // Advected filament ribbons: geometry, brightness and phase share fluid state.
        repeat(24){i->
            val path=Path();val arm=i%5;val base=lag+arm*(2f*PI.toFloat()/5f)+(i/5)*.055f;for(j in 0..56){val t=j/56f;val a=base+t*(3.7f+slosh*.18f)+sin(sloshPhase+t*7f+i)*(.025f+slosh*.025f);val rr=r*(.055f+t*.70f+sin(t*13f+i)*.018f);val x=cx+cos(a)*rr;val y=cy+sin(a)*rr*.61f;if(j==0)path.moveTo(x,y)else path.lineTo(x,y)}
            thin.strokeWidth=r*(if(i<5).020f else .006f);thin.color=if(arm==0||arm==3)0xAA42DED6.toInt() else 0x88E5B75D.toInt();thin.alpha=if(i<5)175 else 78;thin.maskFilter=BlurMaskFilter(r*(if(i<5).018f else .008f),BlurMaskFilter.Blur.NORMAL);c.drawPath(path,thin)
        }
        thin.maskFilter=null;thin.alpha=255
    }
    private fun drawShellRunes(c:Canvas,cx:Float,cy:Float,r:Float){
        c.save();c.rotate((shellRollY+shellRollX*.35f)*57.3f,cx,cy);thin.color=0x337DD2CA;thin.strokeWidth=1.2f
        repeat(14){i->val a=i*2f*PI.toFloat()/14f;val rr=r*.84f;val x=cx+cos(a)*rr;val y=cy+sin(a)*rr;val s=r*.025f;c.drawLine(x-s,y,x+s,y,thin);c.drawLine(x,y-s,x,y+s,thin)}
        c.drawArc(cx-r*.78f,cy-r*.30f,cx+r*.78f,cy+r*.30f,12f,128f,false,thin);c.drawArc(cx-r*.72f,cy-r*.48f,cx+r*.72f,cy+r*.48f,192f,108f,false,thin);c.restore()
    }
    private fun drawParticles(c:Canvas,cx:Float,cy:Float,r:Float){
        val speed=.002f+slosh*.006f
        particles.forEachIndexed{i,p->p.a+=speed*(if(i%3==0)-1 else 1);val rr=r*(.15f+p.r*.77f);val x=cx+cos(p.a*6.283f+sloshPhase*.08f)*rr;val y=cy+sin(p.a*6.283f+sloshPhase*.11f)*rr*.72f;val edge=(1f-(hypot(x-cx,y-cy)/r)).coerceIn(0f,1f);paint.color=if(i%4==0)0xFFE5BB66.toInt() else 0xFF43BFC0.toInt();paint.alpha=(35+edge*155).toInt();c.drawCircle(x,y,1f+p.phase*2.2f,paint)};paint.alpha=255
    }
    private fun drawDie(c:Canvas,cx:Float,cy:Float,r:Float){
        val face=if(committed>=0)committed else facingFace();val driftX=sin(sloshPhase)*slosh*r*.012f;val driftY=cos(sloshPhase*.8f)*slosh*r*.010f
        val scale=r*.235f
        val projected=vertices.map{v->val p=rotate(v);floatArrayOf(cx+p[0]*scale+driftX,cy-p[1]*scale+driftY,p[2])}
        // Painter-sort all physical faces. Side faces remain visible as the body rotates.
        val order=faces.indices.sortedBy{fi->faces[fi].map{projected[it][2]}.average()}
        order.forEach{fi->
            val fn=rotate(normals[fi]);if(fn[2]<-.28f)return@forEach
            val path=Path();faces[fi].forEachIndexed{i,vi->val p=projected[vi];if(i==0)path.moveTo(p[0],p[1])else path.lineTo(p[0],p[1])};path.close()
            val light=((fn[2]+1f)*.5f).coerceIn(0f,1f);paint.color=Color.rgb((4+light*10).toInt(),(18+light*35).toInt(),(21+light*38).toInt());paint.alpha=220;c.drawPath(path,paint);paint.alpha=255
            thin.color=if(fi==face)0xFFF3D077.toInt() else 0xAA9E7A35.toInt();thin.strokeWidth=if(fi==face)3.8f else 1.5f;thin.maskFilter=if(fi==face)BlurMaskFilter(3f+settleGlow*9f,BlurMaskFilter.Blur.NORMAL)else null;c.drawPath(path,thin);thin.maskFilter=null
            if(fi==face){
                // Filigree inset follows the actual settling face.
                val centerX=faces[fi].map{projected[it][0]}.average().toFloat();val centerY=faces[fi].map{projected[it][1]}.average().toFloat();val inner=Path();faces[fi].forEachIndexed{i,vi->val p=projected[vi];val x=centerX+(p[0]-centerX)*.82f;val y=centerY+(p[1]-centerY)*.82f;if(i==0)inner.moveTo(x,y)else inner.lineTo(x,y)};inner.close();thin.strokeWidth=1f;thin.color=0xBBD8B667.toInt();c.drawPath(inner,thin)
                paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD);paint.color=0xFFFFE5A1.toInt();paint.setShadowLayer(12f,0f,0f,0xFFE2A94D.toInt());val lines=wrap(answers[face],18);paint.textSize=if(lines.size>1)r*.040f else r*.052f;val lh=paint.textSize*1.05f;lines.forEachIndexed{i,s->c.drawText(s,centerX,centerY-(lines.size-1)*lh/2+i*lh-paint.ascent()/2-paint.descent()/2,paint)};paint.clearShadowLayer();paint.typeface=null
            }
        }
    }
    private fun wrap(s:String,max:Int):List<String>{val words=s.split(" ");val out=mutableListOf<String>();var line="";for(w in words){if(line.isNotEmpty()&&line.length+1+w.length>max){out+=line;line=w}else line=if(line.isEmpty())w else "$line $w"};if(line.isNotEmpty())out+=line;return out}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{lastTouchX=e.x;lastTouchY=e.y;downNs=System.nanoTime();dragDistance=0f;dragging=hypot(e.x-sphereCx,e.y-sphereCy)<sphereRadius;return dragging}
            MotionEvent.ACTION_MOVE->{if(!dragging)return false;val dx=e.x-lastTouchX;val dy=e.y-lastTouchY;val d=hypot(dx,dy);if(d>2f){dragDistance+=d;shellRollX+=dy/sphereRadius;shellRollY-=dx/sphereRadius;shellX=dy/sphereRadius*28f;shellY=-dx/sphereRadius*28f;shellZ=(dx-dy)/sphereRadius*7f;slosh=(slosh+d/sphereRadius*.34f).coerceAtMost(2.8f);inputAge=0f;committed=-1;stillTime=0f;lastTouchX=e.x;lastTouchY=e.y};return true}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(!dragging)return false;dragging=false;if(dragDistance<12f&&(System.nanoTime()-downNs)<220_000_000L)kick(.28f);return true}
        };return super.onTouchEvent(e)
    }
}
