package com.teamapp.tracker

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SupabaseConfig {
    const val URL = "https://eszwqhemtijsymeuwgus.supabase.co"
    const val ANON_KEY = "sb_publishable_QO6S_RY2y6wCkMHwCc6_Pg_p8XjiVby"
}

data class AuthResult(val accessToken: String, val userId: String, val error: String? = null)

object SupabaseAuth {
    private val client = OkHttpClient()

    // Logs in with email/password and returns an access token to use for API calls.
    fun login(email: String, password: String): AuthResult {
        val json = JSONObject().put("email", email).put("password", password)
        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${SupabaseConfig.URL}/auth/v1/token?grant_type=password")
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try { JSONObject(text).optString("error_description", text) } catch (e: Exception) { text }
                return AuthResult("", "", errMsg)
            }
            val obj = JSONObject(text)
            val token = obj.getString("access_token")
            val userId = obj.getJSONObject("user").getString("id")
            return AuthResult(token, userId)
        }
    }
}
