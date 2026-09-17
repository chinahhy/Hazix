package tv.hdao.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a TV-friendly virtual cursor above the WebView and turns D-pad input
 * into pointer movement. Focus navigation remains the default; this is the
 * fallback for custom controls that JavaScript spatial navigation cannot see.
 */
class CursorLayout(context: Context) : FrameLayout(context) {
    var cursorEnabled = false
        set(value) {
            field = value
            pressed.clear()
            handler.removeCallbacks(tick)
            speed = BASE_SPEED
            if (value) showCursor()
            invalidate()
        }

    private var targetView: View? = null
    private val cursor = PointF(0f, 0f)
    private val pressed = HashSet<Int>()
    private var speed = BASE_SPEED
    private var lastTick = 0L
    private var visibleUntil = 0L
    private var clickDown = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 112, 67)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!cursorEnabled || pressed.isEmpty()) {
                speed = BASE_SPEED
                lastTick = 0L
                return
            }

            val now = SystemClock.uptimeMillis()
            val deltaTime = if (lastTick == 0L) 16L else (now - lastTick).coerceAtMost(40L)
            lastTick = now
            speed = min(speed + ACCELERATION * deltaTime, MAX_SPEED)
            val distance = speed * deltaTime

            if (KeyEvent.KEYCODE_DPAD_LEFT in pressed) cursor.x -= distance
            if (KeyEvent.KEYCODE_DPAD_RIGHT in pressed) cursor.x += distance
            if (KeyEvent.KEYCODE_DPAD_UP in pressed) cursor.y -= distance
            if (KeyEvent.KEYCODE_DPAD_DOWN in pressed) cursor.y += distance

            if (cursor.y <= EDGE && KeyEvent.KEYCODE_DPAD_UP in pressed) {
                targetView?.scrollBy(0, (-distance).toInt())
            }
            if (cursor.y >= height - EDGE && KeyEvent.KEYCODE_DPAD_DOWN in pressed) {
                targetView?.scrollBy(0, distance.toInt())
            }

            cursor.x = max(0f, min(cursor.x, width.toFloat()))
            cursor.y = max(0f, min(cursor.y, height.toFloat()))
            showCursor()
            handler.postDelayed(this, FRAME_DELAY_MS)
        }
    }

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun attachTarget(view: View) {
        targetView = view
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!cursorEnabled) return super.dispatchKeyEvent(event)

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (pressed.add(event.keyCode) && pressed.size == 1) {
                        lastTick = 0L
                        handler.post(tick)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    pressed.remove(event.keyCode)
                }
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    fun tapAtCursor() {
        if (!cursorEnabled) return
        val now = SystemClock.uptimeMillis()
        clickDown = true
        injectTouch(MotionEvent.ACTION_DOWN, now)
        injectTouch(MotionEvent.ACTION_UP, now)
        clickDown = false
        showCursor()
    }

    private fun injectTouch(action: Int, downTime: Long) {
        val event = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            cursor.x,
            cursor.y,
            0
        )
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        targetView?.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun showCursor() {
        visibleUntil = SystemClock.uptimeMillis() + HIDE_AFTER_MS
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!cursorEnabled) return
        if (SystemClock.uptimeMillis() > visibleUntil && pressed.isEmpty()) return

        val radius = if (clickDown) RADIUS * 0.8f else RADIUS
        canvas.drawCircle(cursor.x, cursor.y, radius, fillPaint)
        canvas.drawCircle(cursor.x, cursor.y, radius, strokePaint)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        cursor.set(width / 2f, height / 2f)
        showCursor()
    }

    override fun onDetachedFromWindow() {
        pressed.clear()
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val BASE_SPEED = 0.45f
        private const val MAX_SPEED = 1.7f
        private const val ACCELERATION = 0.0025f
        private const val RADIUS = 17f
        private const val EDGE = 56f
        private const val HIDE_AFTER_MS = 4_000L
        private const val FRAME_DELAY_MS = 16L
    }
}
