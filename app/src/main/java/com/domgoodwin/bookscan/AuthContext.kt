package com.domgoodwin.bookscan

import android.content.Context
import androidx.fragment.app.FragmentActivity

class AuthContext {
    var userID: String? = null
    var apiKey: String? = null
    var fragmentActivity: FragmentActivity? = null

    fun authenticated(): Boolean {
        return apiKey != null && apiKey != "" && userID != null && userID != ""
    }

    fun loadFromPreference(fragment: FragmentActivity) {
        val sharedPref = fragment.getPreferences(Context.MODE_PRIVATE) ?: return
        userID = sharedPref.getString(fragment.getString(R.string.user_id),"")
        apiKey = sharedPref.getString(fragment.getString(R.string.api_token),"")
    }

    fun saveToPreferences() {
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