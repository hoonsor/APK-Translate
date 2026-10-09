package com.hoonsor.screentranslate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmException(message: String) : IOException(message)

/** OpenAI 相容 Chat Completions 用戶端（Groq / MiniMax / Gemini / 自訂共用） */
object LlmClient {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val THINK_REGEX = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE)

    /**
     * 送出一次對話請求並回傳 assistant 的文字內容（已去除 <think> 區塊）。
     * @param messages OpenAI 格式的 messages 陣列
     */
    suspend fun chat(cfg: ProviderConfig, messages: JSONArray, jsonMode: Boolean): String =
        withContext(Dispatchers.IO) {
            if (!cfg.isUsable) throw LlmException("${cfg.id.label} 尚未設定 API key 或模型")

            val body = JSONObject()
            body.put("model", cfg.model)
            body.put("messages", messages)
            body.put("stream", false)
            cfg.temperature.toDoubleOrNull()?.let { body.put("temperature", it) }
            if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))

            // 使用者自訂的額外參數（例如 reasoning_effort、thinking 開關），會覆蓋同名欄位
            if (cfg.extraJson.isNotBlank()) {
                val extra = try {
                    JSONObject(cfg.extraJson)
                } catch (e: Exception) {
                    throw LlmException("${cfg.id.label} 的額外參數不是合法 JSON")
                }
                extra.keys().forEach { k -> body.put(k, extra.get(k)) }
            }

            val req = Request.Builder()
                .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .build()

            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    // 部分供應商不支援 response_format，自動去掉重試一次
                    if (jsonMode && resp.code == 400 && text.contains("response_format", ignoreCase = true)) {
                        return@withContext chat(cfg, messages, jsonMode = false)
                    }
                    throw LlmException("${cfg.id.label} HTTP ${resp.code}：${extractError(text)}")
                }
                val root = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw LlmException("${cfg.id.label} 回應不是 JSON：${text.take(120)}")
                }
                // MiniMax 有時以 HTTP 200 回傳 base_resp 錯誤
                root.optJSONObject("base_resp")?.let { br ->
                    val code = br.optInt("status_code", 0)
                    if (code != 0) throw LlmException("${cfg.id.label} 錯誤 $code：${br.optString("status_msg")}")
                }
                val msg = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?: throw LlmException("${cfg.id.label} 回應缺少 choices：${text.take(160)}")
                val content = when (val c = msg.opt("content")) {
                    is String -> c
                    is JSONArray -> buildString {
                        for (i in 0 until c.length()) c.optJSONObject(i)?.optString("text")?.let { append(it) }
                    }
                    else -> ""
                }
                val cleaned = content.replace(THINK_REGEX, "").trim()
                if (cleaned.isEmpty()) throw LlmException("${cfg.id.label} 回傳空白內容")
                cleaned
            }
        }

    /** 取得供應商的可用模型 ID 清單（GET /models） */
    suspend fun listModels(cfg: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
        if (cfg.baseUrl.isBlank() || cfg.apiKey.isBlank()) throw LlmException("請先填入 Base URL 與 API key")
        val req = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/models")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw LlmException("HTTP ${resp.code}：${extractError(text)}")
            val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { s -> s.isNotBlank() } }
                .map { it.removePrefix("models/") }
                .sorted()
        }
    }

    private fun extractError(body: String): String {
        return try {
            val o = JSONObject(body)
            o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: o.optJSONObject("base_resp")?.optString("status_msg")?.takeIf { it.isNotBlank() }
                ?: body.take(200)
        } catch (e: Exception) {
            body.take(200)
        }
    }
}
