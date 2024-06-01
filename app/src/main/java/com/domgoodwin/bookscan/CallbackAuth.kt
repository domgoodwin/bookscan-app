package com.domgoodwin.bookscan

import android.content.Intent
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

        Log.i("CALLBACK", "start data: $data; action: $action")

        val urlParts = data.toString().split("?")
        var params = ""
        if (urlParts.count() > 1) {
            params = urlParts[1]
        }
        val paramParts = params.split("&")

        val authContext = AuthContext.instance
        for (part in paramParts) {
            val parts = part.split("=")
            if (parts.count() != 2) {
                continue
            }
            val key = parts[0]
            val value = parts[1]
            if (key == "api_token") {
                authContext.apiKey = value
            }
            if (key == "user_id") {
                authContext.userID = value
            }
        }

        authContext.saveToPreferences()


        Log.i("CALLBACK", "data: $data; action: $action")

        startActivity(myIntent)
    }
}
