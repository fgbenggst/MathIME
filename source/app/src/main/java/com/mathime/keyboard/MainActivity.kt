package com.mathime.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mathime", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 40, 30, 20) }
        root.addView(TextView(this).apply { text = "MathIME v0.3 實驗版\n\n離線：單筆畫簡易辨識。完整公式：可選擇雲端 AI 辨識，必須由你每次確認上傳。"; textSize = 18f })
        root.addView(Button(this).apply { text = "1. 啟用 MathIME 鍵盤"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        root.addView(Button(this).apply { text = "2. 選擇 MathIME 鍵盤"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })
        root.addView(TextView(this).apply { text = "選用：Gemini API Key（僅保存在此手機的 App 私有設定中；請勿分享 APK 內嵌金鑰）。雲端請求可能產生費用。" })
        val key = EditText(this).apply { hint = "輸入 API Key"; setSingleLine(true); setText(prefs.getString("api_key", "")); inputType = 129 }
        root.addView(key)
        root.addView(Button(this).apply { text = "儲存 API Key"; setOnClickListener { prefs.edit().putString("api_key", key.text.toString().trim()).apply(); android.widget.Toast.makeText(this@MainActivity, "已儲存", android.widget.Toast.LENGTH_SHORT).show() } })
        root.addView(Button(this).apply { text = "刪除 API Key"; setOnClickListener { prefs.edit().remove("api_key").apply(); key.setText("") } })
        setContentView(root)
    }
}
