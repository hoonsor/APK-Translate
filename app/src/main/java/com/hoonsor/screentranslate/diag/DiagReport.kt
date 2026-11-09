package com.hoonsor.screentranslate.diag

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil

/** 遮蔽紀錄中的 API key：已知的 key 原文＋常見 key 格式 */
object Redactor {
    private val PATTERNS = listOf(
        Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"),
        Regex("gsk_[A-Za-z0-9]{10,}"),
        Regex("\\bsk-[A-Za-z0-9_\\-]{10,}"),
        Regex("AIza[0-9A-Za-z_\\-]{20,}"),
    )

    fun redact(text: String, secrets: Collection<String>): String {
        var out = text
        secrets.filter { it.length >= 8 }.forEach { out = out.replace(it, "***") }
        PATTERNS.forEach { out = it.replace(out, "***") }
        return out
    }
}

/**
 * 把診斷事件整理成給 AI 讀的文字報告：統計在前、原始 JSONL 在後。
 * 純 Kotlin＋org.json，不依賴 Android，可在 JVM 單元測試。
 */
object DiagReport {

    fun build(header: String, events: List<JSONObject>, includeRaw: Boolean = true): String = buildString {
        appendLine("# ScreenTranslate 診斷報告")
        appendLine()
        appendLine(header.trimEnd())
        appendLine()
        appendLine(translateStats(events))
        appendLine(callStats(events))
        appendLine(probeSection(events))
        appendLine(recentErrors(events))
        if (includeRaw) {
            appendLine("## 原始事件（JSONL，舊→新，共 ${events.size} 筆）")
            appendLine("```jsonl")
            events.forEach { appendLine(it.toString()) }
            appendLine("```")
        }
    }

    // ── 翻譯統計 ──────────────────────────────────

    fun translateStats(events: List<JSONObject>): String = buildString {
        val runs = events.filter { it.optString("type") == "translate" }
        appendLine("## 翻譯統計（共 ${runs.size} 次）")
        if (runs.isEmpty()) {
            appendLine("（尚無翻譯紀錄）")
            return@buildString
        }
        val byOutcome = runs.groupingBy { it.optString("outcome", "?") }.eachCount()
        appendLine("- 結果：" + byOutcome.entries.joinToString("、") { "${it.key} ${it.value}" })
        val byMode = runs.groupingBy { it.optString("mode", "?") }.eachCount()
        appendLine("- 模式：" + byMode.entries.joinToString("、") { "${it.key} ${it.value}" })
        val ok = runs.filter { it.optString("outcome") == "ok" }
        appendLine("- 成功的總耗時（點擊→顯示）：${dist(ok.map { it.optLong("total_ms") })}")
        for (stage in listOf("capture_ms", "ocr_ms", "jpeg_ms", "llm_ms")) {
            val v = ok.mapNotNull { r -> r.optJSONObject("stages")?.takeIf { it.has(stage) }?.optLong(stage) }
            if (v.isNotEmpty()) appendLine("- 階段 $stage：${dist(v)}")
        }
        val fb = runs.count { it.optBoolean("fallback_used") }
        if (fb > 0) appendLine("- 改用備援：$fb 次")
        val cache = runs.count { it.optBoolean("cache_hit") }
        if (cache > 0) appendLine("- 快取命中：$cache 次")
        val retry = runs.count { it.optBoolean("capture_retry") }
        if (retry > 0) appendLine("- 截圖太頻繁重試：$retry 次")
    }

    // ── 引擎呼叫統計 ─────────────────────────────────

    fun allCalls(events: List<JSONObject>): List<JSONObject> = events.flatMap { e ->
        when (e.optString("type")) {
            "llm" -> listOf(e)
            else -> e.optJSONArray("calls").objects()
        }
    }

    fun callStats(events: List<JSONObject>): String = buildString {
        val calls = allCalls(events)
        appendLine("## 引擎呼叫統計（共 ${calls.size} 次，含測試與檢測）")
        if (calls.isEmpty()) {
            appendLine("（尚無呼叫紀錄）")
            return@buildString
        }
        calls.groupBy { "${it.optString("provider")}｜${it.optString("model")}" }.forEach { (k, list) ->
            val ok = list.filter { it.optBoolean("ok") }
            appendLine("### $k")
            appendLine("- 次數 ${list.size}，成功 ${ok.size}，失敗 ${list.size - ok.size}")
            val fails = list.filterNot { it.optBoolean("ok") }
                .groupingBy { f -> f.optInt("http", 0).let { if (it > 0) "HTTP $it" else "連線／解析錯誤" } }
                .eachCount()
            if (fails.isNotEmpty()) appendLine("- 失敗原因：" + fails.entries.joinToString("、") { "${it.key}×${it.value}" })
            appendLine("- 總耗時：${dist(ok.map { it.optLong("total_ms") })}")
            val server = ok.mapNotNull { c -> c.optLong("server_ms", -1).takeIf { it >= 0 } }
            if (server.isNotEmpty()) appendLine("- 伺服器處理（送完請求→收到回應標頭）：${dist(server)}")
            val upload = ok.mapNotNull { c -> c.optLong("upload_ms", -1).takeIf { it >= 0 } }
            if (upload.isNotEmpty()) appendLine("- 上傳：${dist(upload)}")
            val connect = list.mapNotNull { c -> c.optLong("connect_ms", -1).takeIf { it >= 0 } }
            if (connect.isNotEmpty()) appendLine("- 新建連線（含 TLS）：${dist(connect)}；重用連線 ${list.count { it.optBoolean("reused") }} 次")
            val comp = ok.mapNotNull { it.optJSONObject("usage")?.optLong("completion_tokens", -1)?.takeIf { v -> v >= 0 } }
            if (comp.isNotEmpty()) appendLine("- 輸出 tokens：${dist(comp, unit = "")}")
            val reason = ok.mapNotNull { reasoningTokens(it) }
            if (reason.isNotEmpty()) appendLine("- 推理 tokens：${dist(reason, unit = "")}")
            val reasonChars = ok.mapNotNull { c -> c.optLong("reasoning_chars", -1).takeIf { it > 0 } }
            if (reasonChars.isNotEmpty()) appendLine("- 推理文字長度：${dist(reasonChars, unit = " 字")}")
            val purposes = list.groupingBy { it.optString("purpose", "?") }.eachCount()
            appendLine("- 用途：" + purposes.entries.joinToString("、") { "${it.key} ${it.value}" })
            appendLine()
        }
    }

    private fun reasoningTokens(c: JSONObject): Long? {
        val u = c.optJSONObject("usage") ?: return null
        val v = u.optJSONObject("completion_tokens_details")?.optLong("reasoning_tokens", -1) ?: -1
        return v.takeIf { it >= 0 }
    }

    // ── 引擎檢測 ─────────────────────────────────────

    fun probeSection(events: List<JSONObject>): String = buildString {
        val probes = events.filter { it.optString("type") == "probe" }
        appendLine("## 引擎檢測（每個引擎最近一次）")
        if (probes.isEmpty()) {
            appendLine("（尚未執行「檢測所有引擎」）")
            return@buildString
        }
        probes.groupBy { it.optString("provider") }.forEach { (provider, list) ->
            val p = list.last()
            appendLine("### $provider（${p.optString("ts")}）")
            appendLine("- 模型：${p.optString("model")}，Base URL：${p.optString("host")}，網路：${p.optString("network")}")
            if (p.has("models_error")) {
                appendLine("- 模型列表：失敗（${p.optString("models_error")}）")
            } else {
                val models = p.optJSONArray("models")
                appendLine("- 模型列表：${models?.length() ?: 0} 個，目前模型${if (p.optBoolean("model_listed")) "有" else "不"}在列表中")
                if (models != null) appendLine("  - " + (0 until models.length()).joinToString(", ") { models.optString(it) })
            }
            p.optJSONArray("text_runs").objects().forEachIndexed { i, r ->
                appendLine("- 文字翻譯第 ${i + 1} 次：${if (r.optBoolean("ok")) "✓" else "✗"} ${r.optLong("ms")} ms ${r.optString("error")}".trimEnd())
            }
            p.optJSONObject("vision")?.let { v ->
                appendLine("- 圖片輸入：${if (v.optBoolean("ok")) "✓" else "✗"} ${v.optLong("ms")} ms ${v.optString("reply").ifBlank { v.optString("error") }}".trimEnd())
            }
        }
    }

    // ── 最近錯誤 ─────────────────────────────────────

    fun recentErrors(events: List<JSONObject>, limit: Int = 20): String = buildString {
        val errs = ArrayList<String>()
        events.forEach { e ->
            val type = e.optString("type")
            if (type == "translate" && e.has("error")) errs += "${e.optString("ts")}｜翻譯 ${e.optString("outcome")}｜${e.optString("error")}"
            if (type == "error") errs += "${e.optString("ts")}｜${e.optString("where")}｜${e.optString("error")}"
            val calls = if (type == "llm") listOf(e) else e.optJSONArray("calls").objects()
            calls.filterNot { it.optBoolean("ok") }.forEach { c ->
                errs += "${c.optString("ts", e.optString("ts"))}｜${c.optString("provider")} ${c.optString("model")}｜" +
                    "${c.optInt("http", 0).takeIf { it > 0 }?.let { "HTTP $it " } ?: ""}${c.optString("error")}"
            }
        }
        appendLine("## 最近錯誤（最多 $limit 筆，新的在後）")
        if (errs.isEmpty()) appendLine("（無）") else errs.takeLast(limit).forEach { appendLine("- $it") }
    }

    // ── 工具 ────────────────────────────────────────

    /** 「n=12 平均 2.3s 中位數 2.1s P90 3.8s 最大 5.0s」 */
    fun dist(values: List<Long>, unit: String = "ms"): String {
        if (values.isEmpty()) return "（無資料）"
        val s = values.sorted()
        fun fmt(v: Double) = if (unit == "ms") String.format(Locale.US, "%.2fs", v / 1000) else String.format(Locale.US, "%.0f%s", v, unit)
        val avg = s.average()
        val p50 = percentile(s, 50)
        val p90 = percentile(s, 90)
        return "n=${s.size} 平均 ${fmt(avg)} 中位數 ${fmt(p50.toDouble())} P90 ${fmt(p90.toDouble())} 最大 ${fmt(s.last().toDouble())}"
    }

    /** nearest-rank 百分位數（輸入需已排序） */
    fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0
        val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
