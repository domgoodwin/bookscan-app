package com.example.homecontrol

// Barcode analysis

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import com.example.homecontrol.databinding.ActivityMainBinding
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double) -> Unit

typealias BarcodeListener = (barcode: List<Barcode>) -> Unit

class MainActivity : AppCompatActivity() {
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
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }


    private val client = OkHttpClient()

    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { storeBarcode() }

        viewBinding.barcodeText.setTextColor(Color.BLACK);
        viewBinding.barcodeText.setBackgroundColor(Color.WHITE)

        viewBinding.bookTitle.setTextColor(Color.BLACK);
        viewBinding.bookTitle.setBackgroundColor(Color.WHITE)

        viewBinding.imageCaptureButton.isEnabled = false
        viewBinding.imageCaptureButton.isClickable = false

        cameraExecutor = Executors.newSingleThreadExecutor()

        textToSpeech = TextToSpeech(
            applicationContext
        ) { i ->
            // if No error is found then only it will run
            if (i != TextToSpeech.ERROR) {
                textToSpeech?.setLanguage(Locale.UK)
            }
        }
    }

    private fun lookupBarcode(isbn: String?) {
        Log.i(TAG, "lookup barcode")
        var url = "http://192.168.0.30:9090/lookup?isbn=$isbn"
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
                val bookJson = jsonBody?.jsonObject?.get("book")?.let { Json.parseToJsonElement(it.toString()) }
                Log.i(TAG, "Response body: $jsonBody")
                if (jsonBody != null) {
                    var title = ""
                    if (bookJson != null) {
                        title = bookJson.jsonObject["title"].toString()
                    }
                        var alreadyStored = jsonBody.jsonObject["already_stored"]?.jsonPrimitive?.boolean
                        Log.i(TAG, "stored: $alreadyStored")
                        if (alreadyStored == true) {
                            setCurrentBook(title, BookState.OWNED)
                        } else {
                            setCurrentBook(title, BookState.NOTOWNED)
                        }
                }
            }
        })
    }

    enum class BookState {
        OWNED, SAVED, NOTOWNED, EMPTY
    }
    private fun setCurrentBook(title: String, bookState: BookState) {
        runOnUiThread {
        when(bookState) {
            BookState.OWNED -> {
                viewBinding.bookTitle.setBackgroundColor(Color.CYAN)
                viewBinding.imageCaptureButton.isClickable = false
                viewBinding.imageCaptureButton.isEnabled = false
            }
            BookState.NOTOWNED -> {
                viewBinding.bookTitle.setBackgroundColor(Color.LTGRAY)
                viewBinding.imageCaptureButton.isClickable = true
                viewBinding.imageCaptureButton.isEnabled = true
            }
            BookState.SAVED -> {
                viewBinding.bookTitle.setBackgroundColor(Color.GREEN)
                viewBinding.imageCaptureButton.isClickable = false
                viewBinding.imageCaptureButton.isEnabled = false
            }
            BookState.EMPTY -> {
                viewBinding.bookTitle.setBackgroundColor(Color.WHITE)
                viewBinding.imageCaptureButton.isClickable = true
                viewBinding.imageCaptureButton.isEnabled = true
            }
        }
        viewBinding.bookTitle.text = title
            }
    }

    private fun storeBarcode() {
        Log.i(TAG, "store barcode")
        var isbn = viewBinding.barcodeText.text
        var url = "http://192.168.0.30:9090/store"
        val jsonObject = JSONObject()
        try {
            jsonObject.put("isbn", isbn)
            jsonObject.put("notion_database_id", "4f311bbe86ce4dd4bdae93fa1206328f")
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
                    viewBinding.bookTitle.setBackgroundColor(Color.GREEN)
                }
            }
        })
    }


    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BookBarcodeScanner { barcodes ->
                        for (barcode in barcodes) {
                            // See API reference for complete list of supported types
                            when (barcode.valueType) {
                                Barcode.TYPE_ISBN -> {
                                    if (viewBinding.barcodeText.text != barcode.rawValue) {
                                        setCurrentBook("loading...", BookState.EMPTY)
                                        lookupBarcode(barcode.rawValue)
                                        viewBinding.barcodeText.text = barcode.rawValue
                                    }
                                }
                                else -> {
                                    setCurrentBook("please scan", BookState.EMPTY)
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

        }, ContextCompat.getMainExecutor(this))


    }



    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
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

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
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

