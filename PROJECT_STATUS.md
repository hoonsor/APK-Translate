# PROJECT_STATUS — 螢幕翻譯 ScreenTranslate

- **目前版本**：v0.3.1（main／latest 測試版；正式 tag 待倬同意）
- **狀態**：倬已安裝試用（2026/11/09 回報）：翻譯一次約 8～15 秒，太慢。v0.3.0 加入診斷紀錄，等倬匯出紀錄後分析
- **目標機型**：OPPO Reno 14F（ColorOS）
- **最後更新**：2026/11/09（診斷紀錄）
- **下載**：正式版 [Releases](https://github.com/hoonsor/APK-Translate/releases)（`ScreenTranslate-vX.Y.Z.apk`）；測試版 `https://github.com/hoonsor/APK-Translate/releases/download/latest/ScreenTranslate-latest.apk`

## 發布與手機端更新
- 發新版：改 `app/build.gradle.kts` 的 `versionCode`／`versionName` → 在「版本歷程」表格加 `| vX.Y.Z | … |` 列（會成為 Release 更新說明）→ 推送 main 與同名 `vX.Y.Z` tag。CI 會檢查 tag 與 versionName 一致、跑單元測試、編譯並發布 `ScreenTranslate-vX.Y.Z.apk`。
- 推送 main 會自動更新 `latest` 預先發行版（測試用，App 內更新與 Obtainium 不會追它）。
- 手機端：App 內更新（每天第一次開啟自動檢查、設定頁「App 更新」手動檢查）或 Obtainium（來源 `https://github.com/hoonsor/APK-Translate`，不要勾 prereleases）。詳細步驟見 README「手機端更新」。
- 簽章：共用金鑰 `hoonsor-release.jks`，Secrets 已寫入。憑證 SHA-256 `514ec00eea070dd3e67b4f6a0c722c9c779497bb0797e7a3446fcca4e4548825`。
- 裝過 v0.1.0 的手機：記下 API key → 解除安裝 → 安裝 v0.2.0 → 重設（只需一次）。
- **接手必讀**：`AGENTS.md`（架構、決策、踩坑、規則）

## 任務

### v0.1.0
- [x] 專案骨架、固定簽章、GitHub Actions 自動編譯與 latest 發佈
- [x] AccessibilityService 截圖（免每次確認）
- [x] 浮動小圓點：拖曳、貼邊、忙碌動畫、長按切換模式
- [x] 快速設定面板開關（Android 13+ 可一鍵加入）
- [x] ML Kit OCR（英／日／韓），對話框碎片合併、閱讀順序排序
- [x] OpenAI 相容翻譯（Groq / MiniMax / Gemini / 自訂），主要＋備援
- [x] 整頁上下文翻譯提示詞、JSON 容錯解析、快取
- [x] 原位蓋字疊加層：點框看原文、按住看全部原文、點空白關閉
- [x] 漫畫模式：截圖＋OCR 混合送視覺模型
- [x] 滑動翻頁轉發，翻頁後自動翻譯
- [x] 設定頁：權限引導、ColorOS 提示、引擎設定、測試翻譯、模型列表
- [x] GitHub Actions 首次編譯通過（APK 約 86 MB）
- [ ] Reno 14F 實機測試：截圖、蓋字座標、ColorOS 背景存活
- [ ] 確認 MiniMax 月訂方案 key 可呼叫 chat completions 與圖片輸入

### v0.2.0（建置標準化，部分）
- [x] 共用簽章 hoonsor-release.jks（GitHub Secrets）、移除 repo 內簽章（2026/10/18）
- [x] GitHub Actions：tag 發正式 Release、main 發 latest、單元測試、tag 與 versionName 檢查（2026/10/18）
- [x] App 內更新（檢查、下載、PackageInstaller 安裝）、Gradle Wrapper（2026/10/18）
- [ ] LAB 版（`.lab`）、`libs.versions.toml`、`versionCode` 由版號推導

### v0.3.0（診斷紀錄）
- [x] 每次翻譯記錄分段耗時、引擎呼叫（網路分段、伺服器處理、tokens、限流標頭）、錯誤（2026/11/09）
- [x] 檢測所有引擎：模型列表、3 次文字翻譯、圖片輸入（2026/11/09）
- [x] 匯出：分享、存檔、複製摘要；清除紀錄；結果標籤顯示分段耗時（2026/11/09）
- [x] 單元測試：報告統計、百分位數、遮蔽 key、RunTrace（2026/11/09）
- [x] 倬實機匯出紀錄（2026/11/09）：慢在漫畫模式的 MiniMax（9.7 秒沒譯文、60 秒逾時），Groq 約 1 秒

### v0.3.1（漫畫模式先快後準）
- [x] 漫畫模式「先快後準」：快速模式與視覺模型同時送，快速譯文先顯示，視覺模型在時限內回來再換（2026/11/09）
- [x] 視覺模型等待上限（預設 15 秒，5～60 可調），逾時真的中斷連線（OkHttp 改 enqueue＋取消）（2026/11/09）
- [x] 關閉／翻頁時取消精修；漫畫模式結果快取（2026/11/09）
- [x] 連線預熱：小圓點出現、切換模式、點擊時先建連線（30 秒內有活動就不送）（2026/11/09）
- [x] 漫畫模式解析失敗時記結構摘要（comic_parse）；報告新增「看到第一個譯文」與漫畫模式成功／逾時統計（2026/11/09）
- [ ] 倬實機驗證：漫畫模式第一個譯文約 1.5～2 秒、精修是否換上

### 待規劃
- [ ] 依實測調整對話框合併參數與字級
- [ ] 譯文歷史紀錄（可複製）
- [ ] 選取區域翻譯（只翻部分畫面）

### 文件
- [x] AGENTS.md、CLAUDE.md、docs/（FEATURES、DESIGN、AGENT_API、agent-manifest.json）、README 使用說明（2026/10/13）

## 待實機驗證（Reno 14F）

v0.1.0 全部項目都還沒實機確認：

- [ ] 安裝：允許受限設定 → 開啟無障礙服務
- [ ] 快速設定面板：一鍵加入、開關小圓點、狀態正確
- [ ] 小圓點：拖曳、貼邊、記住位置、大小與透明度設定即時生效
- [ ] 截圖：小圓點不會被截進去；`FLAG_SECURE` 畫面有正確提示
- [ ] OCR：英文漫畫手寫體的辨識率、對話框碎片是否正確合併
- [ ] 翻譯框位置是否對齊原文（含瀏海／狀態列、全螢幕漫畫 App）
- [ ] 翻譯速度（Groq 快速模式目標約 2～3 秒）與譯文品質
- [ ] 點框看原文、按住看全部原文、點空白關閉
- [ ] 滑動翻頁轉發是否被漫畫 App 正確接收；自動翻譯下一頁的等待時間（預設 900 ms）是否合適
- [ ] 長按切換漫畫模式；MiniMax M3 圖片輸入是否可用
- [ ] MiniMax 月訂方案的 key 能否呼叫 `/chat/completions`
- [ ] 主要引擎失敗時自動改用備援
- [ ] ColorOS：鎖屏、閒置數小時、清除多工後，服務與小圓點是否存活

## 已知問題

1. ~~簽章檔與密碼在 repo 裡~~：v0.2.0 已改用共用簽章、檔案移出 repo。舊的 `screentranslate.jks` 仍在 git 歷史中（repo 公開），已不再使用。
2. **API key 未加密**：存在一般 SharedPreferences（App 私有、`allowBackup=false`），未用 Keystore AES-GCM。
3. 尚未實作標準必備項目：Hoonsor Agent API v1、備份四件套、健康檢查頁、LAB 版、JVM 單元測試。
4. AI 預設值與倬的標準不同：標準是主力 MiniMax M3（`reasoning_split: true`）、備援 Groq、temperature 0.2；目前是主力 Groq（0.3）、MiniMax 備援（1.0）。原因見 AGENTS.md D6、D7；是否對齊原因待倬確認。
5. APK 約 86 MB（內建英、日、韓 OCR 模型）。
6. 依賴版本未集中到 `libs.versions.toml`；`versionCode` 未由版號推導（Gradle Wrapper 已於 v0.2.0 加入）。
7. GitHub Actions 警告：部分 action 使用已棄用的 Node.js 20；`ubuntu-latest` 自 2026/10/19 起改為 Ubuntu 26，可能影響編譯，之後第一次編譯要留意。
8. 日文直排的碎片合併以橫排邏輯為主，直排漫畫效果未知。
9. 漫畫模式沒有要求 `response_format`，依賴容錯 JSON 解析；視覺模型若回傳非 JSON 會退回快速模式。
10. Groq 預設模型 `openai/gpt-oss-120b` 的翻譯品質未實測。

## 下一步（路線）

依序進行，每版一個主題：

| 版本 | 主題 | 內容 | 為什麼排這個順序 |
|---|---|---|---|
| v0.1.x | 實機修正 | 倬裝機測試，依「待實機驗證」回報逐項修正 | 先確認核心流程可用 |
| v0.2.0 | 建置標準化 | ✅ 共用簽章、移除 repo 內簽章、Gradle Wrapper、App 內更新（2026/10/18）；剩 LAB 版（`.lab`）、`libs.versions.toml`、`versionCode` 由版號推導 | 換簽章必須解除安裝一次，越早換，要重設的資料越少 |
| v0.3.0 | 診斷紀錄 | ✅ 分段計時、引擎呼叫紀錄、檢測所有引擎、匯出（2026/11/09） | 倬實測翻譯 8～15 秒，要先有數據才知道慢在哪 |
| v0.3.1 | 漫畫模式先快後準 | ✅ 快速譯文先顯示、視覺模型時限、連線預熱、解析失敗診斷（2026/11/09） | 實測慢的全是漫畫模式的 MiniMax |
| v0.3.x | 加速（續） | 依新紀錄決定：MiniMax 為何沒譯文（改提示詞或換視覺模型，換預設模型先問倬）、Groq `reasoning_effort` 再調低、429 立即換備援、串流顯示 | 先看數據再改 |
| v0.4.0 | 安全與穩定 | API key 改 Keystore AES-GCM；健康檢查頁（無障礙、受限設定、電池最佳化、ColorOS 自啟動／關聯啟動、立即測試）；AI 預設值是否對齊標準（先問倬） | 實機發現 ColorOS 問題時，有健康檢查頁才好引導 |
| v0.5.0 | 備份 | 設定匯出／匯入 zip（分享、SAF 儲存、匯入合併／取代、匯入前自動備份、可復原）；API key 是否納入備份（先問倬） | Agent API 的 `backup_*` 動作依賴它 |
| v0.6.0 | Agent API v1 | `describe`／`execute`／`health`、API 測試台；動作依 `docs/AGENT_API.md` | 讓未來的「AI 大腦 APP」能用語音呼叫 |
| 之後 | 功能 | 譯文歷史（可複製）、選取區域翻譯、APK 瘦身（OCR 語言可選）、純邏輯 JVM 測試（OCR 合併、JSON 解析） | 依倬實際使用需求排序 |

## 版本歷程
- **v0.1.0**（2026/10/09）：第一版。

| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
| v0.3.1 | 2026/11/09 | （本次） | 漫畫模式先快後準：快速譯文先顯示、視覺模型 15 秒時限（可調）、關閉即取消、結果快取；連線預熱；解析失敗診斷 | 診斷紀錄顯示漫畫模式等 MiniMax 9.7～60 秒且沒拿到譯文，Groq 只要約 1 秒 |
| v0.3.0 | 2026/11/09 | `7c2ae34` | 新增診斷紀錄：每次翻譯的分段耗時、引擎呼叫、錯誤、tokens；「檢測所有引擎」；分享／存檔／複製摘要；結果標籤顯示分段耗時 | 倬實測一次翻譯要 8～15 秒，要一次匯出完整資訊給 AI 判斷慢在哪 |
| v0.2.0 | 2026/10/18 | （本次） | 改用共用簽章 hoonsor-release.jks，簽章檔與密碼移出 repo；GitHub Actions 推 tag 發布正式 Release | 倬的共用簽章標準；repo 公開不能放簽章；手機要能不接線更新 |
| v0.2.0 | 2026/10/18 | （本次） | 設定頁新增「App 更新」：每天自動檢查 GitHub 新版，確認後下載並交給系統安裝 | 手機不接線就能更新，設定與 API key 保留 |
| v0.1.0 | 2026/10/09 12:45 | `b3652a4` | 第一版：小圓點、快速設定開關、無障礙截圖、ML Kit OCR、Groq／MiniMax／Gemini 翻譯與備援、原位蓋字、漫畫模式、滑動翻頁自動翻譯 | 倬要用點一下的方式翻譯手機畫面（看英文漫畫），取代需要長按指示條的 Gemini |
| v0.1.0 | 2026/10/09 12:52 | `bf02f57` | README 改用新 repo 名稱 | 倬把 repo 從 APK-Photo 改名為 APK-Translate（APK-Photo 保留給相簿 App） |
| v0.1.0 | 2026/10/09 13:02 | `b636c61` | CI 增加 `ci-test` 分支觸發 | 先在測試分支確認能編譯，再推 main |
| v0.1.0 | 2026/10/09 13:03 | `b8cb189` | 移除 `setup-android`、改 checkout／setup-java v5、失敗時寫 annotation | 首次編譯在 setup-android 步驟失敗；Claude 無法下載 log，需要 annotation 才看得到錯誤 |
| v0.1.0 | 2026/10/09 13:10 | `68c83d2` | 標記首次編譯通過 | 進度紀錄 |
| v0.1.0 | 2026/10/13 | （本次） | 補齊交接文件與使用說明（不改程式） | 讓其他 AI 不看對話紀錄也能接手，並為 AI 大腦 APP 規劃語音動作 |

## 交接紀錄

### 2026/10/09 — Claude 雲端工作階段（非 A／B 電腦）
- **完成**：v0.1.0 全部程式、GitHub Actions、README、PROJECT_STATUS；首次 CI 編譯成功並發佈 `latest`。
- **決定＋原因**：見 AGENTS.md 第 5 節 D1～D17。
- **放棄＋原因**：Grok（倬更正為 Groq，且 xAI API 無免費額度）、MediaProjection（Android 14 每次確認）、前景服務＋Overlay 權限（ColorOS 易被清除）、逐句翻譯（失去上下文）、Android Studio 編譯（倬選 GitHub Actions）、`setup-android` action（runner 上失敗）。
- **未完成**：實機測試、MiniMax key 相容性確認。
- **下一步**：倬安裝後依「待實機驗證」回報。
- **環境注意**：雲端容器連不到 Google Maven，不能本機編譯；Claude 讀不到 Actions log，要看 annotation；GraphQL 不可用，改用 REST。

### 2026/10/13 — Claude 雲端工作階段（補文件模式）
- **完成**：新增 AGENTS.md、CLAUDE.md、docs/FEATURES.md、docs/DESIGN.md、docs/AGENT_API.md、docs/agent-manifest.json；ANTIGRAVITY.md 併入 AGENTS.md；補 README 使用說明與本檔。
- **決定＋原因**：不改程式，只補文件（倬要求）；程式與標準的落差排成 v0.2.0～v0.5.0 路線（倬選擇）；FEATURES／DESIGN 一併建立（倬選擇）。
- **放棄＋原因**：無。
- **未完成**：所有功能的「倬確認」欄位待倬確認；APP-REGISTRY.md 那一列由倬自行更新（Claude 無法存取該 Project）。
- **下一步**：倬安裝實機測試 → v0.1.x 修正 → v0.2.0 建置標準化。
- **環境注意**：同上。

### 2026/10/18 — 電腦 A（家裡）
- **完成**：v0.2.0 免接線更新：共用簽章（環境變數）、`keystore/` 移到本機 `_trash/` 並加入 .gitignore、Gradle Wrapper 8.11.1、App 內更新（移植自鬧鐘 APP）、CI 改為 tag 發正式 Release＋單元測試；本機分支由 `ci-test` 切到 `main`（倬同意）。
- **決定＋原因**：CI 不再觸發 `ci-test` 分支（本機可以編譯，不需要先在測試分支試）；更新說明取自版本歷程表該版本的所有列。
- **放棄＋原因**：重寫 git 歷史刪掉舊簽章（舊簽章不再使用，重寫歷史風險高）。
- **未完成**：推 v0.2.0 tag、實機安裝。
- **下一步**：倬安裝 v0.2.0 實機測試（裝過 v0.1.0 要先解除安裝）。
- **環境注意**：本機建置用 `C:/Users/hoonsor/.jdks/jbr-21.0.11`；`local.properties` 的 `sdk.dir` 要用正斜線。

### 2026/10/25 — 電腦未確認（DESKTOP-5DM4EDT）
- **完成**：核對 v0.2.0 Release APK 憑證 SHA-256 `514ec00e…e4548825`，與共用金鑰一致；`build.yml` 移植 APK-Photo 的簽章保護：沒有 Secrets 時推 tag 直接失敗，其他編譯改用臨時金鑰（取代原本退回 debug 簽章）；AGENTS.md、README 同步說明。
- **決定＋原因**：tag 沒有 Secrets 就擋下，避免發出無法覆蓋安裝的正式版（倬要求，比照 APK-Photo）。
- **放棄＋原因**：無。
- **未完成**：實機安裝 v0.2.0（同上一筆）。
- **下一步**：倬安裝 v0.2.0 實機測試。
- **環境注意**：同上。

### 2026/11/09 — 電腦 A（家裡）
- **完成**：v0.3.0 診斷紀錄（`diag/` 套件）：RunTrace 分段計時、LlmClient 以 OkHttp EventListener 記 DNS／連線／TLS／上傳／伺服器處理、usage 與限流標頭；設定頁「診斷紀錄」卡片（開關、包含原文開關、檢測所有引擎、分享、存檔、複製摘要、清除）；結果標籤顯示分段耗時；6 個新單元測試；本機 `testDebugUnitTest`＋`assembleRelease` 通過。
- **決定＋原因**：先做紀錄、不先改加速邏輯（倬要先匯出數據再判斷）；預設記錄開啟、原文預設不記（畫面可能有個資）；紀錄存 App 私有目錄、1 MB 輪替；版號用 v0.3.0，原 v0.3.0～v0.5.0 路線往後順延一版。
- **放棄＋原因**：用 logcat 匯出（需要 adb 或 READ_LOGS 權限，倬不接線）；記錄每次請求的完整內容（太大，且含畫面文字）。
- **未完成**：實機驗證；推 `v0.3.0` 正式 tag（要先問倬）。
- **下一步**：倬安裝 latest 測試版 → 使用 10 次以上＋檢測所有引擎 → 分享紀錄給 Claude → 依數據做 v0.3.x 加速。
- **環境注意**：新增權限 `ACCESS_NETWORK_STATE`（一般權限，不用使用者授權）、FileProvider authority `com.hoonsor.screentranslate.diagfiles`。

### 2026/11/09 晚 — 電腦 A（家裡）
- **完成**：分析倬的診斷紀錄：2 次漫畫模式翻譯 11.2 秒、61.7 秒，全耗在 MiniMax-M3.1-Flash（伺服器處理 8.7 秒、427 推理 tokens，回 72 字但解析不出譯文；第二次 60 秒逾時）；Groq qwen3.8-27b 4 次全成功、約 0.7～1.8 秒；VPN 下新連線多約 1 秒。依此做 v0.3.1：`Translator.comicFastFirst`（快速與視覺同時送、`onEarly` 先顯示、`keepsEarly` 表示保留快速譯文）、`comicWithLimit`（`withTimeout`）、`LlmClient` 改 `enqueue`＋`invokeOnCancellation` 讓逾時真的斷線、`LlmClient.prewarm`、服務端 `awaitingUpgrade`（使用者關閉就取消精修）、`comic_parse` 診斷、報告新統計；設定頁漫畫模式多「先顯示快速譯文」開關與「視覺模型等待上限」滑桿；新增 1 個單元測試，本機 `testDebugUnitTest`＋`assembleRelease` 通過。
- **決定＋原因**：預設開啟先快後準、時限 15 秒（MiniMax 成功那次 9.7 秒，15 秒留餘裕又不會卡 60 秒）；預熱打不帶 key 的 `GET /models`（只為建連線，不送內容）；精修中小圓點持續轉圈，不另做 UI；不改預設模型與 `reasoning_effort`（要先問倬）。
- **放棄＋原因**：只縮短 readTimeout（還是要等，且沒有先顯示）；漫畫模式改送縮小圖片（瓶頸是伺服器推理 8.7 秒，不是上傳 0.12 秒）。
- **未完成**：倬實機驗證；MiniMax 為何沒譯文（第二份紀錄雖已開啟「包含原文」，但之後沒有新翻譯，需要再翻一頁漫畫）；推 `v0.3.0`／`v0.3.1` 正式 tag（要先問倬）。
- **下一步**：倬裝 latest → 漫畫模式翻 3～5 頁英文漫畫 → 匯出紀錄 → 看 `comic_parse` 與 `reply` 決定改提示詞或換視覺模型。
- **環境注意**：新設定鍵 `comic_fast_first`、`comic_timeout_sec`（SharedPreferences，無需遷移）。
