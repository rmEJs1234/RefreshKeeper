package com.refreshkeeper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager

/**
 * 거의 투명한 작은 오버레이 SurfaceView를 화면 구석에 띄우고,
 * 그 Surface가 120fps로 계속 그려지도록 하면서 프레임레이트 120Hz를 요청한다.
 * 마우스 포인터만 움직일 때 시스템이 주사율을 낮추는 것을 막는 것이 목적이다.
 */
class RefreshService : Service() {

    private var windowManager: WindowManager? = null
    private var overlay: SurfaceView? = null

    @Volatile
    private var running = false
    private var renderThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (overlay == null) {
            try {
                addOverlay()
            } catch (e: Exception) {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "refresh_keeper"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "리프레시 키퍼", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("120Hz 유지 중")
            .setContentText("주사율을 높게 유지하고 있어요")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun addOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val view = SurfaceView(this)
        view.setZOrderOnTop(true)
        view.holder.setFormat(PixelFormat.TRANSLUCENT)

        val lp = WindowManager.LayoutParams(
            16, 16,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.preferredRefreshRate = TARGET_HZ

        // 현재 해상도와 같은 모드 중 가장 높은 주사율 모드를 우선 요청
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val current = display.mode
        val best = display.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
        if (best != null) lp.preferredDisplayModeId = best.modeId

        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                requestFrameRate(holder.surface)
                startRenderLoop(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {
                requestFrameRate(holder.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopRenderLoop()
            }
        })

        wm.addView(view, lp)
        overlay = view
    }

    private fun requestFrameRate(surface: Surface) {
        if (!surface.isValid) return
        if (Build.VERSION.SDK_INT >= 31) {
            surface.setFrameRate(
                TARGET_HZ,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS
            )
        } else {
            surface.setFrameRate(TARGET_HZ, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }

    private fun startRenderLoop(holder: SurfaceHolder) {
        stopRenderLoop()
        running = true
        renderThread = Thread {
            var flip = false
            while (running) {
                val canvas = try {
                    holder.lockCanvas()
                } catch (e: Exception) {
                    null
                }
                if (canvas != null) {
                    // 사실상 안 보이는 알파 1~2 값을 번갈아 그려서 프레임이 계속 갱신되게 함
                    canvas.drawColor(
                        Color.argb(if (flip) 1 else 2, 0, 0, 0),
                        PorterDuff.Mode.SRC
                    )
                    flip = !flip
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                    }
                }
                try {
                    Thread.sleep(7)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also { it.start() }
    }

    private fun stopRenderLoop() {
        running = false
        renderThread?.interrupt()
        renderThread?.join(200)
        renderThread = null
    }

    override fun onDestroy() {
        stopRenderLoop()
        overlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        overlay = null
        super.onDestroy()
    }

    companion object {
        const val TARGET_HZ = 120f
    }
}
