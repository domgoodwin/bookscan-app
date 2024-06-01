package com.domgoodwin.bookscan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.domgoodwin.bookscan.databinding.FragmentAuthBinding


private const val REAUTH = "REAUTH"


class AuthFragment : Fragment() {
    private var reAuth: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            reAuth = it.getBoolean(REAUTH)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val binding = FragmentAuthBinding.inflate(inflater, container, false)
        val webView = binding.main

        webView.loadUrl("https://tower.tailce93f.ts.net:8443/auth")

        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(reAuth: Boolean) =
            AuthFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(REAUTH, reAuth)
                }
            }
    }
}
//
//private class AuthWebViewClient: WebViewClient() {
//    var flag = false
//
//    override fun shouldOverrideUrlLoading(
//        view: WebView?,
//        request: WebResourceRequest?
//    ): Boolean {
//        val url = request!!.url.toString()
//        Log.i("AUTH", "checking override url loading 2 $url")
//
//        if (url.contains("auth/redirect")) {
//            Log.i("AUTH", "url matches $url")
//            val aURL = URL(url)
//            val conn = aURL.openConnection()
//            conn.connect()
//            val inStream = conn.getInputStream()
//            Log.i("AUTH", "json: $inStream")
//            return true
//        }
//        view?.loadUrl(url)
//        return true
//    }
//}
