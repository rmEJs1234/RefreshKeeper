package com.refreshkeeper

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statsText: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var serviceButton: Button
    private lateinit var systemButton: Button
    private lateinit var testView: PointerTestView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "포인터 120"
            textSize = 24f
        }
        statsText = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad / 2, 0, pad / 2)
            text = "화면 주사율: -- Hz\n마우스 입력 속도: -- Hz\n앱 프레임: -- fps"
        }
        val hint = TextView(this).apply {
            textSize = 13f
            text = "마우스 입력 속도가 30~40Hz쯤이면 블루투스 전송이 원인이고, " +
                "100Hz 이상인데도 끊겨 보이면 화면 표시 쪽 문제예요."
            setPadding(0, 0, 0, pad / 2)
        }
        val virtualSwitch = Switch(this).apply {
            text = "가상 포인터 사용 (아래 영역 안에서만 동작)"
            setOnCheckedChangeListener { _, checked -> testView.useVirtual = checked }
        }

        testView = PointerTestView(this)
        testView.onStats = { inputHz, fps ->
            val hz = display?.refreshRate ?: 0f
            val input = if (inputHz > 0) "$inputHz Hz" else "-- Hz (마우스를 움직여 보세요)"
            statsText.text = "화면 주사율: %.0f Hz\n마우스 입력 속도: %s\n앱 프레임: %d fps".format(hz, input, fps)
        }

        serviceStatus = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceButton = Button(this).apply { setOnClickListener { onToggleService() } }
        systemButton = Button(this).apply { setOnClickListener { onToggleSystemRefresh() } }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(serviceButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(systemButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        root.addView(title)
        root.addView(statsText)
        root.addView(hint)
        root.addView(virtualSwitch)
        root.addView(testView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(serviceStatus)
        root.addView(buttons)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun onToggleService() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        val intent = Intent(this, RefreshService::class.java)
        if (isServiceRunning()) stopService(intent) else startForegroundService(intent)
        window.decorView.postDelayed({ refreshUi() }, 300)
    }

    private fun onToggleSystemRefresh() {
        if (!Settings.System.canWrite(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
            return
        }
        try {
            val current = Settings.System.getFloat(contentResolver, "min_refresh_rate", 0f)
            val next = if (current >= 119f) 0f else 120f
            Settings.System.putFloat(contentResolver, "min_refresh_rate", next)
            Toast.makeText(
                this,
                if (next > 0f) "최소 주사율을 120Hz로 설정했어요" else "최소 주사율 설정을 되돌렸어요",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "설정 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
        refreshUi()
    }

    private fun refreshUi() {
        serviceStatus.text = when {
            !Settings.canDrawOverlays(this) -> "120Hz 유지 오버레이: 권한 필요"
            isServiceRunning() -> "120Hz 유지 오버레이: 켜짐"
            else -> "120Hz 유지 오버레이: 꺼짐"
        }
        serviceButton.text = when {
            !Settings.canDrawOverlays(this) -> "오버레이 권한 허용"
            isServiceRunning() -> "오버레이 끄기"
            else -> "오버레이 켜기"
        }
        systemButton.text = when {
            !Settings.System.canWrite(this) -> "시스템 설정 권한 허용"
            Settings.System.getFloat(contentResolver, "min_refresh_rate", 0f) >= 119f -> "최소 주사율 되돌리기"
            else -> "최소 주사율 120Hz 시도"
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(50).any { it.service.className == RefreshService::class.java.name }
    }
}
