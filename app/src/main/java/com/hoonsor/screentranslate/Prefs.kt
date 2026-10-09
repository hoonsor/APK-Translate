package com.hoonsor.screentranslate

import android.content.Context
import android.content.SharedPreferences

/** 翻譯引擎（皆走 OpenAI 相容的 /chat/completions 介面） */
enum class ProviderId(
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val defaultExtraJson: String,
    val defaultTemperature: String,
    val hint: String,
) {
    GROQ(
        "Groq",
        "https://api.groq.com/openai/v1",
        "openai/gpt-oss-120b",
        """{"reasoning_effort":"low"}""",
        "0.3",
        "免費（依模型有每分鐘／每日次數限制）、速度極快。建議當快速模式主力。預設模型為純文字，漫畫模式請改用 MiniMax 或 Gemini。",
    ),
    MINIMAX(
        "MiniMax",
        "https://api.minimax.io/v1",
        "MiniMax-M3",
        "",
        "1.0",
        "支援圖片輸入，適合漫畫模式。M3 建議 temperature 1.0。中國大陸帳號請改用 https://api.minimaxi.com/v1。",
    ),
    GEMINI(
        "Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-flash-latest",
        "",
        "0.3",
        "Google AI Studio 免費 key，支援圖片。免費層的內容可能被 Google 用於改進模型。",
    ),
    CUSTOM(
        "自訂",
        "",
        "",
        "",
        "",
        "任何 OpenAI 相容端點（OpenRouter、本機伺服器等）。",
    );

    companion object {
        fun of(name: String?): ProviderId? = entries.firstOrNull { it.name == name }
    }
}

data class ProviderConfig(
    val id: ProviderId,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val extraJson: String,
    val temperature: String,
) {
    val isUsable: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

enum class TranslateMode(val label: String) {
    FAST("快速模式"),
    COMIC("漫畫模式"),
}

enum class OcrScript(val label: String) {
    LATIN("英文／歐洲語言"),
    JAPANESE("日文"),
    KOREAN("韓文"),
}

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ── 供應商設定 ─────────────────────────────────────
    fun provider(id: ProviderId): ProviderConfig = ProviderConfig(
        id = id,
        baseUrl = sp.getString("${id.name}_url", id.defaultBaseUrl) ?: id.defaultBaseUrl,
        apiKey = sp.getString("${id.name}_key", "") ?: "",
        model = sp.getString("${id.name}_model", id.defaultModel) ?: id.defaultModel,
        extraJson = sp.getString("${id.name}_extra", id.defaultExtraJson) ?: id.defaultExtraJson,
        temperature = sp.getString("${id.name}_temp", id.defaultTemperature) ?: id.defaultTemperature,
    )

    fun saveProvider(cfg: ProviderConfig) {
        sp.edit()
            .putString("${cfg.id.name}_url", cfg.baseUrl.trim().trimEnd('/'))
            .putString("${cfg.id.name}_key", cfg.apiKey.trim())
            .putString("${cfg.id.name}_model", cfg.model.trim())
            .putString("${cfg.id.name}_extra", cfg.extraJson.trim())
            .putString("${cfg.id.name}_temp", cfg.temperature.trim())
            .apply()
    }

    /** 快速模式的主要引擎 */
    var primary: ProviderId
        get() = ProviderId.of(sp.getString("primary", null)) ?: ProviderId.GROQ
        set(v) = sp.edit().putString("primary", v.name).apply()

    /** 主要引擎失敗時的備援；null = 不使用 */
    var fallback: ProviderId?
        get() = ProviderId.of(sp.getString("fallback", ProviderId.MINIMAX.name))
        set(v) = sp.edit().putString("fallback", v?.name ?: "NONE").apply()

    /** 漫畫模式（圖片 + OCR 混合）使用的視覺模型引擎 */
    var vision: ProviderId
        get() = ProviderId.of(sp.getString("vision", null)) ?: ProviderId.MINIMAX
        set(v) = sp.edit().putString("vision", v.name).apply()

    // ── 行為設定 ─────────────────────────────────────
    var mode: TranslateMode
        get() = runCatching { TranslateMode.valueOf(sp.getString("mode", "FAST")!!) }.getOrDefault(TranslateMode.FAST)
        set(v) = sp.edit().putString("mode", v.name).apply()

    var ocrScript: OcrScript
        get() = runCatching { OcrScript.valueOf(sp.getString("ocr", "LATIN")!!) }.getOrDefault(OcrScript.LATIN)
        set(v) = sp.edit().putString("ocr", v.name).apply()

    var bubbleEnabled: Boolean
        get() = sp.getBoolean("bubble_enabled", true)
        set(v) = sp.edit().putBoolean("bubble_enabled", v).apply()

    var bubbleSizeDp: Int
        get() = sp.getInt("bubble_size", 46)
        set(v) = sp.edit().putInt("bubble_size", v).apply()

    var bubbleAlpha: Float
        get() = sp.getFloat("bubble_alpha", 0.85f)
        set(v) = sp.edit().putFloat("bubble_alpha", v).apply()

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) = sp.edit().putInt("bubble_x", v).apply()

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) = sp.edit().putInt("bubble_y", v).apply()

    /** 在翻譯畫面上滑動翻頁後，自動翻譯下一頁 */
    var autoAfterSwipe: Boolean
        get() = sp.getBoolean("auto_after_swipe", true)
        set(v) = sp.edit().putBoolean("auto_after_swipe", v).apply()

    /** 翻頁後等待畫面穩定的時間（毫秒） */
    var swipeSettleMs: Int
        get() = sp.getInt("swipe_settle_ms", 900)
        set(v) = sp.edit().putInt("swipe_settle_ms", v).apply()

    /** 翻譯框文字大小上限（sp） */
    var maxTextSp: Int
        get() = sp.getInt("max_text_sp", 18)
        set(v) = sp.edit().putInt("max_text_sp", v).apply()
}
