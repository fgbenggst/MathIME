# MathIME v0.5.1 啟動穩定性修補

- ONNX 引擎改為按下「辨識」才建立，避免鍵盤顯示階段初始化推論元件。
- 捕捉辨識引擎初始化與執行時的錯誤，避免直接使鍵盤程序崩潰。
- 保留全部輸入法、手寫與 LaTeX 輸入功能。
- 尚未包含 math_ctc.onnx/tokens.txt，因此完整手寫公式辨識仍不可用。

此修補尚未在 Android 手機或 GitHub Actions 編譯驗證。如果仍無法切換，請提供 adb logcat 中 FATAL EXCEPTION 的段落。
