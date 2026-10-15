# MathIME v1.0.9

- Fast mode: single original image, beam width 3; retains previous speed settings.
- Precise mode: at most two image variants, beam width 5, max 40 tokens, 2500ms decoder budget per variant.
- Reduced beam length normalization exponent from 0.6 to 0.25 to reduce bias toward spurious extra symbols.
- Diagnostic logging of decoder time budget and beam width.

Important: A time budget is checked between decoding steps, not during an ONNX invocation. Recognition accuracy and 1–2 second timing must be measured on-device; no APK compilation has been verified.
