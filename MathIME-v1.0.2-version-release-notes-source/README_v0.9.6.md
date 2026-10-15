# MathIME v0.9.6 (CoMER Beam Search)

This release includes the user's real CoMER INT8 encoder/decoder ONNX models and vocab. It builds on the v0.9.5 mask fix (FALSE=valid) and adds beam width 3 decoding with up to 3 candidate equations. Use the **下一候選** key to cycle candidates; manually edit LaTeX before **輸入**.

This is a candidate-generation experiment, **not a verified accuracy improvement**. Greedy decoding was already able to recognize one complete equation; beam search may be slower and may not fix integral-vs-sum confusion. The candidate list is not guaranteed to contain the correct expression.

To build: upload extracted project files to the repository root and run `.github/workflows/main.yml` on GitHub Actions. APK build and Android inference have not been tested in this environment.

The existing **診斷分享** key exports model traces and candidate scores. If recognition fails, share the diagnostic text.
