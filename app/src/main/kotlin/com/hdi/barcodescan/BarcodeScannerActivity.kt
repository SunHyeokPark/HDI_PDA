package com.hdi.barcodescan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScannerActivity : AppCompatActivity() {
    
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var closeButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalysis: ImageAnalysis? = null
    private var isScanning = true
    
    companion object {
        private const val TAG = "BarcodeScanner"
        private const val CAMERA_PERMISSION_CODE = 100
        const val RESULT_BARCODE = "barcode_result"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)
        
        previewView = findViewById(R.id.preview_view)
        statusText = findViewById(R.id.status_text)
        closeButton = findViewById(R.id.close_button)
        
        closeButton.setOnClickListener {
            finish()
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // Image analysis
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        onBarcodeDetected(barcode)
                    })
                }
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onBarcodeDetected(barcode: String) {
        if (!isScanning) return
        
        // GUID 패턴 검증
        val guidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        
        var cleanBarcode = barcode.trim()
        
        // 중괄호 제거
        if (cleanBarcode.startsWith("{") && cleanBarcode.endsWith("}")) {
            cleanBarcode = cleanBarcode.substring(1, cleanBarcode.length - 1)
        }
        
        if (guidPattern.matches(cleanBarcode)) {
            isScanning = false
            
            runOnUiThread {
                statusText.text = "✓ 스캔 성공!"
                statusText.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                
                // 진동
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(200)
            }
            
            // 결과 반환
            val resultIntent = Intent()
            resultIntent.putExtra(RESULT_BARCODE, cleanBarcode)
            setResult(RESULT_OK, resultIntent)
            
            // 약간 지연 후 종료
            previewView.postDelayed({
                finish()
            }, 500)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // ML Kit 바코드 분석기
    private class BarcodeAnalyzer(
        private val onBarcodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        private val scanner = BarcodeScanning.getClient()
        
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                Log.d(TAG, "Barcode detected: $value")
                                onBarcodeDetected(value)
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
