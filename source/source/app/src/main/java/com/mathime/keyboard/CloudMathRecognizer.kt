package com.mathime.keyboard

import android.graphics.Bitmap
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

/** Opt-in cloud formula OCR. Call ONLY from a worker thread after user consent. */
object CloudMathRecognizer {
    fun recognize(bitmap: Bitmap, apiKey: String): String {
        require(apiKey.isNotBlank()) { "請先在 MathIME 主程式設定 API Key" }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        val parts = JSONArray()
            .put(JSONObject().put("text", "Transcribe the handwritten mathematical expression in this image to valid LaTeX. Output ONLY the LaTeX expression, no markdown fences, no explanation. Preserve fractions, limits, integrals, partial derivatives and matrices. If illegible, output UNCLEAR."))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/png").put("data", encoded)))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", 1024))
        val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 45000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("雲端 HTTP $code：${raw.take(180)}")
            val response = JSONObject(raw)
            val candidates = response.optJSONArray("candidates") ?: throw IllegalStateException("雲端未回傳辨識候選")
            val text = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim()
                ?: throw IllegalStateException("雲端未回傳文字")
            val cleaned = text.removePrefix("```latex").removePrefix("```").removeSuffix("```").trim()
            if (cleaned.isBlank() || cleaned.equals("UNCLEAR", true)) throw IllegalStateException("無法可靠辨識，請重寫或手動修正")
            return cleaned
        } finally { connection.disconnect() }
    }
}
