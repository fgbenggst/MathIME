# MathIME v1.0.8

- Fast mode: one original handwriting image, beam width 3, up to 36 decoder tokens, 3 candidates.
- Precise mode (explicit button): three variants, beam width 9, up to 64 tokens, 9 merged candidates.
- Model sessions reused across recognitions.
- Elapsed time in keyboard status and diagnostic logs (model init, decoder, per variant, total).
- Candidate switching does not rerun ONNX.
- Offline CoMER models and MathJax retained.
- 1–2 seconds is a performance target, not a guaranteed result.
