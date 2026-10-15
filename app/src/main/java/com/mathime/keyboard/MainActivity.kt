package com.mathime.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 40, 30, 20) }
        root.addView(TextView(this).apply { text = "MathIME v0.1\n\n目前為可安裝的鍵盤原型：支援手寫筆跡、數學符號及 LaTeX 直接輸入。數學手寫辨識模型尚未整合。"; textSize = 19f })
        root.addView(Button(this).apply { text = "1. 啟用 MathIME 鍵盤"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        root.addView(Button(this).apply { text = "2. 選擇 MathIME 鍵盤"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })
        setContentView(root)
    }
}
