package com.teamapp.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startServiceNow() }

    private val fineLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (!granted) {
                Toast.makeText(this, "Location permission අවශ්‍යයි", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                startServiceNow()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        statusText = findViewById(R.id.statusText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        // Already logged in and service running? just show status.
        val prefs = getSharedPreferences("session", MODE_PRIVATE)
        if (prefs.getString("access_token", null) != null) {
            statusText.text = "Login වෙලා — tracking active"
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email සහ Password දෙකම දාන්න", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            statusText.text = "Login වෙමින්..."
            Thread {
                val result = SupabaseAuth.login(email, password)
                runOnUiThread {
                    if (result.error != null) {
                        statusText.text = "Error: ${result.error}"
                        return@runOnUiThread
                    }
                    prefs.edit()
                        .putString("access_token", result.accessToken)
                        .putString("user_id", result.userId)
                        .putString("email", email)
                        .putString("password", password) // used only for silent token refresh
                        .apply()
                    statusText.text = "Login සාර්ථකයි — permissions ඉල්ලමින්..."
                    requestPermissionsThenStart()
                }
            }.start()
        }
    }

    private fun requestPermissionsThenStart() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                startServiceNow()
            }
        } else {
            fineLocationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun startServiceNow() {
        val intent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, intent)
        statusText.text = "Tracking active — app එක minimize කරන්නත් පුළුවන්"
    }
}
