package com.hoonsor.screentranslate

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 全螢幕翻譯疊加層：在原文位置畫上中文翻譯框。
 * - 點翻譯框：隱藏該框，露出原文（再點一次恢復）
 * - 點空白處：關閉
 * - 按住任意處：暫時顯示全部原文，放開恢復
 * - 滑動：關閉並把滑動手勢轉給底下的 App（翻頁）
 */
@SuppressLint("ViewConstructor")
class ResultOverlayView(
    context: Context,
    private val result: TranslationResult,
    private val screenW: Int,
    private val screenH: Int,
    maxTextSp: Int,
    private val listener: Listener,
) : View(context) {

    interface Listener {
        fun onDismiss()
        fun onSwipe(points: List<PointF>, durationMs: Long)
    }

    private class Box(val rect: RectF, val layout: StaticLayout, val pad: Float) {
        var hidden = false
    }

    private val dp = context.resources.displayMetrics.density
    private val sp = context.resources.displayMetrics.scaledDensity
    private val boxes: List<Box>
    private val loc = IntArray(2)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(246, 0xFF, 0xFD, 0xF7) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
        color = Color.argb(60, 0x2A, 0x3A, 0x55)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 0x18, 0x1D, 0x27) }
    private val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 0xE6, 0xEC, 0xF5)
        textSize = 12f * sp
    }
    private val pillLabel: String = "${result.providerLabel}・${"%.1f".format(result.elapsedMs / 1000f)}s　點空白關閉・按住看原文・滑動翻頁"

    // 觸控狀態
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moving = false
    private var peeking = false
    private var cancelled = false
    private val path = ArrayList<PointF>()
    private val peekRunnable = Runnable {
        peeking = true
        invalidate()
    }

    init {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1C, 0x1F, 0x26) }
        boxes = result.items.map { buildBox(it, textPaint, maxTextSp) }
    }

    private fun buildBox(item: OverlayItem, basePaint: TextPaint, maxSp: Int): Box {
        val pad = 4f * dp
        val r = RectF(item.rect)
        r.inset(-3f * dp, -3f * dp)

        // 直排或極窄的區塊，橫向放寬到至少 72dp，中文橫排才讀得下去
        val minW = 72f * dp
        if (r.width() < minW) {
            val cx = r.centerX()
            r.left = cx - minW / 2
            r.right = cx + minW / 2
        }
        clampX(r)

        val availW = (r.width() - pad * 2).toInt().coerceAtLeast(1)
        val availH = r.height() - pad * 2
        val paint = TextPaint(basePaint)
        var layout: StaticLayout? = null
        var size = maxSp
        while (size >= MIN_SP) {
            paint.textSize = size * sp
            val l = makeLayout(item.zh, paint, availW)
            layout = l
            if (l.height <= availH && fitsWidth(l, availW)) break
            size--
        }
        val finalLayout = layout ?: makeLayout(item.zh, paint, availW)

        // 最小字級仍放不下：往下延伸框的高度（超出螢幕則往上推）
        val needH = finalLayout.height + pad * 2
        if (needH > r.height()) {
            r.bottom = r.top + needH
            if (r.bottom > screenH) {
                r.offset(0f, screenH - r.bottom)
                if (r.top < 0) r.top = 0f
            }
        }
        return Box(r, finalLayout, pad)
    }

    private fun clampX(r: RectF) {
        if (r.left < 0) r.offset(-r.left, 0f)
        if (r.right > screenW) r.offset(screenW - r.right, 0f)
        if (r.left < 0) r.left = 0f
    }

    private fun makeLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(40)
            .build()

    private fun fitsWidth(l: StaticLayout, w: Int): Boolean {
        for (i in 0 until l.lineCount) if (l.getLineWidth(i) > w + 1) return false
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        getLocationOnScreen(loc)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        // 框的座標是「螢幕座標」，扣掉本視窗在螢幕上的偏移
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        if (!peeking) {
            val corner = 6f * dp
            for (b in boxes) {
                if (b.hidden) continue
                canvas.drawRoundRect(b.rect, corner, corner, bgPaint)
                canvas.drawRoundRect(b.rect, corner, corner, borderPaint)
                canvas.save()
                val ty = b.rect.top + (b.rect.height() - b.layout.height) / 2f
                canvas.translate(b.rect.left + b.pad, ty)
                b.layout.draw(canvas)
                canvas.restore()
            }
        }
        canvas.restore()
        drawPill(canvas)
    }

    private fun drawPill(canvas: Canvas) {
        val label = TextUtils.ellipsize(pillLabel, pillText, width - 48f * dp, TextUtils.TruncateAt.END).toString()
        val tw = pillText.measureText(label)
        val h = 28f * dp
        val bottomInset = rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
        val cx = width / 2f
        val bottom = height - bottomInset - 16f * dp
        val rect = RectF(cx - tw / 2 - 12f * dp, bottom - h, cx + tw / 2 + 12f * dp, bottom)
        canvas.drawRoundRect(rect, h / 2, h / 2, pillPaint)
        val baseline = rect.centerY() - (pillText.descent() + pillText.ascent()) / 2
        canvas.drawText(label, rect.left + 12f * dp, baseline, pillText)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                downTime = e.eventTime
                moving = false
                peeking = false
                cancelled = false
                path.clear()
                path += PointF(e.rawX, e.rawY)
                postDelayed(peekRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 雙指（縮放）：直接關閉，讓使用者自行操作底下的 App
                cancelled = true
                removeCallbacks(peekRunnable)
                listener.onDismiss()
            }
            MotionEvent.ACTION_MOVE -> {
                if (cancelled) return true
                if (!moving && !peeking && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                    moving = true
                    removeCallbacks(peekRunnable)
                }
                if (moving) {
                    val last = path.last()
                    if (hypot(e.rawX - last.x, e.rawY - last.y) > 4f * dp) path += PointF(e.rawX, e.rawY)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(peekRunnable)
                if (cancelled) return true
                when {
                    peeking -> {
                        peeking = false
                        invalidate()
                    }
                    moving -> {
                        path += PointF(e.rawX, e.rawY)
                        listener.onSwipe(ArrayList(path), max(60L, min(600L, e.eventTime - downTime)))
                    }
                    else -> handleTap(e.rawX, e.rawY)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(peekRunnable)
                peeking = false
                invalidate()
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val hit = boxes.lastOrNull { it.rect.contains(x, y) }
        if (hit != null) {
            hit.hidden = !hit.hidden
            invalidate()
        } else {
            listener.onDismiss()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(peekRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MIN_SP = 9
    }
}
