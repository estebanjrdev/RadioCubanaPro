package com.ejrm.radiocubana.pro.view

import android.Manifest
import android.content.*
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ejrm.radiocubana.pro.BuildConfig
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.databinding.ActivityFavoritesBinding
import com.ejrm.radiocubana.pro.databinding.ContactoBinding
import com.ejrm.radiocubana.pro.services.RadioService
import com.ejrm.radiocubana.pro.util.Constants
import com.ejrm.radiocubana.pro.util.PlayStoreRatingHelper
import com.ejrm.radiocubana.pro.view.adapters.StationsAdapter
import com.ejrm.radiocubana.pro.viewmodel.FavoriteViewModel
import com.ejrm.radiocubana.pro.viewmodel.MainViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

@AndroidEntryPoint
class FavoriteActivity : AppCompatActivity() {

    // ── binding e instancia privados (antes en companion object → memory leak) ──
    private lateinit var binding: ActivityFavoritesBinding
    private var radioService: RadioService? = null

    lateinit var station: StationsModel
    private var interstitial: InterstitialAd? = null
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: StationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.ENABLE_CRASHLYTICS) {
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    .setCrashlyticsCollectionEnabled(true)
            } catch (e: Exception) {
                Log.e("FavoriteActivity", "Error Crashlytics: ${e.message}")
            }
        }

        initLoadAds()
        initListeners()
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        // Reconectar al servicio si ya estaba corriendo
        bindService(Intent(this, RadioService::class.java), myConnection, 0)

        iniRecyclerView()
        initViewModel()

        // Observer centralizado para el estado de favorito de la emisora actual.
        viewModel.getLiveDataStation().observe(this, Observer { isFavorite ->
            binding.idFavoriteRed.isVisible = isFavorite == true
            binding.idFavoriteWhite.isVisible = isFavorite != true
        })

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
            binding.idFavoriteRed.isVisible = false
            binding.idFavoriteWhite.isVisible = true
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
    }

    private fun iniRecyclerView() {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = StationsAdapter(EmisoraItemClickListener())
        binding.recycler.adapter = adapter
    }

    private fun initListeners() {
        interstitial?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {}
            override fun onAdFailedToShowFullScreenContent(p0: AdError) {}
            override fun onAdShowedFullScreenContent() { interstitial = null }
        }
    }

    private fun initLoadAds() {
        val adRequest = AdRequest.Builder().build()
        binding.banner.loadAd(adRequest)
        binding.banner.adListener = object : AdListener() {
            override fun onAdLoaded() {}
            override fun onAdFailedToLoad(adError: LoadAdError) {}
            override fun onAdOpened() {}
            override fun onAdClicked() {}
            override fun onAdClosed() {}
        }
    }

    private fun initViewModel() {
        val viewModel: FavoriteViewModel = ViewModelProvider(this).get(FavoriteViewModel::class.java)
        viewModel.getLiveDataObserver().observe(this, Observer { list ->
            // Ocultar shimmer siempre que tengamos respuesta del observer
            binding.shimmerFrameLayout.isVisible = false
            if (list != null && list.isNotEmpty()) {
                adapter.setStationsList(list)
                adapter.notifyDataSetChanged()
                binding.view.visibility = android.view.View.VISIBLE
                binding.layoutEmptyState.visibility = android.view.View.GONE
            } else {
                binding.view.visibility = android.view.View.GONE
                binding.layoutEmptyState.visibility = android.view.View.VISIBLE
            }
        })
        viewModel.stationsFavorite()
    }

    fun startService(stations: StationsModel) {
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
                httpConnection.responseCode
            } catch (e: java.io.EOFException) {
                Log.d("FavoriteActivity", "Stream disponible (EOFException esperada): ${e.message}")
                200
            } catch (e: Exception) {
                Log.e("FavoriteActivity", "Error verificando URL: ${e.message}")
                404
            } finally {
                httpConnection?.disconnect()
            }
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET])
    suspend fun dataConexion(url: String): Boolean {
        delay(1500)
        val httpConnection = URL(url).openConnection() as HttpURLConnection
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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
            bindService(Intent(this, RadioService::class.java), myConnection, 0)
        } else {
            // Ya tenemos referencia: sincronizar UI con el estado real del servicio
            syncUIWithServiceState()
        }
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
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
     * activity estaba pausada (ej: se detuvo desde MainActivity o la notificación).
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
            // No hay reproducción activa → ocultar barra
            binding.layoutReproduction.visibility = LinearLayout.INVISIBLE
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
            Log.w("FavoriteActivity", "networkCallback no registrado: ${e.message}")
        }
        try {
            unregisterReceiver(playbackStateReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("FavoriteActivity", "playbackStateReceiver no registrado: ${e.message}")
        }
    }

    /** NetworkCallback reemplaza el deprecated CONNECTIVITY_ACTION */
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
                    binding.idFavoriteRed.isVisible = false
                    binding.idFavoriteWhite.isVisible = true
                }
                Constants.ACTION_PLAYBACK_STATE_CHANGED -> {
                    val isPlaying = intent.getBooleanExtra(Constants.EXTRA_IS_PLAYING, false)
                    binding.btnPlay.setImageResource(
                        if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
                    )
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
            Log.w("FavoriteActivity", "Servicio no estaba vinculado: ${e.message}")
        }
        Log.d("FavoriteActivity", "Activity destruida")
    }

    private inner class EmisoraItemClickListener : StationsAdapter.StationsAdapterListener {
        override fun onEmisoraSelected(stations: StationsModel) {
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
