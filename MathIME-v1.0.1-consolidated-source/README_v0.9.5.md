# MathIME v0.9.5 — CoMER 遮罩及數值診斷版

這是完整 Android Kotlin 輸入法原始碼，內含使用者提供的真實 CoMER ONNX Encoder、Decoder 及 vocab.json。

## 變更
- 修正 pixel_mask：整張 256×512 輸入影像皆為有效像素，BOOL mask 設為 false，不再全部 true。
- 加入 Encoder features 的 NaN、Infinity、min、max 檢查。
- 加入 Encoder mask 的 true/false 數量檢查。
- 加入 Decoder 每步 logits 的 NaN/Infinity 數量檢查。
- 保留原有鍵盤、手寫、LaTeX 編輯、刪除鍵及診斷分享功能。

## 注意
這是根據 v0.9.4 診斷結果提出的修正，尚未完成 Android 編譯及實機正確辨識驗證。模型輸入前處理和遮罩語意仍應對照原始模型程式。請勿將此版本視為已驗證準確的辨識器。

## 測試
GitHub Actions 編譯 APK，安裝後手寫 `2+3=5` 按「辨識」。若失敗，使用「診斷分享」匯出完整文字紀錄。
