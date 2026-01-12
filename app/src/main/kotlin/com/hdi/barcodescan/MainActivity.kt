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
                // 짧은 대기 후 삽입
                webView.postDelayed({
                    insertBarcodeToActiveElement(barcode)
                }, 300)
            }
        } else {
            Log.d(TAG, "Scanner cancelled")
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

        // JavaScript Bridge
        webView.addJavascriptInterface(ScannerBridge(), "Scanner")
        
        // 디버깅 활성화
        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished: $url")
                
                // 스캐너 버튼 연결
                connectScannerButton()
            }
        }
    }

    private fun connectScannerButton() {
        val script = """
            (function() {
                if (typeof Scanner !== 'undefined') {
                    // startLiveScanner 함수를 네이티브 스캐너로 교체
                    window.startLiveScanner = function() {
                        console.log('🔥 Opening native scanner...');
                        try {
                            Scanner.openScanner();
                        } catch(e) {
                            console.error('Scanner error:', e);
                            alert('스캐너 오류: ' + e.message);
                        }
                        return false;
                    };
                    console.log('✓ Native scanner connected');
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    /**
     * 현재 active element 또는 scan_bar에 바코드 삽입
     */
    private fun insertBarcodeToActiveElement(barcode: String) {
        Log.d(TAG, "🔥 Inserting barcode: $barcode")
        
        // 안전한 문자열 이스케이프
        val safeBarcode = barcode
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val script = """
            (function(barcode) {
                try {
                    console.log('========== BARCODE INSERTION ==========');
                    console.log('Barcode:', barcode);
                    
                    // 1. 현재 active element 확인
                    var activeEl = document.activeElement;
                    console.log('Active element:', activeEl ? activeEl.tagName : 'none');
                    
                    // 2. activeElement가 input/textarea인지 확인
                    if (activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA')) {
                        console.log('→ Inserting to active input/textarea');
                        
                        // 커서 위치 확인
                        var start = activeEl.selectionStart || 0;
                        var end = activeEl.selectionEnd || 0;
                        var value = activeEl.value || '';
                        
                        // 커서 위치에 삽입
                        activeEl.value = value.substring(0, start) + barcode + value.substring(end);
                        
                        // 커서 위치 조정
                        var newPos = start + barcode.length;
                        activeEl.selectionStart = newPos;
                        activeEl.selectionEnd = newPos;
                        
                        // 이벤트 발생
                        activeEl.dispatchEvent(new Event('input', {bubbles: true}));
                        activeEl.dispatchEvent(new Event('change', {bubbles: true}));
                        
                        var keyupEvent = new KeyboardEvent('keyup', {
                            bubbles: true,
                            keyCode: 13,
                            which: 13
                        });
                        activeEl.dispatchEvent(keyupEvent);
                        
                        console.log('✓ SUCCESS: Inserted to active element');
                        return 'INSERTED_TO_ACTIVE';
                    }
                    
                    // 3. activeElement가 contenteditable인지 확인
                    if (activeEl && activeEl.isContentEditable) {
                        console.log('→ Inserting to contenteditable');
                        document.execCommand('insertText', false, barcode);
                        console.log('✓ SUCCESS: Inserted to contenteditable');
                        return 'INSERTED_TO_CONTENTEDITABLE';
                    }
                    
                    // 4. scan_bar 폴백
                    var scanBar = document.getElementById('scan_bar');
                    if (scanBar) {
                        console.log('→ Fallback to scan_bar');
                        
                        scanBar.value = barcode;
                        scanBar.focus();
                        
                        scanBar.dispatchEvent(new Event('input', {bubbles: true}));
                        scanBar.dispatchEvent(new Event('change', {bubbles: true}));
                        
                        var keyupEvent = new KeyboardEvent('keyup', {
                            bubbles: true,
                            keyCode: 13,
                            which: 13
                        });
                        scanBar.dispatchEvent(keyupEvent);
                        
                        console.log('✓ SUCCESS: Inserted to scan_bar');
                        return 'INSERTED_TO_SCAN_BAR';
                    }
                    
                    // 5. doIpgoScan 직접 호출 시도
                    if (typeof doIpgoScan === 'function') {
                        console.log('→ Calling doIpgoScan directly');
                        doIpgoScan(barcode);
                        console.log('✓ SUCCESS: Called doIpgoScan');
                        return 'CALLED_DOIPGOSCAN';
                    }
                    
                    console.error('✗ FAILED: No target found');
                    return 'NO_TARGET_FOUND';
                    
                } catch(e) {
                    console.error('✗ ERROR:', e);
                    return 'ERROR:' + e.message;
                }
            })("$safeBarcode");
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Insertion result: $result")
            
            when {
                result?.contains("INSERTED_TO_ACTIVE") == true -> {
                    Toast.makeText(this, "✓ 입력 완료", Toast.LENGTH_SHORT).show()
                }
                result?.contains("INSERTED_TO_SCAN_BAR") == true -> {
                    Toast.makeText(this, "✓ 입력 완료", Toast.LENGTH_SHORT).show()
                }
                result?.contains("CALLED_DOIPGOSCAN") == true -> {
                    Toast.makeText(this, "✓ 처리 완료", Toast.LENGTH_SHORT).show()
                }
                result?.contains("NO_TARGET_FOUND") == true -> {
                    Toast.makeText(this, "⚠ 입력 대상을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                }
                result?.contains("ERROR") == true -> {
                    Toast.makeText(this, "⚠ 입력 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class ScannerBridge {
        @JavascriptInterface
        fun openScanner() {
            Log.d(TAG, "📷 openScanner called from JavaScript")
            runOnUiThread {
                if (hasCameraPermission()) {
                    Log.d(TAG, "Launching scanner activity...")
                    val intent = Intent(this@MainActivity, BarcodeScannerActivity::class.java)
                    scannerLauncher.launch(intent)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "카메라 권한이 필요합니다",
                        Toast.LENGTH_SHORT
                    ).show()
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

        if (requestCode == 100) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
                setupWebView()
                webView.loadUrl(HOME_URL)
            } else {
                Toast.makeText(
                    this,
                    "카메라 권한이 필요합니다",
                    Toast.LENGTH_LONG
                ).show()
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
