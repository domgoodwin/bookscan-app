package com.example.homecontrol

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity


class CallbackAuth : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val myIntent = Intent(this, MainActivity::class.java)

        val action: String? = intent?.action
        val data: Uri? = intent?.data

        var urlParts = data.toString().split("?")
        var params = ""
        if (urlParts.count() > 1) {
            params = urlParts[1]
        }
        var paramParts = params.split("&")

        val authContext = AuthContext.instance
        for (part in paramParts) {
            var parts = part.split("=")
            if (parts.count() != 2) {
                continue
            }
            var key = parts[0]
            var value = parts[1]
            if (key == "api_token") {
                authContext.apiKey = value
            }
            if (key == "user_id") {
                authContext.userID = value
            }
        }

        authContext.SaveToPreferences()


        Log.i("CALLBACK", "data: $data; action: $action")

        startActivity(myIntent)
    }
}
