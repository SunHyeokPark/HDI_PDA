package com.hdi.barcodescan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null
    private var lastValidUrl: String = ""
    private var isProcessingBarcode = false
    
    companion object {
        private const val TAG = "HDI_PDA"
        private const val CAMERA_PERMISSION_CODE = 100
        private const val ALL_PERMISSIONS_CODE = 101
        private const val SCANNER_REQUEST_CODE = 200
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Scanner result: resultCode=${result.resultCode}")
        
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra(BarcodeScannerActivity.RESULT_BARCODE)
            Log.d(TAG, "Scanned barcode: $barcode")
            
            if (barcode != null && barcode.isNotEmpty()) {
                // 바코드 처리 플래그 설정
                isProcessingBarcode = true
                
                // WebView로 포커스 복귀
                webView.requestFocus()
                
                // 1초 후 바코드 주입 (안정성)
                webView.postDelayed({
                    injectScannedBarcode(barcode)
                    
                    // 3초 후 플래그 해제
                    webView.postDelayed({
                        isProcessingBarcode = false
                        Log.d(TAG, "Barcode processing complete")
                    }, 3000)
                }, 1000)
            } else {
                Log.e(TAG, "Empty barcode")
                webView.requestFocus()
            }
        } else {
            Log.d(TAG, "Scanner cancelled")
            webView.requestFocus()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = if (data == null || data.data == null) {
                cameraPhotoUri?.let { arrayOf(it) }
            } else {
                arrayOf(data.data!!)
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        
        if (!hasCameraPermission()) {
            checkAndRequestPermissions()
        } else {
            setupWebView()
            webView.loadUrl("http://erp.hdi21.co.kr/mobile")
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            cacheMode = WebSettings.LOAD_DEFAULT
        }

        WebView.setWebContentsDebuggingEnabled(true)

        webView.addJavascriptInterface(CameraInterface(), "AndroidCamera")

        webView.webChromeClient = object : WebChromeClient() {
            
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(packageManager) != null) {
                    val photoFile = createImageFile()
                    photoFile?.also {
                        cameraPhotoUri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${applicationContext.packageName}.fileprovider",
                            it
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                    }
                }

                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                contentSelectionIntent.type = "image/*"

                val intentArray = arrayOf(takePictureIntent)

                val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "이미지 선택")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)

                fileChooserLauncher.launch(chooserIntent)
                return true
            }
            
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                title = if (newProgress == 100) "HDI PDA" else "로딩중... $newProgress%"
            }

            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                Log.d(TAG, "Console: ${message?.message()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                Log.d(TAG, "URL loading: $url")
                Log.d(TAG, "isProcessingBarcode: $isProcessingBarcode")
                Log.d(TAG, "lastValidUrl: $lastValidUrl")
                
                // 바코드 처리 중에 초기 화면으로 돌아가려고 하면 막기!
                if (isProcessingBarcode) {
                    if (url == "http://erp.hdi21.co.kr/mobile" || 
                        url == "http://erp.hdi21.co.kr/mobile/") {
                        Log.e(TAG, "⛔ BLOCKED: Navigation to home during barcode processing!")
                        Toast.makeText(
                            this@MainActivity,
                            "바코드 처리중...",
                            Toast.LENGTH_SHORT
                        ).show()
                        return true  // true = 막기!
                    }
                }
                
                // 같은 도메인은 허용
                if (url.contains("erp.hdi21.co.kr")) {
                    // BarcodeIn 페이지는 기억
                    if (url.contains("BarcodeIn") || url.contains("barcodein")) {
                        lastValidUrl = url
                        Log.d(TAG, "✓ Saved lastValidUrl: $lastValidUrl")
                    }
                    return false
                }
                
                return super.shouldOverrideUrlLoading(view, request)
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "Page started: $url")
                
                // 바코드 처리 중에 초기 화면으로 가려고 하면 막고 원래 페이지로 복구!
                if (isProcessingBarcode && lastValidUrl.isNotEmpty()) {
                    if (url == "http://erp.hdi21.co.kr/mobile" || 
                        url == "http://erp.hdi21.co.kr/mobile/") {
                        Log.e(TAG, "⛔ BLOCKED page start! Restoring: $lastValidUrl")
                        view?.stopLoading()
                        view?.loadUrl(lastValidUrl)
                        return
                    }
                }
                
                injectCameraPolyfill()
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")
                
                injectCameraPolyfill()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Log.e(TAG, "WebView error: $description")
            }
        }
    }

    private fun injectCameraPolyfill() {
        val polyfill = """
            (function() {
                console.log('=== Polyfill injection ===');
                
                var hasNativeScanner = typeof AndroidCamera !== 'undefined' && 
                                      typeof AndroidCamera.openScanner === 'function';
                
                if (hasNativeScanner) {
                    window.startLiveScanner = function() {
                        console.log('!!! Opening native scanner !!!');
                        try {
                            AndroidCamera.openScanner();
                        } catch(e) {
                            console.error('Scanner error:', e);
                        }
                        return false;
                    };
                }
                
                if (!navigator.mediaDevices) {
                    navigator.mediaDevices = {};
                }
                
                navigator.mediaDevices.getUserMedia = function() {
                    return Promise.reject(new Error('Use native scanner'));
                };
                
                if (!navigator.permissions) {
                    navigator.permissions = {};
                }
                
                navigator.permissions.query = function(desc) {
                    if (desc && desc.name === 'camera') {
                        return Promise.resolve({ state: 'granted', name: 'camera' });
                    }
                    return Promise.resolve({ state: 'prompt' });
                };
            })();
        """.trimIndent()
        
        webView.post {
            webView.evaluateJavascript(polyfill, null)
        }
    }

    private fun injectScannedBarcode(barcode: String) {
        Log.d(TAG, "🔥 Injecting barcode: $barcode")
        
        val script = """
            (function() {
                console.log('🔥 BARCODE INJECTION: $barcode');
                
                var scanInput = document.getElementById('scan_bar');
                
                if (scanInput) {
                    scanInput.value = '$barcode';
                    console.log('✓ Set value');
                    
                    var event = new Event('keyup', { bubbles: true, cancelable: true });
                    scanInput.dispatchEvent(event);
                    console.log('✓ Triggered keyup');
                    
                    scanInput.focus();
                    console.log('✓ SUCCESS!');
                    
                    return 'SUCCESS';
                } else {
                    console.error('✗ scan_bar not found!');
                    
                    if (typeof doIpgoScan === 'function') {
                        console.log('Calling doIpgoScan directly');
                        doIpgoScan('$barcode');
                        return 'DIRECT_CALL';
                    }
                    
                    return 'FAILED';
                }
            })();
        """.trimIndent()
        
        webView.post {
            webView.evaluateJavascript(script) { result ->
                Log.d(TAG, "Injection result: $result")
            }
        }
    }

    inner class CameraInterface {
        @JavascriptInterface
        fun openScanner() {
            Log.d(TAG, "📸 openScanner called")
            runOnUiThread {
                if (hasCameraPermission()) {
                    val intent = Intent(this@MainActivity, BarcodeScannerActivity::class.java)
                    scannerLauncher.launch(intent)
                } else {
                    Toast.makeText(this@MainActivity, "카메라 권한 필요", Toast.LENGTH_SHORT).show()
                    checkAndRequestPermissions()
                }
            }
        }
        
        @JavascriptInterface
        fun hasPermission(): Boolean {
            return hasCameraPermission()
        }
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(null)
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create image file", e)
            null
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                ALL_PERMISSIONS_CODE
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            ALL_PERMISSIONS_CODE, CAMERA_PERMISSION_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                
                if (allGranted) {
                    setupWebView()
                    webView.loadUrl("http://erp.hdi21.co.kr/mobile")
                } else {
                    Toast.makeText(this, "권한 필요", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasCameraPermission()) {
            checkAndRequestPermissions()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
