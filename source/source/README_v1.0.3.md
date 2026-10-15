# MathIME v1.0.3

Changes: preview-adjacent previous/next candidates, candidate count, collapsible LaTeX editing, preview state fixes, soft repetition penalty in beam search, versionCode 23.

## Build
Use `.github/workflows/main.yml` in GitHub Actions, or build with Android SDK and Gradle.

## Verification
Not compiled or tested on device in this environment. The model is unchanged; changes to beam ranking may affect results. Test short expressions (`5x`, `6x`), radicals, integrals, candidate switching and preview updates. If issues occur, share diagnostics.
