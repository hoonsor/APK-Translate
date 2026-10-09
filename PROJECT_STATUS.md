# PROJECT_STATUS — 螢幕翻譯 ScreenTranslate

- **目前版本**：v0.1.0
- **狀態**：已編譯發佈，待實機測試
- **目標機型**：OPPO Reno 14F（ColorOS）
- **最後更新**：2026/10/09 13:15

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

## 版本歷程
- **v0.1.0**（2026/10/09）：第一版。
