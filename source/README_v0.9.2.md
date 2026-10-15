# MathIME v0.9.2 — CoMER spatial-shape experiment

This project contains the actual user-supplied CoMER INT8 encoder/decoder ONNX models and vocab.

## Fix attempt
- The v0.9.1 encoder produced `[1,8,28,256]`, which failed the prior spatial divisibility check.
- The new preprocessing uses a fixed 256x512 image canvas (16x32 expected encoder spatial dimensions) and preserves handwritten aspect ratio.
- Decoder failures now report step number, encoder shape, and token length.

**Not yet tested on Android or verified to decode correctly.** A valid feature shape does not guarantee that the exported ARM decoder is compatible.

## Build
Push all files to your GitHub repository root and run Actions > Build MathIME APK.

## Offline
Both models and vocabulary are bundled under `app/src/main/assets`. No API is used.
