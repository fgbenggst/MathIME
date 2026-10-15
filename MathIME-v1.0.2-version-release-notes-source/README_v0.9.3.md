# MathIME v0.9.3 — decoder diagnostic build

This is a **real CoMER ONNX model integration test**, not a validated handwriting recognizer. Both ONNX model weights and vocabulary are included.

## Changes from v0.9.2
- Treat all pixels of the padded input as valid for the ONNX pixel mask (experimental; verify against upstream model preprocessing).
- Detect decoder token repetition, non-finite logits, absent EOS and vocabulary dimension mismatch.
- Show first six decoder steps with top-3 token IDs/score values in the existing status display.
- Never accept a 160-token run without EOS as a successful formula.
- Preserve IME handwriting, shortcuts, undo, backspace, manual editing and commit.

## Known limitations
- The underlying preprocessing and decoder contract have **not** been checked against upstream CoMER export implementation.
- No Android compilation or device inference has been run in this environment.
- If the repeated-token diagnostic remains, the next task is to compare ONNX inputs and masks with the upstream inference script, not to guess new dimensions.
