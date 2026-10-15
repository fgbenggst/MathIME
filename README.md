# MathIME v0.9 CoMER ONNX (Android Kotlin)

This source package **includes actual uploaded pretrained ONNX weights**: `encoder_int8.onnx`, `decoder_int8.onnx`, and `vocab.json` under `app/src/main/assets/`.

## Architecture

- Encoder inputs: `pixel_values` float32 `[1,1,H,W]`, `pixel_mask` bool `[1,H,W]`.
- Encoder outputs: `encoder_features` float32 `[1,h,w,256]`, `encoder_mask` bool `[1,h,w]`.
- Decoder inputs: `encoder_features`, `encoder_mask`, `input_ids` int64 `[1,sequence_length]`.
- Decoder output: `logits` float32 `[1,sequence_length,113]`.
- Autoregressive greedy decoding, SOS=1, EOS=2, 160-token maximum. No fake recognition answers or cloud APIs.

## Build

Push the **contents of this directory** to the root of your GitHub repository, run GitHub Actions `Build MathIME APK`, download the `MathIME-debug-apk` artifact. Android minSdk 26.

## Verification status

The supplied ONNX files' graph input/output signatures were parsed and matched to the Kotlin inference code. **No Android Gradle build or phone inference was executed in this environment**. In particular, image normalization, padding mask polarity, quantized operator support, and handwriting accuracy need to be checked on the target phone. This is a real-model integration candidate, not a verified working APK. If the model fails, share the on-screen error or GitHub Actions build log for correction.

## License

Model weights originate from user-provided files. Before redistribution, check the upstream model's license and provenance.
