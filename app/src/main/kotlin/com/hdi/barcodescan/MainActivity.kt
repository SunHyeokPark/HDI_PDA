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
        Log.d(TAG, "Scanner closed, result code: ${result.resultCode}")
        
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra(BarcodeScannerActivity.RESULT_BARCODE)
            Log.d(TAG, "Barcode: $barcode")
            
            if (!barcode.isNullOrEmpty()) {
                // 충분한 대기 후 주입
                webView.postDelayed({
                    insertBarcode(barcode)
                }, 1000)
            }
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

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
                
                // 스캐너 버튼 연결
                connectScanner()
            }
        }
    }

    private fun connectScanner() {
        val script = """
            (function() {
                if (typeof Scanner !== 'undefined') {
                    window.startLiveScanner = function() {
                        console.log('Opening scanner...');
                        Scanner.openScanner();
                        return false;
                    };
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun insertBarcode(barcode: String) {
        Log.d(TAG, "Inserting barcode: $barcode")

        // JSON escape
        val safeBarcode = barcode
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")

        val script = """
            (function() {
                try {
                    var barcode = "$safeBarcode";
                    console.log('Inserting:', barcode);
                    
                    var input = document.getElementById('scan_bar');
                    if (!input) {
                        console.error('scan_bar not found');
                        return 'NOT_FOUND';
                    }
                    
                    // 확실하게 입력
                    input.value = '';  // 먼저 비우기
                    setTimeout(function() {
                        input.value = barcode;
                        input.focus();
                        
                        // keyup 이벤트 (웹페이지가 이걸로 감지)
                        var event = new Event('keyup', { bubbles: true });
                        input.dispatchEvent(event);
                        
                        console.log('Done! Value:', input.value);
                    }, 100);
                    
                    return 'OK';
                } catch(e) {
                    console.error('Error:', e);
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
            Log.d(TAG, "Opening scanner...")
            runOnUiThread {
                if (hasCameraPermission()) {
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
