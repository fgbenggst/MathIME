package com.mathime.keyboard

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.util.concurrent.Executors

/**
 * Offline CTC image-to-LaTeX inference adapter.
 * Requires a *compatible trained* model in assets/math_ctc.onnx and
 * a matching UTF-8 tokens.txt (one token per line, ID 0 = CTC blank).
 * Model contract: input float32 [1,1,64,512], white=0, black=1;
 * output float32 [1,time,vocabulary] logits.
 * This contract is NOT compatible with arbitrary pix2tex models.
 */
class OnnxMathRecognizer(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    fun recognize(bitmap: Bitmap, callback: (Result<String>) -> Unit) {
        worker.execute {
            val result = try { Result.success(infer(bitmap)) } catch (t: Throwable) { Result.failure(t) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
        }
    }
    private fun infer(bitmap: Bitmap): String {
        val model = try { context.assets.open("math_ctc.onnx").use { it.readBytes() } }
                    catch (_: Exception) { error("缺少相容的 math_ctc.onnx 模型權重；未執行辨識") }
        val tokens = try { context.assets.open("tokens.txt").bufferedReader().use { it.readLines() } }
                     catch (_: Exception) { error("缺少與模型配對的 tokens.txt") }
        require(tokens.size > 1) { "字元表不可為空" }
        val width = 512; val height = 64
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val input = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            val gray = (((p shr 16) and 255) * 0.299f + ((p shr 8) and 255) * 0.587f + (p and 255) * 0.114f) / 255f
            1f - gray
        }
        val env = OrtEnvironment.getEnvironment()
        env.createSession(model, OrtSession.SessionOptions()).use { session ->
            require(session.inputNames.size == 1) { "預期單一影像輸入" }
            OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 1, height.toLong(), width.toLong())).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { outputs ->
                    val raw = outputs[0].value
                    @Suppress("UNCHECKED_CAST")
                    val logits = raw as? Array<Array<FloatArray>>
                        ?: error("模型輸出必須為 float32 [1,time,vocab]，不能直接使用 encoder-decoder 模型")
                    val frames = logits.firstOrNull() ?: error("模型沒有輸出")
                    val text = StringBuilder()
                    var previous = -1
                    for (frame in frames) {
                        require(frame.size == tokens.size) { "模型輸出字元數與 tokens.txt 不一致" }
                        val id = frame.indices.maxByOrNull { frame[it] } ?: 0
                        if (id != 0 && id != previous) text.append(tokens[id])
                        previous = id
                    }
                    return text.toString().ifBlank { error("未辨識出有效內容") }
                }
            }
        }
    }
    fun close() { worker.shutdown() }
}
