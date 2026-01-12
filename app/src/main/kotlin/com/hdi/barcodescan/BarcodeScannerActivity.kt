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

    companion object {
        private const val TAG = "HDI_PDA"
        private const val HOME_URL = "http://erp.hdi21.co.kr/mobile"
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "========== Scanner returned ==========")
        Log.d(TAG, "Result code: ${result.resultCode}")
        
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra(BarcodeScannerActivity.RESULT_BARCODE)
            Log.d(TAG, "Barcode received: $barcode")
            
            if (!barcode.isNullOrEmpty()) {
                // 즉시 주입 시도 (여러 번!)
                injectBarcodeMultipleTimes(barcode)
            }
        } else {
            Log.d(TAG, "Scanner cancelled or failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        if (hasCameraPermission()) {
            setupWebView()
            webView.loadUrl(HOME_URL)
        } else {
            requestCameraPermission()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        webView.addJavascriptInterface(ScannerBridge(), "Scanner")
        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")
                injectPowerfulOverride()
            }
        }
    }

    private fun injectPowerfulOverride() {
        val script = """
            (function() {
                if (typeof Scanner !== 'undefined') {
                    console.log('✓ Scanner bridge found');
                    
                    var nativeScanner = function() {
                        console.log('!!! Native scanner called !!!');
                        Scanner.openScanner();
                        return false;
                    };
                    
                    try {
                        Object.defineProperty(window, 'startLiveScanner', {
                            value: nativeScanner,
                            writable: false,
                            configurable: false
                        });
                        console.log('✓ startLiveScanner locked');
                    } catch(e) {
                        window.startLiveScanner = nativeScanner;
                        console.log('✓ startLiveScanner overridden');
                    }
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
        
        // 1초 후 다시
        webView.postDelayed({
            webView.evaluateJavascript(script, null)
        }, 1000)
    }

    /**
     * 바코드를 여러 번 시도해서 확실하게 입력!
     */
    private fun injectBarcodeMultipleTimes(barcode: String) {
        Log.d(TAG, "🔥 Starting barcode injection: $barcode")
        
        // 1차 시도: 500ms 후
        webView.postDelayed({
            tryInjectBarcode(barcode, 1)
        }, 500)
        
        // 2차 시도: 1000ms 후
        webView.postDelayed({
            tryInjectBarcode(barcode, 2)
        }, 1000)
        
        // 3차 시도: 1500ms 후 (확실하게!)
        webView.postDelayed({
            tryInjectBarcode(barcode, 3)
        }, 1500)
    }

    private fun tryInjectBarcode(barcode: String, attempt: Int) {
        Log.d(TAG, "Injection attempt #$attempt")
        
        val safeBarcode = barcode
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val script = """
            (function() {
                try {
                    console.log('========== Injection Attempt #$attempt ==========');
                    var barcode = "$safeBarcode";
                    
                    var input = document.getElementById('scan_bar');
                    if (!input) {
                        console.error('scan_bar NOT FOUND!');
                        return 'NOT_FOUND';
                    }
                    
                    console.log('Found scan_bar, current value:', input.value);
                    
                    // 비우기
                    input.value = '';
                    
                    // 포커스
                    input.focus();
                    
                    // 값 설정
                    input.value = barcode;
                    console.log('Set value:', input.value);
                    
                    // 이벤트 발생
                    var events = ['input', 'change'];
                    events.forEach(function(type) {
                        var e = new Event(type, { bubbles: true, cancelable: true });
                        input.dispatchEvent(e);
                    });
                    
                    // keyup 이벤트 (엔터)
                    var keyupEvent = new KeyboardEvent('keyup', {
                        bubbles: true,
                        cancelable: true,
                        key: 'Enter',
                        code: 'Enter',
                        keyCode: 13,
                        which: 13
                    });
                    input.dispatchEvent(keyupEvent);
                    
                    console.log('✓ All events dispatched');
                    console.log('Final value:', input.value);
                    
                    // doIpgoScan 직접 호출 시도
                    if (typeof doIpgoScan === 'function') {
                        console.log('Calling doIpgoScan directly...');
                        setTimeout(function() {
                            doIpgoScan(barcode);
                        }, 100);
                    }
                    
                    return 'SUCCESS';
                    
                } catch(e) {
                    console.error('Injection error:', e);
                    return 'ERROR:' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Attempt #$attempt result: $result")
            
            if (result?.contains("SUCCESS") == true) {
                Log.d(TAG, "✓✓✓ Injection SUCCESS on attempt #$attempt ✓✓✓")
                Toast.makeText(this, "바코드 입력 완료!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class ScannerBridge {
        @JavascriptInterface
        fun openScanner() {
            Log.d(TAG, "========== openScanner called ==========")
            runOnUiThread {
                if (hasCameraPermission()) {
                    Log.d(TAG, "Launching scanner...")
                    val intent = Intent(this@MainActivity, BarcodeScannerActivity::class.java)
                    scannerLauncher.launch(intent)
                } else {
                    Toast.makeText(this@MainActivity, "카메라 권한 필요", Toast.LENGTH_SHORT).show()
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
