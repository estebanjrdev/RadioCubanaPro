package com.ejrm.radiocubana.pro.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class PlayStoreRatingHelper {

    companion object {
        private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=<com.ejrm.radiocubana.pro>"

        fun openPlayStoreForRating(activity: Activity) {
            val appPackageName = activity.packageName

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // Si Google Play Store no está instalado, abrir en un navegador
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            }
        }

        fun showToastForRating(context: Context) {
            Toast.makeText(context, "¡Gracias por tu valoración!", Toast.LENGTH_SHORT).show()
        }
    }
}

