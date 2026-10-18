# AGENTS.md — 螢幕翻譯 ScreenTranslate（APK-Translate）

給接手的 AI 看：Claude（其他對話或其他電腦）、Antigravity、Codex、DeepSeek Harness。
**不需要對話紀錄**，讀完這份文件加上 `PROJECT_STATUS.md` 就能接手。
使用者（倬）看的使用說明在 `README.md`；功能清單在 `docs/FEATURES.md`；語音動作規劃在 `docs/AGENT_API.md`。

> 本檔已合併原 `ANTIGRAVITY.md` 的全部內容（2026/10/13），原檔保留一行指向本檔。

---

## 1. 專案目的

倬個人自用的 Android App，不上架。主要用途是在手機上**閱讀英文漫畫**，也可翻譯網頁、App 介面上的外文。

- 畫面邊緣有一個可拖曳的浮動小圓點；點一下 → 截圖 → 本機 OCR → LLM 翻譯 → 把繁體中文譯文**蓋在原文位置**。
- 從 Android 快速設定面板（下拉通知欄）一鍵顯示／隱藏小圓點。
- 起因：Android 內建 Gemini 的圈選翻譯要長按底部指示條，看漫畫每頁都要重複，太麻煩。
- 倬的兩個要求：**速度快**、**譯文精確**（依上下文、保留語氣）。

目標機型：**OPPO Reno 14F（ColorOS）**。Android 版本：原因待倬確認（未提供）。

## 2. 技術棧

| 項目 | 版本／內容 |
|---|---|
| 語言 | Kotlin 2.0.21（含 Compose compiler plugin） |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.11.1（v0.2.0 起有 Gradle Wrapper `./gradlew`） |
| JDK | 17（CI）；本機可用 17 或 21（倬的電腦用 `C:/Users/hoonsor/.jdks/jbr-21.0.11`，不要用 Android Studio 內建的 JDK 25） |
| SDK | compileSdk 35、targetSdk 35、minSdk 30（Android 11，`takeScreenshot()` 的最低版本） |
| 設定頁 UI | Jetpack Compose（BOM 2024.12.01）＋ Material 3 |
| 小圓點、翻譯疊加層 | 自繪 View（`TYPE_ACCESSIBILITY_OVERLAY`） |
| OCR | Google ML Kit Text Recognition v2 16.0.1：Latin、Japanese、Korean（模型打包進 APK） |
| 網路 | OkHttp 4.12.0 ＋ Kotlin Coroutines 1.9.0 |
| 翻譯 | OpenAI 相容 `/chat/completions`（Groq、MiniMax、Gemini、自訂） |
| 套件名 | `com.hoonsor.screentranslate`（非標準的 `tw.hoonsor.*`；舊 APP 不改套件名） |
| 版本 | `app/build.gradle.kts` 的 `versionName`（SemVer）與 `versionCode` |

依賴版本目前直接寫在 `app/build.gradle.kts` 與根目錄 `build.gradle.kts`，尚未集中到 `libs.versions.toml`（見 PROJECT_STATUS 路線）。

## 3. 建置與安裝

正式版由 GitHub Actions 編譯發布（`.github/workflows/build.yml`），流程與鬧鐘 APP 相同。

| 觸發 | 結果 |
|---|---|
| push 到 `main` | 跑單元測試、編譯 release APK、上傳 artifact，並**刪除重建** `latest` 預先發行版（測試用固定網址） |
| push tag `vX.Y.Z` | 檢查 tag 與 `versionName` 一致 → 建立正式 Release，附 `ScreenTranslate-vX.Y.Z.apk`；更新說明取自 PROJECT_STATUS「版本歷程」表該版本的列 |
| 編譯失敗 | `Report build errors` 步驟把 `build.log` 的錯誤行寫成 annotation |

- **簽章（v0.2.0 起）**：共用金鑰 `hoonsor-release.jks`。`app/build.gradle.kts` 從環境變數 `SIGNING_KEYSTORE_PATH`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD` 讀取，CI 從 repo Secrets 解出；沒有時退回 debug 簽章。憑證 SHA-256 `514ec00eea070dd3e67b4f6a0c722c9c779497bb0797e7a3446fcca4e4548825`。**不要把金鑰或密碼放回 repo**（舊的 `keystore/screentranslate.jks` 已移到本機 `_trash/`，但仍留在 git 歷史中，視為已公開、不再使用）。
- 手機端更新：App 內更新（`update/` 套件，移植自鬧鐘 APP）或 Obtainium，只追正式 Release。測試版網址：`https://github.com/hoonsor/APK-Translate/releases/download/latest/ScreenTranslate-latest.apk`
- 讀取失敗原因：`gh api repos/hoonsor/APK-Translate/check-runs/<job_id>/annotations`（見第 7 節，Claude 雲端環境無法下載完整 log）。
- 本機編譯（需 Android SDK、JDK 17 或 21）：`./gradlew :app:testDebugUnitTest :app:assembleRelease`。
- APK 約 86 MB，主因是內建英、日、韓三種 OCR 模型。

建議的修改流程：先推 `ci-test` 確認編譯成功 → 再推 `main`。

## 4. 目錄與架構

```text
app/src/main/java/com/hoonsor/screentranslate/
 ├── TranslatorAccessibilityService.kt  # 核心：承載小圓點與疊加層、截圖、翻頁手勢、切 App 自動收起
 ├── BubbleController.kt                # 浮動小圓點：拖曳、貼邊、點擊、長按切模式、忙碌／錯誤動畫
 ├── ResultOverlayView.kt               # 全螢幕翻譯疊加層：原位蓋字、點框看原文、按住看全部原文、滑動轉發
 ├── OcrEngine.kt                       # ML Kit OCR、行合併、斷字修復、對話框碎片合併、閱讀順序排序
 ├── Translator.kt                      # 快速／漫畫模式流程、主要→備援、LRU 快取、提示詞、JSON 容錯解析
 ├── LlmClient.kt                       # OpenAI 相容用戶端：chat、listModels、錯誤解析、<think> 移除
 ├── TranslateTileService.kt            # 快速設定面板開關
 ├── MainActivity.kt                    # 設定頁（Compose）：啟用引導、行為、引擎、ColorOS 提示
 └── Prefs.kt                           # SharedPreferences 封裝、ProviderId 預設值、列舉
app/src/main/res/xml/accessibility_service_config.xml  # canTakeScreenshot、canPerformGestures
app/src/main/AndroidManifest.xml
.github/workflows/build.yml
app/src/main/java/.../update/           # App 內更新（檢查 GitHub Release、下載、PackageInstaller 安裝）
docs/                                   # FEATURES、DESIGN、AGENT_API、agent-manifest.json
```

### 翻譯流程

```text
點小圓點
 → 隱藏小圓點（等 110 ms）→ AccessibilityService.takeScreenshot()（頻率太高時等 1 秒重試一次）
 → HardwareBuffer 轉成 ARGB_8888 軟體 Bitmap
 → OcrEngine：去掉狀態列高度以上的文字 → 行合併 → 碎片合併 → 依閱讀順序編號
 → Translator
     快速模式：[{id,text}] → 主要引擎（失敗改備援）→ {"t":[{id,zh}]}
     漫畫模式：JPEG（長邊 ≤1600px）＋ OCR 區塊（座標正規化 0~1000）→ 視覺模型
               → {"t":[...], "extra":[{box,src,zh}]}；失敗自動退回快速模式
 → ResultOverlayView 在原座標畫譯文框；小圓點移到最上層
```

### 設定儲存（`Prefs`）

全部存在 SharedPreferences `settings`（App 私有、`allowBackup=false`）：
各引擎的 url、key、model、extra JSON、temperature；`primary`（預設 GROQ）、`fallback`（預設 MINIMAX）、`vision`（預設 MINIMAX）；`mode`、`ocr`、小圓點大小／透明度／位置、`auto_after_swipe`、`swipe_settle_ms`、`max_text_sp`。

### 引擎預設值

| 引擎 | Base URL | 預設模型 | temperature | 額外參數 |
|---|---|---|---|---|
| Groq | `https://api.groq.com/openai/v1` | `openai/gpt-oss-120b` | 0.3 | `{"reasoning_effort":"low"}` |
| MiniMax | `https://api.minimax.io/v1` | `MiniMax-M3` | 1.0 | 無 |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-flash-latest` | 0.3 | 無 |
| 自訂 | 空 | 空 | 空 | 無 |

## 5. 關鍵設計決策（含原因）

| # | 決策 | 原因 |
|---|---|---|
| D1 | 截圖用 **AccessibilityService.takeScreenshot()**，不用 MediaProjection | Android 14 起 MediaProjection 每次啟動都跳「開始投放」確認，狀態列一直有錄影圖示；無障礙服務授權一次就好 |
| D2 | 小圓點與疊加層也由無障礙服務以 `TYPE_ACCESSIBILITY_OVERLAY` 承載，不另開前景服務＋`SYSTEM_ALERT_WINDOW` | 不需要「顯示在其他應用程式上層」權限；由系統綁定，比一般前景服務更不容易被 ColorOS 清除。（最初規劃是前景服務＋Overlay，實作時改掉） |
| D3 | OCR 用 **ML Kit 本機**，模型打包進 APK | 免費、離線、無次數限制、約 0.2～0.4 秒；會回傳座標，可準確蓋回原位；打包模型讓第一次使用不必等下載（代價是 APK 約 86 MB） |
| D4 | **整頁一次翻譯**，不逐句翻 | 漫畫對話有上下文，逐句翻譯人稱與語氣會錯亂；一次送出也比較快 |
| D5 | 所有引擎走 **OpenAI 相容介面**，可設定主要＋備援 | 可隨時換供應商；主要引擎失敗、超時或額度用完自動改備援，符合倬「穩定可靠優先」 |
| D6 | 快速模式預設主力 **Groq** | 倬提供 Groq 免費 API，Groq 推論速度極快。預設模型 `openai/gpt-oss-120b`＋`reasoning_effort: low` 是 Claude 選的（Groq 上較強的文字模型、降低思考延遲），**尚未實測翻譯品質** |
| D7 | 漫畫模式預設 **MiniMax M3** | 倬有 MiniMax 月訂方案；M3 支援圖片輸入；temperature 1.0 依 MiniMax 對 M3 的建議 |
| D8 | 漫畫模式採 **截圖＋OCR 混合**，不是純視覺 | 座標用 OCR 的比較精準（LLM 回傳座標不可靠）；圖片讓模型修正手寫字體的 OCR 錯字，並可在 `extra` 補上漏抓的對話框 |
| D9 | 翻譯結果上**滑動 → 關閉疊加層、用 `dispatchGesture` 重播滑動、等畫面穩定後自動翻譯下一頁** | 連續看漫畫時不必每頁都點小圓點 |
| D10 | 只請求 `canTakeScreenshot` 與 `canPerformGestures`，`canRetrieveWindowContent="false"` | 不讀取其他 App 的畫面結構，降低隱私疑慮；只在使用者點擊時截圖 |
| D11 | 忽略狀態列高度以上的文字、略過中文佔多數的區塊 | 避免把時間、電量、中文介面也拿去翻 |
| D12 | 編譯用 **GitHub Actions**（倬選擇 A 方案，非 Android Studio） | 倬手機下載即可；Claude 的雲端環境連不到 Google Maven 無法編譯 |
| D13 | v0.2.0 起 release 用共用簽章 `hoonsor-release.jks`（GitHub Secrets），簽章檔移出 repo | 倬的標準；repo 是公開的，v0.1.0 提交的 `keystore/screentranslate.jks` 與寫死的密碼等於公開。原做法（專案簽章提交進 repo）已廢止 |
| D14 | `main` 每次推送都重建 `latest` 預先發行版 | 手機只要記一個固定網址就能拿到最新版 |
| D15 | 長按小圓點切換快速／漫畫模式，漫畫模式時小圓點右上角有琥珀色點 | 不用進設定頁就能換模式，且看得出目前模式 |
| D16 | 日文 OCR 時閱讀順序改為由右而左 | 日文漫畫的閱讀方向 |
| D17 | Repo 名稱 `APK-Photo` → **`APK-Translate`** | 倬的決定：`APK-Photo` 是保留給另一個相簿 App 的 |

### 放棄的方案（含原因）

| 方案 | 放棄原因 |
|---|---|
| xAI **Grok** API | 倬原本說 Grok，後來更正為 **Groq**；而且 xAI 的開發者 API 沒有免費額度 |
| MediaProjection 截圖 | 見 D1 |
| 前景服務＋`SYSTEM_ALERT_WINDOW` 浮動視窗 | 見 D2 |
| 逐句翻譯 | 見 D4 |
| 純視覺模型（不做 OCR） | 慢、座標不準、耗額度；保留為漫畫模式的混合版（D8） |
| 用 Android Studio 在倬的電腦編譯 | 倬選擇 GitHub Actions |
| `android-actions/setup-android@v3` | 在 GitHub runner 上執行失敗（完整 log 無法下載，確切錯誤不明）；runner 已內建 Android SDK，移除後一次編譯成功 |
| EncryptedSharedPreferences 存 API key | 第一版規劃時提過，實作時未採用，目前是一般 SharedPreferences（App 私有）。倬的標準是 Keystore AES-GCM，列入路線 |

## 6. 絕對不要重踩的坑

1. **Claude 雲端容器連不到** `dl.google.com`、`maven.google.com`、`repo.maven.apache.org`（proxy 403）：不要嘗試在容器內編譯，直接推 `ci-test` 交給 Actions。
2. **不要加回** `android-actions/setup-android@v3`：會失敗，runner 已有 `ANDROID_HOME`。
3. Claude 工作階段**無法下載 Actions log**（blob 網址 Forbidden）→ 看 annotation：`gh api repos/hoonsor/APK-Translate/check-runs/<job_id>/annotations`。不要刪掉 `Report build errors` 步驟。
4. Claude 工作階段**不能用 GitHub GraphQL**：`gh release view`、`gh pr view` 這類指令會 403，改用 REST：`gh api repos/hoonsor/APK-Translate/releases/tags/latest`。
5. **換簽章 = 必須先解除安裝**：解除安裝會清掉 App 內的 API key、設定與無障礙授權。v0.1.0 → v0.2.0 是最後一次（之後都用共用金鑰）；不要再換金鑰。
6. `takeScreenshot()` 有頻率限制（約每秒一次）：遇到 `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` 等 1 秒重試一次，不要迴圈重試。
7. 截圖前**一定要先隱藏小圓點**並等一個畫面週期（目前 110 ms），否則小圓點會被截進去並被 OCR。
8. 截圖回傳的是 Hardware Bitmap，ML Kit 與 JPEG 壓縮需要 **copy 成 ARGB_8888**；用完要 `recycle()`、`HardwareBuffer.close()`。
9. 設定 `FLAG_SECURE` 的畫面（銀行、部分影音 App）無法截圖，這是系統限制，不是 bug。
10. Android 13 起，**側載 App 的無障礙權限是「受限設定」**：要先到應用程式資訊 → ⋮ →「允許受限設定」。
11. **ColorOS 會清背景**：服務可能被停用、小圓點消失。電池要設「不限制」、允許自動啟動與背景活動、多工畫面鎖定。
12. MiniMax 可能 **HTTP 200 但 `base_resp.status_code` ≠ 0**；部分模型會在 content 裡夾 `<think>…</think>`；兩者 `LlmClient` 都已處理，修改時不要拿掉。
13. 有些供應商不支援 `response_format: json_object`：400 且訊息含 `response_format` 時自動去掉重試一次。
14. 疊加層的框座標是**螢幕座標**，畫的時候要扣掉 View 在螢幕上的位置（`getLocationOnScreen`）。
15. **模型會被下架**：預設模型 ID 失效時，設定頁「模型列表」呼叫 `GET /models` 讓倬重選，不要寫死。
16. **不可用多個 Groq 帳號繞額度**（Groq 限制是以組織計算，也違反使用條款）。
17. repo 沒有 `gradlew`：本機編譯要自行安裝 Gradle 8.11，或先產生 wrapper（產生後要一併提交）。
18. 程式碼放英文路徑；中文路徑會讓 Gradle 出錯。

## 7. 倬的偏好

- 一律**繁體中文**，技術名詞保留英文。討論與規劃要完整（含理由與替代方案）；完成回報要精簡，只講重點。
- 技術決策**穩定可靠優先**。
- 自用工具，不上架 Google Play。
- UI：深色、HSL 調校色票、不用純原色；按鈕要有邊框或足夠對比；間距一致；不接受廉價 MVP 感。每個設定都要有「測試」按鈕；做不到就直說原因。
- 版本用 SemVer `vX.Y.Z`；commit 用 Conventional Commits；每次工作後更新 `PROJECT_STATUS.md`。
- 新功能先實機確認；倬的回報會用編號，逐項回覆「已修／不修＋原因」。

## 8. Always／Ask／Never

**Always**
- 先推 `ci-test` 確認編譯成功，再推 `main`。
- 每次改版更新 `versionName`／`versionCode` 與 `PROJECT_STATUS.md`（版本歷程要寫「為什麼」）。
- 功能改變時更新 `README.md`、`docs/FEATURES.md`；動作改變時同步 `docs/AGENT_API.md` 與 `docs/agent-manifest.json`。
- 錯誤要顯示給使用者看（Toast 或設定頁文字），不可靜默失敗。
- 文件只寫相對路徑。

**Ask（先問倬）**
- 更換簽章、套件名、minSdk。
- 更換預設翻譯引擎或預設模型。
- 刪除功能或改變小圓點／疊加層的操作手勢。
- 新增會把畫面內容送到新服務的功能。
- 推 `vX.Y.Z` tag 建立正式 Release。

**Never**
- 不在程式碼、文件、commit 裡寫任何 API key 或密碼。
- 不讓 Agent API 讀出或設定 API key。
- 不在背景自動截圖；只在使用者點擊（或翻頁後自動翻譯）時截圖。
- 不用多個帳號繞過免費額度。
- 不 `force push` 到 `main`。
