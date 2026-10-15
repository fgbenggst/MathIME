package com.mathime.keyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MathKeyboardService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var auto = false
    private lateinit var ink: HandwritingView
    private lateinit var formula: EditText
    private lateinit var status: TextView
    private val pending = Runnable { showRecognitionUnavailable() }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 8); setBackgroundColor(Color.rgb(235, 239, 244)) }
        status = TextView(this).apply { text = "手寫辨識尚未整合｜可用符號鍵或輸入 LaTeX"; textSize = 13f }
        root.addView(status)
        formula = EditText(this).apply { hint = "LaTeX 輸入／編輯區"; textSize = 16f; setSingleLine(true); showSoftInputOnFocus = false }
        root.addView(formula)
        ink = HandwritingView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (resources.displayMetrics.density * 175).toInt())
            onStrokeFinished = { if (auto) { handler.removeCallbacks(pending); handler.postDelayed(pending, 800) } }
        }
        root.addView(ink)
        fun row(vararg labels: String, action: (String) -> Unit) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            labels.forEach { label ->
                line.addView(Button(this).apply { text = label; textSize = 13f; setAllCaps(false); setOnClickListener { action(label) } }, LinearLayout.LayoutParams(0, (resources.displayMetrics.density * 46).toInt(), 1f))
            }
            root.addView(line)
        }
        row("∫", "∂", "∞", "Σ", "√", "π") { addSymbol(it) }
        row("^", "_", "\\frac{}{}", "\\lim_{}", "dx", "=") { addSymbol(it) }
        row("辨識", "復原", "清除", "手動", "輸入", "🌐") { label ->
            when (label) {
                "辨識" -> showRecognitionUnavailable()
                "復原" -> ink.undo()
                "清除" -> { ink.clear(); formula.setText("") }
                "手動" -> { auto = !auto; status.text = if (auto) "即時辨識模式（模型尚未整合）" else "手動辨識模式（模型尚未整合）" }
                "輸入" -> { val s = formula.text.toString(); if (s.isNotBlank()) currentInputConnection?.commitText(s, 1) }
                "🌐" -> (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        return root
    }
    private fun addSymbol(s: String) { formula.text.insert(formula.selectionStart.coerceAtLeast(0), s) }
    private fun showRecognitionUnavailable() {
        status.text = if (ink.hasInk()) "已保留手寫筆跡；辨識模型尚未整合，無法自動轉換" else "請先在白色區域書寫"
    }
    override fun onDestroy() { handler.removeCallbacks(pending); super.onDestroy() }
}
