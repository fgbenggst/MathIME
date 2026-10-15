# MathIME v0.9.4 — CoMER diagnostics

包含真實的 encoder_int8.onnx、decoder_int8.onnx 與 vocab.json；完全離線。

## 重要
這是**可建置的診斷原始碼候選**，尚未通過 Android 編譯與正確辨識驗證。
沒有聲稱修好 `i`、`1`、`EiE` 等錯誤辨識。

## 使用
1. 透過 GitHub Actions 建置並安裝 APK。
2. 手寫 `4+5=9`，按「辨識」。
3. 按「診斷分享」，將文字記錄傳回。記錄包括 ONNX IO 形狀、影像黑色像素比例、逐步 token 與 top5 分數、完整例外堆疊。
4. 不需要再次上傳模型。

## 目前風險
- CoMER 影像遮罩、輸入正規化和解碼器是否與原始訓練推論一致仍待驗證。
- 模型在輸出 `i` 或 `1` 時，這仍只是候選而非正確辨識。
- 模型與鍵盤均未在本環境執行 Android 實機測試。
