# MathIME v1.0 — 離線數學公式預覽

以 v0.9.6 CoMER Beam Search 為基礎，保留 ONNX 模型、手寫、候選切換、診斷分享、刪除與輸入法切換。

## 新增
- 在 LaTeX 編輯框上方加入真正的數學公式預覽（WebView + MathJax SVG）。
- 所有 MathJax JavaScript 都包含在 APK 的 `assets`，不使用 CDN、不需網路。
- 辨識結果、下一候選、手動修改、符號快捷鍵都即時刷新預覽。
- 空白時顯示等待辨識，渲染失敗顯示錯誤。
- LaTeX 輸入到聊天程式的邏輯保持不變。

## 技術說明
本版採用 MathJax v3 的本地 `tex-svg.js`，功能等同原規劃的離線 KaTeX 公式預覽，但並非 KaTeX。該引擎將 TeX 轉成 SVG；不需要字型 CDN。

## 編譯
上傳整個專案至 GitHub，透過 `.github/workflows/main.yml` 編譯 APK。

## 測試
請在 Samsung 裝置檢查預覽渲染、手動修改、候選切換、輸入 ChatGPT，以及 WebView 的鍵盤高度。此版本尚未完成 Android 實機測試。
