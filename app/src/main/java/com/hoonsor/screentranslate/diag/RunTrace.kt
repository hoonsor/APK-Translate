package com.hoonsor.screentranslate.diag

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 一次翻譯（或一次檢測）的計時與呼叫紀錄。
 * 結束時由 [finish] 輸出成一筆 JSON 事件寫進 [DiagLog]，並可用 [shortSummary] 顯示在翻譯結果的膠囊上。
 * 不依賴 Android API，方便 JVM 單元測試。
 */
class RunTrace(source: String) {
    private val startNs = System.nanoTime()
    val data: JSONObject = JSONObject().put("source", source)
    private val stages = JSONObject()
    private val calls = JSONArray()

    fun elapsedMs(): Long = (System.nanoTime() - startNs) / 1_000_000

    fun stage(name: String, ms: Long) {
        synchronized(stages) { stages.put(name, ms) }
    }

    fun stageMs(name: String): Long? = synchronized(stages) { if (stages.has(name)) stages.optLong(name) else null }

    fun put(key: String, value: Any?) {
        synchronized(data) { data.put(key, value ?: JSONObject.NULL) }
    }

    fun addCall(call: JSONObject) {
        synchronized(calls) { calls.put(call) }
    }

    fun callList(): List<JSONObject> = synchronized(calls) {
        (0 until calls.length()).mapNotNull { calls.optJSONObject(it) }
    }

    fun finish(outcome: String, error: String? = null): JSONObject {
        put("outcome", outcome)
        if (error != null) put("error", error.take(300))
        put("total_ms", elapsedMs())
        put("stages", synchronized(stages) { JSONObject(stages.toString()) })
        put("calls", synchronized(calls) { JSONArray(calls.toString()) })
        return data
    }

    /** 例：「截圖 0.2・OCR 0.3・Groq ✗4.1・MiniMax 7.8」 */
    fun shortSummary(): String {
        val parts = ArrayList<String>()
        stageMs("capture_ms")?.let { parts += "截圖 ${sec(it)}" }
        stageMs("ocr_ms")?.let { parts += "OCR ${sec(it)}" }
        if (data.optBoolean("cache_hit")) parts += "快取"
        callList().forEach { c ->
            val mark = if (c.optBoolean("ok")) "" else "✗"
            parts += "${c.optString("provider")} $mark${sec(c.optLong("total_ms"))}"
        }
        return parts.joinToString("・")
    }

    private fun sec(ms: Long) = String.format(Locale.US, "%.1f", ms / 1000f)
}

/** 計時一段程式（inline，所以區塊內可以呼叫 suspend 函式） */
inline fun <T> RunTrace?.timed(stage: String, block: () -> T): T {
    if (this == null) return block()
    val t = System.nanoTime()
    try {
        return block()
    } finally {
        stage(stage, (System.nanoTime() - t) / 1_000_000)
    }
}
