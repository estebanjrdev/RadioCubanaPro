package com.ejrm.radiocubana.pro.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.util.Constants
import com.ejrm.radiocubana.pro.util.Constants.CHANNEL_ID
import com.ejrm.radiocubana.pro.util.Constants.RADIO_NOTIFICATION_ID
import com.ejrm.radiocubana.pro.util.MediaPlayerSingleton
import com.ejrm.radiocubana.pro.util.NotificationReceiver

class RadioService : Service() {
    var url: String? = null
    var name: String? = null
    var imagen: Int? = null
    private var myBinder = MyBinder()
    private val TAG: String = "RadioService"
    private lateinit var mediaSession: MediaSessionCompat
    var mediaPlayer: MediaPlayerSingleton? = null

    fun isPlaying() = mediaPlayer?.isPlaying ?: false

    fun controlPlayNotifi() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                //MainActivity.binding.btnPlay.setImageResource(R.drawable.ic_play_24)
                showNotification(R.drawable.ic_play_24)
            } else {
                it.start()
                //MainActivity.binding.btnPlay.setImageResource(R.drawable.ic_pause_24)
                showNotification(R.drawable.ic_pause_24)
            }
        }
    }


    fun controlPlay() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.start()
            }
        }
    }

    fun stopRadio() {
        mediaPlayer?.let {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer = null
            //MainActivity.binding.layoutReproduction.isVisible = false
            stopSelf()
            removeNotification()
            Log.d(TAG, "Servicio StopCerrado")

        }
    }

    fun initReproduction(url: String, context: Context) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayerSingleton
            mediaPlayer?.initMediaPlayerSingleton(context)
            mediaPlayer?.setDataSource(url)
            mediaPlayer?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mediaPlayer?.setScreenOnWhilePlaying(true)
            mediaPlayer?.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener {
                mediaPlayer?.start()
            }
        } else if (mediaPlayer != null) {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer = null
            mediaPlayer = MediaPlayerSingleton
            mediaPlayer?.initMediaPlayerSingleton(context)
            mediaPlayer?.setDataSource(url)
            mediaPlayer?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mediaPlayer?.setScreenOnWhilePlaying(true)
            mediaPlayer?.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener {
                mediaPlayer?.start()
            }
        }
    }

    init {
        Log.d(TAG, "Servicio Corriendo")
    }

    override fun onBind(p0: Intent?): IBinder {
        return myBinder
    }

    inner class MyBinder : Binder() {
        fun currentService(): RadioService = this@RadioService
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio Creado")
        mediaSession = MediaSessionCompat(baseContext, TAG)
        createNotificationChannel()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) {
            Log.d("NOTIFI", "Notificacion Iniciada")
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        url = intent?.getStringExtra("URL")
        name = intent?.getStringExtra("NAME")
        imagen = intent?.getIntExtra("IMAGE", R.mipmap.ic_launcher_round)
        url?.let { initReproduction(it, baseContext) }
       // mediaSession = MediaSessionCompat(baseContext, TAG)
        Log.d(TAG, "Intent Iniciado")
        //showNotification(R.drawable.ic_pause_24)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        Log.d(TAG, "Servicio Destruido")
    }


    fun showNotification(playPauseBtn: Int) {
        val intent = Intent(baseContext, RadioService::class.java)
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 0, intent, flag)
        val playIntent =
            Intent(baseContext, NotificationReceiver::class.java).setAction(Constants.PLAY)
        val stopIntent =
            Intent(baseContext, NotificationReceiver::class.java).setAction(Constants.STOP)
        val playPendingIntent = PendingIntent.getBroadcast(baseContext, 0, playIntent, flag)
        val stopPendingIntent = PendingIntent.getBroadcast(baseContext, 0, stopIntent, flag)
        val notification = NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentText(name)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(BitmapFactory.decodeResource(resources, imagen!!))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView()
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(playPauseBtn, "Pause", playPendingIntent)
            .addAction(R.drawable.ic_stop_24, "Parar", stopPendingIntent)
            .setContentIntent(contentIntent)
            .build()
        startForeground(RADIO_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "My Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

     fun removeNotification() {
        // stopSelf()
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(RADIO_NOTIFICATION_ID)

    }
}