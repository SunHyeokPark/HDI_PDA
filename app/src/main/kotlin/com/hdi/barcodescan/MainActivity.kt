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

    // 스캔 중 홈(/mobile)로 튕김 방지용
    private var isProcessingBarcode: Boolean = false

    // “돌아갈 마지막 정상 URL”
    private var lastValidUrl: String = ""

    companion object {
        private const val TAG = "HDI_PDA"
        private const val REQ_CAMERA = 100
        private const val HOME_URL = "http://erp.hdi21.co.kr/mobile"
        private const val HOME_HOST = "erp.hdi21.co.kr"
        private const val HOME_PATH_PREFIX = "/mobile"
    }

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val barcode = result.data?.getStringExtra(BarcodeScannerActivity.RESULT_BARCODE)
                if (!barcode.isNullOrEmpty()) {
                    Log.d(TAG, "Scanned: $barcode")

                    isProcessingBarcode = true

                    // 혹시 lastValidUrl이 비어있으면 현재 URL이라도 잡아둠
                    if (lastValidUrl.isBlank()) {
                        webView.url?.let { if (it.isNotBlank()) lastValidUrl = it }
                    }

                    // 안정성: 1초 뒤 주입
                    webView.postDelayed({
                        insertBarcodeAndPressEnter(barcode)

                        // 2.5~3초 후 해제(웹 처리 시간 고려)
                        webView.postDelayed({
                            isProcessingBarcode = false
                            Log.d(TAG, "Barcode processing released")
                        }, 2500)
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

            if (savedInstanceState != null) {
                // ✅ 재생성(recreate)되어도 마지막 웹 상태 복원
                webView.restoreState(savedInstanceState)
                // restoreState 후 url이 null일 수 있어 방어
                webView.url?.let { if (it.isNotBlank()) lastValidUrl = it }
            } else {
                // ✅ 최초 실행만 홈 로드
                webView.loadUrl(HOME_URL)
                lastValidUrl = HOME_URL
            }
        } else {
            requestCameraPermission()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // ✅ WebView 상태 저장 (뒤로가기 스택/현재 페이지 등)
        webView.saveState(outState)
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // JavaScript Bridge
        webView.addJavascriptInterface(ScannerBridge(), "Scanner")

        webView.webViewClient = object : WebViewClient() {

            // Android N+ (요즘 대부분 PDA는 여기로 들어옴)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                return handleUrlGuard(view, url)
            }

            // 구형 단말(레거시)
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleUrlGuard(view, url.orEmpty())
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val u = url.orEmpty()
                Log.d(TAG, "Page finished: $u")

                // 정상 URL(도메인 내, 홈 제외)은 계속 lastValidUrl로 갱신
                if (isOurDomain(u) && !isHomeUrl(u)) {
                    lastValidUrl = u
                    Log.d(TAG, "Saved lastValidUrl: $lastValidUrl")
                }

                connectScannerButton()
            }
        }
    }

    /**
     * 스캔 처리 중 홈으로 튕기면 차단 + 복구
     */
    private fun handleUrlGuard(view: WebView?, url: String): Boolean {
        if (url.isBlank()) return false

        Log.d(TAG, "URL loading: $url / isProcessingBarcode=$isProcessingBarcode / lastValidUrl=$lastValidUrl")

        // 스캔 처리 중이면 홈 이동을 막음 (http/https + 쿼리/해시 포함 커버)
        if (isProcessingBarcode && isHomeUrl(url)) {
            Log.w(TAG, "⛔ BLOCKED navigation to HOME during barcode processing: $url")
            Toast.makeText(this@MainActivity, "바코드 처리중...", Toast.LENGTH_SHORT).show()

            // 가능한 경우, 이전 정상 페이지로 즉시 복구 시도
            if (lastValidUrl.isNotBlank() && view != null) {
                view.post {
                    try {
                        view.stopLoading()
                        view.loadUrl(lastValidUrl)
                    } catch (e: Exception) {
                        Log.e(TAG, "Restore failed", e)
                    }
                }
            }
            return true // true = 이 네비게이션을 WebView가 진행하지 않음
        }

        // 도메인 밖은 기본 동작(혹은 필요하면 외부 브라우저로 보내도록 확장 가능)
        if (!isOurDomain(url)) return false

        // 도메인 내는 허용
        return false
    }

    /**
     * 웹의 “실시간 스캔” 버튼이 startLiveScanner()를 호출하도록 연결
     */
    private fun connectScannerButton() {
        val script = """
            (function() {
                if (typeof Scanner !== 'undefined' && Scanner && typeof Scanner.openScanner === 'function') {
                    // 기존 함수가 있으면 백업(원한다면 호출할 수 있음)
                    var __old = window.startLiveScanner;
                    window.startLiveScanner = function() {
                        try { Scanner.openScanner(); } catch(e) { console.error(e); }
                        return false;
                    };
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    /**
     * scan_bar에 바코드 입력 + 엔터 유사 이벤트
     * - 문자열 안전 주입(JSON.stringify)
     * - input/change/keydown/keyup(Enter) 세트로 발사
     */
    private fun insertBarcodeAndPressEnter(barcode: String) {
        val script = """
            (function() {
                try {
                    var barcode = JSON.parse(${toJsStringLiteral(barcode)});
                    console.log('Inject barcode:', barcode);
                    
                    var input = document.getElementById('scan_bar');
                    if (!input) {
                        console.error('scan_bar not found');
                        // 사이트에 따라 직접 호출 함수가 있다면 여기에 추가 가능
                        if (typeof doIpgoScan === 'function') {
                            doIpgoScan(barcode);
                            return 'DIRECT_CALL';
                        }
                        return 'FAIL_NO_INPUT';
                    }
                    
                    input.focus();
                    input.value = barcode;

                    // input/change 이벤트 (프레임워크들이 이걸 보는 경우 많음)
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));

                    // Enter keydown/keyup
                    var kd = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 });
                    var ku = new KeyboardEvent('keyup',   { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 });
                    input.dispatchEvent(kd);
                    input.dispatchEvent(ku);

                    return 'OK';
                } catch (e) {
                    console.error('Injection error', e);
                    return 'FAIL_EXCEPTION';
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Insert result: $result")
        }
    }

    /**
     * JS에 안전하게 넣기 위한 문자열 리터럴
     * 예: "ABC" -> "\"ABC\"" (JSON.parse로 다시 문자열 복원)
     */
    private fun toJsStringLiteral(value: String): String {
        // Kotlin에서 JSON 문자열로 안전하게 만들기(간단 escape)
        val escaped = buildString(value.length + 16) {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"'  -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
        return escaped
    }

    /**
     * 홈 URL 판정 (http/https, 쿼리/해시 포함 커버)
     */
    private fun isHomeUrl(url: String): Boolean {
        return try {
            val u = Uri.parse(url)
            val hostOk = (u.host ?: "").contains(HOME_HOST, ignoreCase = true)
            val path = u.path ?: ""
            hostOk && path.startsWith(HOME_PATH_PREFIX)
                    // /mobile, /mobile/, /mobile/index.asp 등 전부 포함
        } catch (_: Exception) {
            // 파싱 실패 시 보수적으로 문자열로 판단
            url.startsWith(HOME_URL, ignoreCase = true) ||
                    url.startsWith("https://$HOME_HOST/mobile", ignoreCase = true)
        }
    }

    private fun isOurDomain(url: String): Boolean {
        return try {
            val u = Uri.parse(url)
            (u.host ?: "").contains(HOME_HOST, ignoreCase = true)
        } catch (_: Exception) {
            url.contains(HOME_HOST, ignoreCase = true)
        }
    }

    // JS에서 호출되는 브릿지
    inner class ScannerBridge {
        @JavascriptInterface
        fun openScanner() {
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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupWebView()
                webView.loadUrl(HOME_URL)
                lastValidUrl = HOME_URL
            } else {
                Toast.makeText(this, "카메라 권한 필요", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
