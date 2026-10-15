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

    data class Candidate(val latex: String, val score: Double, val source: String = "原始筆跡")
    private var activeVariantIndex = 0
    @Volatile private var precisionMode = false
    fun setPrecisionMode(enabled: Boolean) { precisionMode = enabled }
    private var lastCandidates: List<Candidate> = emptyList()
    fun candidates(): List<Candidate> = lastCandidates.toList()
    @Volatile private var uncertain = false
    fun isUncertain(): Boolean = uncertain

    /** Runs all variants sequentially on the same worker, retaining independent diagnostics.
     * Scores from different images are not calibrated: keep original-image results first,
     * and use other variants as alternative candidates, not asserted improvements.
     */
    fun recognizeVariants(variants: List<Pair<String, Bitmap>>, callback: (Result<String>) -> Unit) {
        worker.execute {
            diagnostics.setLength(0)
            uncertain = false
            record("MathIME v1.0.13 diagnostics; mode=${if (precisionMode) "precise" else "fast"}")
            val totalStart = android.os.SystemClock.elapsedRealtime()
            val result = runCatching {
                val combined = mutableListOf<Candidate>()
                val failures = mutableListOf<String>()
                for ((variantIndex, pair) in variants.take(if (precisionMode) 2 else 1).withIndex()) {
                    val variantStart = android.os.SystemClock.elapsedRealtime()
                    val (name, bitmap) = pair
                    runCatching {
                        File(context.filesDir, "mathime_input_variant_${variantIndex}.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }.onFailure { record("Variant PNG save error: ${it.message}") }
                    record("Saved input variant $variantIndex to internal files: mathime_input_variant_${variantIndex}.png")
                    record("=== variant: $name ===")
                    try {
                        activeVariantIndex = variantIndex
                        infer(bitmap)
                        record("Variant elapsedMs=${android.os.SystemClock.elapsedRealtime() - variantStart}")
                        val candidatesForVariant = lastCandidates.toList()
                        record("Variant $name: ${candidatesForVariant.joinToString { it.latex }}")
                        combined.addAll(candidatesForVariant.map { it.copy(source = name) })
                    } catch (e: Throwable) {
                        failures.add("$name: ${e.message}")
                        record("Variant failed: $name ${e.stackTraceToString()}")
                    }
                }
                lastCandidates = rankAcrossVariants(combined).take(if (precisionMode) 9 else 3)
                record("Total elapsedMs=${android.os.SystemClock.elapsedRealtime() - totalStart}")
                record("Merged candidates: ${lastCandidates.size}; failures=$failures")
                lastCandidates.firstOrNull()?.latex ?: error("辨識失敗：${failures.joinToString()}")
            }
            variants.forEach { (_, bitmap) -> runCatching { bitmap.recycle() } }
            result.onFailure { record("ERROR: ${it.stackTraceToString()}") }
            runCatching { saveDiagnostics() }
            main.post { callback(result) }
        }
    }

    fun recognize(bitmap: Bitmap, callback: (Result<String>) -> Unit) =
        recognizeVariants(listOf("原始筆跡" to bitmap), callback)

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
        val initializeStart = android.os.SystemClock.elapsedRealtime()
        initialize()
        record("Model initialization elapsedMs=${android.os.SystemClock.elapsedRealtime() - initializeStart}")
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
        // Exact 512x256 grayscale input visualization, reconstructed from the FLOAT tensor.
        // White = 0.0, black = 1.0; this is what the encoder receives.
        runCatching {
            val diagnosticPixels = IntArray(floats.size) { i ->
                val gray = (255f * (1f - floats[i].coerceIn(0f, 1f))).roundToInt().coerceIn(0, 255)
                Color.rgb(gray, gray, gray)
            }
            val diagnosticBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                diagnosticBitmap.setPixels(diagnosticPixels, 0, w, 0, 0, w, h)
                val filename = "mathime_tensor_variant_${activeVariantIndex}.png"
                File(context.filesDir, filename).outputStream().use {
                    diagnosticBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                record("Saved exact encoder pixel_values visualization: $filename")
            } finally { diagnosticBitmap.recycle() }
        }.onFailure { record("Tensor PNG save error: ${it.message}") }
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
                    val width = if (precisionMode) 5 else 3
                    val maxTokens = if (precisionMode) 40 else 36
                    val decoderBudgetMs = if (precisionMode) 2500L else 1500L
                    val decodeStart = android.os.SystemClock.elapsedRealtime()
                    var beams = listOf(Beam(listOf(1L), 0.0, false))
                    val completed = ArrayList<Beam>()
                    // Save step-zero single-symbol possibilities. They will be evaluated
                    // with a real decoder EOS step rather than invented substitutions.
                    val firstStepSymbols = ArrayList<Pair<Int, Double>>()
                    for (step in 0 until maxTokens) {
                        if (step > 0 && android.os.SystemClock.elapsedRealtime() - decodeStart >= decoderBudgetMs) {
                            record("Decoder time budget reached at step=$step; completed=${completed.size}")
                            break
                        }
                        val expanded = ArrayList<Beam>()
                        for (beam in beams) {
                            if (beam.ended) { completed.add(beam); continue }
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
                                    if (step == 0 && beam.ids.size == 1) {
                                        firstStepSymbols.clear()
                                        logits.indices.sortedByDescending { logits[it] }
                                            .filter { it != 0 && it != 1 && it != 2 && isSingleGlyphToken(vocab[it] ?: "") }
                                            .take(12).forEach { id ->
                                                firstStepSymbols.add(id to (logits[id].toDouble() - logNorm))
                                            }
                                        record("Single-glyph step0 shortlist: " + firstStepSymbols.joinToString { "${vocab[it.first]}:${"%.3f".format(java.util.Locale.US, it.second)}" })
                                    }
                                    if (step < 5 && beam === beams.first()) {
                                        record("Beam step=$step ids=${beam.ids.joinToString(",")} top=" + best.take(5).joinToString(" | ") { "${vocab[it]}:${"%.2f".format(java.util.Locale.US, logits[it])}" })
                                        val watch = listOf("x", "2", "+", "I", "i", "E", "\\pi", "<eos>")
                                        record("Token probes step=$step: " + watch.joinToString(" | ") { token ->
                                            val id = vocab.entries.firstOrNull { it.value == token }?.key
                                            if (id == null) "$token=not-in-vocab" else {
                                                val rank = logits.count { it > logits[id] } + 1
                                                val prob = kotlin.math.exp(logits[id].toDouble() - logNorm)
                                                "$token rank=$rank p=${"%.4f".format(java.util.Locale.US, prob)}"
                                            }
                                        })
                                    }
                                    for (id in best) {
                                        if (id == 0 || id == 1) continue // disallow PAD/SOS
                                        val nextIds = beam.ids + id.toLong()
                                        // Discourage decoder loops that repeat a complete multi-token
                                        // segment twice (e.g. identical radicals). Do not ban repetition:
                                        // legitimate expressions may contain the same factor twice.
                                        val loopPenalty = if (id != 2 && repeatsTailBlock(nextIds)) 4.0 else 0.0
                                        val symbolPenalty = if (id != 2 && repeatedSingleSymbol(nextIds, vocab)) 2.0 else 0.0
                                        val candidate = Beam(nextIds, beam.logp + logits[id].toDouble() - logNorm - loopPenalty - symbolPenalty, id == 2)
                                        if (candidate.ended) completed.add(candidate) else expanded.add(candidate)
                                    }
                                }
                            }
                        }
                        if (expanded.isEmpty()) break
                        // Keep a short, diverse list; normalization is only used for final ranking.
                        beams = expanded.sortedByDescending { it.logp }.distinctBy { it.ids }.take(width)
                        if (beams.isEmpty()) break
                        // Future log-probability increments cannot exceed zero.
                        // Conservative early exit once three complete sequences dominate.
                        if (!precisionMode && completed.size >= 3 && step >= 5) {
                            val third = completed.sortedByDescending { it.logp }.getOrNull(2)?.logp ?: Double.NEGATIVE_INFINITY
                            if (beams.first().logp < third - 1.5) break
                        }
                    }
                    record("Decoder elapsedMs=${android.os.SystemClock.elapsedRealtime() - decodeStart}; budgetMs=$decoderBudgetMs; width=$width")
                    val finished = completed.distinctBy { it.ids }
                    record("Beam finished hypotheses retained=${finished.size}, active=${beams.size}")
                    if (finished.isEmpty()) record("WARNING: no EOS among final beams")
                    // Rank by model likelihood plus *soft* structure and loop penalties.
                    // Never delete or replace an actual predicted token (including E).
                    val ordered = (if (finished.isNotEmpty()) finished else beams)
                        .map { beam ->
                            val tokens = beam.ids.drop(1).takeWhile { it != 2L }.mapNotNull { vocab[it.toInt()] }
                            val latex = tokens.joinToString(" ").trim()
                            val syntaxPenalty = structurePenalty(tokens)
                            val lengthScore = beam.logp / beam.ids.size.toDouble().coerceAtLeast(2.0).pow(0.25)
                            Triple(latex, lengthScore - syntaxPenalty, beam)
                        }
                        .filter { it.first.isNotBlank() }
                        .sortedByDescending { it.second }
                    val normalCandidates = ordered.distinctBy { it.first }
                        .take(if (precisionMode) 6 else 3).map { Candidate(it.first, it.second) }
                    // Only probe short-form alternatives if the leading prediction is
                    // suspicious. This adds at most twelve tiny decoder passes and is
                    // not a replacement for a dedicated glyph classifier.
                    val suspiciousSymbols = setOf("I", "i", "E", "\\pi", "\\sum")
                    val leadingTokens = normalCandidates.firstOrNull()?.latex?.split(Regex("\\s+")) ?: emptyList()
                    val likelyHallucination = leadingTokens.size >= 3 && leadingTokens.count { it in suspiciousSymbols } >= 2
                    val shortCandidates = ArrayList<Candidate>()
                    if (likelyHallucination) {
                        val probeStart = android.os.SystemClock.elapsedRealtime()
                        for ((id, firstLogP) in firstStepSymbols) {
                            if (android.os.SystemClock.elapsedRealtime() - probeStart > 220L) break
                            runCatching {
                                OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(1L, id.toLong())), longArrayOf(1, 2)).use { idsTensor ->
                                    dec.run(mapOf("encoder_features" to features, "encoder_mask" to encMask, "input_ids" to idsTensor)).use { output ->
                                        val sequence = ((output[0] as OnnxTensor).value as Array<*>)[0] as Array<*>
                                        val logits = sequence.last() as FloatArray
                                        val maxValue = logits.maxOrNull()!!.toDouble()
                                        val norm = maxValue + kotlin.math.ln(logits.sumOf { kotlin.math.exp(it.toDouble() - maxValue) })
                                        val eosLogP = logits[2].toDouble() - norm
                                        val symbol = vocab[id]!!
                                        val fullScore = firstLogP + eosLogP
                                        record("Single-glyph complete: $symbol -> EOS logp=${"%.3f".format(java.util.Locale.US, fullScore)} eosP=${"%.4f".format(java.util.Locale.US, kotlin.math.exp(eosLogP))}")
                                        shortCandidates.add(Candidate(symbol, fullScore, "單字元路徑"))
                                    }
                                }
                            }.onFailure { record("Single-glyph probe failed: ${it.message}") }
                        }
                    }
                    // Preserve the model's main result; expose a complete single-token
                    // candidate in the last slot when its EOS-supported score warrants it.
                    val maxCandidates = if (precisionMode) 6 else 3
                    val distinct = normalCandidates.toMutableList()
                    val bestShort = shortCandidates.maxByOrNull { it.score }
                    if (bestShort != null && distinct.none { it.latex == bestShort.latex }) {
                        if (distinct.size >= maxCandidates) distinct.removeAt(distinct.lastIndex)
                        distinct.add(bestShort)
                        record("Single-glyph alternative added: ${bestShort.latex}; not auto-promoted")
                    }
                    lastCandidates = distinct.take(maxCandidates)
                    val suspicious = setOf("I", "i", "E", "\\pi", "\\sum")
                    val topTokens = lastCandidates.firstOrNull()?.latex?.split(Regex("\\s+")) ?: emptyList()
                    val suspiciousCount = topTokens.count { it in suspicious }
                    uncertain = topTokens.size >= 3 && suspiciousCount >= 2
                    record("Confidence heuristic: uncertain=$uncertain suspicious=$suspiciousCount/${topTokens.size}; heuristic is not a calibrated probability")
                    lastCandidates.forEachIndexed { index, candidate -> record("Candidate ${index + 1}: score=${candidate.score} latex=${candidate.latex}") }
                    return lastCandidates.firstOrNull()?.latex ?: error("模型沒有產生有效公式；請分享診斷檔")
                }
            }
        }
    }

    /** Cross-image log probabilities are not calibrated. Rank by local order,
     * structural validity, and independent variant agreement. No glyph substitutions.
     * A candidate remains the exact token sequence emitted by the ONNX decoder.
     */
    private fun rankAcrossVariants(input: List<Candidate>): List<Candidate> {
        val normalized = input.groupBy { it.latex.replace(" ", "") }
        val sources = input.map { it.source }.distinct()
        val localRanks = sources.associateWith { source ->
            input.filter { it.source == source }.mapIndexed { index, c ->
                c.latex.replace(" ", "") to index
            }.toMap()
        }
        return normalized.map { (key, matches) ->
            val representative = matches.minByOrNull { c ->
                (localRanks[c.source]?.get(key) ?: 99)
            }!!
            val tokens = representative.latex.split(Regex("\\s+")).filter { it.isNotBlank() }
            val agreement = matches.map { it.source }.distinct().size
            val localRank = matches.minOf { localRanks[it.source]?.get(key) ?: 99 }
            val invalidPenalty = structurePenalty(tokens)
            val tailPenalty = if (tokens.size >= 2 && tokens.last() in setOf("=", "+", "-", "^", "_", "\\frac", "\\sqrt")) 1.0 else 0.0
            val extraPenalty = if (tokens.size >= 4 && tokens.takeLast(3).distinct().size == 1) 2.0 else 0.0
            val priority = localRank * 0.28 + invalidPenalty + tailPenalty + extraPenalty - (agreement - 1) * 0.45
            Triple(representative, priority, agreement)
        }.sortedWith(compareBy<Triple<Candidate, Double, Int>> { it.second }
            .thenByDescending { it.third }
            .thenBy { sources.indexOf(it.first.source) })
            .map { it.first }
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

    /** Penalize malformed LaTeX structure; never silently rewrite the user's formula. */
    private fun structurePenalty(tokens: List<String>): Double {
        var braces = 0
        var parentheses = 0
        var penalty = 0.0
        for (token in tokens) {
            when (token) {
                "{" -> braces++
                "}" -> { braces--; if (braces < 0) { penalty += 6.0; braces = 0 } }
                "(" -> parentheses++
                ")" -> { parentheses--; if (parentheses < 0) { penalty += 3.0; parentheses = 0 } }
            }
        }
        penalty += braces * 3.0 + parentheses * 1.5
        // Penalize an implausible run of 4+ identical letter tokens.
        // Repeated terms such as x+x remain possible.
        for (i in 3 until tokens.size) {
            val t = tokens[i]
            if (t.length == 1 && t[0].isLetter() && tokens.subList(i-3, i+1).all { it == t }) penalty += 2.5
        }
        return penalty
    }

    private fun isSingleGlyphToken(token: String): Boolean {
        if (token.length == 1 && token[0].isLetterOrDigit()) return true
        return token in setOf("+", "-", "=", "\\pi", "\\alpha", "\\beta", "\\theta", "\\lambda", "\\infty", "\\times", "\\pm")
    }

    private fun repeatedSingleSymbol(ids: List<Long>, vocab: Map<Int, String>): Boolean {
        if (ids.size < 5) return false
        val last = ids.takeLast(4)
        if (last.distinct().size != 1) return false
        val token = vocab[last[0].toInt()] ?: return false
        return token.length == 1 && token[0].isLetter()
    }

    fun close() {
        worker.execute { decoder?.close(); encoder?.close(); decoder = null; encoder = null }
        worker.shutdown()
    }
}
