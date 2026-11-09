package com.hoonsor.screentranslate.diag

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.hoonsor.screentranslate.Prefs
import com.hoonsor.screentranslate.ProviderId
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 診斷紀錄：每次翻譯、每次引擎呼叫、引擎檢測都寫成一行 JSON（JSONL），存在 App 私有目錄。
 * - 檔案 `files/diag/diag.jsonl`，超過 1 MB 就輪替成 `diag.1.jsonl`（只留一份舊檔，總量約 2 MB）。
 * - 寫入前一律遮蔽 API key；預設不記錄畫面上的原文與譯文（設定頁可開啟）。
 * - 寫檔在單一背景執行緒進行，不拖慢翻譯。
 */
object DiagLog {
    private const val MAX_BYTES = 1_000_000L

    @Volatile private var dir: File? = null
    @Volatile private var prefs: Prefs? = null
    @Volatile private var appContext: Context? = null
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "diag-log").apply { isDaemon = true } }
    private val lock = Any()

    fun init(context: Context) {
        if (dir != null) return
        val ctx = context.applicationContext
        appContext = ctx
        prefs = Prefs(ctx)
        dir = File(ctx.filesDir, "diag").apply { mkdirs() }
    }

    val enabled: Boolean get() = prefs?.diagEnabled == true
    val includeText: Boolean get() = prefs?.diagIncludeText == true

    private val current get() = File(dir, "diag.jsonl")
    private val previous get() = File(dir, "diag.1.jsonl")

    fun record(type: String, data: JSONObject) {
        if (dir == null || !enabled) return
        data.put("type", type)
        data.put("ts", now())
        val line = Redactor.redact(data.toString(), secrets())
        writer.execute {
            synchronized(lock) {
                runCatching {
                    val f = current
                    if (f.length() > MAX_BYTES) {
                        previous.delete()
                        f.renameTo(previous)
                    }
                    current.appendText(line + "\n")
                }
            }
        }
    }

    fun error(where: String, e: Throwable) {
        record("error", JSONObject().put("where", where).put("error", "${e.javaClass.simpleName}: ${e.message}".take(300)))
    }

    /** 舊→新的全部事件 */
    fun readEvents(): List<JSONObject> = synchronized(lock) {
        listOf(previous, current).filter { it.exists() }.flatMap { f ->
            f.readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        }
    }

    fun sizeBytes(): Long = listOf(previous, current).filter { it.exists() }.sumOf { it.length() }

    fun clear() = synchronized(lock) {
        previous.delete()
        current.delete()
    }

    /** 等背景寫入完成（匯出前呼叫，確保最新事件已寫入） */
    fun flush() {
        runCatching { writer.submit {}.get() }
    }

    fun secrets(): List<String> {
        val p = prefs ?: return emptyList()
        return ProviderId.entries.map { p.provider(it).apiKey }.filter { it.length >= 8 }
    }

    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    /** wifi／cellular／ethernet／vpn／none，附「未驗證」標記（連得上但沒網路） */
    fun networkType(): String {
        val ctx = appContext ?: return "?"
        return runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
            val t = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
            val down = caps.linkDownstreamBandwidthKbps
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            "$t${if (validated) "" else "（未驗證）"} 下行約 ${down / 1000} Mbps"
        }.getOrDefault("?")
    }
}
