# MathIME v0.6.1 — 刪除鍵修正版

以 v0.6 離線原型為基礎，新增 ⌫ 刪除鍵：短按刪除游標前一個 Unicode 字元，長按約 450ms 後連續刪除；若 LaTeX 編輯欄有焦點，刪除編輯欄內容，否則刪除 ChatGPT 等目標 App 輸入框文字。支援刪除選取文字。🌐 移至最下排，保留原有功能。

**限制：** 仍未包含訓練完成的完整數學公式辨識 ONNX 模型；不能將此版視為高精度公式辨識完成版。尚未實際執行 Android 編譯或手機測試。

## 編譯
上傳本專案到 GitHub 儲存庫根目錄，使用 `.github/workflows/main.yml` 的 Actions 編譯 Debug APK。
