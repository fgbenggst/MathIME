# MathIME v0.3.0 — experimental hybrid math handwriting keyboard

Android Kotlin IME. This is a source release for GitHub Actions; compilation and device operation have NOT been verified for this revision.

## Working components from prior versions
- Android keyboard, stroke canvas, undo, clear, manual/auto trigger, symbols, editable LaTeX and insertion into focused text field.
- Experimental single-stroke offline template matching (NOT full mathematical handwriting recognition).

## New in v0.3
- Cloud recognition of a *whole handwritten formula image* using Gemini 2.5 Flash REST API, including fractions, integrals, derivatives and matrices when model recognizes them.
- Optional API key entry in the app's launcher screen, kept in Android private preferences (not encrypted; do not use a valuable/shared key on an untrusted device).
- Per-request explicit confirmation before sending ink image. Never uploads ChatGPT conversation text. Offline-only mode blocks cloud requests.
- Recognition results are editable before sending; original ink is preserved. Network calls run on a background thread.

## Caveats
- Offline engine only handles limited isolated glyphs; **full offline formula recognition is not implemented**.
- Cloud recognition accuracy is not guaranteed; check LaTeX manually. Network and API quota/costs apply.
- 'Cloud priority' still requires explicitly pressing Cloud Recognition and approving the request. Auto mode only invokes offline recognition.
- When using a third-party keyboard, avoid entering passwords or other sensitive text. No telemetry implemented.
- Existing formula text is replaced when accepting a cloud candidate; copy it first if needed.

## Build
Upload root files to GitHub repository; run `.github/workflows/main.yml`; download `MathIME-debug-apk` artifact. No API key is embedded in the repository.
