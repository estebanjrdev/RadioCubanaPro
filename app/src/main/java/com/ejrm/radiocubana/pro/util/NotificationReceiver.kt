package com.ejrm.radiocubana.pro.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.ejrm.radiocubana.pro.view.MainActivity

class NotificationReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(p0: Context?, p1: Intent?) {
        when (p1?.action) {
            Constants.PLAY -> if (MainActivity.radioService!!.isPlaying()) PlayPauseRadio() else PlayPauseRadio()
            Constants.STOP -> {
                MainActivity.radioService!!.stopRadio()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun PlayPauseRadio() {
        MainActivity.radioService!!.controlPlayNotifi()
    }

}