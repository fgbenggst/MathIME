# 模型整合契約

必須將已訓練且合法使用的模型放到 `app/src/main/assets/math_ctc.onnx`，並提供配對的 `tokens.txt`。

- 輸入：float32 [1,1,64,512]，白色=0，黑色=1。
- 輸出：float32 [1,T,V] 的 CTC logits。
- tokens.txt：每行一個 token，第一行為 CTC blank（ID=0），V 必須等於行數。
- 解碼：greedy CTC 去重與移除 blank。

**注意：** 這是現有 Kotlin 推論器的介面規格，不表示已存在相容的預訓練權重。多數數學公式辨識模型使用 encoder-decoder 與 autoregressive 解碼，不能直接套用。

要達到完整手寫公式辨識，需取得有授權的 HMER 權重，並依模型實際架構修改輸入、推論與解碼器，再於 Android 實測。
