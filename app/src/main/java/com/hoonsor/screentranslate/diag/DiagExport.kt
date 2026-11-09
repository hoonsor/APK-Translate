package com.hoonsor.screentranslate.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.hoonsor.screentranslate.BuildConfig
import com.hoonsor.screentranslate.Prefs
import com.hoonsor.screentranslate.ProviderId
import com.hoonsor.screentranslate.TranslatorAccessibilityService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 匯出診斷報告：分享（Gmail、LINE、雲端硬碟…）、存成檔案、複製摘要 */
object DiagExport {

    fun fileName(): String =
        "ScreenTranslate-diag-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".txt"

    fun header(context: Context): String {
        val prefs = Prefs(context)
        return buildString {
            appendLine("## 環境")
            appendLine("- 產生時間：${DiagLog.now()}")
            appendLine("- App：v${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）")
            appendLine("- 裝置：${Build.MANUFACTURER} ${Build.MODEL}（${Build.DEVICE}）")
            appendLine("- Android：${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}），系統版本 ${Build.DISPLAY}")
            appendLine("- 網路（匯出當下）：${DiagLog.networkType()}")
            appendLine("- 無障礙服務：${if (TranslatorAccessibilityService.instance != null) "執行中" else "未執行"}")
            appendLine("- 紀錄檔大小：${DiagLog.sizeBytes() / 1024} KB；包含原文與譯文：${if (prefs.diagIncludeText) "是" else "否"}")
            appendLine()
            appendLine("## 設定（不含 API key）")
            appendLine("- 模式：${prefs.mode.name}；OCR：${prefs.ocrScript.name}")
            appendLine("- 主要：${prefs.primary.label}；備援：${prefs.fallback?.label ?: "不使用"}；漫畫模式：${prefs.vision.label}")
            appendLine("- 翻頁後自動翻譯：${prefs.autoAfterSwipe}，等待 ${prefs.swipeSettleMs} ms")
            ProviderId.entries.forEach { id ->
                val c = prefs.provider(id)
                val key = if (c.apiKey.isBlank()) "未設定" else "已設定（${c.apiKey.length} 字元）"
                appendLine("- ${id.label}：key $key｜${c.baseUrl}｜model=${c.model}｜temperature=${c.temperature}｜extra=${c.extraJson.ifBlank { "無" }}")
            }
        }
    }

    fun buildReport(context: Context, includeRaw: Boolean = true): String {
        DiagLog.flush()
        val text = DiagReport.build(header(context), DiagLog.readEvents(), includeRaw)
        return Redactor.redact(text, DiagLog.secrets())
    }

    /** 寫到 cache/export 並回傳系統分享選單的 Intent（在背景執行緒呼叫，再由畫面 startActivity） */
    fun shareIntent(context: Context): Intent {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // 只留這次的匯出檔
        val file = File(dir, fileName())
        file.writeText(buildReport(context))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagfiles", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "匯出診斷紀錄")
    }

    /** 寫進使用者用「存成檔案」選的位置（SAF） */
    fun saveTo(context: Context, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(buildReport(context).toByteArray()) }
            ?: throw IllegalStateException("無法寫入選擇的位置")
    }

    /** 複製不含原始事件的摘要（可直接貼進對話） */
    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("ScreenTranslate 診斷摘要", text))
    }

    /** 設定頁顯示用的一行狀態 */
    fun statusLine(): String {
        val events = DiagLog.readEvents()
        val runs = events.filter { it.optString("type") == "translate" }
        val ok = runs.filter { it.optString("outcome") == "ok" }
        val calls = DiagReport.allCalls(events)
        val fails = calls.count { !it.optBoolean("ok") }
        val median = ok.map { it.optLong("total_ms") }.sorted().let { if (it.isEmpty()) null else DiagReport.percentile(it, 50) }
        return buildString {
            append("已記錄 ${runs.size} 次翻譯、${calls.size} 次引擎呼叫（失敗 $fails）")
            median?.let { append(String.format(Locale.US, "，翻譯中位數 %.1f 秒", it / 1000f)) }
            append("・${DiagLog.sizeBytes() / 1024} KB")
        }
    }
}
