package com.hoonsor.screentranslate.diag

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Base64
import com.hoonsor.screentranslate.LlmClient
import com.hoonsor.screentranslate.Prefs
import com.hoonsor.screentranslate.ProviderId
import com.hoonsor.screentranslate.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 「檢測所有引擎」：對每個已填 API key 的引擎
 * ① 取得模型列表 ② 連續 3 次文字翻譯（第 1 次含建立連線，看冷啟動與穩定耗時）③ 圖片輸入測試。
 * 每個引擎寫一筆 `probe` 事件。
 */
object DiagProbe {

    private const val TEXT_RUNS = 3
    private const val VISION_WORD = "HELLO 42"

    suspend fun runAll(prefs: Prefs, onProgress: (String) -> Unit): String {
        val targets = ProviderId.entries.map { prefs.provider(it) }.filter { it.isUsable }
        if (targets.isEmpty()) return "沒有任何已設定 API key 的引擎"
        val translator = Translator(prefs)
        val lines = ArrayList<String>()
        try {
            for (cfg in targets) {
                val label = cfg.id.label
                val trace = RunTrace("probe")
                val rec = trace.data
                    .put("provider", label)
                    .put("model", cfg.model)
                    .put("host", runCatching { java.net.URI(cfg.baseUrl).host }.getOrNull() ?: cfg.baseUrl)
                    .put("network", DiagLog.networkType())

                onProgress("$label：取得模型列表…")
                try {
                    val t = System.nanoTime()
                    val models = LlmClient.listModels(cfg)
                    rec.put("models_ms", (System.nanoTime() - t) / 1_000_000)
                    rec.put("models", JSONArray(models))
                    rec.put("model_listed", cfg.model in models)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    rec.put("models_error", e.message?.take(200))
                }

                val runs = JSONArray()
                for (i in 1..TEXT_RUNS) {
                    onProgress("$label：文字翻譯測試 $i/$TEXT_RUNS…")
                    val t = System.nanoTime()
                    val r = JSONObject()
                    try {
                        val out = translator.testProviderTraced(cfg, trace)
                        r.put("ok", true)
                        if (DiagLog.includeText) r.put("reply", out.take(200))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        r.put("ok", false).put("error", e.message?.take(200))
                    }
                    r.put("ms", (System.nanoTime() - t) / 1_000_000)
                    runs.put(r)
                }
                rec.put("text_runs", runs)

                onProgress("$label：圖片輸入測試…")
                rec.put("vision", visionTest(cfg, trace))

                DiagLog.record("probe", trace.finish("done"))
                val okRuns = (0 until runs.length()).map { runs.getJSONObject(it) }.filter { it.optBoolean("ok") }
                val avg = if (okRuns.isEmpty()) "全部失敗" else "平均 ${okRuns.map { it.optLong("ms") }.average().toLong()} ms"
                val vis = rec.optJSONObject("vision")?.let { if (it.optBoolean("ok")) "可讀圖" else "不支援圖片" } ?: ""
                lines += "$label：文字 ${okRuns.size}/$TEXT_RUNS 成功，$avg；$vis"
            }
        } finally {
            translator.close()
        }
        return lines.joinToString("\n")
    }

    private suspend fun visionTest(cfg: com.hoonsor.screentranslate.ProviderConfig, trace: RunTrace): JSONObject {
        val r = JSONObject()
        val t = System.nanoTime()
        try {
            val dataUrl = withContext(Dispatchers.Default) { testImageDataUrl() }
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", "Read the text in this image. Reply with the text only."))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
            val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
            val reply = LlmClient.chat(cfg, messages, jsonMode = false, purpose = "probe-vision", trace = trace)
            val ok = reply.uppercase().replace(" ", "").contains(VISION_WORD.replace(" ", ""))
            r.put("ok", ok).put("reply", reply.take(60))
            if (!ok) r.put("error", "回覆沒有讀出圖片文字")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            r.put("ok", false).put("error", e.message?.take(200))
        }
        r.put("ms", (System.nanoTime() - t) / 1_000_000)
        return r
    }

    /** 白底黑字「HELLO 42」的小圖（不含任何畫面內容） */
    private fun testImageDataUrl(): String {
        val bmp = Bitmap.createBitmap(480, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(VISION_WORD, 40f, 110f, p)
        val bytes = ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        bmp.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
