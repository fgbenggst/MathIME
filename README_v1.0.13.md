# MathIME v1.0.13 — Single-glyph decoding experiment

Based on v1.0.12. Keeps offline CoMER ONNX models, eraser, fast/precise modes, and PNG diagnostic sharing.

When a top candidate is a suspicious multi-token sequence containing I/i/E/pi/sum, the recognizer now tests the twelve strongest single-glyph tokens from the **real first-step logits**, evaluates each token followed by the **real decoder EOS probability**, and exposes the highest-scoring short candidate as a selectable alternative. It does **not** replace the top result automatically or force x as an answer. The extra probes are bounded by a 220ms best-effort check between calls.

Known limitations: not a trained single-character classifier, no calibrated confidence, and no guarantee that x will be recovered. Fast mode targets 1–2 seconds but device testing is required. Input preprocessing already uses aspect-preserving scaling and padding.
