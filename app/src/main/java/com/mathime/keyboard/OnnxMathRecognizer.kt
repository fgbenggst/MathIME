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
import java.io.File
import android.util.Log
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Actual pretrained CoMER encoder-decoder ONNX inference; never uses hardcoded recognition results. */
class OnnxMathRecognizer(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val env = OrtEnvironment.getEnvironment()
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var words: Map<Int, String>? = null
    private val diagnostics = StringBuilder()
    private fun record(message: String) {
        diagnostics.append(message).append("\n")
        Log.i("MathIME-CoMER", message.take(3500))
    }
    fun diagnosticFile(): File = File(context.filesDir, "comer_diagnostics.txt")
    private fun saveDiagnostics() { diagnosticFile().writeText(diagnostics.toString()) }

    data class Candidate(val latex: String, val score: Double)
    private var lastCandidates: List<Candidate> = emptyList()
    fun candidates(): List<Candidate> = lastCandidates.toList()

    fun recognize(bitmap: Bitmap, callback: (Result<String>) -> Unit) {
        worker.execute {
            diagnostics.setLength(0)
            record("MathIME v1.0.3 beam search diagnostics")
            val result = runCatching { infer(bitmap) }
            result.onFailure { record("ERROR: ${it.stackTraceToString()}") }
            runCatching { saveDiagnostics() }
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
        record("Encoder inputs: ${enc.inputInfo.map { it.key + ":" + it.value.info.toString() }}")
        record("Encoder outputs: ${enc.outputInfo.map { it.key + ":" + it.value.info.toString() }}")
        record("Decoder inputs: ${dec.inputInfo.map { it.key + ":" + it.value.info.toString() }}")
        record("Decoder outputs: ${dec.outputInfo.map { it.key + ":" + it.value.info.toString() }}")
        record("Bitmap source: ${bitmap.width}x${bitmap.height}")
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
        record("Input: ${w}x${h}; foreground > 0.5 = ${floats.count { it > 0.5f }} / ${floats.size}; range=${floats.minOrNull()}..${floats.maxOrNull()}")
        scaled.recycle(); padded.recycle()
        // In the PyTorch-style attention padding mask, TRUE denotes an
        // invalid/padded pixel. The 512x256 bitmap itself is fully valid,
        // including its white background; therefore every pixel is FALSE.
        // Previous versions set every byte TRUE, which can make attention
        // softmax operate over exclusively masked (-Infinity) positions.
        val mask = ByteBuffer.allocateDirect(w * h).order(ByteOrder.nativeOrder())
        repeat(w * h) { mask.put(0.toByte()) }
        record("Pixel mask: true=0 false=${w * h} (false=valid)")
        mask.rewind()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(floats), longArrayOf(1, 1, h.toLong(), w.toLong())).use { image ->
            OnnxTensor.createTensor(env, mask, longArrayOf(1, h.toLong(), w.toLong()), OnnxJavaType.BOOL).use { pixelMask ->
                enc.run(mapOf("pixel_values" to image, "pixel_mask" to pixelMask)).use { encoded ->
                    val features = encoded.get("encoder_features").orElseThrow { IllegalStateException("Encoder 缺少 features") } as OnnxTensor
                    val featureShape = features.info.shape
                    record("Encoder features: ${featureShape.contentToString()}; mask=${(encoded.get("encoder_mask").orElseThrow() as OnnxTensor).info.shape.contentToString()}")
                    require(featureShape.size == 4 && featureShape[1] % 16L == 0L && featureShape[2] % 16L == 0L) {
                        "ARM 空間尺寸不相容：${featureShape.contentToString()}；輸入 256x512"
                    }
                    val encMask = encoded.get("encoder_mask").orElseThrow { IllegalStateException("Encoder 缺少 mask") } as OnnxTensor
                    // Inspect actual numeric values, not just tensor shapes.
                    val featureData = features.floatBuffer
                    var finiteFeatures = 0L
                    var nanFeatures = 0L
                    var infFeatures = 0L
                    var minFeature = Float.POSITIVE_INFINITY
                    var maxFeature = Float.NEGATIVE_INFINITY
                    while (featureData.hasRemaining()) {
                        val v = featureData.get()
                        when {
                            v.isNaN() -> nanFeatures++
                            v.isInfinite() -> infFeatures++
                            else -> {
                                finiteFeatures++
                                if (v < minFeature) minFeature = v
                                if (v > maxFeature) maxFeature = v
                            }
                        }
                    }
                    record("Encoder values: finite=$finiteFeatures NaN=$nanFeatures Inf=$infFeatures min=$minFeature max=$maxFeature")
                    val maskData = encMask.byteBuffer
                    var masked = 0L
                    var valid = 0L
                    while (maskData.hasRemaining()) {
                        if (maskData.get().toInt() != 0) masked++ else valid++
                    }
                    record("Encoder mask: true(masked)=$masked false(valid)=$valid")
                    check(nanFeatures == 0L && infFeatures == 0L) { "Encoder 輸出含非有限數值；請分享診斷檔" }
                    check(valid > 0L) { "Encoder 遮罩沒有有效像素：全部被遮蔽；請分享診斷檔" }
                    // Beam search: rank complete sequences by accumulated log probability.
                    // All candidates come from the real decoder; no hard-coded formula substitution.
                    data class Beam(val ids: List<Long>, val logp: Double, val ended: Boolean)
                    val width = 3
                    val maxTokens = 96
                    var beams = listOf(Beam(listOf(1L), 0.0, false))
                    for (step in 0 until maxTokens) {
                        val expanded = ArrayList<Beam>()
                        for (beam in beams) {
                            if (beam.ended) { expanded.add(beam); continue }
                            OnnxTensor.createTensor(env, LongBuffer.wrap(beam.ids.toLongArray()), longArrayOf(1, beam.ids.size.toLong())).use { inputIds ->
                                dec.run(mapOf("encoder_features" to features, "encoder_mask" to encMask, "input_ids" to inputIds)).use { decoded ->
                                    val tensor = decoded[0] as OnnxTensor
                                    val seq = (tensor.value as Array<*>)[0] as Array<*>
                                    val logits = seq.last() as FloatArray
                                    require(logits.size == vocab.size && logits.all { it.isFinite() }) {
                                        "Decoder logits invalid at step=$step"
                                    }
                                    val maxLogit = logits.maxOrNull()!!.toDouble()
                                    val sumExp = logits.sumOf { kotlin.math.exp(it.toDouble() - maxLogit) }
                                    val logNorm = maxLogit + kotlin.math.ln(sumExp)
                                    val best = logits.indices.sortedByDescending { logits[it] }.take(width + 2)
                                    if (step < 5 && beam === beams.first()) {
                                        record("Beam step=$step ids=${beam.ids.joinToString(",")} top=" + best.take(5).joinToString(" | ") { "${vocab[it]}:${"%.2f".format(java.util.Locale.US, logits[it])}" })
                                    }
                                    for (id in best) {
                                        if (id == 0 || id == 1) continue // disallow PAD/SOS
                                        val nextIds = beam.ids + id.toLong()
                                        // Discourage decoder loops that repeat a complete multi-token
                                        // segment twice (e.g. identical radicals). Do not ban repetition:
                                        // legitimate expressions may contain the same factor twice.
                                        val loopPenalty = if (id != 2 && repeatsTailBlock(nextIds)) 1.8 else 0.0
                                        expanded.add(Beam(nextIds, beam.logp + logits[id].toDouble() - logNorm - loopPenalty, id == 2))
                                    }
                                }
                            }
                        }
                        if (expanded.isEmpty()) break
                        // Keep a short, diverse list; normalization is only used for final ranking.
                        beams = expanded.sortedByDescending { it.logp }.distinctBy { it.ids }.take(width)
                        if (beams.all { it.ended }) break
                    }
                    val finished = beams.filter { it.ended }
                    if (finished.isEmpty()) record("WARNING: no EOS among final beams")
                    val ordered = (if (finished.isNotEmpty()) finished else beams)
                        .sortedByDescending { it.logp / it.ids.size.toDouble().coerceAtLeast(2.0).pow(0.6) }
                    lastCandidates = ordered.mapNotNull { beam ->
                        val tokens = beam.ids.drop(1).takeWhile { it != 2L }.mapNotNull { vocab[it.toInt()] }
                        val latex = tokens.joinToString(" ").trim()
                        if (latex.isBlank()) null else Candidate(latex, beam.logp)
                    }.distinctBy { it.latex }.take(3)
                    lastCandidates.forEachIndexed { index, candidate -> record("Candidate ${index + 1}: score=${candidate.score} latex=${candidate.latex}") }
                    return lastCandidates.firstOrNull()?.latex ?: error("模型沒有產生有效公式；請分享診斷檔")
                }
            }
        }
    }

    /** True only for immediate repetition of a block of >= 4 tokens.
     * This is a soft beam-ranking penalty, never a forced rewrite of LaTeX.
     */
    private fun repeatsTailBlock(ids: List<Long>): Boolean {
        val tokens = ids.drop(1)
        for (length in 4..minOf(20, tokens.size / 2)) {
            val n = tokens.size
            if (tokens.subList(n - 2 * length, n - length) == tokens.subList(n - length, n)) return true
        }
        return false
    }

    fun close() {
        worker.execute { decoder?.close(); encoder?.close(); decoder = null; encoder = null }
        worker.shutdown()
    }
}
