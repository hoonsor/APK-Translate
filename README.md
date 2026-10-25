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
1. 用手機開啟本 repo 的 [Releases](https://github.com/hoonsor/APK-Translate/releases)，下載標記為 Latest 的正式版 `ScreenTranslate-vX.Y.Z.apk` 安裝（或用 Obtainium，見「手機端更新」）。
2. 開啟 App，依畫面指示：
   1. 「應用程式資訊」→ 右上角 ⋮ →「允許受限設定」
   2. 「開啟無障礙設定」→ 已下載的應用程式 →「螢幕翻譯」→ 開啟
3. 點「加入快速設定面板」。
4. 在「翻譯引擎」填入 API key，按「測試翻譯」確認可用。
5. ColorOS 建議：電池設為「不限制」、允許自動啟動與背景活動。

### 自行編譯
```bash
git clone https://github.com/hoonsor/APK-Translate.git
cd APK-Translate
./gradlew :app:testDebugUnitTest :app:assembleRelease   # 需要 Android SDK 與 JDK 17 或 21（不要用 JDK 25）
```
推送到 `main` 會由 GitHub Actions 自動編譯並更新 `latest` 預先發行版；推送 `vX.Y.Z` tag 會建立正式 Release，見「如何發新版」。

## 📲 首次設定（詳細步驟）

### 1. 下載與安裝
1. 手機瀏覽器打開 [Releases](https://github.com/hoonsor/APK-Translate/releases)，下載最新正式版 `ScreenTranslate-vX.Y.Z.apk`（想搶先測試才用 `latest` 測試版）。
2. 下載完成後點開安裝。若出現「禁止安裝未知應用程式」，依提示允許瀏覽器安裝。
3. 之後有新版，用 App 內更新或 Obtainium 即可，不用接線也不用解除安裝（設定會保留），見「手機端更新」。

### 2. 開啟無障礙服務（只需一次）
Android 13 起，自行安裝的 App 的無障礙權限屬於「受限設定」，要先解鎖：
1. 開啟「螢幕翻譯」→ 點 **應用程式資訊** → 右上角 **⋮** → **允許受限設定**（沒有這個選項就略過）。
2. 回到 App 點 **開啟無障礙設定** → **已下載的應用程式** → **螢幕翻譯（截圖與浮動按鈕）** → 開啟。
3. 回到 App，「無障礙服務已啟用」打勾，畫面右側會出現小圓點。

> 這個權限只用來「在你點小圓點時截圖」與「翻頁時轉發滑動」，App 不會讀取其他 App 的內容。

### 3. 加入快速設定面板
點 App 內 **加入快速設定面板** → 系統詢問時選「新增」。
如果沒有跳出詢問：下拉通知欄 → 編輯（鉛筆圖示）→ 把「螢幕翻譯」拖進面板。

### 4. 申請並填入 API key
至少要填一組。建議 **Groq 當主力、MiniMax 當備援與漫畫模式**。

| 引擎 | 申請位置 | 費用 | 用途 |
|---|---|---|---|
| Groq | console.groq.com → API Keys → Create API Key | 免費（每分鐘、每日次數有上限） | 快速模式主力，速度最快 |
| MiniMax | platform.minimax.io → API Keys | 依你的月訂方案 | 備援、漫畫模式（支援圖片） |
| Gemini | aistudio.google.com → Get API key | 免費（有次數上限；免費層內容可能被 Google 用於改進模型） | 備援或漫畫模式 |

在 App 的 **翻譯引擎 → 各引擎設定** 展開引擎 → 貼上 API key → 按 **測試翻譯**。
出現 `✓ 毫秒數` 與兩句中文就代表可用；出現 `✗` 會顯示錯誤原因。

### 5. OPPO／ColorOS 穩定性設定（很重要）
ColorOS 會主動關閉背景 App，可能讓小圓點消失。在 App 內點 **開啟應用程式資訊**，然後：
1. **電池**（或「耗電管理」）→ 選 **不限制**／關閉「最佳化電池使用」。
2. 開啟 **允許自動啟動**、**允許背景活動**；有「關聯啟動」也一併允許。
3. 打開多工畫面，把「螢幕翻譯」的卡片**往下拉鎖定**。

選單名稱可能因 ColorOS 版本略有不同。

## 📲 手機端更新（不用接線）

v0.2.0 起，正式版由 GitHub Actions 編譯、用共用金鑰 `hoonsor-release.jks` 簽章，發布在 [Releases](https://github.com/hoonsor/APK-Translate/releases)。手機有兩種更新方式，擇一或並用：

**App 內更新（建議）**
- 每天第一次開啟設定頁時在背景檢查一次；有新版會跳出版本號與更新說明，可選「下載並更新」「稍後」或「略過此版」。
- 設定頁最下方「App 更新」可隨時按「檢查更新」。
- 第一次更新時會引導你到「安裝不明應用程式」允許本 App，按返回鍵回到 App 後自動開始下載。
- 沒網路或 GitHub 無法連線時安靜略過，不影響翻譯。
- 更新完成後設定與 API key 都保留，無障礙服務會自動重新連上（小圓點沒出現時，到快速設定面板再開一次）。

**Obtainium 設定**
1. 在手機安裝 [Obtainium](https://github.com/ImranR98/Obtainium/releases)。
2. 「Add App」→ App Source URL 填 `https://github.com/hoonsor/APK-Translate`。
3. 保持「Include prereleases」關閉（`latest` 是每次推送 main 自動編譯的測試版，不要追它）。
4. 若同一個 Release 未來有多個 APK，在「Filter APKs by Regular Expression」填 `ScreenTranslate-v.*\.apk`。
5. 新增後 Obtainium 會定期檢查，有新版時通知並一鍵安裝。

**已裝過 v0.1.0 的手機（只需一次）**：v0.1.0 用的是專案自帶的舊簽章，v0.2.0 起換成共用金鑰，系統不允許直接覆蓋安裝。請先記下 API key → 解除安裝 → 從 Releases 安裝 v0.2.0 → 重新允許受限設定、開啟無障礙服務、填回 API key。之後的版本都能直接更新。

## 🚢 如何發新版

1. 修改 `app/build.gradle.kts` 的 `versionCode`（加 1）與 `versionName`（例如 `0.2.1`）。
2. 在 `PROJECT_STATUS.md` 的「版本歷程」表格加一列 `| v0.2.1 | 日期 | commit | 變更 | 為什麼 |`（同一版可以有多列）；這些列會自動成為 Release 的更新說明（也就是 App 內更新對話框顯示的內容）。
3. 提交並推送 main，接著推送同名 tag：

   ```bash
   git tag v0.2.1
   git push origin main v0.2.1
   ```

4. GitHub Actions 會跑單元測試、編譯 `ScreenTranslate-v0.2.1.apk` 並建立正式 Release。tag 與 `versionName` 不一致時 CI 會直接失敗，避免 App 一直以為有新版。

其他說明：
- 每次推送 main 都會更新名為 `latest` 的預先發行版（`ScreenTranslate-latest.apk`），給想搶先測試時手動下載；App 內更新與 Obtainium 預設都不會把它當成新版。
- 簽章 Secrets（`SIGNING_KEYSTORE_B64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`）已由倬寫入 repo。沒有 Secrets 時，推 tag 的 CI 會直接失敗（不會發出簽錯的正式版）；推 main 或 PR 則改用臨時金鑰簽章並顯示警告，這種 APK 無法覆蓋安裝，只能確認能編譯。
- 本機 `./gradlew :app:assembleRelease` 沒有設定 `SIGNING_KEYSTORE_PATH` 時會用 debug 簽章，只能自己測試。

## 🧭 功能使用說明

### 浮動小圓點
| 操作 | 效果 |
|---|---|
| **點一下** | 翻譯目前畫面（翻譯中再點一下＝取消） |
| **長按** | 切換「快速模式」／「漫畫模式」（漫畫模式時右上角有琥珀色點） |
| **拖曳** | 移動位置，放開會自動貼齊左右邊緣並記住位置 |
| 轉圈 | 翻譯中 |
| 變紅一下 | 失敗，會跳出原因 |

### 翻譯結果畫面
| 操作 | 效果 |
|---|---|
| **點空白處**（或再點小圓點） | 關閉翻譯 |
| **點某個翻譯框** | 隱藏那一框，看原文；再點一次恢復 |
| **按住任意處** | 暫時顯示全部原文，放開恢復 |
| **左右或上下滑動** | 關閉翻譯並把滑動交給底下的 App（例如漫畫翻頁），約 1 秒後**自動翻譯下一頁** |
| **雙指** | 關閉翻譯，讓你縮放底下的 App |
| 切到其他 App | 自動關閉翻譯 |

畫面底部的小標籤會顯示用了哪個引擎、花了幾秒。

### 快速設定面板開關
下拉通知欄點「螢幕翻譯」：開＝顯示小圓點，關＝隱藏。長按這個開關會打開 App 設定頁。
若顯示「需啟用服務」，代表無障礙服務被關掉了，點一下會帶你回設定頁。

### 兩種翻譯模式
- **快速模式（預設）**：手機本機辨識文字，只把文字送去翻譯。最快、最省額度，一般網頁和印刷字體的漫畫用這個。
- **漫畫模式**：把截圖和辨識結果一起送給能看圖的模型（MiniMax M3 或 Gemini），修正手寫字體的辨識錯誤，並補上漏抓的對話框。較準，但多 1～3 秒。失敗時會自動改用快速模式。

### 設定頁其他項目
- **原文語言**：英文／歐洲語言、日文、韓文。看日漫時要切成日文。
- **滑動翻頁後自動翻譯**：可關閉。
- **小圓點大小、不透明度、譯文字級上限**：拖動滑桿即時生效。
- **主要引擎／備援引擎／漫畫模式引擎**：主要引擎失敗、超時或額度用完時自動改用備援。
- **模型列表**：模型被下架時，按這裡從清單重新選一個。
- **還原預設**：把該引擎的網址、模型、參數恢復預設（不會清掉 API key）。

## 🎙 語音指令（規劃中）

> 目前版本**還不能用語音控制**。以下是預定給未來「AI 大腦 APP」呼叫的動作，完整規格見 `docs/AGENT_API.md`。

| 你說 | 動作 |
|---|---|
| 「翻譯這個畫面」「幫我翻一下」 | 翻譯目前畫面 |
| 「用漫畫模式翻譯」 | 以漫畫模式翻譯 |
| 「關掉翻譯」 | 關閉翻譯結果 |
| 「打開／隱藏翻譯按鈕」 | 顯示／隱藏小圓點 |
| 「切換成漫畫模式」「改回快速模式」 | 切換預設模式 |
| 「原文改成日文」 | 切換 OCR 語言 |
| 「翻譯引擎有哪些？」「現在用哪個引擎？」 | 查詢引擎狀態（不會讀出 API key） |
| 「主要引擎改用 MiniMax」 | 切換主要引擎 |
| 「測試 Groq 能不能用」 | 測試翻譯引擎 |
| 「把這句翻成中文：…」 | 翻譯一段文字 |
| 「匯出翻譯 App 的設定」 | 匯出備份（備份功能完成後） |

## 💾 備份與還原

**目前版本（v0.1.0）沒有備份功能**，也關閉了 Android 系統自動備份（`allowBackup=false`），所以：
- 覆蓋安裝新版：設定會保留。
- **解除安裝或換手機：所有設定與 API key 都會消失**，需要重新填寫。建議把 API key 另外記在密碼管理器。
- v0.2.0 已換成共用簽章：裝過 v0.1.0 的手機需要解除安裝一次（步驟見「手機端更新」），之後不必再解除安裝。

備份四件套（分享備份檔、儲存到檔案、從檔案匯入、匯入前自動備份可復原）排在 v0.4.0，進度見 `PROJECT_STATUS.md`。

## ❓ 常見問題

**小圓點不見了？**
多半是 ColorOS 把無障礙服務關掉了。打開 App 看「無障礙服務」狀態，重新開啟即可；並檢查上面第 5 步的穩定性設定。

**跳出「這個畫面禁止截圖」？**
該 App 設定了安全保護（常見於銀行、部分影音 App），系統不允許任何 App 截圖，無法翻譯。

**跳出「截圖太頻繁」？**
Android 限制截圖約每秒一次，稍等再點。

**翻譯失敗，顯示 HTTP 401／403？**
API key 錯誤或過期，重新貼上並按「測試翻譯」。

**翻譯失敗，顯示 HTTP 429？**
免費額度暫時用完。設好備援引擎，就會自動改用備援。

**顯示模型不存在？**
供應商下架了該模型。到該引擎按「模型列表」選一個新的。

**顯示「沒有偵測到需要翻譯的文字」？**
檢查「原文語言」是否正確；中文為主的畫面會被略過。手寫字體辨識不到時，可改用漫畫模式。

**翻譯框太擠、字太小？**
調高「譯文字級上限」；框放不下時會自動往下延伸。位置偏移請截圖回報。

**翻頁後沒有自動翻譯？**
確認「滑動翻頁後自動翻譯」已開啟。如果漫畫 App 翻頁動畫較慢，可能翻譯到上一頁，請回報，之後可加長等待時間。

**會不會一直把畫面傳出去？**
不會。只在你點小圓點（或翻頁後自動翻譯）時截圖。快速模式只送出辨識出的文字；漫畫模式會送出截圖。

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
gradlew、gradle/wrapper/                # Gradle Wrapper 8.11.1
```

## 🔄 最新更新 (Recent Updates)

- **v0.2.0**（2026/10/18）：免接線更新：改用共用簽章、GitHub Actions 發布正式 Release，設定頁新增「App 更新」（檢查、下載、安裝）；簽章檔移出 repo；加入 Gradle Wrapper 與單元測試。

- **2026/10/13**：補齊交接文件（AGENTS.md、docs/）與本使用說明，程式未變動。
- **v0.1.0**（2026/10/09）：第一版。浮動小圓點、快速設定開關、AccessibilityService 截圖、ML Kit OCR、Groq／MiniMax／Gemini 翻譯與自動備援、原位蓋字、漫畫模式、滑動翻頁後自動翻譯。
