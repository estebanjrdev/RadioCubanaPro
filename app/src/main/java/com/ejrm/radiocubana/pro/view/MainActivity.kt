package com.ejrm.radiocubana.pro.view

import android.Manifest
import android.app.ActivityManager
import android.app.ProgressDialog
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import androidx.appcompat.widget.SearchView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ejrm.radiocubana.pro.R
import com.ejrm.radiocubana.pro.data.model.StationsModel
import com.ejrm.radiocubana.pro.data.model.StationsProvider
import com.ejrm.radiocubana.pro.databinding.ActivityMainBinding
import com.ejrm.radiocubana.pro.databinding.ContactoBinding
import com.ejrm.radiocubana.pro.services.RadioService
import com.ejrm.radiocubana.pro.view.adapters.StationsAdapter
import com.ejrm.radiocubana.pro.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var stationsProvider: StationsProvider
    lateinit var station: StationsModel
    companion object {
        lateinit var binding: ActivityMainBinding
        var radioService: RadioService? = null
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: StationsAdapter
    private lateinit var permissioLauncher: ActivityResultLauncher<Array<String>>
    private var isCallPermissionGranted = false
    private var isSMSPermissionGranted = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        permissioLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->
                isCallPermissionGranted =
                    permission[Manifest.permission.CALL_PHONE] ?: isCallPermissionGranted
                isSMSPermissionGranted =
                    permission[Manifest.permission.READ_SMS] ?: isSMSPermissionGranted
            }
        requestPermission()
        if (isServiceRunning(RadioService::class.java)) radioService!!.stopRadio()

        iniRecyclerView()
        initViewModel()
        binding.btnPlay.setOnClickListener(View.OnClickListener {
            if (radioService!!.isPlaying()) {
                radioService!!.controlPlay()
                binding.btnPlay.setImageResource(R.drawable.ic_play_24)
            } else {
                radioService!!.controlPlay()
                binding.btnPlay.setImageResource(R.drawable.ic_pause_24)
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
                //binding.idFavoriteRed.startAnimation(anim)
            }
        })
        binding.idFavoriteRed.setOnClickListener(View.OnClickListener {
            if (binding.idFavoriteRed.isVisible) {
                viewModel.deleteFavorite(station)
                binding.idFavoriteRed.isVisible = false
                binding.idFavoriteWhite.isVisible = true
                //binding.idFavoriteWhite.startAnimation(anim)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.CUPCAKE)
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
        val intent = Intent(this, RadioService::class.java)
        bindService(intent, myConnection, Context.BIND_AUTO_CREATE)
        // Intent(this, RadioService::class.java).also {
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

    fun isServiceRunning(mClass: Class<RadioService>): Boolean {

        val manager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service: ActivityManager.RunningServiceInfo in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (mClass.name.equals(service.service.className)) {
                return true
            }
        }
        return false
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
                val intent = Intent(this, FavoriteActivity::class.java)
                startActivity(intent)
            }
            R.id.info -> {
                val alertdialog = AlertDialog.Builder(this)
                alertdialog.setTitle("Acerca de RadioCubana")
                alertdialog.setMessage(
                    "Esta aplicación nos permite escuchar las principales emisoras nacionales de radio desde el móvil.\n" +
                            "Requiere estar conectado a internet, pero solo consume del bono de los 300 MB nacionales.\n" +
                            "Siempre debe recordar que en caso de que habrá alguna emisora y no este disponible es porque hay emisoras que no están al aire las 24 horas del día, se recomienda tener preferiblemente una conexión 3G o 4G  para que no se le detenga la reproducción del audio.\n" +
                            "Con RadioCubana podemos estar todo el tiempo informado de las últimas noticias, escuchar música y disfrutar de los partidos de béisbol de la serie nacional etc.\n" +
                            "La aplicación utiliza el icecast de teveo por lo que está sujeta a las políticas de privacidad de dicha plataforma."
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
                                " Te estoy invitando a que uses RadioCubana, con ella puedes escuchar las emisoras nacionales desde tu telefono\n" +
                                "\n" +
                                "Descárgala de: https://www.apklis.cu/application/com.ejrm.radiocubana.pro"
                    )
                    intent.type = "text/plain"
                    startActivity(intent)
                })
                bindingcontact.layouttelegram.setOnClickListener(View.OnClickListener {
                    openLink(Uri.parse("https://t.me/susoluciones"))
                })
                bindingcontact.layoutfacebook.setOnClickListener(View.OnClickListener {
                    openLink(Uri.parse("https://www.facebook.com/susoluciones"))
                })
                bindingcontact.layoutweb.setOnClickListener(View.OnClickListener {
                    openLink(Uri.parse("http://susoluciones.125mb.com"))
                })
                alertdialog.setPositiveButton("Aceptar") { _, _ ->

                }
                alertdialog.show()
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

    private val estadoRed = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            iniRecyclerView()
            initViewModel()
            if (checkForInternet(baseContext)) {
                GlobalScope.launch(Dispatchers.Main) {
                    var response =
                        withContext(Dispatchers.IO) { dataConexion("https://icecast.teveo.cu/b3jbfThq") }
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()
        radioService.let {
            it!!.showNotification(R.drawable.ic_pause_24)
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

    private fun requestPermission() {
        isCallPermissionGranted = ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        isSMSPermissionGranted = ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val request: MutableList<String> = ArrayList()
        if (!isCallPermissionGranted) {
            request.add(Manifest.permission.CALL_PHONE)
        }
        if (!isSMSPermissionGranted) {
            request.add(Manifest.permission.READ_SMS)
        }
        if (request.isNotEmpty()) {
            permissioLauncher.launch(request.toTypedArray())
        }
    }
}