# MathIME v1.0.7 — Tensor 檢查與完整候選保留

此版本從 v1.0.6 原始專案直接修改，完整保留鍵盤、離線 CoMER ONNX 模型、候選預覽、三種筆跡前處理與診斷功能。

## 修改
- 每種變體除了 `mathime_input_variant_N.png`，額外儲存 `mathime_tensor_variant_N.png`（512×256），直接依 `pixel_values` FLOAT 數值重建，方便檢查 AI 實際輸入的黑白圖。
- Beam Search 分離已完成 EOS 的路徑與仍在延伸的路徑，避免短公式在後續步驟佔據搜尋寬度，也避免完成候選被後續擴展丟失。
- 診斷新增已完成路徑數量。
- 保留全 false `pixel_mask`，不以人工規則篡改模型輸出。

## 限制
- 本版未在實機或 GitHub Actions 上編譯／驗證辨識準確率；並不保證 `5x` 辨識成功。
- 診斷圖片位於 App 私有儲存目錄，尚未加入分享圖片的介面。
- 需要在 GitHub Actions 使用 Gradle 8.9 編譯 Debug APK。
