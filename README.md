# 螢幕翻譯 ScreenTranslate

> Android 浮動翻譯工具：點一下畫面邊緣的小圓點，就把畫面上的外文（英文漫畫、網頁、App 介面）直接在原位置蓋上中文翻譯。

## 📖 專案簡介 (Overview)

螢幕翻譯是一個個人使用的 Android App。它在畫面邊緣放一個可以拖曳的浮動小圓點，看到外文時點一下，App 會擷取目前畫面、辨識文字，再把每段文字翻譯成台灣用語的繁體中文，並直接蓋在原文的位置上，看起來就像畫面本來就是中文。

開發這個工具，是因為 Android 內建的 Gemini 圈選翻譯需要長按底部指示條，看漫畫時每翻一頁都要重複操作，很不方便。這個 App 把整個流程縮短成「點一下」，而且在翻譯結果上直接滑動，就能翻到下一頁並自動翻譯，適合連續閱讀英文漫畫。

翻譯品質方面，App 會把同一頁的所有對話一次送給 AI 模型，讓模型依照劇情上下文判斷人稱、語氣和說話者，而不是一句一句分開直譯。文字辨識在手機本機進行，速度快且不消耗 API 額度；翻譯引擎可在 Groq、MiniMax、Gemini 或任何 OpenAI 相容服務之間切換，主要引擎失敗時會自動改用備援。

## ✨ 核心功能 (Key Features)

- **一鍵翻譯**：點浮動小圓點即擷取畫面、辨識、翻譯，譯文蓋在原文位置；點空白處關閉。
- **快速設定面板開關**：下拉通知欄就能顯示或隱藏小圓點。
- **連續閱讀**：在翻譯畫面上滑動，會關閉譯文、把滑動傳給漫畫 App 翻頁，再自動翻譯下一頁。
- **按住看原文**：在翻譯畫面按住任意處，暫時顯示原文；點某個翻譯框可單獨隱藏它。
- **兩種模式**（長按小圓點切換）：
  - 快速模式：本機 OCR → 純文字翻譯，最快、最省額度。
  - 漫畫模式：截圖和 OCR 結果一起送給視覺模型，修正手寫字體並補上漏抓的對話框。
- **多引擎與備援**：Groq、MiniMax M3、Gemini、自訂 OpenAI 相容端點；可設定主要、備援與漫畫模式引擎，並內建「測試翻譯」與「模型列表」。
- **支援多種原文**：英文／歐洲語言、日文、韓文（ML Kit 離線辨識）。
- **針對 OPPO ColorOS 的穩定性設計**：以無障礙服務承載，不需「顯示在其他應用程式上層」權限，截圖也不會每次跳出確認視窗。

## 🛠 技術棧 (Tech Stack)

- **語言 / UI**：Kotlin、Jetpack Compose（Material 3，設定頁）、自繪 View（小圓點與翻譯疊加層）
- **截圖與疊加層**：AccessibilityService（`takeScreenshot`、`TYPE_ACCESSIBILITY_OVERLAY`、`dispatchGesture`）
- **OCR**：Google ML Kit Text Recognition v2（Latin / Japanese / Korean，模型內建於 APK）
- **翻譯**：OpenAI 相容 Chat Completions API（OkHttp + Coroutines）
- **建置**：Gradle 8.11、AGP 8.7、GitHub Actions 自動編譯與發佈 APK

## 🚀 快速開始 (Quick Start)

### 環境要求
- Android 11 以上的手機（開發時以 OPPO Reno 14F 為目標機型）
- 至少一組翻譯引擎的 API key：Groq（免費）、MiniMax、或 Google AI Studio（Gemini 免費）

### 安裝
1. 用手機開啟本 repo 的 **Releases → latest**，下載 `ScreenTranslate-latest.apk` 安裝。
2. 開啟 App，依畫面指示：
   1. 「應用程式資訊」→ 右上角 ⋮ →「允許受限設定」
   2. 「開啟無障礙設定」→ 已下載的應用程式 →「螢幕翻譯」→ 開啟
3. 點「加入快速設定面板」。
4. 在「翻譯引擎」填入 API key，按「測試翻譯」確認可用。
5. ColorOS 建議：電池設為「不限制」、允許自動啟動與背景活動。

### 自行編譯
```bash
git clone https://github.com/hoonsor/APK-Photo.git
cd APK-Photo
gradle :app:assembleRelease   # 需要 Android SDK 與 JDK 17
```
推送到 `main` 會由 GitHub Actions 自動編譯並更新 `latest` 預先發行版；推送 `vX.Y.Z` tag 會建立正式 Release。

## 📁 專案結構 (Project Structure)
```text
app/src/main/java/com/hoonsor/screentranslate/
 ├── TranslatorAccessibilityService.kt  # 核心服務：截圖、小圓點、疊加層、翻頁手勢
 ├── BubbleController.kt                # 浮動小圓點（拖曳、貼邊、忙碌動畫）
 ├── ResultOverlayView.kt               # 原位蓋字的翻譯疊加層
 ├── OcrEngine.kt                       # ML Kit OCR、對話框碎片合併、閱讀順序排序
 ├── Translator.kt                      # 快速／漫畫模式流程、備援、快取、提示詞
 ├── LlmClient.kt                       # OpenAI 相容 API 用戶端
 ├── TranslateTileService.kt            # 快速設定面板開關
 ├── MainActivity.kt                    # 設定頁（Compose）
 └── Prefs.kt                           # 設定儲存與引擎預設值
.github/workflows/build.yml             # 自動編譯與發佈 APK
keystore/                               # 固定簽章（讓新版可直接覆蓋安裝）
```

## 🔄 最新更新 (Recent Updates)

- **v0.1.0**（2026/10/09）：第一版。浮動小圓點、快速設定開關、AccessibilityService 截圖、ML Kit OCR、Groq／MiniMax／Gemini 翻譯與自動備援、原位蓋字、漫畫模式、滑動翻頁後自動翻譯。
