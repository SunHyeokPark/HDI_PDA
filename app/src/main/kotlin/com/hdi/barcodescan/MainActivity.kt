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

    companion object {
        private const val TAG = "HDI_PDA"
        private const val HOME_URL = "http://erp.hdi21.co.kr/mobile"
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Scanner result: ${result.resultCode}")
        
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
                webView.restoreState(savedInstanceState)
                pendingBarcode = savedInstanceState.getString("pendingBarcode")
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
        outState.putString("pendingBarcode", pendingBarcode)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - pendingBarcode: $pendingBarcode")
        
        webView.onResume()
        
        pendingBarcode?.let { barcode ->
            webView.postDelayed({
                forceInsertBarcode(barcode)
                pendingBarcode = null
            }, 800)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // JavaScript 브릿지 추가
        webView.addJavascriptInterface(ScannerBridge(), "Scanner")
        
        // 디버깅 활성화
        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")
                
                // 강력한 오버라이드 주입!
                injectPowerfulOverride()
                
                // 대기 중인 바코드 처리
                pendingBarcode?.let { barcode ->
                    webView.postDelayed({
                        forceInsertBarcode(barcode)
                        pendingBarcode = null
                    }, 500)
                }
            }
        }
    }

    /**
     * 강력한 오버라이드!
     * Object.defineProperty를 사용하여 덮어쓸 수 없게 만듦
     */
    private fun injectPowerfulOverride() {
        val script = """
            (function() {
                console.log('========== HDI PDA Override Injection ==========');
                
                // Scanner 객체 확인
                if (typeof Scanner === 'undefined') {
                    console.error('✗ Scanner object not found!');
                    return;
                }
                
                console.log('✓ Scanner object found');
                
                // 네이티브 스캐너 함수
                var nativeScanner = function() {
                    console.log('!!! NATIVE SCANNER CALLED !!!');
                    try {
                        Scanner.openScanner();
                        return false;
                    } catch(e) {
                        console.error('Native scanner error:', e);
                        alert('스캐너 오류: ' + e.message);
                        return false;
                    }
                };
                
                // startLiveScanner를 읽기 전용으로 덮어쓸 수 없게 만들기
                try {
                    Object.defineProperty(window, 'startLiveScanner', {
                        value: nativeScanner,
                        writable: false,      // 덮어쓸 수 없음!
                        configurable: false,  // 재정의 불가!
                        enumerable: true
                    });
                    console.log('✓ startLiveScanner locked with native scanner');
                } catch(e) {
                    // 이미 정의되어 있으면 강제로 교체
                    console.log('Forcing override...');
                    try {
                        delete window.startLiveScanner;
                        Object.defineProperty(window, 'startLiveScanner', {
                            value: nativeScanner,
                            writable: false,
                            configurable: false,
                            enumerable: true
                        });
                        console.log('✓ startLiveScanner force-locked');
                    } catch(e2) {
                        // 마지막 수단: 그냥 덮어쓰기
                        window.startLiveScanner = nativeScanner;
                        console.log('✓ startLiveScanner overridden (not locked)');
                    }
                }
                
                console.log('========== Override Complete ==========');
            })();
        """.trimIndent()

        // 즉시 실행
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Override injection result: $result")
        }
        
        // 1초 후 다시 실행 (jQuery ready 이후)
        webView.postDelayed({
            webView.evaluateJavascript(script) { result ->
                Log.d(TAG, "Override re-injection (1s) result: $result")
            }
        }, 1000)
        
        // 2초 후 한 번 더 (확실하게!)
        webView.postDelayed({
            webView.evaluateJavascript(script) { result ->
                Log.d(TAG, "Override re-injection (2s) result: $result")
            }
        }, 2000)
    }

    private fun forceInsertBarcode(barcode: String) {
        Log.d(TAG, "Force inserting: $barcode")

        val safeBarcode = barcode
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        val script = """
            (function() {
                try {
                    var barcode = "$safeBarcode";
                    console.log('Inserting barcode:', barcode);
                    
                    var input = document.getElementById('scan_bar');
                    if (!input) {
                        console.error('scan_bar not found');
                        return 'NOT_FOUND';
                    }
                    
                    input.value = '';
                    setTimeout(function() {
                        input.value = barcode;
                        input.focus();
                        
                        var event = new KeyboardEvent('keyup', {
                            bubbles: true,
                            cancelable: true,
                            key: 'Enter',
                            keyCode: 13
                        });
                        input.dispatchEvent(event);
                        
                        console.log('✓ Barcode inserted:', input.value);
                    }, 100);
                    
                    return 'OK';
                } catch(e) {
                    console.error('Insert error:', e);
                    return 'ERROR';
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Insert result: $result")
        }
    }

    inner class ScannerBridge {
        @JavascriptInterface
        fun openScanner() {
            Log.d(TAG, "========== openScanner called ==========")
            runOnUiThread {
                if (hasCameraPermission()) {
                    Log.d(TAG, "Launching scanner activity...")
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
