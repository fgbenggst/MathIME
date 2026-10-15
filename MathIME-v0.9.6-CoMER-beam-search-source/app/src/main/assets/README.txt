To enable recognition, provide BOTH files:
 math_ctc.onnx: trained CTC model with input float32 [1,1,64,512], output float32 [1,T,V]
 tokens.txt: UTF-8 tokens, one per line, index 0 reserved for CTC blank.
No weights are bundled. Arbitrary pix2tex ONNX models are NOT compatible.
