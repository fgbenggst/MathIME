# MathIME v0.7.0-dev — 完整 Android 專案原始碼

本版本保留已實測的手寫鍵盤、數學符號、刪除鍵、LaTeX 編輯與 ChatGPT 輸入。

**重要：尚未提供訓練完成且符合本專案規格的完整公式辨識模型。**
此 ZIP 是可供編譯的完整 Android 原始碼專案，不是已完成的高精度手寫數學辨識產品。

## 使用方式
1. 將 ZIP 內容放進 GitHub 專案根目錄。
2. 使用 `.github/workflows/main.yml` 編譯。
3. 安裝 APK 後啟用 MathIME。
4. 點「模型狀態」查看模型檔案是否存在。

## 模型介面（僅支援特定 CTC 架構）
請參考 `MODEL_INTEGRATION.md`。不能把任意 pix2tex/CoMER 權重改名成 math_ctc.onnx 使用。

## 驗證狀態
此版本尚未在 GitHub Actions 或 Android 實機編譯測試。
