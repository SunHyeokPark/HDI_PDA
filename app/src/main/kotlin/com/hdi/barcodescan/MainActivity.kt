package com.hdi.barcodescan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isProcessingBarcode = false
    private var savedUrl: String = ""
    private var isReturningFromScanner = false

    companion object {
        private const val TAG = "HDI_PDA"
        private const val HOME_URL = "http://erp.hdi21.co.kr/mobile"
        private const val BARCODE_PAGE_PATTERN = "BarcodeIn"
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "======== Scanner returned ========")
        Log.d(TAG, "Result code: ${result.resultCode}")
        Log.d(TAG, "Saved URL: $savedUrl")
        
        isReturningFromScanner = true
        
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra(BarcodeScannerActivity.RESULT_BARCODE)
            if (!barcode.isNullOrEmpty()) {
                Log.d(TAG, "✓ Barcode received: $barcode")
                
                // 바코드 처리 플래그 설정
                isProcessingBarcode = true
                
                // WebView 활성화 대기
                webView.post {
                    // URL 확인 및 복구
                    val currentUrl = webView.url ?: ""
                    Log.d(TAG, "Current URL after scanner: $currentUrl")
                    
                    if (shouldRestoreUrl(currentUrl)) {
                        Log.w(TAG, "⚠️ Wrong page detected! Restoring: $savedUrl")
                        webView.loadUrl(savedUrl)
                        
                        // 페이지 로드 완료 후 바코드 주입
                        webView.postDelayed({
                            injectBarcode(barcode)
                        }, 1500)
                    } else {
                        // 정상 페이지면 바로 주입
                        webView.postDelayed({
                            injectBarcode(barcode)
                        }, 800)
                    }
                }
            }
        } else {
            Log.d(TAG, "Scanner cancelled")
            isReturningFromScanner = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        if (hasCameraPermission()) {
            setupWebView()
            
            if (savedInstanceState != null) {
                // Activity 재생성 시 상태 복원
                webView.restoreState(savedInstanceState)
                savedUrl = savedInstanceState.getString("savedUrl", "")
                Log.d(TAG, "Restored URL: $savedUrl")
            } else {
                webView.loadUrl(HOME_URL)
            }
        } else {
            requestCameraPermission()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        outState.putString("savedUrl", savedUrl)
        Log.d(TAG, "Saved URL to bundle: $savedUrl")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - isReturningFromScanner: $isReturningFromScanner")
        
        // 스캐너에서 돌아온 경우 WebView 재활성화
        if (isReturningFromScanner) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        
        // 스캐너로 가는 경우가 아니면 일시정지
        if (!isProcessingBarcode) {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            
            // 백그라운드 상태 유지
            setSupportMultipleWindows(false)
        }

        webView.addJavascriptInterface(ScannerBridge(), "Scanner")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                return handleUrlChange(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleUrlChange(url ?: "")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "Page started: $url")
                Log.d(TAG, "isProcessingBarcode: $isProcessingBarcode")
                
                // 바코드 처리 중 홈으로 가려는 시도 차단
                if (isProcessingBarcode && isHomeUrl(url)) {
                    Log.e(TAG, "⛔ BLOCKED home navigation during barcode processing")
                    view?.stopLoading()
                    
                    if (savedUrl.isNotEmpty()) {
                        view?.post {
                            view.loadUrl(savedUrl)
                        }
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")

                val currentUrl = url ?: ""
                
                // 바코드 페이지는 저장
                if (isBarcodePageUrl(currentUrl)) {
                    savedUrl = currentUrl
                    Log.d(TAG, "✓ Saved barcode page URL: $savedUrl")
                }

                // 스캐너 버튼 연결
                connectScannerButton()
            }
        }
    }

    private fun handleUrlChange(url: String): Boolean {
        Log.d(TAG, "URL change attempt: $url")
        Log.d(TAG, "isProcessingBarcode: $isProcessingBarcode, savedUrl: $savedUrl")

        // 바코드 처리 중 홈으로 가는 것 차단
        if (isProcessingBarcode && isHomeUrl(url)) {
            Log.e(TAG, "⛔ BLOCKED URL change to home during barcode processing")
            Toast.makeText(this, "바코드 처리중...", Toast.LENGTH_SHORT).show()
            
            // 저장된 URL로 복구
            if (savedUrl.isNotEmpty()) {
                webView.post {
                    webView.stopLoading()
                    webView.loadUrl(savedUrl)
                }
            }
            return true // 차단
        }

        // 도메인 내 이동은 허용
        if (url.contains("erp.hdi21.co.kr")) {
            return false
        }

        return false
    }

    private fun shouldRestoreUrl(currentUrl: String): Boolean {
        // 홈 페이지로 잘못 갔거나, 저장된 URL과 다른 경우
        return savedUrl.isNotEmpty() && 
               (isHomeUrl(currentUrl) || !currentUrl.contains(BARCODE_PAGE_PATTERN))
    }

    private fun isHomeUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val cleanUrl = url.split("?")[0].split("#")[0].trimEnd('/')
        return cleanUrl == HOME_URL || 
               cleanUrl == "$HOME_URL/" ||
               cleanUrl == "http://erp.hdi21.co.kr/mobile"
    }

    private fun isBarcodePageUrl(url: String): Boolean {
        return url.contains(BARCODE_PAGE_PATTERN, ignoreCase = true)
    }

    private fun connectScannerButton() {
        val script = """
            (function() {
                if (typeof Scanner !== 'undefined') {
                    window.startLiveScanner = function() {
                        console.log('Native scanner opening...');
                        try {
                            Scanner.openScanner();
                        } catch(e) {
                            console.error('Scanner error:', e);
                        }
                        return false;
                    };
                    console.log('Scanner button connected');
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun injectBarcode(barcode: String) {
        Log.d(TAG, "🔥 Injecting barcode: $barcode")

        val script = """
            (function() {
                try {
                    console.log('🔥 Barcode injection start: $barcode');
                    
                    var input = document.getElementById('scan_bar');
                    if (!input) {
                        console.error('scan_bar not found!');
                        return 'FAIL';
                    }
                    
                    // 1. 포커스
                    input.focus();
                    
                    // 2. 값 설정
                    input.value = '$barcode';
                    console.log('✓ Value set');
                    
                    // 3. 이벤트 발생
                    var events = ['input', 'change', 'keyup'];
                    events.forEach(function(eventType) {
                        var event = new Event(eventType, { bubbles: true, cancelable: true });
                        input.dispatchEvent(event);
                    });
                    
                    // 4. Enter 키 이벤트
                    var keyEvent = new KeyboardEvent('keyup', {
                        bubbles: true,
                        cancelable: true,
                        keyCode: 13,
                        which: 13
                    });
                    input.dispatchEvent(keyEvent);
                    
                    console.log('✓ Events triggered');
                    return 'SUCCESS';
                    
                } catch(e) {
                    console.error('Injection error:', e);
                    return 'ERROR';
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Injection result: $result")
            
            // 3초 후 플래그 해제
            webView.postDelayed({
                isProcessingBarcode = false
                isReturningFromScanner = false
                Log.d(TAG, "Barcode processing complete")
            }, 3000)
        }
    }

    inner class ScannerBridge {
        @JavascriptInterface
        fun openScanner() {
            Log.d(TAG, "📸 openScanner called")
            runOnUiThread {
                if (hasCameraPermission()) {
                    // 현재 URL 저장
                    webView.url?.let { url ->
                        if (isBarcodePageUrl(url)) {
                            savedUrl = url
                            Log.d(TAG, "✓ Saved URL before scanner: $savedUrl")
                        }
                    }
                    
                    val intent = Intent(this@MainActivity, BarcodeScannerActivity::class.java)
                    scannerLauncher.launch(intent)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "카메라 권한 필요",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            100
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                setupWebView()
                webView.loadUrl(HOME_URL)
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

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
