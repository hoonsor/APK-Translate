package com.hoonsor.screentranslate.diag

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagReportTest {

    private fun call(provider: String, ok: Boolean, ms: Long, http: Int = 200, reasoning: Long? = null) =
        JSONObject().put("provider", provider).put("model", "m-$provider").put("ok", ok)
            .put("total_ms", ms).put("server_ms", ms - 100).put("http", http).put("purpose", "fast")
            .apply {
                if (reasoning != null) {
                    put("usage", JSONObject().put("completion_tokens", 300)
                        .put("completion_tokens_details", JSONObject().put("reasoning_tokens", reasoning)))
                }
                if (!ok) put("error", "rate limit")
            }

    private fun run(outcome: String, total: Long, vararg calls: JSONObject) =
        JSONObject().put("type", "translate").put("ts", "t").put("mode", "FAST").put("outcome", outcome)
            .put("total_ms", total)
            .put("stages", JSONObject().put("capture_ms", 200).put("ocr_ms", 300))
            .put("calls", JSONArray().apply { calls.forEach { put(it) } })
            .apply { if (calls.size > 1) put("fallback_used", true) }

    @Test
    fun percentileNearestRank() {
        val v = listOf(1L, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        assertEquals(5L, DiagReport.percentile(v, 50))
        assertEquals(9L, DiagReport.percentile(v, 90))
        assertEquals(10L, DiagReport.percentile(v, 100))
        assertEquals(1L, DiagReport.percentile(listOf(1L), 90))
        assertEquals(0L, DiagReport.percentile(emptyList(), 50))
    }

    @Test
    fun reportContainsStatsAndErrors() {
        val events = listOf(
            run("ok", 2000, call("Groq", true, 1500)),
            run("ok", 9000, call("Groq", false, 3000, http = 429), call("MiniMax", true, 5500, reasoning = 800)),
            run("error", 4000, call("Groq", false, 4000, http = 429)),
        )
        val text = DiagReport.build("## 環境\n- 測試", events)
        assertTrue(text.contains("翻譯統計（共 3 次）"))
        assertTrue(text.contains("ok 2"))
        assertTrue(text.contains("改用備援：1 次"))
        assertTrue(text.contains("### Groq｜m-Groq"))
        assertTrue(text.contains("HTTP 429×2"))
        assertTrue(text.contains("推理 tokens"))
        assertTrue(text.contains("rate limit"))
        assertTrue(text.contains("```jsonl"))
        assertFalse(DiagReport.build("h", events, includeRaw = false).contains("```jsonl"))
    }

    @Test
    fun comicFastFirstStats() {
        fun comic(early: Long?, total: Long, extra: JSONObject.() -> Unit) =
            JSONObject().put("type", "translate").put("ts", "t").put("mode", "COMIC").put("outcome", "ok")
                .put("total_ms", total)
                .put("stages", JSONObject().apply { if (early != null) put("early_ms", early) })
                .put("calls", JSONArray())
                .apply(extra)
        val events = listOf(
            comic(1500, 9000) { put("comic_upgraded", true) },
            comic(1400, 15200) { put("comic_error", "MiniMax 超過 15 秒").put("comic_timed_out", true) },
            comic(1600, 4000) { put("comic_upgrade_cancelled", true) },
            comic(null, 8000) { },
        )
        val text = DiagReport.translateStats(events)
        assertTrue(text.contains("看到第一個譯文"))
        assertTrue(text.contains("階段 early_ms"))
        assertTrue(text, text.contains("視覺模型成功 2、逾時 1、失敗 0、使用者先關閉 1（共 4）"))
    }

    @Test
    fun allCallsFlattensStandaloneAndNested() {
        val events = listOf(
            run("ok", 1000, call("Groq", true, 900)),
            JSONObject().put("type", "llm").put("provider", "Gemini").put("ok", true).put("total_ms", 800),
        )
        assertEquals(2, DiagReport.allCalls(events).size)
    }

    @Test
    fun emptyEventsDoNotCrash() {
        val text = DiagReport.build("h", emptyList())
        assertTrue(text.contains("尚無翻譯紀錄"))
        assertTrue(text.contains("尚未執行"))
    }

    @Test
    fun redactorMasksKnownSecretsAndPatterns() {
        val raw = "key=abcd1234efgh Authorization: Bearer xyz.123 gsk_ABCDEFGHIJKLMNOP sk-abcdefghijklmn AIzaSyA1234567890123456789012"
        val out = Redactor.redact(raw, listOf("abcd1234efgh", "short"))
        assertFalse(out.contains("abcd1234efgh"))
        assertFalse(out.contains("xyz.123"))
        assertFalse(out.contains("gsk_ABCDEFGHIJKLMNOP"))
        assertFalse(out.contains("sk-abcdefghijklmn"))
        assertFalse(out.contains("AIzaSy"))
        assertTrue(out.contains("key=***"))
    }

    @Test
    fun runTraceSummaryAndFinish() {
        val t = RunTrace("tap")
        t.stage("capture_ms", 210)
        t.stage("ocr_ms", 340)
        t.addCall(JSONObject().put("provider", "Groq").put("ok", false).put("total_ms", 4100))
        t.addCall(JSONObject().put("provider", "MiniMax").put("ok", true).put("total_ms", 7800))
        assertEquals("截圖 0.2・OCR 0.3・Groq ✗4.1・MiniMax 7.8", t.shortSummary())
        val j = t.finish("ok")
        assertEquals("ok", j.getString("outcome"))
        assertEquals(2, j.getJSONArray("calls").length())
        assertEquals(340, j.getJSONObject("stages").getInt("ocr_ms"))
    }
}
