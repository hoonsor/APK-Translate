# AGENT_API — 螢幕翻譯 語音動作規格

> **狀態：全部「規劃中」**。v0.1.0 尚未實作 Hoonsor Agent API v1，預定 v0.5.0 實作（見 `PROJECT_STATUS.md`）。
> 機器可讀版本：`docs/agent-manifest.json`（每個動作都有 `"status": "planned"`）。實作後 `describe` 的回傳必須與該檔完全相同，並用單元測試比對。
> 動作清單要與 `docs/FEATURES.md` 的「語音」欄一致。

## 介面摘要（Hoonsor Agent API v1）

- Provider：`.agent.AgentProvider`，authorities `com.hoonsor.screentranslate.agent`，exported，permission `tw.hoonsor.permission.AGENT`（signature），intent-filter action `tw.hoonsor.agent.PROVIDER`。
- `ContentProvider.call()` 方法：`describe`、`execute`（extras `json`）、`health`；結果放在回傳 Bundle 的 `json`。
- 請求：`{api, requestId, action, params, dryRun, confirmToken, utterance}`
- 成功：`{ok:true, requestId, summary, data, undo:{action, params}}`
- 失敗：`{ok:false, error:{code, message}}`，code：`INVALID_PARAMS`、`NOT_FOUND`、`AMBIGUOUS`、`NEEDS_CONFIRMATION`、`NEEDS_UI`、`PERMISSION`、`INTERNAL`
- risk：`read` 直接執行；`reversible` 直接執行並回 `undo`；`destructive` 一律先回 `NEEDS_CONFIRMATION`。
- Provider 只解析與分派；動作要走 App 既有的邏輯（`Prefs`、`Translator`、`TranslatorAccessibilityService`），不另寫一套。
- 背景執行緒、逾時 5 秒。

### 本 App 特有的規則

1. **任何動作都不能讀出或設定 API key**（`settings_get`、`provider_list` 的回傳也不能包含 key）。
2. 需要無障礙服務的動作（`screen_translate`、`overlay_dismiss`、`bubble_set`）在服務未啟用時回 `PERMISSION`，`message` 說明要到無障礙設定開啟；或回 `NEEDS_UI`，`data.intentUri` 指向 App 設定頁。
3. **`screen_translate` 一定會超過 5 秒逾時**（截圖＋OCR＋雲端翻譯約 2～5 秒，再加上 `delayMs`）。規劃做法：`execute` 立即回傳 `ok:true`、`data.started:true`，翻譯結果照常顯示在疊加層；呼叫端若要讀譯文，之後用 `history_list` 取得。**設計待倬確認**。
4. `screen_translate` 截的是「當下最上層的畫面」。AI 大腦若有自己的介面浮在上面，要先收起，或用 `delayMs` 等待。
5. `provider_test`、`text_translate` 會消耗 API 額度，但不改變任何資料，所以列為 `read`。

### `health` 規劃回傳的 issues

`accessibility_disabled`（無障礙服務未啟用）、`bubble_hidden`、`no_provider_configured`（沒有任何引擎有 key）、`fallback_not_configured`、`vision_provider_not_configured`、`battery_optimized`（未豁免電池最佳化）。

---

## 動作一覽

| 動作 | 用途 | risk | 對應功能 | 狀態 |
|---|---|---|---|---|
| `screen_translate` | 翻譯目前畫面 | reversible | F03、F10 | 規劃中 |
| `overlay_dismiss` | 關閉翻譯結果 | reversible | F06 | 規劃中 |
| `bubble_set` | 顯示／隱藏小圓點 | reversible | F01、F02 | 規劃中 |
| `mode_set` | 切換快速／漫畫模式 | reversible | F10、F11 | 規劃中 |
| `ocr_language_set` | 設定原文語言 | reversible | F13 | 規劃中 |
| `settings_get` | 查詢目前設定 | read | F26 | 規劃中 |
| `provider_list` | 列出翻譯引擎 | read | F07 | 規劃中 |
| `provider_set` | 指定主要／備援／漫畫模式引擎 | reversible | F08 | 規劃中 |
| `provider_test` | 測試翻譯引擎 | read | F09 | 規劃中 |
| `text_translate` | 翻譯一段文字 | read | F21（建議，未確認） | 規劃中 |
| `history_list` | 列出最近的翻譯 | read | F22（待規劃） | 規劃中 |
| `backup_export` | 匯出設定備份 | read | F19 | 規劃中 |
| `backup_import` | 從備份匯入設定 | destructive | F19 | 規劃中 |

---

## 動作細節

### `screen_translate` — 翻譯目前畫面（規劃中）
- **risk**：reversible
- **參數**
  ```json
  {"type":"object","properties":{
    "mode":{"type":"string","enum":["fast","comic"]},
    "delayMs":{"type":"integer","minimum":0,"maximum":3000,"default":600}
  },"additionalProperties":false}
  ```
  `mode` 省略時用目前設定；只影響這一次，不改預設。
- **範例句**：「翻譯這個畫面」「幫我翻一下」「用漫畫模式翻譯這頁」
- **回傳**：`{ok:true, summary:"開始翻譯", data:{started:true, mode:"fast"}, undo:{action:"overlay_dismiss", params:{}}}`
- **錯誤**：`PERMISSION`（無障礙未啟用）、`INVALID_PARAMS`、`INTERNAL`（截圖失敗，例如安全畫面；`message` 用 App 內相同的中文說明）。翻譯本身的失敗會照常以 Toast 顯示在畫面上。

### `overlay_dismiss` — 關閉翻譯結果（規劃中）
- **risk**：reversible（關閉後可再呼叫 `screen_translate`）
- **參數**：`{}`
- **範例句**：「關掉翻譯」「把翻譯收起來」
- **回傳**：`{ok:true, summary:"已關閉翻譯", data:{wasShown:true}, undo:null}`
- **錯誤**：`PERMISSION`

### `bubble_set` — 顯示或隱藏浮動小圓點（規劃中）
- **risk**：reversible
- **參數**：`{"type":"object","properties":{"visible":{"type":"boolean"}},"required":["visible"],"additionalProperties":false}`
- **範例句**：「打開翻譯按鈕」「把翻譯小圓點藏起來」
- **回傳**：`{ok:true, summary:"已顯示小圓點", data:{visible:true}, undo:{action:"bubble_set", params:{visible:<原本的值>}}}`
- **錯誤**：`PERMISSION`、`INVALID_PARAMS`

### `mode_set` — 切換翻譯模式（規劃中）
- **risk**：reversible
- **參數**：`{"type":"object","properties":{"mode":{"type":"string","enum":["fast","comic"]}},"required":["mode"],"additionalProperties":false}`
- **範例句**：「切換成漫畫模式」「改回快速模式」
- **回傳**：`{ok:true, summary:"已切換為漫畫模式", data:{mode:"comic"}, undo:{action:"mode_set", params:{mode:"fast"}}}`
- **錯誤**：`INVALID_PARAMS`
- 若漫畫模式引擎沒有 key，仍可切換，但 `summary` 要提醒「漫畫模式引擎尚未設定，會改用快速模式」。

### `ocr_language_set` — 設定原文語言（規劃中）
- **risk**：reversible
- **參數**：`{"type":"object","properties":{"language":{"type":"string","enum":["latin","japanese","korean"]}},"required":["language"],"additionalProperties":false}`
- **範例句**：「原文改成日文」「我要看韓文漫畫」「改回英文」
- **回傳**：`{ok:true, summary:"原文語言改為日文", data:{language:"japanese"}, undo:{action:"ocr_language_set", params:{language:"latin"}}}`
- **錯誤**：`INVALID_PARAMS`

### `settings_get` — 查詢目前設定（規劃中）
- **risk**：read
- **參數**：`{}`
- **範例句**：「現在是什麼翻譯模式？」「翻譯按鈕有開嗎？」
- **回傳**：`{ok:true, summary:"快速模式、英文、小圓點顯示中", data:{mode:"fast", ocrLanguage:"latin", bubbleVisible:true, serviceEnabled:true, autoAfterSwipe:true}}`（不含 key）

### `provider_list` — 列出翻譯引擎（規劃中）
- **risk**：read
- **參數**：`{}`
- **範例句**：「翻譯引擎有哪些？」「現在用哪個引擎翻譯？」
- **回傳**：
  ```json
  {"ok":true,"summary":"主要 Groq、備援 MiniMax、漫畫模式 MiniMax","data":{
    "roles":{"primary":"groq","fallback":"minimax","vision":"minimax"},
    "providers":[{"id":"groq","label":"Groq","configured":true,"model":"openai/gpt-oss-120b"}]
  }}
  ```
  `configured` 只表示有填 key，不回傳 key 本身。

### `provider_set` — 指定引擎用途（規劃中）
- **risk**：reversible
- **參數**：
  ```json
  {"type":"object","properties":{
    "role":{"type":"string","enum":["primary","fallback","vision"]},
    "provider":{"type":"string","enum":["groq","minimax","gemini","custom","none"]}
  },"required":["role","provider"],"additionalProperties":false}
  ```
- **範例句**：「主要引擎改用 MiniMax」「備援改成 Gemini」「漫畫模式用 Gemini」
- **回傳**：`{ok:true, summary:"主要引擎改為 MiniMax", data:{role:"primary", provider:"minimax"}, undo:{action:"provider_set", params:{role:"primary", provider:"groq"}}}`
- **錯誤**：`INVALID_PARAMS`（`none` 用在 fallback 以外）；指定的引擎沒有 key 時仍可設定，但 `summary` 要提醒。

### `provider_test` — 測試翻譯引擎（規劃中）
- **risk**：read（會消耗一次 API 額度）
- **參數**：`{"type":"object","properties":{"provider":{"type":"string","enum":["groq","minimax","gemini","custom"]}},"required":["provider"],"additionalProperties":false}`
- **範例句**：「測試 Groq 能不能用」「MiniMax 還正常嗎？」
- **回傳**：`{ok:true, summary:"Groq 正常，1240 毫秒", data:{elapsedMs:1240, sample:"等等！你不能就這樣把我丟在這裡！"}}`
- **錯誤**：`NOT_FOUND`（該引擎沒有 key）、`INTERNAL`（`message` 帶 HTTP 狀態與供應商的錯誤訊息）

### `text_translate` — 翻譯一段文字（規劃中，功能本身為建議未確認）
- **risk**：read
- **參數**：`{"type":"object","properties":{"text":{"type":"string","minLength":1,"maxLength":4000}},"required":["text"],"additionalProperties":false}`
- **範例句**：「把這句翻成中文：I'll be right back」「翻譯 hold your horses」
- **回傳**：`{ok:true, summary:"我馬上回來", data:{zh:"我馬上回來", provider:"groq"}}`
- **錯誤**：`INVALID_PARAMS`、`INTERNAL`（所有引擎都失敗）

### `history_list` — 列出最近的翻譯（規劃中，依賴 F22 譯文歷史）
- **risk**：read
- **參數**：`{"type":"object","properties":{"limit":{"type":"integer","minimum":1,"maximum":50,"default":5}},"additionalProperties":false}`
- **範例句**：「剛剛翻了什麼？」「上一頁的翻譯再唸一次」
- **回傳**：`{ok:true, summary:"最近 1 次翻譯，共 6 段", data:{items:[{time:"2026/10/13 21:30", provider:"groq", segments:[{source:"Watch me.", zh:"你看著吧。"}]}]}}`

### `backup_export` — 匯出設定備份（規劃中，依賴 F19）
- **risk**：read（不改變 App 內資料）
- **參數**：`{}`
- **範例句**：「匯出翻譯 App 的設定」「備份螢幕翻譯」
- **回傳**：`{ok:true, summary:"已匯出備份", data:{fileName:"ScreenTranslate-backup-20261013-2130.zip"}}`；需要使用者選位置時回 `NEEDS_UI`。
- 備份是否包含 API key：**待倬確認**。

### `backup_import` — 從備份匯入設定（規劃中，依賴 F19）
- **risk**：destructive（第一次呼叫一律回 `NEEDS_CONFIRMATION`，`data.confirmToken`；帶 token 再呼叫才執行）
- **參數**：
  ```json
  {"type":"object","properties":{
    "uri":{"type":"string"},
    "mode":{"type":"string","enum":["merge","replace"],"default":"merge"}
  },"required":["uri"],"additionalProperties":false}
  ```
- **範例句**：「從備份還原翻譯設定」
- **回傳**：`{ok:true, summary:"已匯入設定（合併）", data:{restoredKeys:12}, undo:{action:"backup_import", params:{uri:"<匯入前自動備份的 URI>", mode:"replace"}}}`
- **錯誤**：`NEEDS_CONFIRMATION`、`NOT_FOUND`（檔案不存在）、`INVALID_PARAMS`（格式或 schema 不符）

## 刻意不開放的動作

| 動作 | 原因 |
|---|---|
| 設定或讀取 API key | 安全；key 只能在 App 設定頁手動輸入 |
| 修改 Base URL、模型 ID、額外參數 | 填錯會讓翻譯失效，且口語不易精確表達 |
| 調整小圓點大小、透明度、字級 | 需要看著畫面調整，語音操作沒有意義 |
| 持續／定時自動翻譯 | 隱私與額度考量（見 FEATURES「不做的項目」） |
