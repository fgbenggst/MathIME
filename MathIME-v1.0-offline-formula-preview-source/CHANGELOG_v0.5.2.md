# MathIME v0.5.2 — IME switching improvements

- 🌐 now calls InputMethodService.switchToNextInputMethod(false), falling back to system picker.
- Main app has a button to open the system input-method picker.
- Cancels pending recognition when input view starts/finishes.
- Keeps the existing IME service declaration and package name.
- Model weights remain absent; full handwriting recognition is not available.

## Testing
1. Install APK and confirm MathIME is enabled in system keyboard settings.
2. Launch MathIME app > 選擇目前使用的鍵盤 > MathIME.
3. In ChatGPT press 🌐 to switch out.
4. Open MathIME app > 選擇目前使用的鍵盤 > MathIME to switch back.
5. If system picker does not list MathIME even though enabled, toggle it off/on in settings and inspect Android logcat; app code cannot force Android to list an IME.
