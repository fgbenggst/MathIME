package com.mathime.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxJavaType
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.roundToInt

/** Actual pretrained CoMER encoder-decoder ONNX inference; never uses hardcoded recognition results. */
class OnnxMathRecognizer(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val env = OrtEnvironment.getEnvironment()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var words: Map<Int, String>? = null

    fun recognize(bitmap: Bitmap, callback: (Result<String>) -> Unit) {
        worker.execute {
            val result = runCatching { infer(bitmap) }
            main.post { callback(result) }
        }
    }

    private fun initialize() {
        if (encoder != null) return
        val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        try {
            val enc = context.assets.open("encoder_int8.onnx").use { env.createSession(it.readBytes(), opts) }
            try {
                val dec = context.assets.open("decoder_int8.onnx").use { env.createSession(it.readBytes(), opts) }
                val json = context.assets.open("vocab.json").bufferedReader().use { JSONObject(it.readText()) }
                val dict = json.getJSONObject("idx2word")
                val map = (0 until dict.length()).associateWith { dict.getString(it.toString()) }
                encoder = enc; decoder = dec; words = map
            } catch (t: Throwable) { enc.close(); throw t }
        } finally { opts.close() }
    }

    private fun infer(bitmap: Bitmap): String {
        initialize()
        val enc = checkNotNull(encoder)
        val dec = checkNotNull(decoder)
        val vocab = checkNotNull(words)
        // ARM exported graph has a 16-way spatial reshape. A 64-pixel
        // input multiple is NOT enough: the encoder downsamples 16x,
        // yielding shapes like [1,8,28,256] (not divisible by 16).
        // Use 256x512 input => expected spatial features [16,32].
        // This is a compatibility experiment, not a validated model fix.
        val h = 256
        val w = 512
        val margin = 16
        val scale = minOf((w - 2 * margin).toFloat() / bitmap.width,
                          (h - 2 * margin).toFloat() / bitmap.height)
        val scaledW = max(1, (bitmap.width * scale).roundToInt())
        val scaledH = max(1, (bitmap.height * scale).roundToInt())
        val offsetX = (w - scaledW) / 2
        val offsetY = (h - scaledH) / 2
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        val padded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(padded).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, offsetX.toFloat(), offsetY.toFloat(), null)
        }
        val pixels = IntArray(w * h)
        padded.getPixels(pixels, 0, w, 0, 0, w, h)
        val floats = FloatArray(w * h) { i ->
            val p = pixels[i]
            1f - ((((p shr 16) and 255) * .299f + ((p shr 8) and 255) * .587f + (p and 255) * .114f) / 255f)
        }
        scaled.recycle(); padded.recycle()
        val mask = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder())
        repeat(h) { y -> repeat(w) { x ->
            val valid = x >= offsetX && x < offsetX + scaledW && y >= offsetY && y < offsetY + scaledH
            mask.put(if (valid) 1.toByte() else 0.toByte())
        } }
        mask.rewind()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(floats), longArrayOf(1, 1, h.toLong(), w.toLong())).use { image ->
            OnnxTensor.createTensor(env, mask, longArrayOf(1, h.toLong(), w.toLong()), OnnxJavaType.BOOL).use { pixelMask ->
                enc.run(mapOf("pixel_values" to image, "pixel_mask" to pixelMask)).use { encoded ->
                    val features = encoded.get("encoder_features").orElseThrow { IllegalStateException("Encoder 缺少 features") } as OnnxTensor
                    val featureShape = features.info.shape
                    require(featureShape.size == 4 && featureShape[1] % 16L == 0L && featureShape[2] % 16L == 0L) {
                        "ARM 空間尺寸不相容：${featureShape.contentToString()}；輸入 256x512"
                    }
                    val encMask = encoded.get("encoder_mask").orElseThrow { IllegalStateException("Encoder 缺少 mask") } as OnnxTensor
                    val ids = ArrayList<Long>(); ids.add(1L) // <sos>
                    val output = ArrayList<String>()
                    for (step in 0 until 160) {
                        OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, ids.size.toLong())).use { inputIds ->
                            val decodedResult = try {
                                dec.run(mapOf("encoder_features" to features, "encoder_mask" to encMask, "input_ids" to inputIds))
                            } catch (e: Exception) {
                                throw IllegalStateException(
                                    "Decoder 第 $step 步失敗；特徵=${featureShape.contentToString()}；tokens=${ids.size}；${e.message}", e
                                )
                            }
                            decodedResult.use { decoded ->
                                val logits = decoded[0].value as? Array<*> ?: error("Decoder 輸出格式不符")
                                val sequence = logits[0] as? Array<*> ?: error("Decoder 輸出序列格式不符")
                                val last = sequence.last() as? FloatArray ?: error("Decoder logits 不是 float32")
                                val next = last.indices.maxByOrNull { last[it] } ?: error("空白 logits")
                                if (next == 2) return output.joinToString(" ").trim().ifBlank { error("模型未辨識出公式") }
                                if (next != 0 && next != 1) output.add(vocab[next] ?: error("未知 token $next"))
                                ids.add(next.toLong())
                            }
                        }
                    }
                    return output.joinToString(" ").trim().ifBlank { error("辨識達長度上限，沒有有效輸出") }
                }
            }
        }
    }

    fun close() {
        worker.execute { decoder?.close(); encoder?.close(); decoder = null; encoder = null }
        worker.shutdown()
    }
}
