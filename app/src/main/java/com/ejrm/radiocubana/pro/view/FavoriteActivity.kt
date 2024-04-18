package com.ejrm.radiocubana.pro.view

import android.Manifest
import android.app.ProgressDialog
import android.content.*
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
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
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.databinding.ActivityFavoritesBinding
import com.ejrm.radiocubana.pro.databinding.ContactoBinding
import com.ejrm.radiocubana.pro.services.RadioService
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
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

@AndroidEntryPoint
class FavoriteActivity : AppCompatActivity() {
    lateinit var station: StationsModel
    private var interstitial: InterstitialAd? = null
    companion object {
        lateinit var binding: ActivityFavoritesBinding
        var radioService: RadioService? = null
    }
    private  lateinit var viewModel: MainViewModel
    private lateinit var adapter: StationsAdapter
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        initLoadAds()
        initListeners()
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        // if (isServiceRunning(RadioService::class.java)) radioService!!.stopRadio()

        iniRecyclerView()
        initViewModel()

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
            binding.idFavoriteRed.isVisible = false
            binding.idFavoriteWhite.isVisible = true
        })

        binding.idFavoriteWhite.setOnClickListener(View.OnClickListener {
            if(binding.idFavoriteWhite.isVisible){
                viewModel.addFavorite(station)
                binding.idFavoriteWhite.isVisible = false
                binding.idFavoriteRed.isVisible = true
                binding.idFavoriteRed.contentDescription = "Eliminar de Favoritos"
            }
        })
        binding.idFavoriteRed.setOnClickListener(View.OnClickListener {
            if(binding.idFavoriteRed.isVisible){
                viewModel.deleteFavorite(station)
                binding.idFavoriteRed.isVisible = false
                binding.idFavoriteWhite.isVisible = true
                binding.idFavoriteWhite.contentDescription = "Agregar a Favoritos"
            }
        })

    }

    @RequiresApi(Build.VERSION_CODES.CUPCAKE)
    private fun iniRecyclerView() {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = StationsAdapter(EmisoraItemClickListener())
        binding.recycler.adapter = adapter
    }


    private fun initListeners() {
        interstitial?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
            }

            override fun onAdShowedFullScreenContent() {
                interstitial = null
            }
        }
    }


    private fun initLoadAds() {
        val adRequest = AdRequest.Builder().build()
        FavoriteActivity.binding.banner.loadAd(adRequest)

        FavoriteActivity.binding.banner.adListener = object : AdListener() {
            override fun onAdLoaded() {
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
            }

            override fun onAdOpened() {
            }

            override fun onAdClicked() {
            }

            fun onAdLeftApplication() {
            }

            override fun onAdClosed() {
            }
        }
    }

    private fun initViewModel(){
        val viewModel: FavoriteViewModel = ViewModelProvider(this).get(FavoriteViewModel::class.java)
        viewModel.getLiveDataObserver().observe(this, Observer {
            if(it != null) {
                adapter.setStationsList(it)
                adapter.notifyDataSetChanged()
            } else {
                Toast.makeText(this, "Error in getting list", Toast.LENGTH_SHORT).show()
            }
        })
        viewModel.stationsFavorite()
    }

    fun startService(stations: StationsModel) {
        val intent = Intent(this, RadioService::class.java)
        bindService(intent, myConnection, Context.BIND_AUTO_CREATE)
        // Intent(this, RadioService::class.java).also {
        intent.putExtra("URL", stations.link)
        intent.putExtra("NAME", stations.name)
        intent.putExtra("IMAGE", stations.imagen)
        startService(intent)
        val viewModel: MainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        viewModel.getLiveDataStation().observe(this, Observer {
            if(it) {
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
        //  }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.INTERNET])
    suspend fun getResponseCode(url: String): Int {
        delay(1500)
        val httpConnection: HttpURLConnection =
            URL(url)
                .openConnection() as HttpURLConnection
        if (checkForInternet(this)) {
            try {
                httpConnection.setRequestProperty("User-Agent", "Android")
                httpConnection.connectTimeout = 8000
                httpConnection.connect()
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
            URL(url)
                .openConnection() as HttpURLConnection
        if (checkForInternet(this)) {
            try {
                httpConnection.setRequestProperty("User-Agent", "Android")
                httpConnection.connectTimeout = 1500
                httpConnection.connect()
                return httpConnection.responseCode == 200
            } catch (e: Exception) {
                println(e.toString())
                return false
            }
        }
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home ->{
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun checkForInternet(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(estadoRed, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }



    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private val estadoRed = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            iniRecyclerView()
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
                val binder = p1 as RadioService.MyBinder
                radioService = binder.currentService()
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            radioService = null
        }
    }
    override fun onStop() {
        super.onStop()
        // Toast.makeText(this@MainActivity, "onStop", Toast.LENGTH_SHORT).show()

        //   radioService?.let {
        //        it.showNotification(R.drawable.ic_pause_24)
        //   }
        Log.d("Notifi","onStop")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()
        MainActivity.radioService?.let {
            it.showNotification(R.drawable.ic_pause_24)
        }
        Log.d("RadioService", "Avtivity Destruida")
    }
    private inner class EmisoraItemClickListener: StationsAdapter.StationsAdapterListener {
        override fun onEmisoraSelected(stations: StationsModel) {
            val progres = ProgressDialog(this@FavoriteActivity)
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