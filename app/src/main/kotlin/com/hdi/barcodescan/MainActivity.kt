package com.hdi.barcodescan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val ALL_PERMISSIONS_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 권한 확인 및 요청
        checkAndRequestPermissions()

        webView = findViewById(R.id.webview)
        setupWebView()
        
        // HTTP 사이트 로드 - 여기에 실제 URL 입력
        webView.loadUrl("http://erp.hdi21.co.kr/mobile/BarcodeIn_scan_camera_ver.asp")
    }

    private fun setupWebView() {
        // WebView 기본 설정
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            
            // HTTP 컨텐츠 허용
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // 캐시 설정
            cacheMode = WebSettings.LOAD_DEFAULT
            setAppCacheEnabled(true)
        }

        // WebChromeClient - 카메라 권한 처리
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.resources?.forEach { resource ->
                        if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                            if (hasCameraPermission()) {
                                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                            } else {
                                request.deny()
                                Toast.makeText(
                                    this@MainActivity,
                                    "카메라 권한이 필요합니다",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@runOnUiThread
                        }
                    }
                    request?.grant(request.resources)
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                // 진행률 표시 (선택적)
                title = if (newProgress == 100) {
                    "입고 스캐너"
                } else {
                    "로딩중... $newProgress%"
                }
            }
        }

        // WebViewClient - 에러 처리
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Toast.makeText(
                    this@MainActivity,
                    "오류: $description",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

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
                
                val message = if (allGranted) {
                    "권한이 허용되었습니다"
                } else {
                    "카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요."
                }
                
                Toast.makeText(this, message, 
                    if (allGranted) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
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
