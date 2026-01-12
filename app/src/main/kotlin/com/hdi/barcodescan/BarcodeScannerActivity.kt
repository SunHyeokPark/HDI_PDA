package com.hdi.barcodescan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
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

class BarcodeScannerActivity : AppCompatActivity() {

    // UI 컴포넌트
    private lateinit var cameraContainer: RelativeLayout
    private lateinit var resultContainer: LinearLayout
    private lateinit var previewView: PreviewView
    private lateinit var barcodeValueText: TextView
    private lateinit var scanInstruction: TextView
    
    // 카메라
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // 상태
    private var isScanning = true
    private var scannedBarcode: String = ""

    companion object {
        private const val TAG = "BarcodeScanner"
        const val RESULT_BARCODE = "barcode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        initViews()
        setupButtons()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        startScanning()
    }

    private fun initViews() {
        cameraContainer = findViewById(R.id.camera_container)
        resultContainer = findViewById(R.id.result_container)
        previewView = findViewById(R.id.preview_view)
        barcodeValueText = findViewById(R.id.barcode_value_text)
        scanInstruction = findViewById(R.id.scan_instruction)
    }

    private fun setupButtons() {
        // 스캔 중 취소
        findViewById<Button>(R.id.cancel_scan_button).setOnClickListener {
            Log.d(TAG, "Scan cancelled by user")
            setResult(RESULT_CANCELED)
            finish()
        }
        
        // 값 사용
        findViewById<Button>(R.id.use_button).setOnClickListener {
            Log.d(TAG, "Use button clicked: $scannedBarcode")
            val resultIntent = Intent()
            resultIntent.putExtra(RESULT_BARCODE, scannedBarcode)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        
        // 다시 스캔
        findViewById<Button>(R.id.rescan_button).setOnClickListener {
            Log.d(TAG, "Rescan button clicked")
            startScanning()
        }
        
        // 결과에서 취소
        findViewById<Button>(R.id.cancel_result_button).setOnClickListener {
            Log.d(TAG, "Result cancelled by user")
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun startScanning() {
        Log.d(TAG, "Starting scanning...")
        
        // UI 전환
        cameraContainer.visibility = View.VISIBLE
        resultContainer.visibility = View.GONE
        
        // 상태 초기화
        isScanning = true
        scannedBarcode = ""
        scanInstruction.text = "코드를 가이드 안에 위치시키세요"
        
        // 카메라 시작
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                showError("카메라 초기화 실패: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val cameraProvider = this.cameraProvider ?: run {
            Log.e(TAG, "Camera provider is null")
            showError("카메라를 사용할 수 없습니다")
            return
        }

        try {
            // 기존 바인딩 해제
            cameraProvider.unbindAll()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // ImageAnalysis
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        onBarcodeDetected(barcode)
                    })
                }

            // Camera Selector
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Bind to lifecycle
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

            Log.d(TAG, "Camera bound successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            showError("카메라 시작 실패: ${e.message}")
        }
    }

    private fun onBarcodeDetected(barcode: String) {
        if (!isScanning) return
        
        Log.d(TAG, "Barcode detected: $barcode")
        
        // GUID 패턴 검증
        val guidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        
        var cleanBarcode = barcode.trim()
        
        // 중괄호 제거
        if (cleanBarcode.startsWith("{") && cleanBarcode.endsWith("}")) {
            cleanBarcode = cleanBarcode.substring(1, cleanBarcode.length - 1)
        }
        
        // GUID 검증
        if (guidPattern.matches(cleanBarcode)) {
            isScanning = false
            scannedBarcode = cleanBarcode
            
            Log.d(TAG, "Valid barcode: $scannedBarcode")
            
            // 진동
            try {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                vibrator?.vibrate(200)
            } catch (e: Exception) {
                Log.e(TAG, "Vibration failed", e)
            }
            
            // 결과 화면 표시
            runOnUiThread {
                showResult(scannedBarcode)
            }
        } else {
            Log.d(TAG, "Invalid barcode format: $cleanBarcode")
        }
    }

    private fun showResult(barcode: String) {
        Log.d(TAG, "Showing result: $barcode")
        
        // 카메라 일시 중지
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing camera", e)
        }
        
        // UI 전환
        cameraContainer.visibility = View.GONE
        resultContainer.visibility = View.VISIBLE
        
        // 바코드 값 표시
        barcodeValueText.text = barcode
    }

    private fun showError(message: String) {
        Log.e(TAG, "Error: $message")
        runOnUiThread {
            scanInstruction.text = "⚠ $message"
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        isScanning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy - releasing resources")
        
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
            Log.d(TAG, "Executor shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down executor", e)
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
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scanning failed", e)
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
