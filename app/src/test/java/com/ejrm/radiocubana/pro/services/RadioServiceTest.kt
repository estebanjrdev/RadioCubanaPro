package com.ejrm.radiocubana.pro.services

import android.content.Context
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import com.ejrm.radiocubana.pro.util.MediaPlayerSingleton
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.android.controller.ServiceController

class RadioServiceTest{
    lateinit var mockMediaPlayer: MediaPlayerSingleton
    lateinit var mockContext: Context
    private lateinit var serviceController: ServiceController<RadioService>
    private lateinit var radioService: RadioService
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockMediaPlayer = mockk(relaxed = true)
        mockContext = mockk()
        //serviceController = Robolectric.buildService(RadioService::class.java)
       // radioService = serviceController.create().startCommand(0, 0).get()
        every { mockContext.applicationContext } returns mockContext
    }
    @Test
    fun `deberia inicializar la reproduccion correctamente`() {
        // Configurar comportamiento esperado del MediaPlayer
        every { mockMediaPlayer.setOnPreparedListener(any()) } answers {
            val listener = arg<(MediaPlayerSingleton) -> Unit>(0)
            listener.invoke(mockMediaPlayer)
        }
        // Llamar a la función que se va a probar
        RadioService().initReproduction("https://icecast.teveo.cu/b3jbfThq", mockContext)

        // Verificar que los métodos esperados hayan sido llamados
        verify { mockMediaPlayer.initMediaPlayerSingleton(mockContext) }
        verify { mockMediaPlayer.setDataSource("https://icecast.teveo.cu/b3jbfThq") }
        verify { mockMediaPlayer.setAudioAttributes(any()) }
        verify { mockMediaPlayer.setScreenOnWhilePlaying(true) }
        verify { mockMediaPlayer.setWakeMode(mockContext, PowerManager.PARTIAL_WAKE_LOCK) }
        verify { mockMediaPlayer.prepareAsync() }
        verify { mockMediaPlayer.start() }
    }

    /*@Test
    fun `if mediaPlayer is null`(){
        //GIVEN
        every { radioService.mediaPlayer } returns null
        //WHEN
           radioService.initReproduction("https://icecast.teveo.cu/b3jbfThq",context.radioService)
        //THEN
        verify(exactly = 1) { radioService.initReproduction("https://icecast.teveo.cu/b3jbfThq",context.radioService) }
    }*/
}