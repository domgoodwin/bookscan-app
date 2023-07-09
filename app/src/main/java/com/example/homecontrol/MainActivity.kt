package com.example.homecontrol

// Barcode analysis

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.navigation.fragment.NavHostFragment
import com.example.homecontrol.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*


typealias LumaListener = (luma: Double) -> Unit

typealias BarcodeListener = (barcode: List<Barcode>) -> Unit

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.nav_host_fragment, ScanFragment())
            }
        }

//        viewBinding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(viewBinding.root)
//        val navHostFragment =
//            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
//        val navController = navHostFragment.navController
//
//        val manager = supportFragmentManager
//        val ft = manager.beginTransaction()
//        val settingsFragment: Fragment = SettingsFragment()


//        viewBinding.button.setOnClickListener {
//            val scanFragment: Fragment = ScanFragment()
//            ft.replace(R.id.nav_host_fragment, scanFragment)
//            ft.commit()
//        }
//        navController.navigate(R.id.scanFragment)
    }


}

