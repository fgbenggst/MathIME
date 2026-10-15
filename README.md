# MathIME v0.6.0 — 完整 Android 原始碼（離線基礎版）

這是以 v0.5.2 為基礎的完整 Android 輸入法專案，包含原有鍵盤、手寫畫布、數學快捷鍵、LaTeX 編輯和插入 ChatGPT。

## 實際可用功能
- Android InputMethodService 輸入法
- 手寫筆跡、復原、清除
- 數學符號快捷鍵與 LaTeX 輸出
- 無網路、無 API
- **有限的單筆畫模板辨識**：0–9、部分符號；辨識不確定時不產生虛假結果
- 如果未來放入符合專案約定的 `math_ctc.onnx` 與 `tokens.txt`，才會使用 ONNX 推論

## 重要限制
**此包不含訓練完成的數學公式神經網路權重，因此不能辨識完整工程數學公式。**
ONNX Runtime 本身不是模型。隨便下載不相容模型並不能使用。模板分數是形狀相似度，不是辨識信心機率。

## 編譯
保留 `.github/workflows/main.yml`，推送到 GitHub 的 main 分支，使用 Actions 編譯。Android 實機編譯尚未驗證。

## 後續要真正實現離線公式辨識
需要可合法使用且有訓練權重的 HMER 模型、匹配的 tokenizer、與模型相容的前後處理和 Android 推論實測。
