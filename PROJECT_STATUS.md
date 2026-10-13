# PROJECT_STATUS — 螢幕翻譯 ScreenTranslate

- **目前版本**：v0.1.0
- **狀態**：已編譯發佈，**尚未安裝、尚未實機測試**（2026/10/13 倬確認）
- **目標機型**：OPPO Reno 14F（ColorOS）
- **最後更新**：2026/10/13 21:40（補齊交接文件）
- **下載**：`https://github.com/hoonsor/APK-Translate/releases/download/latest/ScreenTranslate-latest.apk`
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

1. **簽章檔與密碼在 repo 裡**（`keystore/screentranslate.jks`、`app/build.gradle.kts`）。違反倬的共用簽章標準；repo 若公開等於簽章公開。
2. **API key 未加密**：存在一般 SharedPreferences（App 私有、`allowBackup=false`），未用 Keystore AES-GCM。
3. 尚未實作標準必備項目：Hoonsor Agent API v1、備份四件套、健康檢查頁、LAB 版、JVM 單元測試。
4. AI 預設值與倬的標準不同：標準是主力 MiniMax M3（`reasoning_split: true`）、備援 Groq、temperature 0.2；目前是主力 Groq（0.3）、MiniMax 備援（1.0）。原因見 AGENTS.md D6、D7；是否對齊原因待倬確認。
5. APK 約 86 MB（內建英、日、韓 OCR 模型）。
6. repo 沒有 Gradle Wrapper；依賴版本未集中到 `libs.versions.toml`；`versionCode` 未由版號推導。
7. GitHub Actions 警告：部分 action 使用已棄用的 Node.js 20；`ubuntu-latest` 自 2026/10/19 起改為 Ubuntu 26，可能影響編譯，之後第一次編譯要留意。
8. 日文直排的碎片合併以橫排邏輯為主，直排漫畫效果未知。
9. 漫畫模式沒有要求 `response_format`，依賴容錯 JSON 解析；視覺模型若回傳非 JSON 會退回快速模式。
10. Groq 預設模型 `openai/gpt-oss-120b` 的翻譯品質未實測。

## 下一步（路線）

依序進行，每版一個主題：

| 版本 | 主題 | 內容 | 為什麼排這個順序 |
|---|---|---|---|
| v0.1.x | 實機修正 | 倬裝機測試，依「待實機驗證」回報逐項修正 | 先確認核心流程可用 |
| v0.2.0 | 建置標準化 | 改用共用簽章 hoonsor-release.jks（GitHub Secrets，倬需把 repo 加進 `setup-hoonsor-signing.ps1` 清單）、移除 repo 內簽章、LAB 版（`.lab`）、`libs.versions.toml`、`versionCode` 由版號推導、Gradle Wrapper | 換簽章必須解除安裝一次，越早換，要重設的資料越少 |
| v0.3.0 | 安全與穩定 | API key 改 Keystore AES-GCM；健康檢查頁（無障礙、受限設定、電池最佳化、ColorOS 自啟動／關聯啟動、立即測試）；AI 預設值是否對齊標準（先問倬） | 實機發現 ColorOS 問題時，有健康檢查頁才好引導 |
| v0.4.0 | 備份 | 設定匯出／匯入 zip（分享、SAF 儲存、匯入合併／取代、匯入前自動備份、可復原）；API key 是否納入備份（先問倬） | Agent API 的 `backup_*` 動作依賴它 |
| v0.5.0 | Agent API v1 | `describe`／`execute`／`health`、API 測試台；動作依 `docs/AGENT_API.md` | 讓未來的「AI 大腦 APP」能用語音呼叫 |
| 之後 | 功能 | 譯文歷史（可複製）、選取區域翻譯、APK 瘦身（OCR 語言可選）、純邏輯 JVM 測試（OCR 合併、JSON 解析） | 依倬實際使用需求排序 |

## 版本歷程
- **v0.1.0**（2026/10/09）：第一版。

| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
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
