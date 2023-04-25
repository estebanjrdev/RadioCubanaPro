package com.ejrm.radiocubana.pro.util

import android.content.Context
import android.media.MediaPlayer

object MediaPlayerSingleton: MediaPlayer() {
    lateinit var context: Context
    fun initMediaPlayerSingleton(context: Context){
        MediaPlayerSingleton.context = context.applicationContext
    }
}