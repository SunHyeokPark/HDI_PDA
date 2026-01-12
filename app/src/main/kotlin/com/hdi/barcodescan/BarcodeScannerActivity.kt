package com.hdi.barcodescan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private var isScanning = true

    companion object {
        private const val TAG = "BarcodeScanner"
        const val RESULT_BARCODE = "barcode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                finishWithError("카메라 초기화 실패")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: run {
            Log.e(TAG, "Camera provider is null")
            return
        }

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                    onBarcodeDetected(barcode)
                })
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // 기존 바인딩 해제
            cameraProvider.unbindAll()

            // 새로 바인딩
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

            Log.d(TAG, "Camera bound successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            finishWithError("카메라 바인딩 실패")
        }
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
                
                // 진동
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(200)
            }
            
            Log.d(TAG, "Valid barcode detected: $cleanBarcode")
            
            // 결과 반환 및 즉시 종료
            val resultIntent = Intent()
            resultIntent.putExtra(RESULT_BARCODE, cleanBarcode)
            setResult(RESULT_OK, resultIntent)
            
            // 즉시 종료!
            finish()
        }
    }

    private fun finishWithError(message: String) {
        runOnUiThread {
            statusText.text = "⚠ $message"
        }
        finish()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause - stopping scanner")
        isScanning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy - releasing resources")
        
        // 스캔 중지
        isScanning = false
        
        // 카메라 리소스 해제
        try {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            
            camera = null
            
            cameraProvider?.unbindAll()
            cameraProvider = null
            
            Log.d(TAG, "Camera resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
        
        // Executor 종료
        try {
            cameraExecutor.shutdown()
            if (!cameraExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                cameraExecutor.shutdownNow()
            }
            Log.d(TAG, "Executor shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down executor", e)
            cameraExecutor.shutdownNow()
        }
    }

    private inner class BarcodeAnalyzer(
        private val barcodeListener: (barcode: String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null && isScanning) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            when (barcode.valueType) {
                                Barcode.TYPE_TEXT,
                                Barcode.TYPE_URL -> {
                                    barcode.rawValue?.let { value ->
                                        barcodeListener(value)
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        // 실패는 무시 (계속 스캔)
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
