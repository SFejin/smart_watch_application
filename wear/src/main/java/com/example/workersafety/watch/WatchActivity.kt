package com.example.workersafety.watch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import com.example.workersafety.link.WatchLinkProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable

/** Sensor pages and an accessible phone connection / delivery page. */
class WatchActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPage = 0 // 0: 시작, 1: 심박수, 2: 피부온도, 3: 연결
    private var checkingPhone = false
    private var lastPhoneCheck = 0L

    private fun showPage(page: Int) {
        if (page !in 0..3) return
        currentPage = page
        findViewById<View>(R.id.pageStart).visibility =
            if (page == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pageHeart).visibility =
            if (page == 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pageSkin).visibility =
            if (page == 2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pageStatus).visibility = if (page == 3) View.VISIBLE else View.GONE
        if (page == 3) refreshPhoneConnection()
    }

    private val refresh = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences(SensorService.PREFS, MODE_PRIVATE)
            findViewById<TextView>(R.id.txtHeartRate).text =
                if (prefs.contains("live_hr"))
                    prefs.getInt("live_hr", 0).toString()
                else "--"
            findViewById<TextView>(R.id.txtTemperature).text =
                if (prefs.contains("live_skin"))
                    "%.1f°C".format(Locale.KOREA, prefs.getFloat("live_skin", 0f))
                else "-- °C"

            // 기존 상태 정보는 유지하되, 긴 진단 문구가 원형 화면을 가리지 않도록 숨긴다.
            findViewById<TextView>(R.id.txtStatus).apply {
                text = prefs.getString("status", "대기 중") ?: "대기 중"
                visibility = View.GONE
            }
            findViewById<TextView>(R.id.txtConnection).text = prefs.getString("phone_connection", "휴대폰 연결 확인 중")
            findViewById<TextView>(R.id.txtTransmission).text = prefs.getString("delivery", "전송 기록 없음")
            findViewById<TextView>(R.id.txtSensorStatus).text = prefs.getString("status", "측정을 시작하세요")
            if (android.os.SystemClock.elapsedRealtime() - lastPhoneCheck >= 15_000L) refreshPhoneConnection()
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch)

        findViewById<Button>(R.id.btnStart).setOnClickListener { requestAndStart() }
        findViewById<Button>(R.id.navHeart).setOnClickListener { showPage(1) }
        findViewById<Button>(R.id.navSkin).setOnClickListener { showPage(2) }

        findViewById<Button>(R.id.navStatus).setOnClickListener { showPage(3) }
        findViewById<Button>(R.id.btnStatusBack).setOnClickListener { showPage(0) }

        // Manual send is reachable from the connection page.
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            startService(
                Intent(this, SensorService::class.java)
                    .setAction(SensorService.ACTION_SEND_NOW)
            )
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, SensorService::class.java))
            showPage(0)
        }
        showPage(savedInstanceState?.getInt("page") ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", currentPage)
        super.onSaveInstanceState(outState)
    }

    private fun refreshPhoneConnection() {
        if (checkingPhone) return
        checkingPhone = true
        lastPhoneCheck = android.os.SystemClock.elapsedRealtime()
        fun status(message: String) {
            getSharedPreferences(SensorService.PREFS, MODE_PRIVATE).edit()
                .putString("phone_connection", message).apply()
            checkingPhone = false
        }
        try {
            Wearable.getCapabilityClient(this).getCapability(
                WatchLinkProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { info ->
                    val phone = info.nodes.singleOrNull()
                    status(when {
                        info.nodes.isEmpty() -> "휴대폰 미연결 · 앱 설치/페어링 확인"
                        phone == null -> "휴대폰 여러 대 · 한 대만 연결하세요"
                        phone.isNearby -> "휴대폰 연결됨 · 가까운 기기"
                        else -> "휴대폰 연결됨 · 원격 경로"
                    })
                }
                .addOnFailureListener { status("연결 확인 실패 · Google Play 서비스 확인") }
        } catch (_: Exception) {
            status("연결 확인 실패 · Google Play 서비스 확인")
        }
    }

    private fun requestAndStart() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 101)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notification_asked", false)
        ) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
            return
        }
        startSession()
    }

    private fun startSession() {
        val selected = findViewById<RadioGroup>(R.id.groupInterval).checkedRadioButtonId
        val minutes = if (selected == R.id.radio10) 10 else 5
        val intent = Intent(this, SensorService::class.java).apply {
            action = SensorService.ACTION_START
            putExtra(SensorService.EXTRA_MINUTES, minutes)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        showPage(1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 || requestCode == 102) {
            if (requestCode == 101 && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "심박/피부온도 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            } else if (requestCode == 102) {
                startSession()
            } else {
                requestAndStart()
            }
        }
    }

    @Deprecated("Wear OS의 뒤로 가기 동작")
    override fun onBackPressed() {
        if (currentPage != 0) showPage(0) else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
        refreshPhoneConnection()
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
