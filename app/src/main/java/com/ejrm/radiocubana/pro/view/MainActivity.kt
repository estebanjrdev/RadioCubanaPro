package com.ejrm.radiocubana.pro.view

import android.Manifest
import com.ejrm.radiocubana.pro.BuildConfig
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.databinding.ActivityMainBinding
import com.ejrm.radiocubana.pro.databinding.ContactoBinding
import com.ejrm.radiocubana.pro.services.RadioService
import com.ejrm.radiocubana.pro.util.Constants
import com.ejrm.radiocubana.pro.util.PlayStoreRatingHelper
import com.ejrm.radiocubana.pro.view.adapters.StationsAdapter
import com.ejrm.radiocubana.pro.viewmodel.MainViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // ── Binding como instancia privada (antes en companion object → memory leak) ──
    private lateinit var binding: ActivityMainBinding

    // ── radioService como instancia privada (ya no depende NotificationReceiver de él) ──
    private var radioService: RadioService? = null

    lateinit var station: StationsModel
    private var lastBackPressedTime: Long = 0
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: StationsAdapter

    // Control de anuncios intersticiales
    private var interstitial: InterstitialAd? = null
    private var ultimoAnuncioTimestamp: Long = 0
    private val INTERVALO_MINIMO_ANUNCIOS_MS = 120000L
    private var contadorSesiones = 0

    // Contador de cambios de emisora
    private var contadorCambiosEmisora = 0
    private val CAMBIOS_PARA_ANUNCIO = 3
    private var anuncioPendiente = false

    // Anuncios recompensados
    private var rewardedAd: RewardedAd? = null
    private var tiempoSinAnunciosHasta: Long = 0

    private val appPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (!result.all { it.value }) {
                showMessage("Debes conceder los permisos para continuar.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar Firebase solo si está habilitado (solo en prod)
        if (BuildConfig.ENABLE_CRASHLYTICS) {
            try {
                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
                Log.d("MainActivity", "Firebase Crashlytics habilitado")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al inicializar Crashlytics: ${e.message}")
            }
        }

        Log.d("MainActivity", "Iniciando en modo: ${BuildConfig.ENVIRONMENT}")
        Log.d("MainActivity", "Anuncios habilitados: ${BuildConfig.SHOW_ADS}")
        Log.d("MainActivity", "Crashlytics habilitado: ${BuildConfig.ENABLE_CRASHLYTICS}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appPermissionLauncher.launch(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            )
        }

        if (BuildConfig.ENABLE_CRASHLYTICS) {
            checkForUpdates()
        }

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        // Reconectar al servicio si ya estaba corriendo (usuario volvió a abrir la app)
        // Flag 0 = solo bind si el servicio ya existe, no lo crea
        bindService(Intent(this, RadioService::class.java), myConnection, 0)

        iniRecyclerView()
        initViewModel()

        // Observer centralizado para el estado de favorito de la emisora actual.
        // Se registra UNA sola vez aquí para evitar observers duplicados.
        viewModel.getLiveDataStation().observe(this, Observer { isFavorite ->
            binding.idFavoriteRed.isVisible = isFavorite == true
            binding.idFavoriteWhite.isVisible = isFavorite != true
        })

        precargarAnuncioIntersticial()
        precargarAnuncioRecompensado()

        initListeners()

        binding.btnPlay.setOnClickListener {
            radioService?.let { service ->
                service.controlPlayNotifi()
                if (service.isPlaying()) {
                    binding.btnPlay.setImageResource(R.drawable.ic_pause_24)
                    binding.btnPlay.contentDescription = "Detener"
                } else {
                    binding.btnPlay.setImageResource(R.drawable.ic_play_24)
                    binding.btnPlay.contentDescription = "Reproducir"
                }
            }
        }

        binding.btnStop.setOnClickListener {
            radioService?.stopRadio()
            binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
            if (binding.idFavoriteRed.isVisible) {
                binding.idFavoriteRed.isVisible = false
                binding.idFavoriteWhite.isVisible = true
            }
        }

        binding.idFavoriteWhite.setOnClickListener {
            if (binding.idFavoriteWhite.isVisible) {
                viewModel.addFavorite(station)
                binding.idFavoriteWhite.isVisible = false
                binding.idFavoriteRed.isVisible = true
                binding.idFavoriteRed.contentDescription = "Eliminar de Favoritos"
            }
        }

        binding.idFavoriteRed.setOnClickListener {
            if (binding.idFavoriteRed.isVisible) {
                viewModel.deleteFavorite(station)
                binding.idFavoriteRed.isVisible = false
                binding.idFavoriteWhite.isVisible = true
                binding.idFavoriteWhite.contentDescription = "Agregar a Favoritos"
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressedTime < 2000) {
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Presione nuevamente para salir", Toast.LENGTH_SHORT).show()
                    lastBackPressedTime = currentTime
                }
            }
        })
    }

    fun checkForUpdates() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val latestAppVersion = remoteConfig.getLong("latest_app_version")
                    if (latestAppVersion > currentVersion) showUpdateDialog()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al verificar actualizaciones: ${e.message}")
        }
    }

    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle("Nueva versión")
            .setIcon(R.mipmap.ic_launcher_round)
            .setMessage("Por favor, actualice la aplicación.")
            .setCancelable(false)
            .setPositiveButton("Actualizar") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
            }
            .create().also {
                it.setCanceledOnTouchOutside(false)
                it.show()
            }
    }

    private fun initListeners() {
        // Los listeners se configuran en cada función de precarga
    }

    private fun precargarAnuncioIntersticial() {
        if (!BuildConfig.SHOW_ADS) {
            Log.d("Anuncios", "Anuncios deshabilitados en ${BuildConfig.ENVIRONMENT}")
            return
        }
        if (interstitial == null) {
            InterstitialAd.load(this, getString(R.string.interstitial_ad_unit_id),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitial = ad
                        configurarCallbacksIntersticial()
                        Log.d("Anuncios", "Intersticial precargado [${BuildConfig.ENVIRONMENT}]")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitial = null
                        Log.d("Anuncios", "Error al cargar intersticial: ${error.message}")
                    }
                })
        }
    }

    private fun configurarCallbacksIntersticial() {
        interstitial?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                precargarAnuncioIntersticial()
                Log.d("Anuncios", "Anuncio intersticial cerrado")
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                precargarAnuncioIntersticial()
                Log.d("Anuncios", "Error al mostrar intersticial: ${error.message}")
            }
            override fun onAdShowedFullScreenContent() {
                interstitial = null
                Log.d("Anuncios", "Anuncio intersticial mostrado")
            }
        }
    }

    private fun precargarAnuncioRecompensado() {
        if (!BuildConfig.SHOW_ADS) {
            Log.d("Anuncios", "Anuncios recompensados deshabilitados en ${BuildConfig.ENVIRONMENT}")
            return
        }
        if (rewardedAd == null) {
            RewardedAd.load(this, getString(R.string.rewarded_ad_unit_id),
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        configurarCallbacksRecompensado()
                        Log.d("Anuncios", "Recompensado precargado [${BuildConfig.ENVIRONMENT}]")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        Log.d("Anuncios", "Error al cargar recompensado: ${error.message}")
                    }
                })
        }
    }

    private fun configurarCallbacksRecompensado() {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                precargarAnuncioRecompensado()
                Log.d("Anuncios", "Anuncio recompensado cerrado")
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                precargarAnuncioRecompensado()
                Log.d("Anuncios", "Error al mostrar recompensado: ${error.message}")
            }
            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
                Log.d("Anuncios", "Anuncio recompensado mostrado")
            }
        }
    }

    private fun deberiasMostrarAnuncio(): Boolean {
        val tiempoActual = System.currentTimeMillis()
        if (tiempoActual < tiempoSinAnunciosHasta) {
            Log.d("Anuncios", "Período sin anuncios activo. Quedan ${(tiempoSinAnunciosHasta - tiempoActual) / 60000} minutos")
            return false
        }
        if (!anuncioPendiente) {
            Log.d("Anuncios", "No hay anuncio pendiente. Cambios: $contadorCambiosEmisora/$CAMBIOS_PARA_ANUNCIO")
            return false
        }
        val tiempoDesdeUltimoAnuncio = tiempoActual - ultimoAnuncioTimestamp
        if (tiempoDesdeUltimoAnuncio < INTERVALO_MINIMO_ANUNCIOS_MS) {
            Log.d("Anuncios", "Intervalo mínimo no cumplido. Faltan ${(INTERVALO_MINIMO_ANUNCIOS_MS - tiempoDesdeUltimoAnuncio) / 1000} segundos")
            return false
        }
        return true
    }

    private fun hayReproduccionActiva(): Boolean {
        return try {
            radioService?.isPlaying() == true
        } catch (e: Exception) {
            Log.e("Anuncios", "Error al verificar reproducción: ${e.message}")
            false
        }
    }

    private fun mostrarAnuncioSiCorresponde() {
        if (hayReproduccionActiva()) {
            Log.d("Anuncios", "No mostrar: reproducción activa")
            return
        }
        if (!deberiasMostrarAnuncio()) return
        if (interstitial != null) {
            interstitial?.show(this@MainActivity)
            ultimoAnuncioTimestamp = System.currentTimeMillis()
            contadorCambiosEmisora = 0
            anuncioPendiente = false
            Log.d("Anuncios", "Anuncio mostrado. Contador reseteado.")
        } else {
            Log.d("Anuncios", "No hay anuncio precargado disponible")
            precargarAnuncioIntersticial()
        }
    }

    private fun intentarMostrarAnuncioEnPausaNatural() {
        contadorSesiones++
        if (anuncioPendiente) {
            binding.root.postDelayed({ mostrarAnuncioSiCorresponde() }, 1000)
        }
    }

    private fun mostrarAnuncioRecompensado() {
        if (rewardedAd != null) {
            rewardedAd?.show(this, OnUserEarnedRewardListener {
                tiempoSinAnunciosHasta = System.currentTimeMillis() + (30 * 60 * 1000)
                Toast.makeText(this, "¡Disfruta 30 minutos sin anuncios!", Toast.LENGTH_LONG).show()
                Log.d("Anuncios", "Recompensa otorgada: 30 min sin anuncios")
            })
        } else {
            Toast.makeText(this, "Anuncio no disponible, intenta más tarde", Toast.LENGTH_SHORT).show()
            precargarAnuncioRecompensado()
        }
    }

    private fun mostrarDialogoAnuncioRecompensado() {
        val tiempoActual = System.currentTimeMillis()
        if (tiempoActual < tiempoSinAnunciosHasta) {
            val minutosRestantes = (tiempoSinAnunciosHasta - tiempoActual) / 60000
            AlertDialog.Builder(this)
                .setTitle("Sin Anuncios Activo")
                .setIcon(R.mipmap.ic_launcher_round)
                .setMessage("Ya tienes $minutosRestantes minutos restantes sin anuncios.\n\n¿Deseas ver otro anuncio para extender el tiempo?")
                .setPositiveButton("Ver Anuncio") { _, _ -> mostrarAnuncioRecompensado() }
                .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Escucha sin Anuncios")
                .setIcon(R.mipmap.ic_launcher_round)
                .setMessage("Ve un anuncio corto y disfruta de 30 minutos escuchando tus emisoras favoritas sin interrupciones.\n\nDurante este tiempo no se mostrarán anuncios mientras cambias de emisora.")
                .setPositiveButton("Ver Anuncio") { _, _ -> mostrarAnuncioRecompensado() }
                .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun iniRecyclerView() {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = StationsAdapter(EmisoraItemClickListener())
        binding.recycler.adapter = adapter
    }

    private fun initViewModel() {
        viewModel.getLiveDataObserver().observe(this, Observer {
            adapter.setStationsList(it)
            adapter.notifyDataSetChanged()
            // Ocultar shimmer y mostrar lista cuando los datos estén listos
            binding.viewLoading.isVisible = false
            binding.view.isVisible = true
        })
        viewModel.stationsProviders()
    }

    fun startService(stations: StationsModel) {
        contadorCambiosEmisora++
        Log.d("Anuncios", "Cambio de emisora detectado. Contador: $contadorCambiosEmisora/$CAMBIOS_PARA_ANUNCIO")

        if (contadorCambiosEmisora >= CAMBIOS_PARA_ANUNCIO) {
            anuncioPendiente = true
            Log.d("Anuncios", "3 cambios completados. Anuncio pendiente para próxima pausa natural")
            binding.root.postDelayed({ mostrarAnuncioSiCorresponde() }, 2000)
        }

        val intent = Intent(this, RadioService::class.java).apply {
            putExtra("URL", stations.link)
            putExtra("NAME", stations.name)
            putExtra("IMAGE", stations.imagen)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(Intent(this, RadioService::class.java), myConnection, Context.BIND_AUTO_CREATE)

        viewModel.checkStation(stations)
        binding.layoutReproduction.visibility = LinearLayout.VISIBLE
        binding.imagelogo.setImageResource(stations.imagen)
        binding.title.text = stations.name
        binding.title.isSelected = true
        binding.btnPlay.setImageResource(R.drawable.ic_pause_24)
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET])
    suspend fun getResponseCode(url: String): Int {
        delay(1500)
        if (!checkForInternet(this)) return 404
        return withContext(Dispatchers.IO) {
            var httpConnection: HttpURLConnection? = null
            try {
                httpConnection = URL(url).openConnection() as HttpURLConnection
                httpConnection.setRequestProperty("User-Agent", "Android")
                httpConnection.connectTimeout = 8000
                httpConnection.readTimeout = 8000
                httpConnection.instanceFollowRedirects = false
                httpConnection.connect()
                val code = httpConnection.responseCode
                code
            } catch (e: java.io.EOFException) {
                // Los streams de audio Icecast cierran la conexión antes de dar respuesta HTTP
                // Si se pudo conectar lo suficiente para recibir esta excepción → está disponible
                Log.d("MainActivity", "Stream disponible (EOFException esperada): ${e.message}")
                200
            } catch (e: Exception) {
                Log.e("MainActivity", "Error verificando URL: ${e.message}")
                404
            } finally {
                httpConnection?.disconnect()
            }
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET])
    suspend fun dataConexion(url: String): Boolean {
        delay(1500)
        val httpConnection = withContext(Dispatchers.IO) {
            URL(url).openConnection()
        } as HttpURLConnection
        if (checkForInternet(this)) {
            try {
                httpConnection.setRequestProperty("User-Agent", "Android")
                httpConnection.connectTimeout = 1500
                withContext(Dispatchers.IO) { httpConnection.connect() }
                return httpConnection.responseCode == 200
            } catch (e: Exception) {
                println(e.toString())
                return false
            }
        }
        return false
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val search = menu!!.findItem(R.id.search)
        val searchView = search.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.getLiveDataObserver().observe(this@MainActivity, Observer {
                    adapter.setStationsList(it)
                    adapter.notifyDataSetChanged()
                })
                viewModel.search(newText!!)
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.favoriteList -> startActivity(Intent(baseContext, FavoriteActivity::class.java))
            R.id.info -> {
                AlertDialog.Builder(this)
                    .setTitle("Acerca de Radio Cubana")
                    .setMessage("Esta aplicación nos permite escuchar las principales emisoras nacionales de radio desde el móvil.\nRequiere estar conectado a internet.\nSiempre debe recordar que en caso de que habrá alguna emisora y no este disponible es porque hay emisoras que no están al aire las 24 horas del día.\nCon Radio Cubana podemos estar todo el tiempo informado de las últimas noticias, escuchar música y disfrutar de los partidos de béisbol de la serie nacional etc.")
                    .setPositiveButton("Aceptar") { _, _ -> }
                    .show()
            }
            R.id.contact -> {
                val bindingcontact = ContactoBinding.inflate(layoutInflater)
                AlertDialog.Builder(this)
                    .setTitle("Contacto")
                    .setView(bindingcontact.root)
                    .setPositiveButton("Aceptar") { _, _ -> }
                    .show()
                bindingcontact.layoutemail.setOnClickListener {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        data = Uri.parse("Email")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("susoluciones.software@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "suSoluciones")
                        putExtra(Intent.EXTRA_TEXT, "")
                        type = "message/rfc822"
                    }
                    startActivity(Intent.createChooser(intent, "Launch Email"))
                }
                bindingcontact.layoutshare.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_SEND).apply {
                        putExtra("android.intent.extra.TEXT", "¡Hola!\nTe estoy invitando a que uses Radio Cubana, con ella puedes escuchar las emisoras nacionales desde tu telefono\n\nDescárgala de: https://play.google.com/store/apps/details?id=com.ejrm.radiocubana.pro")
                        type = "text/plain"
                    })
                }
                bindingcontact.layouttelegram.setOnClickListener {
                    openLink(Uri.parse("https://t.me/susoluciones"))
                }
            }
            R.id.valoracion -> PlayStoreRatingHelper.openPlayStoreForRating(this)
            R.id.sin_anuncios -> mostrarDialogoAnuncioRecompensado()
            R.id.politica -> openLink(Uri.parse("https://www.app-privacy-policy.com/live.php?token=fUsNDhObFBDnkj2oAGyPoCvnmP8KqnCl"))
        }
        return super.onOptionsItemSelected(item)
    }

    fun openLink(uri: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun checkForInternet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    override fun onResume() {
        super.onResume()
        if (radioService == null) {
            // Intentar reconectar si el servicio está corriendo
            bindService(Intent(this, RadioService::class.java), myConnection, 0)
        } else {
            // Ya tenemos referencia: sincronizar UI con el estado real del servicio
            // (puede haber cambiado mientras la activity estaba pausada en el back stack)
            syncUIWithServiceState()
        }
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        intentarMostrarAnuncioEnPausaNatural()
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            playbackStateReceiver,
            IntentFilter().apply {
                addAction(Constants.ACTION_PLAYBACK_STOPPED)
                addAction(Constants.ACTION_PLAYBACK_STATE_CHANGED)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Sincroniza la barra de reproducción con el estado real del RadioService.
     * Se llama en onResume() para cubrir eventos que ocurrieron mientras la
     * activity estaba pausada (ej: se detuvo desde FavoriteActivity o la notificación).
     */
    private fun syncUIWithServiceState() {
        val service = radioService
        if (service != null && (service.isPlaying() || service.isPreparing)) {
            binding.layoutReproduction.visibility = LinearLayout.VISIBLE
            service.name?.let { binding.title.text = it }
            service.imagen?.let { binding.imagelogo.setImageResource(it) }
            binding.title.isSelected = true
            binding.btnPlay.setImageResource(
                if (service.isPlaying() || service.isPreparing) R.drawable.ic_pause_24 else R.drawable.ic_play_24
            )
            // Verificar si la emisora actual está en favoritos para mostrar el ícono correcto
            service.url?.let { url ->
                viewModel.checkStation(
                    StationsModel(
                        link = url,
                        name = service.name ?: "",
                        description = "",
                        imagen = service.imagen ?: R.mipmap.ic_launcher_round
                    )
                )
            }
        } else {
            binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
            // Resetear corazón al estado por defecto al ocultar la barra
            binding.idFavoriteRed.isVisible = false
            binding.idFavoriteWhite.isVisible = true
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w("MainActivity", "networkCallback no registrado: ${e.message}")
        }
        try {
            unregisterReceiver(playbackStateReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "playbackStateReceiver no registrado: ${e.message}")
        }
    }

    /** NetworkCallback reemplaza el BroadcastReceiver CONNECTIVITY_ACTION (deprecado) */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                lifecycleScope.launch(Dispatchers.Main) {
                    val response = withContext(Dispatchers.IO) { dataConexion("https://www.google.com") }
                    if (response) {
                        binding.viewLoading.isVisible = false
                        binding.view.isVisible = true
                        Snackbar.make(binding.root, "Conectado!!!", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                Snackbar.make(binding.idConstrain, "Active sus datos móviles o wifi", Snackbar.LENGTH_INDEFINITE).show()
                binding.viewLoading.isVisible = true
                binding.view.isVisible = false
                binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
            }
        }
    }

    /** Receiver para sincronizar la UI con los cambios de estado del RadioService */
    private val playbackStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_PLAYBACK_STOPPED -> {
                    binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
                    if (binding.idFavoriteRed.isVisible) {
                        binding.idFavoriteRed.isVisible = false
                        binding.idFavoriteWhite.isVisible = true
                    }
                    Log.d("MainActivity", "UI actualizada: reproducción detenida desde notificación")
                }
                Constants.ACTION_PLAYBACK_STATE_CHANGED -> {
                    val isPlaying = intent.getBooleanExtra(Constants.EXTRA_IS_PLAYING, false)
                    if (isPlaying) {
                        binding.btnPlay.setImageResource(R.drawable.ic_pause_24)
                        binding.btnPlay.contentDescription = "Detener"
                    } else {
                        binding.btnPlay.setImageResource(R.drawable.ic_play_24)
                        binding.btnPlay.contentDescription = "Reproducir"
                    }
                }
            }
        }
    }

    private val myConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as? RadioService.MyBinder
            radioService = binder?.currentService()
            // Sincronizar UI con el estado real al conectar
            syncUIWithServiceState()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            radioService = null
            binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(myConnection)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Servicio no estaba vinculado: ${e.message}")
        }
        Log.d("MainActivity", "Activity destruida")
    }

    private inner class EmisoraItemClickListener : StationsAdapter.StationsAdapterListener {
        override fun onEmisoraSelected(stations: StationsModel) {
            // Snackbar de carga — menos intrusivo que un diálogo
            val loadingSnackbar = Snackbar.make(binding.root, "Verificando emisora...", Snackbar.LENGTH_INDEFINITE)
            loadingSnackbar.show()
            lifecycleScope.launch(Dispatchers.Main) {
                val response = withContext(Dispatchers.IO) {
                    getResponseCode(stations.link)
                }
                loadingSnackbar.dismiss()
                if (response != 200) {
                    Snackbar.make(binding.root, "Emisora no disponible", 2000).show()
                } else {
                    station = stations
                    startService(stations)
                }
            }
        }
    }
}
