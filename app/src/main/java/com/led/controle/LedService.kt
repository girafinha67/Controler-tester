package com.led.controle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.hardware.input.InputManager
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.InputDevice

class LedService : Service() {
    companion object { const val STOP = "com.led.controle.STOP" }

    private lateinit var im: InputManager
    private val h = Handler(Looper.getMainLooper())
    private val sessions = HashMap<Int, LightsManager.LightsSession>()
    private var running = false
    private var hue = 0f
    private var tick = 0

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) { open(id) }
        override fun onInputDeviceRemoved(id: Int) { sessions.remove(id)?.let { runCatching { it.close() } } }
        override fun onInputDeviceChanged(id: Int) {}
    }

    private val loop = object : Runnable {
        override fun run() { step(); h.postDelayed(this, 50) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            Cfg.sp(this).edit().putBoolean("active", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1, notif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!running) {
            running = true
            im = getSystemService(INPUT_SERVICE) as InputManager
            im.registerInputDeviceListener(listener, h)
            for (id in InputDevice.getDeviceIds()) open(id)
            h.post(loop)
        }
        return START_STICKY
    }

    private fun open(id: Int) {
        if (sessions.containsKey(id)) return
        val d = InputDevice.getDevice(id) ?: return
        if ((d.sources and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) return
        val lm = d.lightsManager
        if (lm.lights.none { it.hasRgbControl() }) return
        sessions[id] = lm.openSession()
    }

    private fun step() {
        val p = Cfg.sp(this)
        if (tick % 40 == 0) for (id in InputDevice.getDeviceIds()) open(id)
        tick++
        val base: Int
        if (p.getInt("mode", 0) == 1) {
            hue = (hue + p.getInt("speed", 3) * 0.8f) % 360f
            base = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        } else {
            if (tick % 10 != 0) return   // cor fixa: reaplica a cada 0,5 s
            base = Color.rgb(p.getInt("r", 255), p.getInt("g", 0), p.getInt("b", 0))
        }
        val br = p.getInt("br", 100) / 100f
        apply(Color.rgb((Color.red(base) * br).toInt(), (Color.green(base) * br).toInt(), (Color.blue(base) * br).toInt()))
    }

    private fun apply(color: Int) {
        val iter = sessions.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            val d = InputDevice.getDevice(e.key)
            if (d == null) { runCatching { e.value.close() }; iter.remove(); continue }
            try {
                val b = LightsRequest.Builder()
                for (l in d.lightsManager.lights) {
                    if (l.hasRgbControl()) b.addLight(l, LightState.Builder().setColor(color).build())
                }
                e.value.requestLights(b.build())
            } catch (_: Exception) {}
        }
    }

    private fun notif(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("led", "LED", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, LedService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "led")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LED Controle ativo")
            .setContentText("Toque para ajustar")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(0, "Parar", stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        if (running) runCatching { im.unregisterInputDeviceListener(listener) }
        for (s in sessions.values) runCatching { s.close() }
        sessions.clear()
        running = false
        super.onDestroy()
    }
}
