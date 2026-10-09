package com.hoonsor.screentranslate

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 疊加層要畫的一個翻譯框 */
data class OverlayItem(val rect: Rect, val zh: String, val source: String)

data class TranslationResult(
    val items: List<OverlayItem>,
    val providerLabel: String,
    val elapsedMs: Long,
    val note: String? = null,
)

/**
 * 翻譯流程：OCR → （快速模式）文字 LLM 或（漫畫模式）圖片 + OCR 混合 → 對回座標。
 * 主要引擎失敗會自動改用備援，漫畫模式失敗會自動退回快速模式。
 */
class Translator(private val prefs: Prefs) {

    private val ocr = OcrEngine()
    private val cache = LruCache<String, Map<Int, String>>(60)

    suspend fun translateScreen(screen: Bitmap, statusBarPx: Int): TranslationResult {
        val t0 = System.currentTimeMillis()
        val blocks = ocr.recognize(screen, prefs.ocrScript, statusBarPx)
        val mode = prefs.mode

        if (mode == TranslateMode.COMIC) {
            val visionCfg = prefs.provider(prefs.vision)
            if (visionCfg.isUsable) {
                try {
                    val items = translateComic(screen, blocks, visionCfg)
                    return TranslationResult(items, "${visionCfg.id.label}・漫畫", System.currentTimeMillis() - t0)
                } catch (e: Exception) {
                    if (blocks.isEmpty()) throw e
                    val r = translateFast(blocks, t0)
                    return r.copy(note = "漫畫模式失敗，已改用快速模式（${e.message?.take(60)}）")
                }
            }
        }

        if (blocks.isEmpty()) {
            return TranslationResult(emptyList(), "OCR", System.currentTimeMillis() - t0, note = "畫面上沒有偵測到需要翻譯的文字")
        }
        return translateFast(blocks, t0)
    }

    // ── 快速模式：純文字翻譯 ─────────────────────────────

    private suspend fun translateFast(blocks: List<OcrBlock>, t0: Long): TranslationResult {
        val key = "F|" + blocks.joinToString("\u0001") { it.text }
        cache.get(key)?.let { hit ->
            return TranslationResult(toItems(blocks, hit), "快取", System.currentTimeMillis() - t0)
        }

        val chain = buildList {
            add(prefs.provider(prefs.primary))
            prefs.fallback?.takeIf { it != prefs.primary }?.let { add(prefs.provider(it)) }
        }.filter { it.isUsable }
        if (chain.isEmpty()) throw LlmException("尚未設定任何翻譯引擎的 API key，請開啟 App 設定")

        var lastError: Exception? = null
        for ((index, cfg) in chain.withIndex()) {
            try {
                val map = translateTexts(cfg, blocks)
                cache.put(key, map)
                val note = if (index > 0) "主要引擎失敗，已改用 ${cfg.id.label}（${lastError?.message?.take(60)}）" else null
                return TranslationResult(toItems(blocks, map), cfg.id.label, System.currentTimeMillis() - t0, note)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: LlmException("翻譯失敗")
    }

    /** 公開給設定頁的「測試」按鈕使用 */
    suspend fun testProvider(cfg: ProviderConfig): String {
        val sample = listOf(
            OcrBlock(1, "WAIT! YOU CAN'T JUST LEAVE ME HERE!", Rect()),
            OcrBlock(2, "Watch me.", Rect()),
        )
        val map = translateTexts(cfg, sample)
        return sample.joinToString("\n") { "${it.text} → ${map[it.id] ?: "（缺）"}" }
    }

    private suspend fun translateTexts(cfg: ProviderConfig, blocks: List<OcrBlock>): Map<Int, String> {
        val input = JSONArray()
        blocks.forEach { input.put(JSONObject().put("id", it.id).put("text", it.text)) }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", TEXT_SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", input.toString()))
        val reply = LlmClient.chat(cfg, messages, jsonMode = true)
        val map = parseIdMap(reply)
        if (map.isEmpty()) throw LlmException("${cfg.id.label} 回傳格式無法解析：${reply.take(100)}")
        return map
    }

    private fun toItems(blocks: List<OcrBlock>, map: Map<Int, String>): List<OverlayItem> =
        blocks.mapNotNull { b ->
            val zh = map[b.id]?.trim().orEmpty()
            if (zh.isEmpty()) null else OverlayItem(b.rect, zh, b.text)
        }

    // ── 漫畫模式：截圖 + OCR 區塊一起給視覺模型 ───────────────

    private suspend fun translateComic(screen: Bitmap, blocks: List<OcrBlock>, cfg: ProviderConfig): List<OverlayItem> {
        val maxSide = 1600f
        val scale = minOf(1f, maxSide / max(screen.width, screen.height))
        val w = (screen.width * scale).roundToInt()
        val h = (screen.height * scale).roundToInt()
        val small = if (scale < 1f) Bitmap.createScaledBitmap(screen, w, h, true) else screen
        val jpeg = ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        }
        if (small !== screen) small.recycle()
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)

        // OCR 區塊座標換成 0~1000 正規化，方便模型對照圖片
        val ocrJson = JSONArray()
        blocks.forEach { b ->
            ocrJson.put(
                JSONObject()
                    .put("id", b.id)
                    .put("text", b.text)
                    .put("box", normBox(b.rect, screen.width, screen.height))
            )
        }
        val userText = "OCR 結果（可能有錯字或漏字）：\n$ocrJson"
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", userText))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", COMIC_SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", content))

        val reply = LlmClient.chat(cfg, messages, jsonMode = false)
        val obj = extractJsonObject(reply) ?: throw LlmException("${cfg.id.label} 回傳格式無法解析：${reply.take(100)}")

        val items = ArrayList<OverlayItem>()
        val byId = blocks.associateBy { it.id }
        obj.optJSONArray("t")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val b = byId[o.optInt("id", -1)] ?: continue
                val zh = o.optString("zh").trim()
                if (zh.isNotEmpty() && zh != "-") items += OverlayItem(b.rect, zh, b.text)
            }
        }
        obj.optJSONArray("extra")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val box = o.optJSONArray("box") ?: continue
                if (box.length() != 4) continue
                val zh = o.optString("zh").trim()
                if (zh.isEmpty()) continue
                val r = denormBox(box, screen.width, screen.height) ?: continue
                items += OverlayItem(r, zh, o.optString("src"))
            }
        }
        if (items.isEmpty() && blocks.isNotEmpty()) throw LlmException("${cfg.id.label} 沒有回傳任何譯文")
        return items
    }

    private fun normBox(r: Rect, w: Int, h: Int) = JSONArray()
        .put(r.left * 1000 / w).put(r.top * 1000 / h).put(r.right * 1000 / w).put(r.bottom * 1000 / h)

    private fun denormBox(a: JSONArray, w: Int, h: Int): Rect? {
        val v = IntArray(4)
        for (i in 0 until 4) {
            val d = a.optDouble(i, Double.NaN)
            if (d.isNaN()) return null
            v[i] = d.roundToInt()
        }
        val l = minOf(v[0], v[2]).coerceIn(0, 1000) * w / 1000
        val t = minOf(v[1], v[3]).coerceIn(0, 1000) * h / 1000
        val r = maxOf(v[0], v[2]).coerceIn(0, 1000) * w / 1000
        val b = maxOf(v[1], v[3]).coerceIn(0, 1000) * h / 1000
        return if (r - l < 8 || b - t < 8) null else Rect(l, t, r, b)
    }

    // ── JSON 解析（容忍模型多講話、包 ```json 區塊等情況） ─────────

    private fun parseIdMap(reply: String): Map<Int, String> {
        val result = HashMap<Int, String>()
        val arr: JSONArray? = extractJsonObject(reply)?.let { o ->
            o.optJSONArray("t") ?: o.optJSONArray("translations") ?: o.optJSONArray("items")
        } ?: extractJsonArray(reply)
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt("id", -1)
                val zh = o.optString("zh").ifBlank { o.optString("text") }
                if (id >= 0 && zh.isNotBlank()) result[id] = zh
            }
        }
        return result
    }

    private fun extractJsonObject(s: String): JSONObject? {
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(s.substring(start, end + 1)) }.getOrNull()
    }

    private fun extractJsonArray(s: String): JSONArray? {
        val start = s.indexOf('[')
        val end = s.lastIndexOf(']')
        if (start < 0 || end <= start) return null
        return runCatching { JSONArray(s.substring(start, end + 1)) }.getOrNull()
    }

    fun close() = ocr.close()

    companion object {
        val TEXT_SYSTEM_PROMPT = """
你是專業的漫畫與螢幕文字翻譯員。使用者會給你一個 JSON 陣列，每個項目是從手機畫面 OCR 辨識出的一段文字（id、text）。
請把每個項目翻譯成自然流暢、符合台灣用語的繁體中文。

規則：
1. 項目已依閱讀順序排列，同一頁的文字通常屬於同一段劇情或同一篇內容。請先通讀全部項目、理解上下文（誰在說話、語氣、人物關係）再翻譯，但每個 id 必須各自輸出對應的譯文，不可合併、拆分或省略。
2. OCR 可能有錯字、斷行、大小寫錯亂或把 I 認成 l、0 認成 O，請依上下文推斷正確原文後再翻譯。
3. 保留說話者的語氣與情緒（驚訝、生氣、撒嬌、粗魯等），用口語化的中文表達；不要逐字直譯。
4. 擬聲詞、狀聲詞翻成對應的中文擬聲詞；人名、地名用常見中文譯名，沒有常見譯名就保留原文。
5. 已經是中文、純數字、網址、程式碼或看不懂的雜訊，zh 直接輸出原文。
6. 只輸出 JSON，格式為 {"t":[{"id":1,"zh":"譯文"}]}，不要有任何其他文字或說明。
""".trim()

        val COMIC_SYSTEM_PROMPT = """
你是專業的漫畫翻譯員。你會收到一張手機截圖，以及 OCR 從截圖中辨識出的文字區塊（id、text、box；box 為 [左,上,右,下]，座標已正規化到 0~1000）。
OCR 對漫畫手寫字體常有錯誤，請以圖片為準。

任務：
1. 對照圖片，修正每個 OCR 區塊的原文，再依劇情上下文（說話者、語氣、畫格順序）翻譯成自然、口語化的台灣繁體中文。
2. 若某個 OCR 區塊其實不是對話或旁白（例如畫面雜訊、介面按鈕、浮水印），zh 輸出 "-"。
3. 若圖片中有 OCR 漏掉的對話框、旁白或重要的手寫字，請放進 extra，並給出該文字在圖片中的 box（[左,上,右,下]，0~1000 正規化）。
4. 擬聲詞翻成中文擬聲詞；人名用常見譯名或保留原文。
5. 只輸出 JSON，不要任何說明文字：
{"t":[{"id":1,"zh":"譯文"}],"extra":[{"box":[100,200,300,260],"src":"原文","zh":"譯文"}]}
""".trim()
    }
}
