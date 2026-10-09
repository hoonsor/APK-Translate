package com.hoonsor.screentranslate

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.min

/** 一個要翻譯的文字區塊（通常對應一個對話框或段落），座標為截圖像素 = 螢幕座標 */
data class OcrBlock(val id: Int, val text: String, val rect: Rect)

/** ML Kit 離線 OCR 包裝：辨識、清理、合併同一對話框的碎片 */
class OcrEngine {

    private val recognizers = HashMap<OcrScript, TextRecognizer>()

    private fun recognizer(script: OcrScript): TextRecognizer = recognizers.getOrPut(script) {
        when (script) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
    }

    /**
     * @param ignoreTopPx 忽略此高度以上的文字（狀態列的時間、電量等）
     */
    suspend fun recognize(bitmap: Bitmap, script: OcrScript, ignoreTopPx: Int): List<OcrBlock> {
        val result: Text = recognizer(script).process(InputImage.fromBitmap(bitmap, 0)).await()
        val cjk = script != OcrScript.LATIN

        // 1) 以 ML Kit 的 TextBlock 為單位，取得文字與外框
        val raw = ArrayList<Frag>()
        for (block in result.textBlocks) {
            val box = block.boundingBox ?: continue
            if (box.bottom <= ignoreTopPx) continue
            val text = joinLines(block.lines.map { it.text }, cjk)
            if (!isWorthTranslating(text, script)) continue
            val lineHeights = block.lines.mapNotNull { l ->
                l.boundingBox?.let { if (cjk && it.height() > it.width()) it.width() else it.height() }
            }
            val lineH = if (lineHeights.isEmpty()) box.height() else lineHeights.average().toInt()
            raw += Frag(text, Rect(box), lineH.coerceAtLeast(1))
        }

        // 2) 合併上下緊鄰、水平重疊的區塊（漫畫對話框常被拆成多塊）
        val merged = mergeNearby(raw, cjk)

        // 3) 依閱讀順序排序：由上而下、由左而右（日文漫畫為由右而左）
        val rtl = script == OcrScript.JAPANESE
        val sorted = merged.sortedWith { a, b ->
            val rowTol = min(a.rect.height(), b.rect.height()) / 2
            if (kotlin.math.abs(a.rect.top - b.rect.top) > rowTol) a.rect.top - b.rect.top
            else if (rtl) b.rect.left - a.rect.left else a.rect.left - b.rect.left
        }
        return sorted.mapIndexed { i, f -> OcrBlock(i + 1, f.text, f.rect) }
    }

    fun close() {
        recognizers.values.forEach { runCatching { it.close() } }
        recognizers.clear()
    }

    private fun joinLines(lines: List<String>, cjk: Boolean): String {
        if (cjk) return lines.joinToString("") { it.trim() }
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isEmpty()) {
                sb.append(line)
            } else if (sb.endsWith("-") && sb.length >= 2 && sb[sb.length - 2].isLetter()) {
                // 行尾斷字：WON- / DERFUL → WONDERFUL
                sb.setLength(sb.length - 1)
                sb.append(line)
            } else {
                sb.append(' ').append(line)
            }
        }
        return sb.toString()
    }

    private fun isWorthTranslating(text: String, script: OcrScript): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        if (script == OcrScript.LATIN) {
            if (letters < 2 && text.length < 3) return false
            // 已經是中文的文字（例如介面上的中文按鈕）不必翻譯
            val han = text.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
            if (han.toFloat() / letters > 0.5f) return false
        }
        return true
    }

    private data class Frag(val text: String, val rect: Rect, val lineH: Int)

    private fun mergeNearby(blocks: List<Frag>, cjk: Boolean): List<Frag> {
        val items = blocks.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            loop@ for (i in items.indices) {
                for (j in i + 1 until items.size) {
                    val fa = items[i]
                    val fb = items[j]
                    val (upper, lower) = if (fa.rect.top <= fb.rect.top) fa to fb else fb to fa
                    val lineH = min(fa.lineH, fb.lineH)
                    val vGap = lower.rect.top - upper.rect.bottom
                    val hOverlap = min(fa.rect.right, fb.rect.right) - max(fa.rect.left, fb.rect.left)
                    val minW = min(fa.rect.width(), fb.rect.width()).coerceAtLeast(1)
                    // 字高相近、垂直距離小於約 0.6 行、水平至少重疊一半，才視為同一個對話框
                    val similarSize = max(fa.lineH, fb.lineH) <= lineH * 1.6f
                    if (similarSize && vGap in -lineH / 2..(lineH * 0.6f).toInt() && hOverlap > minW / 2) {
                        val text = if (cjk) upper.text + lower.text else upper.text + " " + lower.text
                        val r = Rect(fa.rect).apply { union(fb.rect) }
                        items[i] = Frag(text, r, (fa.lineH + fb.lineH) / 2)
                        items.removeAt(j)
                        changed = true
                        break@loop
                    }
                }
            }
        }
        return items
    }
}
