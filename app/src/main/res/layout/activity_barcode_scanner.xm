<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- 카메라 프리뷰 -->
    <androidx.camera.view.PreviewView
        android:id="@+id/preview_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 스캔 가이드 오버레이 -->
    <View
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#80000000" />

    <!-- 스캔 가이드 프레임 -->
    <FrameLayout
        android:layout_width="280dp"
        android:layout_height="280dp"
        android:layout_centerInParent="true">
        
        <View
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@android:color/transparent" />
        
        <!-- 상단 좌측 코너 -->
        <View
            android:layout_width="40dp"
            android:layout_height="4dp"
            android:layout_gravity="top|start"
            android:background="#00FF00" />
        <View
            android:layout_width="4dp"
            android:layout_height="40dp"
            android:layout_gravity="top|start"
            android:background="#00FF00" />
        
        <!-- 상단 우측 코너 -->
        <View
            android:layout_width="40dp"
            android:layout_height="4dp"
            android:layout_gravity="top|end"
            android:background="#00FF00" />
        <View
            android:layout_width="4dp"
            android:layout_height="40dp"
            android:layout_gravity="top|end"
            android:background="#00FF00" />
        
        <!-- 하단 좌측 코너 -->
        <View
            android:layout_width="40dp"
            android:layout_height="4dp"
            android:layout_gravity="bottom|start"
            android:background="#00FF00" />
        <View
            android:layout_width="4dp"
            android:layout_height="40dp"
            android:layout_gravity="bottom|start"
            android:background="#00FF00" />
        
        <!-- 하단 우측 코너 -->
        <View
            android:layout_width="40dp"
            android:layout_height="4dp"
            android:layout_gravity="bottom|end"
            android:background="#00FF00" />
        <View
            android:layout_width="4dp"
            android:layout_height="40dp"
            android:layout_gravity="bottom|end"
            android:background="#00FF00" />
    </FrameLayout>

    <!-- 상단 닫기 버튼 -->
    <Button
        android:id="@+id/close_button"
        android:layout_width="50dp"
        android:layout_height="50dp"
        android:layout_alignParentTop="true"
        android:layout_alignParentEnd="true"
        android:layout_margin="16dp"
        android:background="@android:color/transparent"
        android:text="✕"
        android:textColor="#FFFFFF"
        android:textSize="24sp"
        android:textStyle="bold" />

    <!-- 하단 상태 텍스트 -->
    <TextView
        android:id="@+id/status_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:layout_centerHorizontal="true"
        android:layout_marginBottom="80dp"
        android:background="#CC000000"
        android:padding="12dp"
        android:text="QR 코드를 가이드 안에 위치시키세요"
        android:textColor="#FFFFFF"
        android:textSize="14sp" />

    <!-- 안내 텍스트 -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentTop="true"
        android:layout_centerHorizontal="true"
        android:layout_marginTop="60dp"
        android:background="#CC000000"
        android:padding="12dp"
        android:text="바코드 스캐너"
        android:textColor="#FFFFFF"
        android:textSize="18sp"
        android:textStyle="bold" />

</RelativeLayout>
