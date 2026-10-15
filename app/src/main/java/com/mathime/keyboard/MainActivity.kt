package com.mathime.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*density).toInt(), (28*density).toInt(), (20*density).toInt(), 0)
        }
        root.addView(TextView(this).apply {
            text = "MathIME 0.5（離線 ONNX 推論介面）\n\n不需要 API Key，不使用網路。\n\n需要在 assets 放入相容的 math_ctc.onnx 與 tokens.txt。尚未附模型權重，因此目前不能辨識。\n\n啟用鍵盤後，在 ChatGPT 輸入框切換至 MathIME。"
            textSize = 18f
        })
        root.addView(Button(this).apply {
            text = "開啟輸入法設定"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        setContentView(root)
    }
}
