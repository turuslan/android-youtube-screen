package com.youtube.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.absoluteValue

fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

data class ViewLayout<T : View>(val view: T, val layout: WindowManager.LayoutParams)

fun WindowManager.add(view: ViewLayout<*>) {
    addView(view.view, view.layout)
}

fun WindowManager.remove(view: ViewLayout<*>) {
    view.view.parent?.also { removeView(view.view) }
}

fun WindowManager.update(view: ViewLayout<*>) {
    updateViewLayout(view.view, view.layout)
}

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service()
    }

    private fun service() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, MainService::class.java))
            finish()
            return
        }
        toast("${resources.getString(R.string.app_name)} requires permission")
        startActivityForResult(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")
            ),
            0,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        service()
    }
}

class MainService : Service() {
    private fun image(r: Int): ViewLayout<ImageView> {
        val x1 = 144f
        val x2 = 176f
        val x3 = 170f
        val s2 = x3 / x1
        val (w, h) = when (r) {
            R.drawable.icon -> Pair(x1, x1 * 126 / x2)
            R.drawable.close -> Pair(92f, 92f)
            else -> throw Exception()
        }
        val view = ImageView(this)
        view.setImageResource(r)
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        view.alpha = 0.6f
        val layout = WindowManager.LayoutParams(
            (w * s2).toInt(), (h * s2).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        return ViewLayout(view, layout)
    }

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val screenView by lazy {
        val view = View(this)
        view.setBackgroundColor(Color.BLACK)
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        )
        ViewLayout(view, layout)
    }
    private val iconView by lazy { image(R.drawable.icon) }
    private val closeView by lazy { image(R.drawable.close) }

    private var screen = false
        set(dark) {
            screenView.view.visibility = if (dark) View.VISIBLE else View.INVISIBLE
            iconView.layout.screenBrightness = if (dark) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            windowManager.update(iconView)
            field = dark
        }

    private var close = false
        set(close) {
            if (close) {
                val ws = screenView.view.width
                val wi = iconView.view.width
                val wc = closeView.view.width
                val p = 14
                val xi = iconView.layout.x
                val dx = p + (wi + wc) / 2
                val x1 = xi - dx
                val x2 = xi + dx
                closeView.layout.x = if (-ws / 2 < x1 - wc / 2) x1 else x2
                closeView.layout.y = iconView.layout.y
                windowManager.update(closeView)
            }
            closeView.view.visibility = if (close) View.VISIBLE else View.INVISIBLE
            field = close
        }

    data class Touch(
        val x: Float, val y: Float,
        val vx: Int, val vy: Int,
        var move: Boolean,
        val canScreen: Boolean,
    )

    private val hold = 600
    private val slop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private var touch: Touch? = null

    private fun touchUpdate(e: MotionEvent) {
        touch?.let {
            val dx = (e.rawX - it.x).toInt()
            val dy = (e.rawY - it.y).toInt()
            if (!it.move && (dx.absoluteValue > slop || dy.absoluteValue > slop)) it.move = true
            if (it.move) {
                iconView.layout.x = it.vx + dx
                iconView.layout.y = it.vy + dy
                windowManager.update(iconView)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        screenView.view.setOnTouchListener { _, _ ->
            screen = false
            true
        }
        iconView.view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    touch = Touch(
                        e.rawX, e.rawY,
                        iconView.layout.x, iconView.layout.y,
                        false,
                        !screen && !close,
                    )
                    close = false
                    screen = false
                }

                MotionEvent.ACTION_MOVE -> {
                    touch?.let {
                        touchUpdate(e)
                        val dt = e.eventTime - e.downTime
                        close = !it.move && dt > hold
                    }
                }

                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    touch?.let {
                        touchUpdate(e)
                        if (it.canScreen && !it.move && !close) screen = !screen
                        touch = null
                    }
                }
            }
            true
        }
        closeView.view.setOnClickListener { stopSelf() }
        windowManager.add(screenView)
        windowManager.add(iconView)
        windowManager.add(closeView)
        screen = false
        close = false
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.remove(screenView)
        windowManager.remove(iconView)
        windowManager.remove(closeView)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}
