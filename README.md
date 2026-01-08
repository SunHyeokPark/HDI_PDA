# HDI 입고 스캐너 Android 앱 (Kotlin 버전)

현대산업개발 입고 바코드 스캐너 모바일 앱입니다.

## 🎯 Kotlin으로 개발

- ✅ 최신 Kotlin 언어 사용
- ✅ Android 모던 개발 방식
- ✅ 간결하고 안전한 코드

## 🚀 빠른 시작

### 방법 1: GitHub Actions로 자동 빌드 (추천!)

1. 이 저장소를 GitHub에 업로드
2. "Actions" 탭에서 자동 빌드 시작
3. 완료 후 APK 다운로드

### 방법 2: 로컬 빌드

```bash
# Windows
gradlew.bat assembleDebug

# Mac/Linux
./gradlew assembleDebug
```

APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

## 📝 URL 변경 방법

`app/src/main/kotlin/com/hdi/barcodescan/MainActivity.kt` 파일의 28번째 줄:

```kotlin
// 현재 URL
webView.loadUrl("http://erp.hdi21.co.kr/mobile/BarcodeIn_scan_camera_ver.asp")

// 변경 예시
webView.loadUrl("여기에_새로운_URL_입력")
```

변경 후 다시 빌드하면 됩니다!

## 🔧 기능

- ✅ HTTP 사이트 지원 (HTTPS 불필요)
- ✅ 카메라 완전 권한
- ✅ 실시간 바코드 스캔
- ✅ WebView 기반으로 웹사이트 자동 업데이트 반영
- ✅ Kotlin의 간결한 문법으로 유지보수 용이

## 📦 배포

1. APK 파일을 직원들에게 공유
2. Android 기기에서 "출처를 알 수 없는 앱 설치" 허용
3. APK 설치

## 🛠 요구사항

- Android 7.0 (API 24) 이상
- 카메라 권한

## 📋 Kotlin의 장점

### Java vs Kotlin 비교

**Java:**
```java
private boolean hasCameraPermission() {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED;
}
```

**Kotlin:**
```kotlin
private fun hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}
```

더 간결하고 안전한 코드! 🎉

## 📄 라이선스

내부 사용 전용
