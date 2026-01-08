package com.hdi.barcodescan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
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
    
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val ALL_PERMISSIONS_CODE = 101
    }

    // 파일 선택 결과 처리
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
        
        // 권한이 없으면 먼저 요청
        if (!hasCameraPermission()) {
            checkAndRequestPermissions()
        } else {
            setupWebView()
            webView.loadUrl("http://erp.hdi21.co.kr/mobile")
        }
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
        }

        // JavaScript Bridge 추가 - 중요!
        webView.addJavascriptInterface(CameraInterface(), "AndroidCamera")

        // WebChromeClient - 카메라 권한 및 파일 선택 처리
        webView.webChromeClient = object : WebChromeClient() {
            
            // 파일 선택 (카메라 포함)
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
            
            // 카메라 권한 요청 - 항상 승인
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    // 시스템 권한을 이미 받았으므로 WebView 권한도 자동 승인
                    request?.grant(request.resources)
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                title = if (newProgress == 100) {
                    "HDI PDA"
                } else {
                    "로딩중... $newProgress%"
                }
            }

            // 콘솔 로그를 Android 로그로 출력 (디버깅용)
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                android.util.Log.d("WebView", "${message?.message()} -- From line ${message?.lineNumber()} of ${message?.sourceId()}")
                return true
            }
        }

        // WebViewClient - 에러 처리
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 페이지 로드 완료 후 getUserMedia polyfill 주입
                injectGetUserMediaPolyfill()
            }

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

    // getUserMedia를 네이티브로 대체하는 polyfill 주입
    private fun injectGetUserMediaPolyfill() {
        val polyfill = """
            (function() {
                // getUserMedia를 오버라이드하여 항상 성공한 것처럼 처리
                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                    const originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
                    
                    navigator.mediaDevices.getUserMedia = function(constraints) {
                        console.log('getUserMedia called with constraints:', constraints);
                        
                        // 카메라 요청인 경우
                        if (constraints.video) {
                            // 실제로는 input file을 사용하도록 유도
                            console.log('Camera access requested - use file input instead');
                        }
                        
                        // 원본 함수 호출 (실패할 수 있지만 일단 시도)
                        return originalGetUserMedia(constraints);
                    };
                }
                
                // 카메라 권한 체크를 항상 granted로 리턴
                if (navigator.permissions && navigator.permissions.query) {
                    const originalQuery = navigator.permissions.query.bind(navigator.permissions);
                    
                    navigator.permissions.query = function(permissionDesc) {
                        if (permissionDesc.name === 'camera') {
                            return Promise.resolve({state: 'granted'});
                        }
                        return originalQuery(permissionDesc);
                    };
                }
                
                console.log('Camera polyfill injected successfully');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(polyfill, null)
    }

    // JavaScript에서 호출할 수 있는 네이티브 카메라 인터페이스
    inner class CameraInterface {
        @JavascriptInterface
        fun hasPermission(): Boolean {
            return hasCameraPermission()
        }

        @JavascriptInterface
        fun requestPermission() {
            runOnUiThread {
                if (!hasCameraPermission()) {
                    checkAndRequestPermissions()
                }
            }
        }
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(null)
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            null
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )
        
        // Android 13 이상
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
                    Toast.makeText(this, "권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
                    setupWebView()
                    webView.loadUrl("http://erp.hdi21.co.kr/mobile")
                } else {
                    Toast.makeText(this, 
                        "카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요.", 
                        Toast.LENGTH_LONG).show()
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
        // 앱이 다시 활성화될 때 권한 재확인
        if (!hasCameraPermission()) {
            checkAndRequestPermissions()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
