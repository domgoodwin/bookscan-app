package com.example.homecontrol

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

class AuthContext {
    var userID: String? = null
    var apiKey: String? = null
    var fragmentActivity: FragmentActivity? = null

    fun Authenticated(): Boolean {
        return apiKey != null && apiKey != "" && userID != null && userID != ""
    }

    fun LoadFromPreferences(fragment: FragmentActivity) {
        val sharedPref = fragment?.getPreferences(Context.MODE_PRIVATE) ?: return
        userID = sharedPref.getString(fragment.getString(R.string.user_id),"")
        apiKey = sharedPref.getString(fragment.getString(R.string.api_token),"")
    }

    fun SaveToPreferences() {
        val sharedPref = fragmentActivity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(fragmentActivity!!.getString(R.string.user_id), userID)
            putString(fragmentActivity!!.getString(R.string.api_token), apiKey)
            commit()
        }
    }

    companion object {
        val instance = AuthContext()
    }
}