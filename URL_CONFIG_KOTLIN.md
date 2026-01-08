# 📌 URL 설정 가이드 (Kotlin 버전)

## 현재 설정된 URL
```kotlin
webView.loadUrl("http://erp.hdi21.co.kr/mobile/BarcodeIn_scan_camera_ver.asp")
```

## URL 변경 방법

### 파일 위치
`app/src/main/kotlin/com/hdi/barcodescan/MainActivity.kt`

### 변경할 줄
28번째 줄:
```kotlin
// 여기를 수정하세요
webView.loadUrl("여기에_새로운_URL_입력")
```

### 예시

#### 개발 서버
```kotlin
webView.loadUrl("http://dev.erp.hdi21.co.kr/mobile/BarcodeIn_scan_camera_ver.asp")
```

#### 운영 서버
```kotlin
webView.loadUrl("http://erp.hdi21.co.kr/mobile/BarcodeIn_scan_camera_ver.asp")
```

#### 로컬 테스트
```kotlin
webView.loadUrl("http://192.168.0.100/mobile/BarcodeIn_scan_camera_ver.asp")
```

#### HTTPS 사이트
```kotlin
webView.loadUrl("https://secure.example.com/scanner")
```

## 도메인 추가

여러 도메인을 사용하는 경우, `network_security_config.xml` 파일에도 추가하세요:

### 파일 위치
`app/src/main/res/xml/network_security_config.xml`

### 추가 방법
```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">erp.hdi21.co.kr</domain>
    <domain includeSubdomains="true">dev.erp.hdi21.co.kr</domain>  <!-- 추가 -->
    <domain includeSubdomains="true">새로운도메인.com</domain>    <!-- 추가 -->
    <domain includeSubdomains="true">192.168.0.100</domain>       <!-- 로컬 IP -->
</domain-config>
```

## Kotlin 코드 수정 팁

### 여러 URL 지원 (환경별 분기)
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    checkAndRequestPermissions()
    webView = findViewById(R.id.webview)
    setupWebView()
    
    // 환경별 URL 설정
    val baseUrl = when (BuildConfig.BUILD_TYPE) {
        "debug" -> "http://dev.erp.hdi21.co.kr"  // 개발 환경
        else -> "http://erp.hdi21.co.kr"          // 운영 환경
    }
    
    webView.loadUrl("$baseUrl/mobile/BarcodeIn_scan_camera_ver.asp")
}
```

### 상수로 관리
```kotlin
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val ALL_PERMISSIONS_CODE = 101
        
        // URL을 상수로 관리
        private const val BASE_URL = "http://erp.hdi21.co.kr"
        private const val PAGE_PATH = "/mobile/BarcodeIn_scan_camera_ver.asp"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // ...
        webView.loadUrl("$BASE_URL$PAGE_PATH")
    }
}
```

## 변경 후 빌드

### 로컬 빌드
```bash
# Windows
gradlew.bat assembleDebug

# Mac/Linux  
./gradlew assembleDebug
```

### GitHub Actions
```bash
# 코드를 GitHub에 푸시하면 자동으로 빌드됩니다
git add .
git commit -m "URL 변경"
git push origin main
```

## 주의사항

1. **HTTP 사이트를 사용하는 경우**
   - `network_security_config.xml`에 도메인 추가 필수
   - `AndroidManifest.xml`의 `usesCleartextTraffic="true"` 확인

2. **HTTPS 사이트를 사용하는 경우**
   - 별도 설정 불필요
   - 보안이 더 강화됨

3. **IP 주소를 사용하는 경우**
   - `network_security_config.xml`에 IP 추가
   - 포트 번호도 포함 가능 (예: 192.168.0.100:8080)

## 문제 해결

### URL이 로드되지 않는 경우
1. `network_security_config.xml` 확인
2. 인터넷 권한 확인 (`AndroidManifest.xml`)
3. 로그 확인: `adb logcat | grep WebView`

### 카메라가 작동하지 않는 경우
1. 권한이 허용되었는지 확인
2. `AndroidManifest.xml`의 카메라 권한 확인
3. 앱 설정에서 수동으로 권한 허용

## 실제 사용 예시

**개발팀용:**
```kotlin
webView.loadUrl("http://dev.erp.hdi21.co.kr/mobile/BarcodeIn_scan_camera_ver.asp")
```

**현장팀용:**
```kotlin
webView.loadUrl("http://erp.hdi21.co.kr/mobile/BarcodeIn_scan_camera_ver.asp")
```

**테스트용:**
```kotlin
webView.loadUrl("http://192.168.1.100:3000/mobile/BarcodeIn_scan_camera_ver.asp")
```
