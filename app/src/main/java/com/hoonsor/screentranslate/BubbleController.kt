package com.hoonsor.screentranslate

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/** 浮動小圓點：可拖曳、放開自動貼邊；點一下翻譯、長按切換模式 */
class BubbleController(
    private val context: Context,
    private val wm: WindowManager,
    private val prefs: Prefs,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val view = BubbleView(context)
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }
    private var attached = false
    private var snapAnimator: ValueAnimator? = null

    val isShown: Boolean get() = attached

    init {
        view.setOnTouchListener(DragTouchListener())
    }

    fun show() {
        if (attached) return
        applyStyle()
        val (sw, sh) = screenSize()
        val size = sizePx()
        params.x = if (prefs.bubbleX < 0) sw - size - edgeMargin() else prefs.bubbleX.coerceIn(0, (sw - size).coerceAtLeast(0))
        params.y = if (prefs.bubbleY < 0) (sh * 0.4f).toInt() else prefs.bubbleY.coerceIn(0, (sh - size).coerceAtLeast(0))
        wm.addView(view, params)
        attached = true
    }

    fun hide() {
        if (!attached) return
        snapAnimator?.cancel()
        runCatching { wm.removeView(view) }
        attached = false
    }

    /** 讓小圓點維持在最上層（翻譯疊加層加入後呼叫） */
    fun bringToFront() {
        if (!attached) return
        runCatching { wm.removeView(view) }
        runCatching { wm.addView(view, params) }
    }

    fun applyStyle() {
        val size = sizePx()
        params.width = size
        params.height = size
        view.alpha = prefs.bubbleAlpha
        view.comicMode = prefs.mode == TranslateMode.COMIC
        view.invalidate()
        if (attached) runCatching { wm.updateViewLayout(view, params) }
    }

    /** 截圖前隱藏，避免小圓點被截進畫面 */
    fun setHiddenForCapture(hidden: Boolean) {
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun setBusy(busy: Boolean) = view.setBusy(busy)

    fun flashError() = view.flashError()

    private fun sizePx() = (prefs.bubbleSizeDp * density).roundToInt()
    private fun edgeMargin() = (2 * density).roundToInt()

    private fun screenSize(): Pair<Int, Int> {
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }

    private fun snapToEdge() {
        val (sw, sh) = screenSize()
        val size = sizePx()
        val targetX = if (params.x + size / 2 < sw / 2) edgeMargin() else sw - size - edgeMargin()
        params.y = params.y.coerceIn(0, (sh - size).coerceAtLeast(0))
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (attached) runCatching { wm.updateViewLayout(view, params) }
            }
            start()
        }
        prefs.bubbleX = targetX
        prefs.bubbleY = params.y
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPressRunnable = Runnable {
            longPressed = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressed = false
                    v.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop) && !longPressed) {
                        dragging = true
                        v.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPressRunnable)
                    when {
                        dragging -> snapToEdge()
                        !longPressed -> onTap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    if (dragging) snapToEdge()
                }
            }
            return true
        }
    }
}

/** 小圓點的繪製：漸層圓 + 「譯」字；忙碌時外圈轉動，漫畫模式右上角有琥珀色點 */
private class BubbleView(context: Context) : View(context) {
    var comicMode = false
    private var busy = false
    private var errorUntil = 0L
    private var spinAngle = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(70, 0, 0, 0)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF2, 0xB2, 0x4C) }
    private val oval = RectF()

    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            spinAngle = it.animatedValue as Float
            invalidate()
        }
    }

    fun setBusy(b: Boolean) {
        busy = b
        if (b) spinner.start() else spinner.cancel()
        invalidate()
    }

    fun flashError() {
        errorUntil = System.currentTimeMillis() + 1500
        invalidate()
        postDelayed({ invalidate() }, 1550)
    }

    override fun onDetachedFromWindow() {
        spinner.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = minOf(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f
        val inner = r * 0.9f

        val error = System.currentTimeMillis() < errorUntil
        fill.shader = if (error) {
            LinearGradient(0f, 0f, w, h, Color.rgb(0xE0, 0x6C, 0x6C), Color.rgb(0xB0, 0x45, 0x58), Shader.TileMode.CLAMP)
        } else {
            // HSL 調校過的青綠 → 靛藍漸層
            LinearGradient(0f, 0f, w, h, Color.rgb(0x4F, 0xC3, 0xB4), Color.rgb(0x3A, 0x6E, 0xC4), Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(cx, cy, inner, fill)
        ring.strokeWidth = r * 0.08f
        canvas.drawCircle(cx, cy, inner, ring)

        text.textSize = r * 0.9f
        val baseline = cy - (text.descent() + text.ascent()) / 2f
        canvas.drawText(if (busy) "…" else "譯", cx, baseline, text)

        if (busy) {
            arc.strokeWidth = r * 0.12f
            val inset = r * 0.16f
            oval.set(cx - inner + inset, cy - inner + inset, cx + inner - inset, cy + inner - inset)
            canvas.drawArc(oval, spinAngle, 100f, false, arc)
        }
        if (comicMode) {
            canvas.drawCircle(cx + inner * 0.66f, cy - inner * 0.66f, r * 0.2f, dot)
        }
    }
}
