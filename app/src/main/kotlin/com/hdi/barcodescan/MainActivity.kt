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
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra(BarcodeScannerActivity.RESULT_BARCODE)
            if (!barcode.isNullOrEmpty()) {
                Log.d(TAG, "Scanned: $barcode")
                
                // 1초 대기 후 바코드 입력
                webView.postDelayed({
                    insertBarcodeAndPressEnter(barcode)
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
            webView.loadUrl("http://erp.hdi21.co.kr/mobile")
        } else {
            requestCameraPermission()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // JavaScript Bridge 추가
        webView.addJavascriptInterface(ScannerBridge(), "Scanner")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // 페이지 로드 완료 시 스캐너 버튼 연결
                connectScannerButton()
            }
        }
    }

    // 스캐너 버튼과 네이티브 스캐너 연결
    private fun connectScannerButton() {
        val script = """
            (function() {
                // 실시간 스캔 버튼 클릭 시 네이티브 스캐너 호출
                if (typeof Scanner !== 'undefined') {
                    window.startLiveScanner = function() {
                        Scanner.openScanner();
                        return false;
                    };
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }

    // 바코드를 scan_bar에 입력하고 엔터
    private fun insertBarcodeAndPressEnter(barcode: String) {
        val script = """
            (function() {
                var input = document.getElementById('scan_bar');
                if (input) {
                    // 1. 값 설정
                    input.value = '$barcode';
                    
                    // 2. 포커스
                    input.focus();
                    
                    // 3. keyup 이벤트 (웹페이지에서 감지)
                    var event = new KeyboardEvent('keyup', {
                        bubbles: true,
                        cancelable: true,
                        keyCode: 13,
                        which: 13
                    });
                    input.dispatchEvent(event);
                    
                    return 'OK';
                }
                return 'FAIL';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Insert result: $result")
        }
    }

    // JavaScript에서 호출할 브릿지
    inner class ScannerBridge {
        @JavascriptInterface
        fun openScanner() {
            runOnUiThread {
                if (hasCameraPermission()) {
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
        
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupWebView()
                webView.loadUrl("http://erp.hdi21.co.kr/mobile")
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
