package com.haseltonmedia.oraclesphere

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.hardware.*
import android.view.*
import android.widget.*

class MainActivity : Activity(), SensorEventListener {
    private lateinit var oracle: OracleView
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var last = FloatArray(3)
    private var primed = false
    private var lastImpulseNs = 0L

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        oracle = OracleView(this)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(4,8,12)) }
        root.addView(oracle, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(12,0,12,12) }
        fun button(label: String, action: () -> Unit) = TextView(this).apply {
            text = label; setTextColor(Color.rgb(221,184,106)); textSize = 12f; gravity = Gravity.CENTER
            setPadding(18,16,18,16); setOnClickListener { action() }
        }
        controls.addView(button("SHAKE") { oracle.kick(1.0f) }, LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(button("FLICK") { oracle.kick(.62f) }, LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(button("TAP") { oracle.kick(.32f) }, LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(button("HISTORY") { showHistory() }, LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(button("TUNE") { showTuning() }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(controls)
        return root
    }

    private fun showHistory() {
        val text = oracle.history.ifEmpty { listOf("No readings yet") }.joinToString("\n")
        AlertDialog.Builder(this).setTitle("RECENT READINGS").setMessage(text).setPositiveButton("CLOSE",null).setNeutralButton("LEGAL") { _,_->
            AlertDialog.Builder(this).setTitle("Legal").setMessage("ORACLE SPHERE is an entertainment experience. Its readings are not factual predictions or professional advice.\n\nOriginal presentation. No affiliation with any toy, game, or fortune-telling brand.").setPositiveButton("CLOSE",null).show()
        }.show()
    }

    private fun showTuning() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,10,40,0) }
        fun slider(name:String, min:Float, max:Float, value:Float, update:(Float)->Unit) {
            val label = TextView(this).apply { setTextColor(Color.WHITE) }
            val seek = SeekBar(this).apply { progress = (((value-min)/(max-min))*1000).toInt() }
            fun refresh(p:Int) { val v=min+(max-min)*p/1000f; label.text="$name  ${"%.2f".format(v)}"; update(v) }
            seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{ override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean)=refresh(p); override fun onStartTrackingTouch(s:SeekBar?){}; override fun onStopTrackingTouch(s:SeekBar?){} })
            refresh(seek.progress); box.addView(label); box.addView(seek)
        }
        slider("Viscosity μ",.35f,2.4f,oracle.mu){oracle.mu=it}
        slider("Density ρ",.55f,1.8f,oracle.rho){oracle.rho=it}
        slider("Die mass",.45f,2.2f,oracle.dieMass){oracle.dieMass=it}
        slider("Haptic gain",0f,2f,oracle.hapticGain){oracle.hapticGain=it}
        AlertDialog.Builder(this).setTitle("VISCOUS MODEL LAB").setView(box).setMessage("Decay τ ∝ ρL²/μ. Changes apply live.").setPositiveButton("DONE",null).setNeutralButton("RESET") { _,_->oracle.resetTuning() }.show()
    }

    override fun onResume(){ super.onResume(); sensorManager?.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_GAME) }
    override fun onPause(){ sensorManager?.unregisterListener(this); super.onPause() }
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}
    override fun onSensorChanged(e:SensorEvent){
        if(!primed){ last=e.values.clone(); primed=true; return }
        val dx=e.values[0]-last[0]; val dy=e.values[1]-last[1]; val dz=e.values[2]-last[2]
        val impulse=kotlin.math.sqrt(dx*dx+dy*dy+dz*dz)
        val now=System.nanoTime()
        // Ignore hand tremor, walking vibration and sensor noise. A deliberate shake
        // must cross the gate and onset haptics are rate-limited.
        if(impulse>5.2f && now-lastImpulseNs>180_000_000L) {
            oracle.imuImpulse(dx,dy,dz,impulse)
            lastImpulseNs=now
        }
        for(i in 0..2) last[i]=last[i]*.35f+e.values[i]*.65f
    }
}
