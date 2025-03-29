package com.example.carbofiber

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.carbofiber.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetector: ObjectDetector

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Minta izin akses kamera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        outputDirectory = getOutputDirectory() // Direktori untuk simpan gambar
        cameraExecutor = Executors.newSingleThreadExecutor()
        objectDetector = ObjectDetector(this, "model_mobilenet.tflite")
        binding.captureButton.setOnClickListener{
            takePhoto()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Menampilkan preview kamera
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }
            imageCapture = ImageCapture.Builder().build()

            // Pilih kamera belakang sebagai default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Gagal menampilkan kamera", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun takePhoto() {
        binding.progressBar.visibility = View.VISIBLE

        val imageCapture = imageCapture ?: return
        val photoFile = File(outputDirectory, SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Pengambilan foto gagal: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
//                val msg = "Foto berhasil diambil: $savedUri"
//                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                Log.d(TAG, msg)
                binding.progressBar.visibility = View.GONE

                //hentikan kamera ketika gambar diambil
                stopCameraPreview()

                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                val detectionResult = objectDetector.recognizeImage(bitmap)

                // Tampilkan gambar dalam modal bottom sheet
                showBottomSheet(savedUri, detectionResult)
            }
        })
    }

    private fun stopCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin tidak diberikan oleh pengguna.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showBottomSheet(imageUri: Uri, detectionResult: Pair<String, Float>) {
        val bottomSheetDialog = BottomSheetDialog(this)

        bottomSheetDialog.setContentView(R.layout.bottom_sheet_result)
        bottomSheetDialog.findViewById<ImageView>(R.id.captured_image)?.setImageURI(imageUri)

        val label = detectionResult.first
        bottomSheetDialog.findViewById<TextView>(R.id.tv_nama_objek)!!.text = label

        val probability = detectionResult.second
        val chipAkurasi = bottomSheetDialog.findViewById<Chip>(R.id.chip_akurasi)
        AdjustChipAkurasi(chipAkurasi, probability)

        //progress bar
        val pbKarbo = bottomSheetDialog.findViewById<ProgressBar>(R.id.pb_karbo)
        val nilaiKarbo = 38.5 * 10
        nilaiKarbo.setProgressBarKarbo(pbKarbo)
        val pbSerat = bottomSheetDialog.findViewById<ProgressBar>(R.id.pb_serat)
        val nilaiSerat = 3.8 * 10
        nilaiSerat.setProgressBarSerat(pbSerat)

        bottomSheetDialog.setOnDismissListener{
            startCamera()
        }
        // Dapatkan referensi ke BottomSheetBehavior
        val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet!!)

        // Atur tinggi bottom sheet
//        behavior.peekHeight = 3000// Atur tinggi peek (tinggi awal)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        bottomSheetDialog.show()
    }

    private fun AdjustChipAkurasi(chipAkurasi: Chip?, probability: Float) {
        val formattedProbability = String.format("%.1f", probability * 100)
        if (probability >= 0.7) {
            chipAkurasi?.text = "Kecocokan : $formattedProbability%"
            chipAkurasi?.setChipBackgroundColorResource(R.color.hijau_muda_2)
            chipAkurasi?.setChipStrokeColorResource(R.color.hijau_muda)
            chipAkurasi?.setTextColor(ContextCompat.getColor(this, R.color.hijau_muda))
        } else if (probability >= 0.5 && probability < 0.7){
            chipAkurasi?.text = "Kecocokan : $formattedProbability%"
            chipAkurasi?.setChipBackgroundColorResource(R.color.kuning_muda)
            chipAkurasi?.setChipStrokeColorResource(R.color.oranye)
            chipAkurasi?.setTextColor(ContextCompat.getColor(this, R.color.oranye))
        } else {
            chipAkurasi?.text = "Kecocokan : $formattedProbability%"
            chipAkurasi?.setChipBackgroundColorResource(R.color.merah_muda)
            chipAkurasi?.setChipStrokeColorResource(R.color.merah)
            chipAkurasi?.setTextColor(ContextCompat.getColor(this, R.color.merah))
        }
    }

    private fun Double.setProgressBarKarbo(pbKarbo: ProgressBar?) {
        val max = 1000
        pbKarbo?.max = max

        if (toInt() / max >= 0.8) {
            pbKarbo?.progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.layer_green)
        } else if (toInt() / max >= 0.5 && toInt() / max < 0.8){
            pbKarbo?.progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.layer_yellow)
        } else {
            pbKarbo?.progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.layer_red)
        }

        ObjectAnimator.ofInt(pbKarbo, "progress", toInt())
            .setDuration(2000)
            .start()
    }

    private fun Double.setProgressBarSerat(pbSerat: ProgressBar?) {
        val max = 200
        pbSerat?.max = max

        if (toInt() / max >= 0.8) {
            pbSerat?.progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.layer_green)
        } else if (toInt() / max >= 0.5 && toInt() / max < 0.8){
            pbSerat?.progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.layer_yellow)
        } else {
            pbSerat?.progressDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.layer_red)
        }

        ObjectAnimator.ofInt(pbSerat, "progress", toInt())
            .setDuration(2000)
            .start()
    }
}