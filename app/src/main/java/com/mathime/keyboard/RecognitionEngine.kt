package com.mathime.keyboard

import android.graphics.Bitmap

/** Future local/cloud models implement this interface. No fake recognition results. */
interface RecognitionEngine {
    val name: String
    fun recognize(image: Bitmap, callback: (Result<String>) -> Unit)
}

class UnavailableRecognitionEngine : RecognitionEngine {
    override val name = "尚未安裝辨識模型"
    override fun recognize(image: Bitmap, callback: (Result<String>) -> Unit) {
        callback(Result.failure(IllegalStateException("尚未整合數學手寫辨識模型；筆跡已保留")))
    }
}
