# MathIME v0.1 (Android)

A minimal Android IME prototype for entering math symbols and LaTeX directly into the focused text field (including ChatGPT).

## Current functionality
- System keyboard based on `InputMethodService`.
- Handwriting canvas with undo/clear and auto/manual mode toggle.
- Math symbol keys and editable LaTeX field.
- Direct insertion using `InputConnection.commitText()`; no clipboard.

**Important limitation:** Mathematical handwriting recognition is **not implemented**. The canvas records strokes, but cannot yet turn them into LaTeX. Auto/manual mode currently controls when a placeholder status appears. The preview is an editable plain-text field, not rendered math. Do not confuse this prototype with a finished handwriting recognition app.

## Build on GitHub (no local Android Studio needed)
1. Create a new GitHub repository.
2. Upload the **contents** of this folder to the repository root (including `.github/workflows/android.yml`).
3. Open the repository's **Actions** tab → **Build MathIME APK** → **Run workflow** (or push to main).
4. After the job succeeds, open the run and download the `MathIME-debug-apk` artifact.
5. Unzip the artifact, install `app-debug.apk` on Android, enable MathIME under system keyboard settings, and select it as the current keyboard.

## Next engineering milestone
Integrate an on-device handwritten mathematical expression recognition model that accepts multi-stroke 2D ink and outputs structured LaTeX; test accuracy and inference latency on actual Android hardware. A regular text OCR model will not reliably capture fractions, indices, matrices, or integral limits.

## Security
The keyboard requests no Internet permission and sends no strokes to a server. Android may display a generic warning when enabling third-party keyboards. Do not use this experimental keyboard to enter passwords or sensitive information.
