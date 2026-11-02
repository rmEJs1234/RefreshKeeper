package com.refreshkeeper

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var rate: TextView
    private lateinit var toggle: Button
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            rate.text = "현재 화면 주사율: %.0f Hz".format(display?.refreshRate ?: 0f)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "리프레시 키퍼"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        val desc = TextView(this).apply {
            text = "마우스 포인터가 끊겨 보일 때 화면을 120Hz로 유지해 줘요.\n" +
                "'다른 앱 위에 표시' 권한이 필요해요."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        }
        status = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
        }
        rate = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad / 3, 0, pad)
        }
        toggle = Button(this).apply {
            setOnClickListener { onToggle() }
        }

        root.addView(title)
        root.addView(desc)
        root.addView(status)
        root.addView(rate)
        root.addView(toggle)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun onToggle() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        val intent = Intent(this, RefreshService::class.java)
        if (isServiceRunning()) stopService(intent) else startForegroundService(intent)
        handler.postDelayed({ refreshUi() }, 300)
    }

    private fun refreshUi() {
        when {
            !Settings.canDrawOverlays(this) -> {
                status.text = "권한이 필요해요"
                toggle.text = "권한 허용하러 가기"
            }
            isServiceRunning() -> {
                status.text = "120Hz 유지 중 ✔"
                toggle.text = "끄기"
            }
            else -> {
                status.text = "꺼져 있어요"
                toggle.text = "켜기"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(50).any { it.service.className == RefreshService::class.java.name }
    }
}
