package com.led.controle

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var sp: SharedPreferences
    private lateinit var status: TextView
    private lateinit var preview: View

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        sp = Cfg.sp(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }
        root.addView(TextView(this).apply { text = "LED Controle"; textSize = 26f })
        status = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(12)) }
        root.addView(status)
        preview = View(this)
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        root.addView(row("Vermelho", "r", 255, 255))
        root.addView(row("Verde", "g", 255, 0))
        root.addView(row("Azul", "b", 255, 0))
        root.addView(row("Brilho %", "br", 100, 100))
        root.addView(row("Velocidade do loop", "speed", 10, 3, 1))

        root.addView(btn("Aplicar cor fixa (permanente)") { go(0) })
        root.addView(btn("RGB em loop infinito") { go(1) })
        root.addView(btn("Parar e devolver o LED ao normal") {
            sp.edit().putBoolean("active", false).apply()
            stopService(Intent(this, LedService::class.java))
        })
        root.addView(Switch(this).apply {
            text = "Iniciar com o celular"
            isChecked = sp.getBoolean("boot", false)
            setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, c -> sp.edit().putBoolean("boot", c).apply() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        paint()
    }

    private fun row(label: String, key: String, max: Int, def: Int, min: Int = 0): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
        val t = TextView(this)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = sp.getInt(key, def) - min
        t.text = "$label: ${sb.progress + min}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) {
                sp.edit().putInt(key, p + min).apply()
                t.text = "$label: ${p + min}"
                paint()
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        box.addView(t); box.addView(sb)
        return box
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun go(mode: Int) {
        sp.edit().putInt("mode", mode).putBoolean("active", true).apply()
        startForegroundService(Intent(this, LedService::class.java))
    }

    private fun paint() {
        val br = sp.getInt("br", 100) / 100f
        preview.setBackgroundColor(Color.rgb(
            (sp.getInt("r", 255) * br).toInt(), (sp.getInt("g", 0) * br).toInt(), (sp.getInt("b", 0) * br).toInt()))
    }

    private fun refresh() {
        val sb = StringBuilder()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if ((d.sources and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) continue
            val n = d.lightsManager.lights.count { it.hasRgbControl() }
            sb.append("• ${d.name}: ").append(if (n > 0) "LED RGB disponível" else "sem LED RGB controlável").append("\n")
        }
        status.text = if (sb.isEmpty()) "Nenhum controle detectado. Conecte e aperte um botão." else sb.toString().trim()
    }

    override fun onResume() {
        super.onResume()
        (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(this, null)
        refresh()
    }

    override fun onPause() {
        (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(this)
        super.onPause()
    }

    override fun onInputDeviceAdded(id: Int) = refresh()
    override fun onInputDeviceRemoved(id: Int) = refresh()
    override fun onInputDeviceChanged(id: Int) = refresh()
}
