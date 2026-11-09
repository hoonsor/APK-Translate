package com.hoonsor.screentranslate

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.service.quicksettings.TileService
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.hoonsor.screentranslate.diag.DiagLog
import com.hoonsor.screentranslate.diag.RunTrace
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 核心服務：承載浮動小圓點與翻譯疊加層，並負責截圖。
 * 以無障礙服務實作的好處：截圖不必每次確認、不需要「顯示在其他應用程式上層」權限、
 * 由系統綁定，比一般前景服務更不容易被 ColorOS 清除。
 */
class TranslatorAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TranslatorAccessibilityService? = null
            private set

        fun refreshTile(context: android.content.Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, TranslateTileService::class.java))
            }
        }
    }

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private lateinit var translator: Translator
    private var bubble: BubbleController? = null
    private var overlay: ResultOverlayView? = null
    private var job: Job? = null

    /** 漫畫模式「先快後準」：快速譯文已顯示、正在等視覺模型精修 */
    private var awaitingUpgrade = false

    val isBubbleShown: Boolean get() = bubble?.isShown == true

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        prefs = Prefs(this)
        DiagLog.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        translator = Translator(prefs)
        bubble = BubbleController(this, wm, prefs, onTap = this::onBubbleTap, onLongPress = this::onBubbleLongPress)
        instance = this
        if (prefs.bubbleEnabled) {
            runCatching { bubble?.show() }
            prewarm()
        }
        refreshTile(this)
    }

    /** 先跟要用到的引擎建好連線（第一次翻譯少約 1 秒）；回傳有預熱的主機 */
    private fun prewarm(): List<String> {
        if (!::prefs.isInitialized) return emptyList()
        val cfgs = buildList {
            add(prefs.provider(prefs.primary))
            prefs.fallback?.let { add(prefs.provider(it)) }
            if (prefs.mode == TranslateMode.COMIC) add(prefs.provider(prefs.vision))
        }.filter { it.isUsable }
        return runCatching { LlmClient.prewarm(cfgs) }.getOrDefault(emptyList())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 切換到其他 App 時自動收起翻譯結果
        if (overlay == null || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (System.currentTimeMillis() - overlayShownAt < 800) return
        dismissOverlay()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance == null && bubble == null) return
        instance = null
        job?.cancel()
        dismissOverlay()
        bubble?.hide()
        bubble = null
        if (::translator.isInitialized) translator.close()
        scope.cancel()
        refreshTile(this)
    }

    // ── 對外操作（設定頁、Tile 呼叫） ─────────────────────

    fun setBubbleEnabled(enabled: Boolean) {
        prefs.bubbleEnabled = enabled
        if (enabled) {
            runCatching { bubble?.show() }.onFailure { toast("無法顯示浮動按鈕：${it.message}") }
            prewarm()
        } else {
            job?.cancel()
            dismissOverlay()
            bubble?.hide()
        }
        refreshTile(this)
    }

    fun refreshBubbleStyle() {
        bubble?.applyStyle()
    }

    // ── 小圓點事件 ─────────────────────────────────

    private fun onBubbleTap() {
        if (overlay != null) {
            dismissOverlay()
            return
        }
        if (job?.isActive == true) {
            job?.cancel()
            bubble?.setBusy(false)
            toast("已取消翻譯")
            return
        }
        job = scope.launch { runTranslate("tap") }
    }

    private fun onBubbleLongPress() {
        prefs.mode = if (prefs.mode == TranslateMode.FAST) TranslateMode.COMIC else TranslateMode.FAST
        bubble?.applyStyle()
        prewarm()
        val hint = if (prefs.mode == TranslateMode.COMIC) "（圖片 + OCR，較準、較慢）" else "（純文字，最快）"
        toast("已切換為${prefs.mode.label}$hint")
    }

    // ── 翻譯流程 ──────────────────────────────────

    /** @param source tap＝點小圓點、swipe＝翻頁後自動翻譯（寫進診斷紀錄） */
    private suspend fun runTranslate(source: String) {
        val b = bubble ?: return
        val trace = RunTrace(source)
        trace.put("network", DiagLog.networkType())
        // 截圖＋OCR 的這段時間順便建連線（連線還活著就不會送）
        prewarm().takeIf { it.isNotEmpty() }?.let { trace.put("prewarm", JSONArray(it)) }
        val shot: Bitmap = try {
            b.setHiddenForCapture(true)
            val tCap = System.nanoTime()
            delay(110) // 等一個畫面週期，確保小圓點已從畫面消失
            captureWithRetry(trace).also { trace.stage("capture_ms", (System.nanoTime() - tCap) / 1_000_000) }
        } catch (e: CancellationException) {
            DiagLog.record("translate", trace.finish("cancelled"))
            throw e
        } catch (e: Exception) {
            b.flashError()
            toast(e.message ?: "截圖失敗")
            DiagLog.record("translate", trace.finish("capture_failed", e.message))
            return
        } finally {
            b.setHiddenForCapture(false)
        }

        b.setBusy(true)
        val topInset = statusBarHeight()
        var earlyView: ResultOverlayView? = null
        try {
            val res = withContext(Dispatchers.Default) {
                translator.translateScreen(shot, topInset, trace) { early ->
                    // 漫畫模式「先快後準」：快速譯文先顯示，小圓點繼續轉圈代表精修中
                    withContext(Dispatchers.Main) {
                        val t = trace.elapsedMs()
                        showOverlay(early.copy(elapsedMs = t, breakdown = trace.shortSummary()))
                        earlyView = overlay
                        if (earlyView != null) {
                            awaitingUpgrade = true
                            trace.stage("early_ms", t)
                            early.note?.let { toast(it) }
                        }
                        earlyView != null
                    }
                }
            }
            awaitingUpgrade = false
            trace.put("items", res.items.size)
            res.note?.let { trace.put("note", it) }
            if (DiagLog.includeText) {
                trace.put("texts", JSONArray().apply {
                    res.items.forEach { put(JSONObject().put("src", it.source).put("zh", it.zh)) }
                })
            }
            when {
                res.keepsEarly -> {
                    // 視覺模型逾時或失敗，畫面上的快速譯文就是最終結果
                    if (overlay != null && overlay === earlyView) res.note?.let { toast(it) }
                    DiagLog.record("translate", trace.finish("ok"))
                }
                res.items.isEmpty() -> {
                    if (earlyView == null) toast(res.note ?: "沒有偵測到需要翻譯的文字")
                    DiagLog.record("translate", trace.finish(if (earlyView != null) "ok" else "empty"))
                }
                earlyView != null && overlay !== earlyView -> {
                    // 使用者已關閉先顯示的譯文（理論上會先取消，這裡保險）
                    trace.put("comic_upgrade_dropped", true)
                    DiagLog.record("translate", trace.finish("ok"))
                }
                else -> {
                    val t = trace.elapsedMs()
                    showOverlay(res.copy(elapsedMs = t, breakdown = trace.shortSummary()))
                    trace.stage("show_ms", trace.elapsedMs() - t)
                    res.note?.let { toast(it) }
                    DiagLog.record("translate", trace.finish("ok"))
                }
            }
        } catch (e: CancellationException) {
            awaitingUpgrade = false
            if (earlyView != null) {
                // 快速譯文已顯示，使用者關閉／翻頁時取消精修，這次仍算成功
                trace.put("comic_upgrade_cancelled", true)
                DiagLog.record("translate", trace.finish("ok"))
            } else {
                DiagLog.record("translate", trace.finish("cancelled"))
            }
            throw e
        } catch (e: Exception) {
            awaitingUpgrade = false
            b.flashError()
            toast("翻譯失敗：${e.message ?: e.javaClass.simpleName}", long = true)
            DiagLog.record("translate", trace.finish("error", e.message ?: e.javaClass.simpleName))
        } finally {
            b.setBusy(false)
            shot.recycle()
        }
    }

    private class ScreenshotException(msg: String, val code: Int) : IOException(msg)

    private suspend fun captureWithRetry(trace: RunTrace? = null): Bitmap {
        return try {
            capture()
        } catch (e: ScreenshotException) {
            // 系統限制截圖頻率（約每秒一次），稍等後重試一次
            if (e.code == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                trace?.put("capture_retry", true)
                delay(1000)
                capture()
            } else {
                throw e
            }
        }
    }

    private suspend fun capture(): Bitmap = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val hb = result.hardwareBuffer
                try {
                    val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                    // ML Kit 與 JPEG 壓縮需要可讀取的軟體 Bitmap
                    val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    if (sw == null) cont.resumeWithException(ScreenshotException("截圖轉換失敗", -1))
                    else cont.resume(sw)
                } catch (e: Exception) {
                    cont.resumeWithException(ScreenshotException("截圖轉換失敗：${e.message}", -1))
                } finally {
                    hb.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                val msg = when (errorCode) {
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "這個畫面禁止截圖（App 設定了安全保護），無法翻譯"
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "截圖太頻繁，請稍候再試"
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "無障礙服務沒有截圖權限，請重新開啟服務"
                    else -> "截圖失敗（代碼 $errorCode）"
                }
                cont.resumeWithException(ScreenshotException(msg, errorCode))
            }
        })
    }

    private fun statusBarHeight(): Int =
        wm.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top

    // ── 疊加層 ───────────────────────────────────

    private var overlayShownAt = 0L

    private fun showOverlay(res: TranslationResult) {
        removeOverlay()
        val bounds = wm.currentWindowMetrics.bounds
        val view = ResultOverlayView(
            this, res, bounds.width(), bounds.height(), prefs.maxTextSp,
            object : ResultOverlayView.Listener {
                override fun onDismiss() = dismissOverlay()
                override fun onSwipe(points: List<PointF>, durationMs: Long) = replaySwipe(points, durationMs)
            },
        )
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        try {
            wm.addView(view, lp)
            overlay = view
            overlayShownAt = System.currentTimeMillis()
            bubble?.bringToFront()
        } catch (e: Exception) {
            toast("無法顯示翻譯結果：${e.message}")
        }
    }

    /** 使用者關閉譯文（點空白、點小圓點、切換 App、翻頁）：一併取消還在等的漫畫精修 */
    private fun dismissOverlay() {
        if (awaitingUpgrade) {
            awaitingUpgrade = false
            job?.cancel()
            bubble?.setBusy(false)
        }
        removeOverlay()
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        runCatching { wm.removeView(v) }
    }

    /** 關閉疊加層後，把使用者的滑動手勢重播給底下的 App（例如漫畫翻頁），並可自動翻譯下一頁 */
    private fun replaySwipe(points: List<PointF>, durationMs: Long) {
        dismissOverlay()
        if (points.size < 2) return
        scope.launch {
            delay(60)
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            dispatchGesture(gesture, null, null)
            if (prefs.autoAfterSwipe && job?.isActive != true) {
                delay(durationMs + prefs.swipeSettleMs)
                if (overlay == null && bubble?.isShown == true) job = scope.launch { runTranslate("swipe") }
            }
        }
    }

    private fun toast(msg: String, long: Boolean = false) {
        Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
