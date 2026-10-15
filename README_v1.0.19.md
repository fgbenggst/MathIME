# MathIME v1.0.19 — 2x 單字元幾何改善

本版本以 v1.0.18 完整 Android 專案為基礎，保留 CoMER ONNX 模型、MathJax、IME、橡皮擦、復原、候選、診斷分享及快速／精準模式。

- 修改 `GroupedGlyphRecognizer.classifyTwo`，分析真正的筆畫行進順序、上方右彎、左下轉折、右上尾線。允許底線向上彎曲。
- 保留既有的 `3`、`5`、`x` 規則與分式保護。
- 不符合條件時拒絕幾何判斷，改由 CoMER 候選提供結果；不強制替換。
- 仍須在手機上驗證 `2x`，以及 `3x`、`5x`、分式回歸測試。
- ZIP 已驗證，尚未執行 Android Gradle 編譯。
