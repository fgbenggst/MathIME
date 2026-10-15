# MathIME v0.5 — Offline ONNX integration (MODEL NOT INCLUDED)

This is a real ONNX Runtime inference path, not a fake result or template recognizer. **It does not recognize anything until a compatible trained model and matching vocabulary are provided.**

- Android IME, handwriting canvas, undo/clear, math symbol keys, ChatGPT commitText retained.
- On-device background inference with ONNX Runtime Android; no API key, INTERNET permission, or cloud requests.
- Required model assets: `app/src/main/assets/math_ctc.onnx` and `tokens.txt`.
- Exact model contract: float32 input `[1,1,64,512]` grayscale black=1; float32 output `[1,T,V]` CTC logits; blank token ID 0. `tokens.txt` one token per line, V entries. CTC greedy decoding.
- **No trained model or vocabulary is bundled.** Models such as pix2tex are encoder-decoder architectures and cannot be used with this CTC adapter without writing a different decoder. Handwritten full-formula accuracy is not established.
- Image is stretched to 512x64; a production model must match preprocessing used during training.
- Build with the existing GitHub Actions workflow; build has not been verified here.

For a usable full-formula offline recognizer, next obtain an appropriately licensed HMER model with weights, identify its exact architecture and preprocessing, and implement the corresponding ONNX export and decoder. Do not mistake this adapter for a completed recognizer.
