package com.ejrm.radiocubana.pro
import android.app.Application
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RadioCubanaProApp: Application(){
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
    }
}