# ANTIGRAVITY.md — AI 協作說明

給接手這個專案的 AI（Claude / Antigravity / Codex / DeepSeek Harness）。

## 專案目的
個人用 Android 螢幕翻譯工具，主要用途是閱讀英文漫畫。點浮動小圓點 → 截圖 → OCR → LLM 翻譯 → 原位蓋字。

## 關鍵設計決策（修改前請先理解）
1. **以 AccessibilityService 為核心**，不用 MediaProjection：Android 14+ 的 MediaProjection 每次都要使用者確認；無障礙服務一次授權即可截圖，也能用 `TYPE_ACCESSIBILITY_OVERLAY` 畫浮動視窗，且較不易被 ColorOS 清除。
2. **OCR 在本機（ML Kit），翻譯走雲端**：座標由 OCR 提供，比 LLM 回傳的座標精準；LLM 只負責文字。
3. **整頁一次翻譯**：所有區塊以 `{id,text}` JSON 一次送出，模型回 `{"t":[{"id","zh"}]}`，以保留劇情上下文。
4. **所有引擎走 OpenAI 相容介面**：新增供應商只要在 `ProviderId` 加預設值。
5. **固定簽章** `keystore/screentranslate.jks`：讓 CI 每次的 APK 可直接覆蓋安裝，不要更換。

## 建置
- 只靠 GitHub Actions 編譯（`.github/workflows/build.yml`），本機需 Android SDK + JDK 17。
- 版本號在 `app/build.gradle.kts` 的 `versionName`（SemVer），`versionCode` 同步遞增。

## 慣例
- 回覆與文件用繁體中文，技術名詞保留英文。
- Conventional Commits；每次工作後更新 `PROJECT_STATUS.md`。
