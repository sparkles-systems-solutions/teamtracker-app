package com.teamapp.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
            val token = prefs.getString("access_token", null)
            if (token != null) {
                val serviceIntent = Intent(context, LocationService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
