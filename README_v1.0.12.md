# MathIME v1.0.12 — 診斷文字與 PNG 一起分享

按「辨識」後點「診斷分享」，Android 分享面板會收到診斷文字內容、`comer_diagnostics.txt` 與該次辨識實際生成的 `mathime_tensor_variant_*.png`。

透過 FileProvider 的 `content://` URI 與暫時讀取授權分享，不需要 ADB，也不需要儲存空間權限。

注意：部分接收 App 可能不支援同時接收文字及多個附件；可以改用 Gmail 或其他支援多附件的分享目標。分享前先辨識一次，以免沒有最新圖片。
