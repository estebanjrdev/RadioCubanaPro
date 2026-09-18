package com.ejrm.radiocubana.pro.util

object Constants {
    const val STATIONS_DATABASE = "stations_database"
    const val CHANNEL_ID = "radio_playback_v2"  // v2: fuerza recreación del canal con nueva importancia
    const val RADIO_NOTIFICATION_ID = 123
    const val PLAY = "play"
    const val STOP = "stop"
    // Acciones enviadas desde NotificationReceiver al RadioService
    const val ACTION_PLAY_PAUSE = "com.ejrm.radiocubana.pro.ACTION_PLAY_PAUSE"
    const val ACTION_STOP = "com.ejrm.radiocubana.pro.ACTION_STOP"
    // Broadcasts del servicio → Activity para sincronizar la UI
    const val ACTION_PLAYBACK_STOPPED = "com.ejrm.radiocubana.pro.ACTION_PLAYBACK_STOPPED"
    const val ACTION_PLAYBACK_STATE_CHANGED = "com.ejrm.radiocubana.pro.ACTION_PLAYBACK_STATE_CHANGED"
    const val EXTRA_IS_PLAYING = "isPlaying"
}
