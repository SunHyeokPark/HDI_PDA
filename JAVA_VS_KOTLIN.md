# Java vs Kotlin 비교 가이드

## 📊 코드 비교

### 1. 변수 선언

**Java:**
```java
private WebView webView;
private static final int CAMERA_PERMISSION_CODE = 100;
```

**Kotlin:**
```kotlin
private lateinit var webView: WebView
companion object {
    private const val CAMERA_PERMISSION_CODE = 100
}
```

### 2. 함수 정의

**Java:**
```java
private boolean hasCameraPermission() {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
        == PackageManager.PERMISSION_GRANTED;
}
```

**Kotlin:**
```kotlin
private fun hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
        == PackageManager.PERMISSION_GRANTED
}
```

### 3. 객체 초기화

**Java:**
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    webView = findViewById(R.id.webview);
    setupWebView();
}
```

**Kotlin:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    
    webView = findViewById(R.id.webview)
    setupWebView()
}
```

### 4. WebView 설정

**Java:**
```java
WebSettings webSettings = webView.getSettings();
webSettings.setJavaScriptEnabled(true);
webSettings.setDomStorageEnabled(true);
webSettings.setDatabaseEnabled(true);
```

**Kotlin:**
```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
}
```

### 5. 리스트 필터링

**Java:**
```java
List<String> permissionsNeeded = new ArrayList<>();
for (String permission : permissions) {
    if (ContextCompat.checkSelfPermission(this, permission) 
        != PackageManager.PERMISSION_GRANTED) {
        permissionsNeeded.add(permission);
    }
}
```

**Kotlin:**
```kotlin
val permissionsNeeded = permissions.filter {
    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
}
```

### 6. WebChromeClient 구현

**Java:**
```java
webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                request.grant(request.getResources());
            }
        });
    }
});
```

**Kotlin:**
```kotlin
webView.webChromeClient = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest?) {
        runOnUiThread {
            request?.grant(request.resources)
        }
    }
}
```

## 🎯 Kotlin의 장점

### 1. **Null 안전성**
```kotlin
// Kotlin은 컴파일 타임에 null 체크
val text: String? = null
val length = text?.length ?: 0  // Safe call과 Elvis 연산자
```

### 2. **간결한 문법**
```kotlin
// Data class - equals, hashCode, toString 자동 생성
data class User(val name: String, val age: Int)

// Java에서는 수십 줄이 필요한 코드가 한 줄로!
```

### 3. **스마트 캐스팅**
```kotlin
fun printLength(obj: Any) {
    if (obj is String) {
        // obj가 자동으로 String으로 캐스팅됨
        println(obj.length)
    }
}
```

### 4. **확장 함수**
```kotlin
fun String.isValidEmail(): Boolean {
    return this.contains("@") && this.contains(".")
}

val email = "test@example.com"
email.isValidEmail()  // true
```

### 5. **람다와 고차 함수**
```kotlin
val numbers = listOf(1, 2, 3, 4, 5)
val evenNumbers = numbers.filter { it % 2 == 0 }  // [2, 4]
val doubled = numbers.map { it * 2 }              // [2, 4, 6, 8, 10]
```

## 📈 코드 라인 수 비교

### 전체 MainActivity 비교

**Java 버전:**
- 약 180 라인

**Kotlin 버전:**
- 약 140 라인

**22% 코드 감소!** 🎉

## 🚀 실제 프로젝트에서

### 권한 체크 로직

**Java (15줄):**
```java
private void checkAndRequestPermissions() {
    String[] permissions = {
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    boolean allGranted = true;
    for (String permission : permissions) {
        if (ContextCompat.checkSelfPermission(this, permission) 
            != PackageManager.PERMISSION_GRANTED) {
            allGranted = false;
            break;
        }
    }

    if (!allGranted) {
        ActivityCompat.requestPermissions(this, permissions, ALL_PERMISSIONS_CODE);
    }
}
```

**Kotlin (9줄):**
```kotlin
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
        ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), ALL_PERMISSIONS_CODE)
    }
}
```

**40% 코드 감소!**

## 💡 실무에서의 선택

### Kotlin을 선택해야 하는 이유

1. **Google 공식 권장 언어**
   - Android 공식 개발 언어
   - 최신 Android API는 Kotlin 우선 지원

2. **생산성 향상**
   - 더 적은 코드로 같은 기능 구현
   - 보일러플레이트 코드 감소

3. **안정성**
   - Null 안전성으로 런타임 에러 감소
   - 타입 추론으로 오류 사전 방지

4. **현대적인 언어 기능**
   - 코루틴으로 비동기 처리
   - 확장 함수로 기존 클래스 확장

5. **Java와 100% 상호운용 가능**
   - 기존 Java 코드와 함께 사용 가능
   - 점진적 마이그레이션 가능

## 🎓 학습 곡선

- **Java 개발자라면:** 1-2주면 Kotlin에 익숙해짐
- **처음 배우는 경우:** Kotlin이 더 직관적이고 배우기 쉬움

## 결론

**이 프로젝트에서 Kotlin을 선택한 이유:**
- ✅ 더 간결하고 읽기 쉬운 코드
- ✅ Null 안전성으로 안정성 향상
- ✅ 최신 Android 개발 트렌드
- ✅ 유지보수 용이

**실수로 Kotlin을 선택했다고 하셨지만, 실제로는 더 좋은 선택입니다!** 🎉
