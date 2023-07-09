package com.example.homecontrol

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData.Item
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.homecontrol.databinding.ActivityMainBinding
import com.example.homecontrol.databinding.FragmentScanBinding
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An example full-screen fragment that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class ScanFragment : Fragment(R.layout.fragment_scan) {
    private val hideHandler = Handler(Looper.myLooper()!!)

    private val TAG = "LOG_TAG"

    companion object {
        /**
         * Whether or not the system UI should be auto-hidden after
         * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
         */
        private const val AUTO_HIDE = true

        /**
         * If [AUTO_HIDE] is set, the number of milliseconds to wait after
         * user interaction before hiding the system UI.
         */
        private const val AUTO_HIDE_DELAY_MILLIS = 3000

        /**
         * Some older devices needs a small delay between UI widget updates
         * and a change of the status and navigation bar.
         */
        private const val UI_ANIMATION_DELAY = 300
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(activity,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    @Suppress("InlinedApi")
    private val hidePart2Runnable = Runnable {
        // Delayed removal of status and navigation bar

        // Note that some of these constants are new as of API 16 (Jelly Bean)
        // and API 19 (KitKat). It is safe to use them, as they are inlined
        // at compile-time and do nothing on earlier devices.
        val flags =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        activity?.window?.decorView?.systemUiVisibility = flags
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
    }
    private val showPart2Runnable = Runnable {
        // Delayed display of UI elements
        fullscreenContentControls?.visibility = View.VISIBLE
    }
    private var visible: Boolean = false
    private val hideRunnable = Runnable { hide() }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private val delayHideTouchListener = View.OnTouchListener { _, _ ->
        if (false) {
            delayedHide(100)
        }
        false
    }

    private var dummyButton: Button? = null
    private var fullscreenContent: View? = null
    private var fullscreenContentControls: View? = null

    private val client = OkHttpClient()

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    var textToSpeech: TextToSpeech? = null
    var barcodeType: BarcodeType = BarcodeType.UNKNOWN

    enum class ItemState {
        OWNED, SAVED, NOTOWNED, EMPTY, ERROR
    }
    enum class BarcodeType {
        BOOK, PRODUCT, UNKNOWN
    }

    private var _binding: FragmentScanBinding? = null
    private lateinit var fragBinding: FragmentScanBinding

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentScanBinding.inflate(inflater, container, false)
        if (_binding != null ) {
            fragBinding = _binding as FragmentScanBinding
        }

        // Request camera permissions
        var hasPermission = false
        context?.let { hasPermission = ActivityCompat.checkSelfPermission(it, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        if (hasPermission) {
            Log.e(TAG, "already have permissions for camera")
            startCamera()
        } else {
            Log.e(TAG, "requesting camera permissions")
            requestPermissions()
        }

        // Set up the listeners for saving the barcode
        _binding?.imageCaptureButton?.setOnClickListener { storeBarcode() }

        _binding?.barcodeText?.setTextColor(Color.BLACK);
        _binding?.barcodeText?.setBackgroundColor(Color.WHITE)

        _binding?.bookTitle?.setTextColor(Color.BLACK);
        _binding?.bookTitle?.setBackgroundColor(Color.WHITE)

        _binding?.imageCaptureButton?.isEnabled = false
        _binding?.imageCaptureButton?.isClickable = false

        cameraExecutor = Executors.newSingleThreadExecutor()

        textToSpeech = TextToSpeech(
            activity
        ) { i ->
            // if No error is found then only it will run
            if (i != TextToSpeech.ERROR) {
                textToSpeech?.setLanguage(Locale.UK)
            }
        }


        fragBinding.settingsButton.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.nav_host_fragment, SettingsFragment())
                addToBackStack(null)
            }
        }

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

    }

    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100)
    }

    override fun onPause() {
        super.onPause()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // Clear the systemUiVisibility flag
        activity?.window?.decorView?.systemUiVisibility = 0
        show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun toggle() {
        if (visible) {
            hide()
        } else {
            show()
        }
    }

    private fun hide() {
        // Hide UI first
        fullscreenContentControls?.visibility = View.GONE
        visible = false

        // Schedule a runnable to remove the status and navigation bar after a delay
        hideHandler.removeCallbacks(showPart2Runnable)
        hideHandler.postDelayed(hidePart2Runnable, 100)
    }

    @Suppress("InlinedApi")
    private fun show() {
        // Show the system bar
        fullscreenContent?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        visible = true

        // Schedule a runnable to display UI elements after a delay
        hideHandler.removeCallbacks(hidePart2Runnable)
        hideHandler.postDelayed(showPart2Runnable, 100)
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }

    /**
     * Schedules a call to hide() in [delayMillis], canceling any
     * previously scheduled calls.
     */
    private fun delayedHide(delayMillis: Int) {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, delayMillis.toLong())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startCamera() {
        val cameraProviderFuture = activity?.let { ProcessCameraProvider.getInstance(it.baseContext) }

        if (cameraProviderFuture != null) {
            cameraProviderFuture.addListener({
                // Used to bind the lifecycle of cameras to the lifecycle owner
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                Log.e(TAG, "building preview")
                // Preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(fragBinding.viewFinder.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, BookBarcodeScanner { barcodes ->
                            for (barcode in barcodes) {
                                // See API reference for complete list of supported type
                                if (fragBinding.barcodeText.text != barcode.rawValue) {
                                    setCurrentThing("loading...", ItemState.EMPTY)
                                    when (barcode.valueType) {
                                        Barcode.TYPE_ISBN -> {
                                            fragBinding.bookType.text = "\uD83D\uDCDA"
                                            barcodeType = BarcodeType.BOOK
                                            lookupBarcode(barcode.rawValue, BarcodeType.BOOK)
                                            fragBinding.barcodeText.text = barcode.rawValue
                                        }

                                        Barcode.TYPE_PRODUCT -> {
                                            fragBinding.bookType.text = "\uD83D\uDCBD"
                                            barcodeType = BarcodeType.PRODUCT
                                            lookupBarcode(barcode.rawValue, BarcodeType.PRODUCT )
                                            fragBinding.barcodeText.text = barcode.rawValue
                                        }

                                        else -> {
                                            Log.i(
                                                TAG,
                                                "unknown type, is: ${barcode.valueType}"
                                            )
                                            setCurrentThing("please scan", ItemState.EMPTY)
                                        }
                                    }
                                }
                            }
                        })
                    }

                // Select back camera as a default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer)



                } catch(exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }

            }, activity?.let { ContextCompat.getMainExecutor(it.baseContext) })
        }
    }

    private fun storeBarcode() {
            when (barcodeType) {
                BarcodeType.BOOK -> {
                    storeBook()
                }

                BarcodeType.PRODUCT -> {
                    storeVinyl()
                }

                else -> {
                    // Shouldn't button be disabled?
                }
            }


    }

    private fun getSharedPrefNotionValue(type: BarcodeType): String? {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
        if (sharedPref != null) {
            when (type) {
                BarcodeType.BOOK -> {
                    val value = sharedPref.getString(getString(R.string.books_notion_database_id), "")
                    Log.e(TAG, "book: $value")
                    return value
                }

                BarcodeType.PRODUCT -> {
                    val value = sharedPref.getString(getString(R.string.records_notion_database_id), "")
                    Log.e(TAG, "record: $value")
                    return value
                }

                else -> {
                    Log.e(TAG, "type is unknown")
                    return ""
                }
            }
        }
        Log.e(TAG, "prefs is null")
        return ""
    }

    private fun storeVinyl() {
        val notionDatabaseID = getSharedPrefNotionValue(BarcodeType.PRODUCT)
        Log.i(TAG, "store barcode")
        var barcode = fragBinding.barcodeText.text
        var url = "http://192.168.0.30:9090/record/store"
        val jsonObject = JSONObject()
        try {
            jsonObject.put("barcode", barcode)
            jsonObject.put("notion_database_id", notionDatabaseID)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val JSON = MediaType.parse("application/json; charset=utf-8")
        val body = RequestBody.create(JSON, jsonObject.toString())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "request failure: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val jsonBody = response.body()?.let { Json.parseToJsonElement(it.string()) }
                val bookJson = jsonBody?.jsonObject?.get("record")?.let { Json.parseToJsonElement(it.toString()) }
                Log.i(TAG, "Response body: $jsonBody")
                if (jsonBody != null) {
                    var title = bookJson?.jsonObject?.get("title").toString()
                    Log.i(TAG, "title: $title")
                    textToSpeech?.speak(title,TextToSpeech.QUEUE_FLUSH,null, "");
                    fragBinding.bookTitle.setBackgroundColor(Color.GREEN)
                }
            }
        })
    }
    private fun storeBook() {
        val notionDatabaseID = getSharedPrefNotionValue(BarcodeType.BOOK)
        Log.i(TAG, "store barcode")
        var isbn = fragBinding.barcodeText.text
        var url = "http://192.168.0.30:9090/book/store"
        val jsonObject = JSONObject()
        try {
            jsonObject.put("isbn", isbn)
            jsonObject.put("notion_database_id", notionDatabaseID)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val JSON = MediaType.parse("application/json; charset=utf-8")
        val body = RequestBody.create(JSON, jsonObject.toString())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "request failure: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val jsonBody = response.body()?.let { Json.parseToJsonElement(it.string()) }
                val bookJson = jsonBody?.jsonObject?.get("book")?.let { Json.parseToJsonElement(it.toString()) }
                Log.i(TAG, "Response body: $jsonBody")
                if (jsonBody != null) {
                    var title = bookJson?.jsonObject?.get("title").toString()
                    Log.i(TAG, "title: $title")
                    textToSpeech?.speak(title,TextToSpeech.QUEUE_FLUSH,null, "");
                    _binding?.bookTitle?.setBackgroundColor(Color.GREEN)
                }
            }
        })
    }

    private fun lookupBarcode(barcode: String?, type: BarcodeType) {
        var path = ""
        var param = ""
        val databaseID = getSharedPrefNotionValue(barcodeType)
        if (databaseID == null || databaseID == "") {
            Log.e(TAG, "error, database ID not set?")
            setCurrentThing("Failed to get database ID", ItemState.ERROR)
            return
        }
        when (barcodeType) {
            BarcodeType.BOOK -> {
                path = "book"
                param = "isbn"
            }
            BarcodeType.PRODUCT -> {
                path = "record"
                param = "barcode"
            }
            else -> {
                return
            }
        }
        Log.i(TAG, "lookup $path $barcode $databaseID")
        var url = "http://192.168.0.30:9090/$path/lookup?$param=$barcode&database_id=$databaseID"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "request failure: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                val jsonBody = response.body()?.let { Json.parseToJsonElement(it.string()) }
                val bookJson = jsonBody?.jsonObject?.get(path)?.let { Json.parseToJsonElement(it.toString()) }
                Log.i(TAG, "Response body: $jsonBody")
                if (jsonBody != null) {
                    var title = ""
                    if (bookJson != null) {
                        title = bookJson.jsonObject["title"].toString()
                    }
                    var alreadyStored = jsonBody.jsonObject["already_stored"]?.jsonPrimitive?.boolean
                    Log.i(TAG, "stored: $alreadyStored")
                    if (alreadyStored == true) {
                        setCurrentThing(title, ItemState.OWNED)
                    } else {
                        setCurrentThing(title, ItemState.NOTOWNED)
                    }
                }
            }
        })
    }

    private fun setCurrentThing(title: String, itemState: ItemState) {
        activity?.runOnUiThread {
            when(itemState) {
                ItemState.OWNED -> {
                    _binding?.bookTitle?.setBackgroundColor(Color.CYAN)
                    _binding?.imageCaptureButton?.isClickable = false
                    _binding?.imageCaptureButton?.isEnabled = false
                }

                ItemState.NOTOWNED -> {
                    _binding?.bookTitle?.setBackgroundColor(Color.LTGRAY)
                    _binding?.imageCaptureButton?.isClickable = true
                    _binding?.imageCaptureButton?.isEnabled = true
                }

                ItemState.SAVED -> {
                    _binding?.bookTitle?.setBackgroundColor(Color.GREEN)
                    _binding?.imageCaptureButton?.isClickable = false
                    _binding?.imageCaptureButton?.isEnabled = false
                }

                ItemState.EMPTY -> {
                    _binding?.bookTitle?.setBackgroundColor(Color.WHITE)
                    _binding?.imageCaptureButton?.isClickable = false
                    _binding?.imageCaptureButton?.isEnabled = false
                }
                ItemState.ERROR -> {
                    _binding?.bookTitle?.setBackgroundColor(Color.WHITE)
                    _binding?.bookTitle?.setTextColor(Color.RED)
                    _binding?.imageCaptureButton?.isClickable = false
                    _binding?.imageCaptureButton?.isEnabled = false
                }
            }
            _binding?.bookTitle?.text = title
        }
    }


    private class BookBarcodeScanner(private val listener : BarcodeListener) : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                // FORMAT_EAN_13 is book barcodes
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13)
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                val result = scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        listener(barcodes)
                    }
                    .addOnFailureListener {exception ->
                        Log.d(TAG, "fail: $exception")
                        var mlEx = exception as? MlKitException
                        if (mlEx != null) {
                            Log.d(TAG, "fail: ${mlEx.errorCode}")
                        }
                    }
                    .addOnCompleteListener {
                        mediaImage.close()
                        imageProxy.close() }
            }
        }
    }



}