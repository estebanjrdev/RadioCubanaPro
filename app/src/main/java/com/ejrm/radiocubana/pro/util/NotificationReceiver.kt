package com.ejrm.radiocubana.pro.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ejrm.radiocubana.pro.services.RadioService

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = when (intent?.action) {
            Constants.PLAY -> Constants.ACTION_PLAY_PAUSE
            Constants.STOP -> Constants.ACTION_STOP
            else -> return
        }
        val serviceIntent = Intent(context, RadioService::class.java).apply {
            this.action = action
        }
        context?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.startForegroundService(serviceIntent)
            } else {
                it.startService(serviceIntent)
            }
        }
    }
}
