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
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.util.Constants
import com.ejrm.radiocubana.pro.util.Constants.CHANNEL_ID
import com.ejrm.radiocubana.pro.util.Constants.RADIO_NOTIFICATION_ID
import com.ejrm.radiocubana.pro.util.MediaPlayerSingleton
import com.ejrm.radiocubana.pro.util.NotificationReceiver
import com.ejrm.radiocubana.pro.view.MainActivity

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
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                showNotification(R.drawable.ic_play_24)
            } else {
                it.start()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                showNotification(R.drawable.ic_pause_24)
            }
            // Notificar a la Activity para que sincronice su UI
            sendBroadcast(Intent(Constants.ACTION_PLAYBACK_STATE_CHANGED).apply {
                putExtra(Constants.EXTRA_IS_PLAYING, it.isPlaying)
            })
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
            it.stop()
            it.reset()
            mediaPlayer = null
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // Notificar a la Activity para que oculte la barra de reproducción
            sendBroadcast(Intent(Constants.ACTION_PLAYBACK_STOPPED))
            stopSelf()
            Log.d(TAG, "Servicio detenido y notificación eliminada")
        }
    }

    fun initReproduction(url: String, context: Context) {
        // Detener reproducción anterior si existe
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
            // Actualizar metadatos y estado para el panel Multimedia de Samsung
            updateMediaSessionMetadata()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            showNotification(R.drawable.ic_pause_24)
            Log.d(TAG, "Reproducción iniciada: $url")
        }
    }

    init {
        Log.d(TAG, "Servicio creado")
    }

    override fun onBind(p0: Intent?): IBinder {
        return myBinder
    }

    inner class MyBinder : Binder() {
        fun currentService(): RadioService = this@RadioService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio onCreate")
        mediaSession = MediaSessionCompat(baseContext, TAG).apply {
            isActive = true
            // Callbacks del MediaSession: Samsung y otros usan estos para controlar
            // la reproducción desde el panel Multimedia / pantalla de bloqueo
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                        showNotification(R.drawable.ic_pause_24)
                    }
                }
                override fun onPause() {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.pause()
                        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                        showNotification(R.drawable.ic_play_24)
                    }
                }
                override fun onStop() {
                    stopRadio()
                }
                // Radio en vivo → no tiene canciones anteriores/siguientes
                override fun onSkipToNext() {}
                override fun onSkipToPrevious() {}
            })
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_PLAY_PAUSE -> {
                // Comando desde la notificación: play/pause sin cambiar emisora
                controlPlayNotifi()
                Log.d(TAG, "Acción: PLAY_PAUSE")
            }
            Constants.ACTION_STOP -> {
                // Comando desde la notificación: detener todo
                stopRadio()
                Log.d(TAG, "Acción: STOP")
            }
            else -> {
                // Nueva emisora seleccionada desde la app
                url = intent?.getStringExtra("URL")
                name = intent?.getStringExtra("NAME")
                imagen = intent?.getIntExtra("IMAGE", R.mipmap.ic_launcher_round)
                // Mostrar notificación inmediatamente (requerido por Android 8+ antes de 5 seg)
                showNotification(R.drawable.ic_pause_24)
                url?.let { initReproduction(it, baseContext) }
                Log.d(TAG, "Nueva emisora: $name")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        Log.d(TAG, "Servicio destruido")
    }

    /** Actualiza los metadatos del MediaSession (nombre de emisora visible en Samsung Multimedia) */
    private fun updateMediaSessionMetadata() {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, name ?: "Radio Cubana")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Radio Cubana")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "En vivo")
                .build()
        )
    }

    /** Actualiza el estado de reproducción para que Samsung muestre ▶ o ⏸ correctamente */
    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    fun showNotification(playPauseBtn: Int) {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Al tocar la notificación → abre la app
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            flag
        )

        val playIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(Constants.PLAY)
        val stopIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(Constants.STOP)
        val playPendingIntent = PendingIntent.getBroadcast(baseContext, 0, playIntent, flag)
        val stopPendingIntent = PendingIntent.getBroadcast(baseContext, 0, stopIntent, flag)

        val notification = NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(name ?: "Radio Cubana")
            .setContentText(
                if (playPauseBtn == R.drawable.ic_pause_24) "Reproduciendo..." else "En pausa"
            )
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(BitmapFactory.decodeResource(resources, imagen ?: R.mipmap.ic_launcher_round))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    // No vinculamos el token del MediaSession al estilo visual:
                    // así la notificación aparece en la lista normal en TODOS los Samsung
                    // (en vez de moverse al panel "Multimedia" del A02s y similares)
                    .setShowActionsInCompactView(0, 1)
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(playPauseBtn, if (playPauseBtn == R.drawable.ic_pause_24) "Pausar" else "Reproducir", playPendingIntent)
            .addAction(R.drawable.ic_stop_24, "Detener", stopPendingIntent)
            .setContentIntent(contentIntent)
            .build()

        startForeground(RADIO_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // IMPORTANCE_DEFAULT en lugar de IMPORTANCE_LOW:
            // Samsung y otros dispositivos suprimen notificaciones de baja importancia
            // en segundo plano. DEFAULT garantiza que siempre sea visible.
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Radio Cubana - Reproducción",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Controles de reproducción de radio en segundo plano"
                setShowBadge(false)  // No mostrar badge en el icono de la app
            }
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }
}
