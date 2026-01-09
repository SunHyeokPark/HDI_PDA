package com.hdi.barcodescan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingBarcode: String? = null
    private var lastPageUrl: String = ""

    companion object {
        private const val TAG = "HDI_PDA"
        private const val HOME_URL = "http://erp.hdi21.co.kr/mobile"
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "========== Scanner returned ==========")
        Log.d(TAG, "Result: ${result.resultCode}")
        
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra(BarcodeScannerActivity.RESULT_BARCODE)
            if (!barcode.isNullOrEmpty()) {
                Log.d(TAG, "Barcode: $barcode")
                pendingBarcode = barcode
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        if (hasCameraPermission()) {
            setupWebView()
            
            if (savedInstanceState != null) {
                // 상태 복원
                webView.restoreState(savedInstanceState)
                lastPageUrl = savedInstanceState.getString("lastPageUrl", HOME_URL)
                pendingBarcode = savedInstanceState.getString("pendingBarcode")
                Log.d(TAG, "Restored - URL: $lastPageUrl, Barcode: $pendingBarcode")
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
        outState.putString("lastPageUrl", lastPageUrl)
        outState.putString("pendingBarcode", pendingBarcode)
        Log.d(TAG, "Saved state - URL: $lastPageUrl, Barcode: $pendingBarcode")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - pendingBarcode: $pendingBarcode")
        
        webView.onResume()
        
        // 대기 중인 바코드가 있으면 주입
        pendingBarcode?.let { barcode ->
            Log.d(TAG, "Processing pending barcode: $barcode")
            
            // 충분한 시간 대기 후 주입
            webView.postDelayed({
                val currentUrl = webView.url ?: ""
                Log.d(TAG, "Current URL: $currentUrl")
                
                // 잘못된 페이지면 복구
                if (currentUrl.contains("/mobile") && !currentUrl.contains("BarcodeIn")) {
                    Log.w(TAG, "Wrong page! Restoring: $lastPageUrl")
                    if (lastPageUrl.isNotEmpty() && lastPageUrl != HOME_URL) {
                        webView.loadUrl(lastPageUrl)
                        
                        // 페이지 로드 후 바코드 주입
                        webView.postDelayed({
                            forceInsertBarcode(barcode)
                            pendingBarcode = null
                        }, 2000)
                    } else {
                        // lastPageUrl이 없으면 바로 주입 시도
                        forceInsertBarcode(barcode)
                        pendingBarcode = null
                    }
                } else {
                    // 정상 페이지면 바로 주입
                    forceInsertBarcode(barcode)
                    pendingBarcode = null
                }
            }, 500)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            
            // 백그라운드에서 상태 유지
            setSupportMultipleWindows(false)
        }

        webView.addJavascriptInterface(ScannerBridge(), "Scanner")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")
                
                url?.let {
                    // BarcodeIn 페이지는 저장
                    if (it.contains("BarcodeIn", ignoreCase = true)) {
                        lastPageUrl = it
                        Log.d(TAG, "Saved page URL: $lastPageUrl")
                    }
                }
                
                // 스캐너 버튼 연결
                connectScanner()
                
                // 대기 중인 바코드가 있으면 주입
                pendingBarcode?.let { barcode ->
                    Log.d(TAG, "Injecting pending barcode after page load: $barcode")
                    webView.postDelayed({
                        forceInsertBarcode(barcode)
                        pendingBarcode = null
                    }, 500)
                }
            }
        }
    }

    private fun connectScanner() {
        val script = """
            (function() {
                if (typeof Scanner !== 'undefined') {
                    window.startLiveScanner = function() {
                        console.log('Opening native scanner...');
                        Scanner.openScanner();
                        return false;
                    };
                    console.log('Scanner connected');
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun forceInsertBarcode(barcode: String) {
        Log.d(TAG, "========== Force inserting barcode: $barcode ==========")

        val safeBarcode = barcode
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val script = """
            (function() {
                try {
                    var barcode = "$safeBarcode";
                    console.log('========== FORCE INSERT START ==========');
                    console.log('Barcode:', barcode);
                    console.log('Current URL:', window.location.href);
                    
                    var input = document.getElementById('scan_bar');
                    console.log('scan_bar found:', input !== null);
                    
                    if (!input) {
                        console.error('scan_bar NOT FOUND!');
                        console.log('Available inputs:', 
                            Array.from(document.querySelectorAll('input')).map(i => i.id || i.name).join(', '));
                        return 'NOT_FOUND';
                    }
                    
                    // 강제 입력 시퀀스
                    console.log('Step 1: Clear');
                    input.value = '';
                    
                    console.log('Step 2: Focus');
                    input.focus();
                    
                    console.log('Step 3: Set value');
                    input.value = barcode;
                    
                    console.log('Step 4: Trigger events');
                    
                    // input 이벤트
                    var inputEvent = new Event('input', { bubbles: true, cancelable: true });
                    input.dispatchEvent(inputEvent);
                    
                    // change 이벤트
                    var changeEvent = new Event('change', { bubbles: true, cancelable: true });
                    input.dispatchEvent(changeEvent);
                    
                    // keyup 이벤트 (웹페이지가 이걸로 감지)
                    var keyupEvent = new KeyboardEvent('keyup', {
                        bubbles: true,
                        cancelable: true,
                        key: 'Enter',
                        code: 'Enter',
                        keyCode: 13,
                        which: 13
                    });
                    input.dispatchEvent(keyupEvent);
                    
                    console.log('Final value:', input.value);
                    console.log('========== FORCE INSERT COMPLETE ==========');
                    
                    return 'SUCCESS:' + input.value;
                    
                } catch(e) {
                    console.error('Insert error:', e);
                    return 'ERROR:' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "========== Insert result: $result ==========")
            
            // 결과 확인
            if (result?.contains("SUCCESS") == true) {
                Toast.makeText(this, "바코드 입력 완료", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "입력 실패: $result", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class ScannerBridge {
        @JavascriptInterface
        fun openScanner() {
            Log.d(TAG, "========== Opening scanner ==========")
            Log.d(TAG, "Current URL: ${webView.url}")
            
            runOnUiThread {
                if (hasCameraPermission()) {
                    // 현재 URL 저장
                    webView.url?.let { url ->
                        if (url.contains("BarcodeIn", ignoreCase = true)) {
                            lastPageUrl = url
                            Log.d(TAG, "Saved URL before scanner: $lastPageUrl")
                        }
                    }
                    
                    val intent = Intent(this@MainActivity, BarcodeScannerActivity::class.java)
                    scannerLauncher.launch(intent)
                } else {
                    Toast.makeText(this@MainActivity, "카메라 권한 필요", Toast.LENGTH_SHORT).show()
                    requestCameraPermission()
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

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            setupWebView()
            webView.loadUrl(HOME_URL)
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
