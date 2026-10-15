## v1.0.4
- Candidate ranking and UI preview fixes.
- versionCode 24 / versionName 1.0.4.

# MathIME 更新公告與版本紀錄

## v1.0.2 — 版本資訊修正（2026-10-15）

- Android `versionName` 更新為 `1.0.2`，避免系統設定仍顯示舊版名稱。
- Android `versionCode` 更新為 `22`，使 APK 可覆蓋安裝較早版本（前提是簽章相同）。
- 新增統一的 `CHANGELOG.md`，方便每次發佈時核對版本。
- 保留 v1.0.1 的鍵盤、CoMER 辨識、Beam Search、離線公式預覽與整合操作。

**注意：本版只修正版本資訊與公告，沒有宣稱修好公式預覽顯示問題。**

## v1.0.1 — 操作整合
- 整合重複的清除與重寫操作；增加預覽狀態提示。

## v1.0 — 公式預覽
- 加入離線數學公式預覽。

## v0.9.6 — 多候選辨識
- 加入 Beam Search 與候選切換。

## v0.9.5 — 遮罩修正
- 修正 Encoder 遮罩語意，恢復有效推論。

## v1.0.3 — 2026-10-15
- Move previous/next recognition candidate buttons to the sides of the formula preview.
- Collapse the LaTeX editor by default; open it with the edit button.
- Fix preview status so it clears after MathJax finishes rendering.
- Hide MathJax assistive MathML to avoid double visual rendering on WebView implementations.
- Add a conservative repeated-token-block penalty in beam search to reduce decoding loops; does not guarantee improved recognition.
- Preserve CoMER ONNX weights, all-false pixel mask, handwriting, shortcuts and offline rendering.
- Bump Android versionCode to 23 and versionName to 1.0.3.
