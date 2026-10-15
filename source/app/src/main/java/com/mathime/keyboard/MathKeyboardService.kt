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
    private var mode = "離線優先"
    private var engine: RecognitionEngine = UnavailableRecognitionEngine()
    private lateinit var ink: HandwritingView
    private lateinit var formula: EditText
    private lateinit var status: TextView
    private lateinit var modeButton: Button
    private val pending = Runnable { recognize() }

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(235, 239, 244))
        }
        status = TextView(this).apply {
            text = "v0.2.1｜辨識引擎尚未安裝，符號及 LaTeX 可正常輸入"
            textSize = 13f
        }
        root.addView(status)
        formula = EditText(this).apply {
            hint = "LaTeX 輸入／修正區"
            textSize = 16f
            setSingleLine(true)
            showSoftInputOnFocus = false
        }
        root.addView(formula)
        ink = HandwritingView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (density * 175).toInt())
            onStrokeFinished = {
                if (auto) {
                    handler.removeCallbacks(pending)
                    handler.postDelayed(pending, 800)
                }
            }
        }
        root.addView(ink)
        fun row(vararg labels: String, action: (String) -> Unit) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            labels.forEach { label ->
                val button = Button(this).apply {
                    text = label
                    textSize = 12f
                    setAllCaps(false)
                    setOnClickListener { action(label) }
                }
                line.addView(button, LinearLayout.LayoutParams(0, (density * 46).toInt(), 1f))
            }
            root.addView(line)
        }
        row("∫", "∂", "∞", "Σ", "√", "π") { addSymbol(it) }
        row("^", "_", "\\frac{}{}", "\\lim_{}", "dx", "=") { addSymbol(it) }
        row("辨識", "復原", "清除", "手動", "輸入", "🌐") { label ->
            when (label) {
                "辨識" -> recognize()
                "復原" -> { handler.removeCallbacks(pending); ink.undo(); status.text = "已復原上一筆" }
                "清除" -> { handler.removeCallbacks(pending); ink.clear(); formula.setText(""); status.text = "已清除" }
                "手動" -> {
                    auto = !auto
                    handler.removeCallbacks(pending)
                    status.text = if (auto) "即時辨識：停筆 0.8 秒觸發（模型尚未安裝）" else "手動辨識：按辨識觸發"
                }
                "輸入" -> {
                    val s = formula.text.toString()
                    if (s.isNotBlank()) {
                        val connection = currentInputConnection
                        if (connection?.commitText(s, 1) == true) status.text = "已輸入 LaTeX" else status.text = "找不到可輸入的文字框"
                    } else status.text = "請先輸入或編輯 LaTeX"
                }
                "🌐" -> (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        row("離線優先", "完全離線", "雲端優先") { selected ->
            mode = selected
            handler.removeCallbacks(pending)
            status.text = "$mode：尚未配置辨識模型／雲端服務；不會上傳筆跡"
        }
        return root
    }
    private fun addSymbol(s: String) {
        val cursor = formula.selectionStart.coerceAtLeast(0)
        formula.text.insert(cursor, s)
    }
    private fun recognize() {
        if (!ink.hasInk()) { status.text = "請先在白色區域書寫"; return }
        val bitmap = ink.exportInkBitmap()
        if (bitmap == null) { status.text = "筆跡影像產生失敗"; return }
        status.text = "已擷取 ${bitmap.width}×${bitmap.height} 筆跡影像；準備辨識"
        engine.recognize(bitmap) { result ->
            handler.post {
                result.onSuccess { formula.setText(it); status.text = "辨識完成，請確認公式" }
                    .onFailure { status.text = it.message ?: "辨識失敗，筆跡已保留" }
                bitmap.recycle()
            }
        }
    }
    override fun onDestroy() { handler.removeCallbacks(pending); super.onDestroy() }
}
