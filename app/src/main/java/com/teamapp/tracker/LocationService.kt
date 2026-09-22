package com.teamapp.tracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val httpClient = OkHttpClient()

    companion object {
        const val CHANNEL_ID = "team_tracker_channel"
        const val NOTIF_ID = 1001
        const val SEND_INTERVAL_MS = 20000L
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Location tracking active")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SEND_INTERVAL_MS)
            .setMinUpdateIntervalMillis(SEND_INTERVAL_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                sendLocationToServer(loc.latitude, loc.longitude, loc.accuracy, loc.speed)
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission not granted — nothing to do here, MainActivity handles the permission request.
        }
    }

    private fun sendLocationToServer(lat: Double, lng: Double, accuracy: Float, speed: Float) {
        val prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        val userId = prefs.getString("user_id", null)
        if (token == null || userId == null) return

        val battery = getBatteryLevel()
        val network = getNetworkType()

       val json = JSONObject().apply {
            put("user_id", userId)
            put("lat", lat)
            put("lng", lng)
            put("accuracy", accuracy.toDouble())
            put("speed", speed.toDouble())
            put("battery", battery)
            put("network", network)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${SupabaseConfig.URL}/rest/v1/locations")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                // 401 means the token expired — try a silent re-login using saved credentials.
                if (response.code == 401) reLogin()
                response.close()
            }
        })
    }

    private fun reLogin() {
        val prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null)
        val password = prefs.getString("password", null)
        if (email == null || password == null) return

        val result = SupabaseAuth.login(email, password)
        if (result.error == null) {
            prefs.edit().putString("access_token", result.accessToken).apply()
        }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getNetworkType(): String {
        return "mobile" // simplified — Android network-type detection needs extra permissions/APIs
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Team Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?) = null
}
