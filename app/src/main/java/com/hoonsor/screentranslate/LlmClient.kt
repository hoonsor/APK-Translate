package com.hoonsor.screentranslate

import com.hoonsor.screentranslate.diag.DiagLog
import com.hoonsor.screentranslate.diag.RunTrace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    /** 每個主機最後一次有連線活動的時間（預熱用，避免重複預熱） */
    private val lastActive = ConcurrentHashMap<String, Long>()
    private const val WARM_INTERVAL_MS = 30_000L

    /** 已讀完的回應（在 OkHttp 執行緒讀完 body，協程端只處理字串） */
    private class RawResponse(val code: Int, val text: String, val protocol: Protocol, val headers: Headers) {
        val isSuccessful get() = code in 200..299
    }

    /**
     * 執行請求，協程被取消（例如漫畫模式超過時限、使用者關閉結果）時會真的中斷連線，
     * 不會像 execute() 一樣卡到 readTimeout 才結束。
     */
    private suspend fun Call.await(): RawResponse = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val raw = response.use { RawResponse(it.code, it.body?.string().orEmpty(), it.protocol, it.headers) }
                    if (cont.isActive) cont.resume(raw)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    /**
     * 連線預熱：先跟引擎主機建好 DNS＋TCP＋TLS 連線放進連線池，真正翻譯時直接重用。
     * 只打不帶 key 的 GET /models（回 401 也沒關係，目的只是建連線），不送任何畫面內容。
     * @return 這次有送出預熱的主機
     */
    fun prewarm(cfgs: Collection<ProviderConfig>): List<String> {
        val now = System.currentTimeMillis()
        val warmed = ArrayList<String>()
        cfgs.filter { it.baseUrl.startsWith("http") }.distinctBy { hostOf(it.baseUrl) }.forEach { cfg ->
            val host = hostOf(cfg.baseUrl)
            if (now - (lastActive[host] ?: 0L) < WARM_INTERVAL_MS) return@forEach
            lastActive[host] = now
            val req = runCatching {
                Request.Builder().url(cfg.baseUrl.trimEnd('/') + "/models").get().build()
            }.getOrNull() ?: return@forEach
            http.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    lastActive.remove(host)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
            warmed += host
        }
        return warmed
    }

    /** 記進診斷紀錄的回應標頭（額度與排隊資訊，沒有機密） */
    private val LOGGED_HEADERS = setOf("retry-after", "x-groq-region")

    /**
     * 送出一次對話請求並回傳 assistant 的文字內容（已去除 <think> 區塊）。
     * @param messages OpenAI 格式的 messages 陣列
     * @param purpose 診斷紀錄用的用途標籤（fast、comic、test、probe-vision…）
     * @param trace 有傳入時，呼叫紀錄併入該次翻譯；沒有時單獨寫一筆 `llm` 事件
     */
    suspend fun chat(
        cfg: ProviderConfig,
        messages: JSONArray,
        jsonMode: Boolean,
        purpose: String = "chat",
        trace: RunTrace? = null,
    ): String = withContext(Dispatchers.IO) {
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

        val bodyText = body.toString()
        val rec = JSONObject()
            .put("ts", DiagLog.now())
            .put("purpose", purpose)
            .put("provider", cfg.id.label)
            .put("model", cfg.model)
            .put("host", hostOf(cfg.baseUrl))
            .put("json_mode", jsonMode)
            .put("extra", cfg.extraJson)
            .put("temperature", cfg.temperature)
            .put("req_bytes", bodyText.length)

        val req = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(bodyText.toRequestBody(JSON_TYPE))
            .build()

        val timer = NetTimer()
        val t0 = System.nanoTime()
        var retryWithoutJsonMode = false
        val host = hostOf(cfg.baseUrl)
        val result: String = try {
            val resp = http.newBuilder().eventListener(timer).build().newCall(req).await()
            lastActive[host] = System.currentTimeMillis()
            val text = resp.text
            timer.fill(rec)
            rec.put("http", resp.code)
            rec.put("resp_bytes", text.length)
            rec.put("protocol", resp.protocol.toString())
            putHeaders(rec, resp.headers)
            if (!resp.isSuccessful) {
                // 部分供應商不支援 response_format，自動去掉重試一次
                if (jsonMode && resp.code == 400 && text.contains("response_format", ignoreCase = true)) {
                    retryWithoutJsonMode = true
                    ""
                } else {
                    throw LlmException("${cfg.id.label} HTTP ${resp.code}：${extractError(text)}")
                }
            } else {
                parseReply(cfg, text, rec)
            }
        } catch (e: Exception) {
            if (!rec.has("http")) timer.fill(rec)
            val msg = if (e is CancellationException) "已中止（超過時限或使用者關閉）" else (e.message ?: e.javaClass.simpleName)
            rec.put("ok", false).put("error", msg.take(300))
            if (e is CancellationException) rec.put("cancelled", true)
            rec.put("total_ms", (System.nanoTime() - t0) / 1_000_000)
            log(rec, trace)
            throw e
        }
        rec.put("total_ms", (System.nanoTime() - t0) / 1_000_000)
        if (retryWithoutJsonMode) {
            rec.put("ok", false).put("error", "不支援 response_format，改用一般模式重試")
            log(rec, trace)
            return@withContext chat(cfg, messages, jsonMode = false, purpose = purpose, trace = trace)
        }
        rec.put("ok", true)
        log(rec, trace)
        result
    }

    private fun parseReply(cfg: ProviderConfig, text: String, rec: JSONObject): String {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw LlmException("${cfg.id.label} 回應不是 JSON：${text.take(120)}")
        }
        root.optJSONObject("usage")?.let { rec.put("usage", it) }
        root.optJSONObject("x_groq")?.optJSONObject("usage")?.let { rec.put("groq_usage", it) }
        // MiniMax 有時以 HTTP 200 回傳 base_resp 錯誤
        root.optJSONObject("base_resp")?.let { br ->
            val code = br.optInt("status_code", 0)
            if (code != 0) throw LlmException("${cfg.id.label} 錯誤 $code：${br.optString("status_msg")}")
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
        val msg = choice?.optJSONObject("message")
            ?: throw LlmException("${cfg.id.label} 回應缺少 choices：${text.take(160)}")
        rec.put("finish_reason", choice.optString("finish_reason"))
        val content = when (val c = msg.opt("content")) {
            is String -> c
            is JSONArray -> buildString {
                for (i in 0 until c.length()) c.optJSONObject(i)?.optString("text")?.let { append(it) }
            }
            else -> ""
        }
        // 推理模型的思考內容（Groq gpt-oss 放 reasoning、MiniMax 可能放 reasoning_content／reasoning_details 或 <think>）
        val reasoningChars = msg.optString("reasoning").length +
            msg.optString("reasoning_content").length +
            (msg.optJSONArray("reasoning_details")?.toString()?.length ?: 0)
        val withoutThink = content.replace(THINK_REGEX, "")
        val cleaned = withoutThink.trim()
        rec.put("content_chars", cleaned.length)
        rec.put("reasoning_chars", reasoningChars)
        rec.put("think_tag_chars", content.length - withoutThink.length)
        if (DiagLog.includeText) rec.put("reply", cleaned.take(2000))
        if (cleaned.isEmpty()) throw LlmException("${cfg.id.label} 回傳空白內容")
        return cleaned
    }

    private fun log(rec: JSONObject, trace: RunTrace?) {
        if (trace != null) trace.addCall(rec) else DiagLog.record("llm", rec)
    }

    private fun putHeaders(rec: JSONObject, headers: Headers) {
        val h = JSONObject()
        headers.names().forEach { name ->
            val n = name.lowercase()
            if (n.startsWith("x-ratelimit") || n in LOGGED_HEADERS) h.put(n, headers[name])
        }
        if (h.length() > 0) rec.put("headers", h)
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

    /** 分段計時：DNS、建連線（含 TLS）、上傳、伺服器處理（送完請求→收到回應標頭） */
    private class NetTimer : EventListener() {
        private var callStart = 0L
        private var dnsStart = 0L
        private var dnsMs = -1L
        private var connectStart = 0L
        private var connectMs = -1L
        private var tlsStart = 0L
        private var tlsMs = -1L
        private var acquired = 0L
        private var reqEnd = 0L
        private var headersStart = 0L
        private var newConnection = false

        private fun now() = System.nanoTime()
        private fun ms(a: Long, b: Long) = if (a > 0 && b >= a) (b - a) / 1_000_000 else -1L

        override fun callStart(call: Call) { callStart = now() }
        override fun dnsStart(call: Call, domainName: String) { dnsStart = now() }
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { dnsMs = ms(dnsStart, now()) }
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            connectStart = now()
            newConnection = true
        }
        override fun secureConnectStart(call: Call) { tlsStart = now() }
        override fun secureConnectEnd(call: Call, handshake: Handshake?) { tlsMs = ms(tlsStart, now()) }
        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
            connectMs = ms(connectStart, now())
        }
        override fun connectionAcquired(call: Call, connection: Connection) { acquired = now() }
        override fun requestBodyEnd(call: Call, byteCount: Long) { reqEnd = now() }
        override fun responseHeadersStart(call: Call) { headersStart = now() }

        fun fill(rec: JSONObject) {
            rec.put("reused", !newConnection)
            if (dnsMs >= 0) rec.put("dns_ms", dnsMs)
            if (connectMs >= 0) rec.put("connect_ms", connectMs)
            if (tlsMs >= 0) rec.put("tls_ms", tlsMs)
            ms(acquired, reqEnd).takeIf { it >= 0 }?.let { rec.put("upload_ms", it) }
            ms(reqEnd, headersStart).takeIf { it >= 0 }?.let { rec.put("server_ms", it) }
            ms(callStart, headersStart).takeIf { it >= 0 }?.let { rec.put("ttfb_ms", it) }
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
