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
    private val particles=List(110){ P(Math.random().toFloat(),Math.random().toFloat(),Math.random().toFloat(),Math.random().toFloat()) }
    private data class P(var a:Float,var r:Float,var z:Float,var phase:Float)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val thin=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=2f}
    private var ax=.18f; private var ay=-.36f; private var az=.08f
    private var wx=0f; private var wy=0f; private var wz=0f
    private var fluidX=0f; private var fluidY=0f; private var fluidZ=0f
    private var shellX=0f; private var shellY=0f; private var shellZ=0f
    private var slosh=0f; private var sloshPhase=0f; private var impact=0f
    private var lastNs=0L; private var lastTouchX=0f; private var lastTouchY=0f; private var downNs=0L
    private var committed=-1; private var stillTime=0f; private var inputAge=99f; private var settleGlow=0f
    private val vibrator = if(Build.VERSION.SDK_INT>=31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
    private var hapticClock=0f

    init { isFocusable=true; Choreographer.getInstance().postFrameCallback(this) }
    fun resetTuning(){mu=1.15f;rho=1f;dieMass=1f;hapticGain=1f}
    fun imuImpulse(x:Float,y:Float,z:Float,strength:Float){ excite(-y*.055f,x*.055f,z*.025f,(strength/12f).coerceIn(.18f,1.25f)) }
    fun kick(strength:Float){ excite((Math.random()-.5).toFloat()*strength,(Math.random()-.5).toFloat()*strength,(Math.random()-.5).toFloat()*strength,strength) }
    private fun excite(ix:Float,iy:Float,iz:Float,s:Float){
        shellX+=ix*13f; shellY+=iy*13f; shellZ+=iz*9f; slosh=(slosh+s*1.2f).coerceAtMost(2.8f); inputAge=0f; committed=-1; stillTime=0f
        pulse((45+25*s).toLong(),(55+70*s*hapticGain).toInt().coerceIn(1,220))
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
        sloshPhase+=dt*(5.2f+slosh); slosh*=exp(-dt/tau); impact*=exp(-dt*12f); settleGlow*=exp(-dt*1.7f)
        val energy=sqrt(wx*wx+wy*wy+wz*wz)+sqrt(fluidX*fluidX+fluidY*fluidY+fluidZ*fluidZ)+slosh*.32f
        if(energy<.105f && inputAge>.45f){ stillTime+=dt; creepToFace(dt); if(stillTime>.38f && committed<0)commitFace() } else stillTime=0f
        hapticClock+=dt
        if(inputAge>.08f && energy>.14f && hapticClock>.095f){
            val diss=(mu*(abs(fluidX-wx)+abs(fluidY-wy)+abs(fluidZ-wz))*.10f + slosh*.035f)*hapticGain
            if(diss>.035f){pulse(35,(18+diss*92).toInt().coerceIn(10,90));hapticClock=0f}
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
        super.onDraw(c); val w=width.toFloat();val h=height.toFloat();val cx=w/2;val cy=h*.47f;val radius=min(w*.43f,h*.33f)
        paint.shader=LinearGradient(0f,0f,0f,h,Color.rgb(3,10,16),Color.rgb(1,3,7),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null
        // cinematic shafts
        paint.shader=LinearGradient(cx,0f,cx,cy,0x443CB7B0,0x00104042,Shader.TileMode.CLAMP); val beam=Path().apply{moveTo(cx-w*.17f,0f);lineTo(cx+radius*.45f,cy);lineTo(cx-radius*.45f,cy);close()};c.drawPath(beam,paint);paint.shader=null
        drawTitle(c,w)
        // matte cradle behind and in front of sphere
        paint.shader=RadialGradient(cx,cy+radius*.87f,radius*.9f,intArrayOf(0xFF34383A.toInt(),0xFF101416.toInt(),0xFF020405.toInt()),floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP)
        c.drawOval(cx-radius*.82f,cy+radius*.64f,cx+radius*.82f,cy+radius*1.27f,paint);paint.shader=null
        c.save();c.clipPath(Path().apply{addCircle(cx,cy,radius,Path.Direction.CW)})
        paint.shader=RadialGradient(cx-radius*.28f,cy-radius*.32f,radius*1.45f,intArrayOf(0xFF12343C.toInt(),0xFF06141D.toInt(),0xFF010407.toInt()),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP);c.drawCircle(cx,cy,radius,paint);paint.shader=null
        drawNebula(c,cx,cy,radius); drawParticles(c,cx,cy,radius); drawDie(c,cx,cy,radius)
        c.restore()
        // glass reflections and sapphire edge
        thin.strokeWidth=radius*.018f;thin.shader=SweepGradient(cx,cy,intArrayOf(0xFF133944.toInt(),0xAAE4C77D.toInt(),0x333DCFD0,0xFF081219.toInt(),0xFF133944.toInt()),null);c.drawCircle(cx,cy,radius*.985f,thin);thin.shader=null
        paint.shader=LinearGradient(cx-radius*.6f,cy-radius,cx,cy-radius*.1f,0xAAFFFFFF.toInt(),0x00FFFFFF,Shader.TileMode.CLAMP);c.drawArc(cx-radius*.75f,cy-radius*.86f,cx+radius*.35f,cy+radius*.15f,198f,77f,false,paint);paint.shader=null
        paint.color=0x66000000;c.drawOval(cx-radius*.82f,cy+radius*.84f,cx+radius*.82f,cy+radius*1.23f,paint)
        if(committed<0){paint.color=0xB8B7CBCB.toInt();paint.textSize=13f*resources.displayMetrics.scaledDensity;paint.textAlign=Paint.Align.CENTER;paint.letterSpacing=.15f;c.drawText(if(inputAge<2f)"THE VEIL IS MOVING" else "SHAKE · FLICK · TAP",cx,cy+radius*1.47f,paint);paint.letterSpacing=0f}
    }
    private fun drawTitle(c:Canvas,w:Float){paint.textAlign=Paint.Align.CENTER;paint.color=0xFFE5C77B.toInt();paint.typeface=Typeface.create("serif",Typeface.NORMAL);paint.textSize=22f*resources.displayMetrics.scaledDensity;paint.letterSpacing=.22f;c.drawText("ORACLE SPHERE",w/2,48f*resources.displayMetrics.density,paint);paint.letterSpacing=0f;paint.typeface=null}
    private fun drawNebula(c:Canvas,cx:Float,cy:Float,r:Float){
        val lag=atan2(fluidY,fluidX)+sloshPhase*.16f
        repeat(7){i-> val rr=r*(.23f+i*.09f); val a=lag+i*.91f+sin(sloshPhase+i)*slosh*.12f; thin.strokeWidth=r*(.045f-i*.003f);thin.color=if(i%2==0)0x4038D7CF else 0x40E5B85F;thin.maskFilter=BlurMaskFilter(r*.035f,BlurMaskFilter.Blur.NORMAL);val oval=RectF(cx-rr,cy-rr*.55f,cx+rr,cy+rr*.55f);c.save();c.rotate(a*57.3f,cx,cy);c.drawArc(oval,20f,215f,false,thin);c.restore()}
        thin.maskFilter=null
    }
    private fun drawParticles(c:Canvas,cx:Float,cy:Float,r:Float){
        val speed=.002f+slosh*.006f
        particles.forEachIndexed{i,p->p.a+=speed*(if(i%3==0)-1 else 1);val rr=r*(.15f+p.r*.77f);val x=cx+cos(p.a*6.283f+sloshPhase*.08f)*rr;val y=cy+sin(p.a*6.283f+sloshPhase*.11f)*rr*.72f;val edge=(1f-(hypot(x-cx,y-cy)/r)).coerceIn(0f,1f);paint.color=if(i%4==0)0xFFE5BB66.toInt() else 0xFF43BFC0.toInt();paint.alpha=(35+edge*155).toInt();c.drawCircle(x,y,1f+p.phase*2.2f,paint)};paint.alpha=255
    }
    private fun drawDie(c:Canvas,cx:Float,cy:Float,r:Float){
        val face=if(committed>=0)committed else facingFace();val n=rotate(normals[face]);val scale=r*(.34f+n[2]*.055f);val dcx=cx+n[0]*r*.07f+sin(sloshPhase)*slosh*r*.012f;val dcy=cy-n[1]*r*.07f+cos(sloshPhase*.8f)*slosh*r*.010f
        val path=Path();for(i in 0..4){val a=-PI/2+2*PI*i/5+az*.18;val x=dcx+cos(a).toFloat()*scale;val y=dcy+sin(a).toFloat()*scale*.82f;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close()
        paint.shader=RadialGradient(dcx,dcy-scale*.2f,scale*1.2f,intArrayOf(0xDD103D40.toInt(),0xEE07181C.toInt(),0xFF020709.toInt()),null,Shader.TileMode.CLAMP);c.drawPath(path,paint);paint.shader=null
        thin.color=0xFFE6C878.toInt();thin.strokeWidth=if(settleGlow>0f)5f else 2.5f;thin.maskFilter=BlurMaskFilter((2f+settleGlow*12f),BlurMaskFilter.Blur.NORMAL);c.drawPath(path,thin);thin.maskFilter=null
        val inner=Path();for(i in 0..4){val a=-PI/2+2*PI*i/5+az*.18;val x=dcx+cos(a).toFloat()*scale*.84f;val y=dcy+sin(a).toFloat()*scale*.69f;if(i==0)inner.moveTo(x,y)else inner.lineTo(x,y)};inner.close();thin.strokeWidth=1.2f;thin.color=0x99D8B667.toInt();c.drawPath(inner,thin)
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD);paint.color=0xFFFFE5A1.toInt();paint.setShadowLayer(12f,0f,0f,0xFFE2A94D.toInt());val txt=answers[face];val lines=wrap(txt,18);paint.textSize=(if(lines.size>1)r*.070f else r*.088f);val lineH=paint.textSize*1.08f;lines.forEachIndexed{i,s->c.drawText(s,dcx,dcy-(lines.size-1)*lineH/2+i*lineH-paint.ascent()/2-paint.descent()/2,paint)};paint.clearShadowLayer();paint.typeface=null
    }
    private fun wrap(s:String,max:Int):List<String>{val words=s.split(" ");val out=mutableListOf<String>();var line="";for(w in words){if(line.isNotEmpty()&&line.length+1+w.length>max){out+=line;line=w}else line=if(line.isEmpty())w else "$line $w"};if(line.isNotEmpty())out+=line;return out}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{lastTouchX=e.x;lastTouchY=e.y;downNs=System.nanoTime();return true}
            MotionEvent.ACTION_MOVE->{val dx=e.x-lastTouchX;val dy=e.y-lastTouchY;if(hypot(dx,dy)>5f){excite(dy/width,-dx/width,(dx-dy)/width*.2f,(hypot(dx,dy)/width*1.7f).coerceAtMost(1f));lastTouchX=e.x;lastTouchY=e.y};return true}
            MotionEvent.ACTION_UP->{if((System.nanoTime()-downNs)<220_000_000L)kick(.32f);return true}
        };return super.onTouchEvent(e)
    }
}
