package com.example.homecontrol

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.commit
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.homecontrol.databinding.FragmentScanBinding
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

class SettingsFragment : PreferenceFragmentCompat(),SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: SettingsFragment? = null
    private lateinit var fragBinding: FragmentScanBinding

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun reload() {
        parentFragmentManager.commit {
            replace(R.id.nav_host_fragment, SettingsFragment())
        }
    }


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key:String) {
        if (key != getString(R.string.books_notion_database_link_id) && key != getString(R.string.records_notion_database_link_id)) {
            Log.e("logtag", "dropping event $key")
            return
        }
        // https://www.notion.so/domgoodwin/4f311bbe86ce4dd4bdae93fa1206328f?v=ad75379bd1be4bc2847e4333b4a45988&pvs=4
        Log.e("logtag", "preference changed $key")
        val prefValue = sharedPreferences.getString(key, "")
        Log.e("logtag", "preference value $prefValue")
        var urlParts = prefValue.toString().split("/")
        val idPart = urlParts[urlParts.size-1].split("?")[0]

        var pref: String = ""
        if (key == getString(R.string.books_notion_database_link_id)) {
            pref = getString(R.string.books_notion_database_id)
        } else if (key == getString(R.string.records_notion_database_link_id)) {
            pref = getString(R.string.records_notion_database_id)
        } else {
            Log.e("logtag", "didn't find field to update")
        }
        Log.e("logtag", "updating field $pref")

        // for showing errors on DB
//        val mySnackbar = view?.let { Snackbar.make(it, "qwdqwdqwdqw", BaseTransientBottomBar.LENGTH_LONG) }
//        if (mySnackbar != null) {
//            mySnackbar.show()
//        }

        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        with (sharedPref.edit()) {
            putString(pref, idPart)
            commit()
        }
        // Commit it to the private default and this specific one so it shows up
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putString(pref, idPart)
        editor.commit()
        reload()
    }
}