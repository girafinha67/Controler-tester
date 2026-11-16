package com.led.controle

import android.content.Context

object Cfg {
    fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
}
