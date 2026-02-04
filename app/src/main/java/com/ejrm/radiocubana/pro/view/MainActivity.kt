package com.ejrm.radiocubana.pro.view

import android.Manifest
import android.app.ActivityManager
import com.ejrm.radiocubana.pro.BuildConfig
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.databinding.ActivityMainBinding
import com.ejrm.radiocubana.pro.databinding.ContactoBinding
import com.ejrm.radiocubana.pro.services.RadioService
import com.ejrm.radiocubana.pro.util.PlayStoreRatingHelper
import com.ejrm.radiocubana.pro.view.adapters.StationsAdapter
import com.ejrm.radiocubana.pro.viewmodel.MainViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    lateinit var station: StationsModel

    companion object {
        lateinit var binding: ActivityMainBinding
        var radioService: RadioService? = null
    }
    private var lastBackPressedTime: Long = 0
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: StationsAdapter
    
    // Control de anuncios intersticiales
    private var interstitial: InterstitialAd? = null
    
    // Anuncios recompensados
    private var rewardedAd: RewardedAd? = null
    private var tiempoSinAnunciosHasta: Long = 0
    
    private val appPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (!result.all { it.value }) {
                showMessage("Debes conceder los permisos para continuar.")
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
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
        
        // Log del entorno actual
        Log.d("MainActivity", "Iniciando en modo: ${BuildConfig.ENVIRONMENT}")
        Log.d("MainActivity", "Anuncios habilitados: ${BuildConfig.SHOW_ADS}")
        Log.d("MainActivity", "Crashlytics habilitado: ${BuildConfig.ENABLE_CRASHLYTICS}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val NOTIFICATION_PERMISSION = arrayOf(
                Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.POST_NOTIFICATIONS
            )
            appPermissionLauncher.launch(NOTIFICATION_PERMISSION)

        }

        // Verificar actualizaciones solo si Firebase está disponible
        if (BuildConfig.ENABLE_CRASHLYTICS) {
            checkForUpdates()
        }
        
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        if (isServiceRunning(RadioService::class.java)) radioService!!.stopRadio()
        iniRecyclerView()
        initViewModel()
        
        // Precargar anuncios al iniciar la app
        precargarAnuncioIntersticial()
        precargarAnuncioRecompensado()
        
        initListeners()
        binding.btnPlay.setOnClickListener(View.OnClickListener {
            if (radioService!!.isPlaying()) {
                radioService!!.controlPlay()
                binding.btnPlay.setImageResource(R.drawable.ic_play_24)
                binding.btnPlay.contentDescription = "Reproducir"
            } else {
                radioService!!.controlPlay()
                binding.btnPlay.setImageResource(R.drawable.ic_pause_24)
                binding.btnPlay.contentDescription = "Detener"
            }
        })

        binding.btnStop.setOnClickListener(View.OnClickListener {
            radioService!!.stopRadio()
            binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
            if (binding.idFavoriteRed.isVisible) {
                binding.idFavoriteRed.isVisible = false
                binding.idFavoriteWhite.isVisible = true
            }
        })
        binding.idFavoriteWhite.setOnClickListener(View.OnClickListener {
            if (binding.idFavoriteWhite.isVisible) {
                viewModel.addFavorite(station)
                binding.idFavoriteWhite.isVisible = false
                binding.idFavoriteRed.isVisible = true
                binding.idFavoriteRed.contentDescription = "Eliminar de Favoritos"
            }
        })
        binding.idFavoriteRed.setOnClickListener(View.OnClickListener {
            if (binding.idFavoriteRed.isVisible) {
                viewModel.deleteFavorite(station)
                binding.idFavoriteRed.isVisible = false
                binding.idFavoriteWhite.isVisible = true
                binding.idFavoriteWhite.contentDescription = "Agregar a Favoritos"
                //binding.idFavoriteWhite.startAnimation(anim)
            }
        })

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - lastBackPressedTime

                if (elapsedTime < 2000) {
                    finish() // Cierra la actividad actual
                } else {
                    Toast.makeText(this@MainActivity, "Presione nuevamente para salir", Toast.LENGTH_SHORT).show()
                    lastBackPressedTime = currentTime
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }


    fun checkForUpdates() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode

            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val latestAppVersion = remoteConfig.getLong("latest_app_version")

                    if (latestAppVersion > currentVersion) {
                        showUpdateDialog()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al verificar actualizaciones: ${e.message}")
        }
    }

    private fun showUpdateDialog() {
        val alertdialog = AlertDialog.Builder(this)
        alertdialog.setTitle("Nueva versión")
        alertdialog.setIcon(R.mipmap.ic_launcher_round)
        alertdialog.setMessage("Por favor, actualice la aplicación.")
        alertdialog.setCancelable(false)
        alertdialog.setPositiveButton("Actualizar") { _, _ ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        }
        val alert = alertdialog.create()
        alert.setCanceledOnTouchOutside(false)
        alert.show()
    }

    private fun initListeners() {
        // Los listeners se configuran en cada función de precarga
    }

    /**
     * Precarga un anuncio intersticial sin mostrarlo
     * Se ejecuta al iniciar la app y después de mostrar cada anuncio
     */
    private fun precargarAnuncioIntersticial() {
        // No cargar anuncios en modo debug si está deshabilitado
        if (!BuildConfig.SHOW_ADS) {
            Log.d("Anuncios", "Anuncios deshabilitados en ${BuildConfig.ENVIRONMENT}")
            return
        }
        
        if (interstitial == null) {
            val adRequest = AdRequest.Builder().build()
            val adUnitId = getString(R.string.interstitial_ad_unit_id)
            
            InterstitialAd.load(
                this,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        interstitial = interstitialAd
                        configurarCallbacksIntersticial()
                        Log.d("Anuncios", "Anuncio intersticial precargado [${BuildConfig.ENVIRONMENT}]")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitial = null
                        Log.d("Anuncios", "Error al cargar intersticial: ${error.message}")
                    }
                })
        }
    }

    /**
     * Configura los callbacks del anuncio intersticial
     */
    private fun configurarCallbacksIntersticial() {
        interstitial?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                precargarAnuncioIntersticial() // Precargar el siguiente
                Log.d("Anuncios", "Anuncio intersticial cerrado")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                precargarAnuncioIntersticial() // Intentar precargar otro
                Log.d("Anuncios", "Error al mostrar intersticial: ${error.message}")
            }

            override fun onAdShowedFullScreenContent() {
                interstitial = null
                Log.d("Anuncios", "Anuncio intersticial mostrado")
            }
        }
    }

    /**
     * Precarga un anuncio recompensado sin mostrarlo
     */
    private fun precargarAnuncioRecompensado() {
        // No cargar anuncios en modo debug si está deshabilitado
        if (!BuildConfig.SHOW_ADS) {
            Log.d("Anuncios", "Anuncios recompensados deshabilitados en ${BuildConfig.ENVIRONMENT}")
            return
        }
        
        if (rewardedAd == null) {
            val adRequest = AdRequest.Builder().build()
            val adUnitId = getString(R.string.rewarded_ad_unit_id)
            
            RewardedAd.load(
                this,
                adUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        configurarCallbacksRecompensado()
                        Log.d("Anuncios", "Anuncio recompensado precargado [${BuildConfig.ENVIRONMENT}]")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        Log.d("Anuncios", "Error al cargar recompensado: ${error.message}")
                    }
                })
        }
    }

    /**
     * Configura los callbacks del anuncio recompensado
     */
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

    /**
     * Determina si debe mostrarse un anuncio intersticial
     * Solo verifica si hay período sin anuncios activo
     */
    private fun deberiasMostrarAnuncio(): Boolean {
        val tiempoActual = System.currentTimeMillis()
        
        // Verificar si está en período sin anuncios (recompensa activa)
        if (tiempoActual < tiempoSinAnunciosHasta) {
            val tiempoRestante = (tiempoSinAnunciosHasta - tiempoActual) / 1000 / 60
            Log.d("Anuncios", "Período sin anuncios activo. Quedan $tiempoRestante minutos")
            return false
        }
        
        return true
    }

    /**
     * Verifica si hay reproducción activa
     * Retorna false si el servicio no está disponible o no inicializado
     */
    private fun hayReproduccionActiva(): Boolean {
        return try {
            radioService?.isPlaying() == true
        } catch (e: Exception) {
            Log.e("Anuncios", "Error al verificar reproducción: ${e.message}")
            false
        }
    }

    /**
     * Muestra un anuncio intersticial si cumple las condiciones
     */
    private fun mostrarAnuncioSiCorresponde() {
        // NUNCA interrumpir reproducción activa
        if (hayReproduccionActiva()) {
            Log.d("Anuncios", "No mostrar: reproducción activa")
            return
        }
        
        // Verificar si debe mostrar el anuncio
        if (!deberiasMostrarAnuncio()) {
            Log.d("Anuncios", "Período sin anuncios activo")
            return
        }
        
        // Mostrar anuncio si está disponible
        if (interstitial != null) {
            interstitial?.show(this@MainActivity)
            Log.d("Anuncios", "Mostrando anuncio intersticial")
        } else {
            Log.d("Anuncios", "No hay anuncio precargado disponible")
            precargarAnuncioIntersticial() // Intentar precargar para la próxima vez
        }
    }

    /**
     * Muestra un anuncio recompensado para obtener tiempo sin anuncios
     */
    private fun mostrarAnuncioRecompensado() {
        if (rewardedAd != null) {
            rewardedAd?.show(this, OnUserEarnedRewardListener { reward ->
                // Usuario gana 30 minutos sin anuncios
                tiempoSinAnunciosHasta = System.currentTimeMillis() + (30 * 60 * 1000)
                val mensaje = "¡Disfruta 30 minutos sin anuncios!"
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                Log.d("Anuncios", "Recompensa otorgada: 30 min sin anuncios")
            })
        } else {
            Toast.makeText(this, "Anuncio no disponible, intenta más tarde", Toast.LENGTH_SHORT).show()
            precargarAnuncioRecompensado() // Intentar precargar
        }
    }

    /**
     * Muestra un diálogo explicando el anuncio recompensado
     */
    private fun mostrarDialogoAnuncioRecompensado() {
        val tiempoActual = System.currentTimeMillis()
        
        // Verificar si ya tiene tiempo sin anuncios activo
        if (tiempoActual < tiempoSinAnunciosHasta) {
            val minutosRestantes = (tiempoSinAnunciosHasta - tiempoActual) / 1000 / 60
            val alertDialog = AlertDialog.Builder(this)
            alertDialog.setTitle("Sin Anuncios Activo")
            alertDialog.setIcon(R.mipmap.ic_launcher_round)
            alertDialog.setMessage("Ya tienes $minutosRestantes minutos restantes sin anuncios.\n\n¿Deseas ver otro anuncio para extender el tiempo?")
            alertDialog.setPositiveButton("Ver Anuncio") { _, _ ->
                mostrarAnuncioRecompensado()
            }
            alertDialog.setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog.show()
        } else {
            val alertDialog = AlertDialog.Builder(this)
            alertDialog.setTitle("Escucha sin Anuncios")
            alertDialog.setIcon(R.mipmap.ic_launcher_round)
            alertDialog.setMessage(
                "Ve un anuncio corto y disfruta de 30 minutos escuchando tus emisoras favoritas sin interrupciones.\n\n" +
                "Durante este tiempo no se mostrarán anuncios mientras cambias de emisora."
            )
            alertDialog.setPositiveButton("Ver Anuncio") { _, _ ->
                mostrarAnuncioRecompensado()
            }
            alertDialog.setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            alertDialog.show()
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
        })
        viewModel.stationsProviders()
    }

    fun startService(stations: StationsModel) {
        // Mostrar anuncio cada vez que inicia una emisora (si no está en período sin anuncios)
        mostrarAnuncioSiCorresponde()
        
        val intent = Intent(this, RadioService::class.java)
        bindService(intent, myConnection, Context.BIND_AUTO_CREATE)
        intent.putExtra("URL", stations.link)
        intent.putExtra("NAME", stations.name)
        intent.putExtra("IMAGE", stations.imagen)
        startService(intent)
        
        val viewModel: MainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        viewModel.getLiveDataStation().observe(this, Observer {
            if (it) {
                binding.idFavoriteRed.isVisible = true
                binding.idFavoriteWhite.isVisible = false
            }
        })
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
        val httpConnection: HttpURLConnection =
            withContext(Dispatchers.IO) {
                URL(url)
                    .openConnection()
            } as HttpURLConnection
        if (checkForInternet(this)) {
            try {
                httpConnection.setRequestProperty("User-Agent", "Android")
                httpConnection.connectTimeout = 8000
                withContext(Dispatchers.IO) {
                    httpConnection.connect()
                }
            } catch (e: Exception) {
                println(e.toString())
                return 404
            }
        }
        return httpConnection.responseCode
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET])
    suspend fun dataConexion(url: String): Boolean {
        delay(1500)
        val httpConnection: HttpURLConnection =
            withContext(Dispatchers.IO) {
                URL(url)
                    .openConnection()
            } as HttpURLConnection
        if (checkForInternet(this)) {
            try {
                httpConnection.setRequestProperty("User-Agent", "Android")
                httpConnection.connectTimeout = 1500
                withContext(Dispatchers.IO) {
                    httpConnection.connect()
                }
                return httpConnection.responseCode == 200
            } catch (e: Exception) {
                println(e.toString())
                return false
            }
        }
        return false
    }

    fun isServiceRunning(mClass: Class<RadioService>): Boolean {

        val manager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service: ActivityManager.RunningServiceInfo in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (mClass.name.equals(service.service.className)) {
                return true
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
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

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
            R.id.favoriteList -> {
                val intent = Intent(baseContext, FavoriteActivity::class.java)
                startActivity(intent)
            }

            R.id.info -> {
                val alertdialog = AlertDialog.Builder(this)
                alertdialog.setTitle("Acerca de Radio Cubana")
                alertdialog.setMessage(
                    "Esta aplicación nos permite escuchar las principales emisoras nacionales de radio desde el móvil.\n" +
                            "Requiere estar conectado a internet.\n" +
                            "Siempre debe recordar que en caso de que habrá alguna emisora y no este disponible es porque hay emisoras que no están al aire las 24 horas del día.\n" +
                            "Con Radio Cubana podemos estar todo el tiempo informado de las últimas noticias, escuchar música y disfrutar de los partidos de béisbol de la serie nacional etc."
                )
                alertdialog.setPositiveButton("Aceptar") { _, _ ->

                }
                alertdialog.show()
            }

            R.id.contact -> {
                val bindingcontact = ContactoBinding.inflate(layoutInflater)
                val alertdialog = AlertDialog.Builder(this)
                alertdialog.setTitle("Contacto")
                alertdialog.setView(bindingcontact.root)
                bindingcontact.layoutemail.setOnClickListener(View.OnClickListener {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.data = Uri.parse("Email")
                    val array_email = arrayOf("susoluciones.software@gmail.com")
                    intent.putExtra(Intent.EXTRA_EMAIL, array_email)
                    intent.putExtra(Intent.EXTRA_SUBJECT, "suSoluciones")
                    intent.putExtra(Intent.EXTRA_TEXT, "")
                    intent.type = "message/rfc822"
                    val a = Intent.createChooser(intent, "Launch Email")
                    startActivity(a)
                })
                bindingcontact.layoutshare.setOnClickListener(View.OnClickListener {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.putExtra(
                        "android.intent.extra.TEXT", "¡Hola!\n" +
                                "Te estoy invitando a que uses Radio Cubana, con ella puedes escuchar las emisoras nacionales desde tu telefono\n" +
                                "\n" +
                                "Descárgala de: https://play.google.com/store/apps/details?id=com.ejrm.radiocubana.pro"
                    )
                    intent.type = "text/plain"
                    startActivity(intent)
                })
                bindingcontact.layouttelegram.setOnClickListener(View.OnClickListener {
                    openLink(Uri.parse("https://t.me/susoluciones"))
                })

                alertdialog.setPositiveButton("Aceptar") { _, _ ->

                }
                alertdialog.show()
            }

            R.id.valoracion -> {
                PlayStoreRatingHelper.openPlayStoreForRating(this)
            }
            
            R.id.sin_anuncios -> {
                mostrarDialogoAnuncioRecompensado()
            }
            
            R.id.politica -> {
                openLink(Uri.parse("https://www.app-privacy-policy.com/live.php?token=fUsNDhObFBDnkj2oAGyPoCvnmP8KqnCl"))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun openLink(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    fun checkForInternet(context: Context): Boolean {

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }



    override fun onResume() {
        super.onResume()
        registerReceiver(estadoRed, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

   /* override fun onStop() {
        super.onStop()
         radioService?.let {
            it.showNotification(R.drawable.ic_pause_24)
        }
        Log.d("Notifi","onStop")
    }*/


    private val estadoRed = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            iniRecyclerView()
            initViewModel()
            if (checkForInternet(baseContext)) {
                GlobalScope.launch(Dispatchers.Main) {
                    var response =
                        withContext(Dispatchers.IO) { dataConexion("https://www.google.com") }
                    if (!response) {
                        Snackbar.make(
                            binding.root,
                            "No tiene conexión con la red",
                            Snackbar.LENGTH_INDEFINITE
                        ).show()
                        binding.viewLoading.isVisible = true
                        binding.view.isVisible = false
                        binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
                    } else {
                        binding.viewLoading.isVisible = false
                        binding.view.isVisible = true
                        Snackbar.make(binding.root, "Conectado!!!", Snackbar.LENGTH_LONG).show()
                    }
                }
            } else {
                val snackbar: Snackbar = Snackbar.make(
                    binding.idConstrain,
                    "Active sus datos móviles o wifi",
                    Snackbar.LENGTH_INDEFINITE
                )
                snackbar.show()
                binding.viewLoading.isVisible = true
                binding.view.isVisible = false
                binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
            }
        }

    }
    private val myConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            if (radioService == null) {
                val binder = p1 as? RadioService.MyBinder
                radioService = binder?.currentService()
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            radioService = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()
        radioService?.let {
            it.showNotification(R.drawable.ic_pause_24)
        }
        Log.d("RadioService", "Avtivity Destruida")
    }

    private inner class EmisoraItemClickListener : StationsAdapter.StationsAdapterListener {
        override fun onEmisoraSelected(stations: StationsModel) {
            val progres = ProgressDialog(this@MainActivity)
            progres.setMessage("Cargando...")
            progres.show()
            GlobalScope.launch(Dispatchers.Main) {
                var response = withContext(Dispatchers.IO) {
                    getResponseCode(stations.link)
                }
                progres.dismiss()
                if (response != 200) {
                    println(response)
                    Snackbar.make(binding.root, "Emisora no disponible", 2000).show()
                } else {
                    station = stations
                    startService(stations)
                }
            }
        }
    }
}