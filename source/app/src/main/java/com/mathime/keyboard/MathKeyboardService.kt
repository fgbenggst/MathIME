package com.mathime.keyboard

import android.graphics.Color
import android.app.AlertDialog
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
    private var busy = false
    private val gestureRecognizer = StrokeRecognizer()
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
            text = "v0.3｜離線單符號；完整公式可按「雲端辨識」"
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
                    status.text = if (auto) "即時辨識：停筆 0.8 秒觸發" else "手動辨識：按辨識觸發"
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
            status.text = "$mode：離線按辨識；雲端必須按雲端辨識並確認上傳"
        }
        row("雲端辨識", "重新輸入") { label ->
            when (label) {
                "雲端辨識" -> confirmCloudRecognition()
                "重新輸入" -> { handler.removeCallbacks(pending); ink.clear(); status.text = "筆跡已清除，可書寫下一題" }
            }
        }
        return root
    }
    private fun addSymbol(s: String) {
        val cursor = formula.selectionStart.coerceAtLeast(0)
        formula.text.insert(cursor, s)
    }
    private fun recognize() {
        if (!ink.hasInk()) { status.text = "請先在白色區域書寫"; return }
        val strokes = ink.getStrokes()
        val guess = gestureRecognizer.recognize(strokes)
        if (guess == null) {
            status.text = "暫無可靠辨識：目前僅支援單筆畫數字／少量符號，請手動輸入 LaTeX（筆跡保留）"
        } else {
            // Do not silently overwrite manually edited formulas.
            val existing = formula.text.toString()
            formula.setText(existing + guess.symbol)
            formula.setSelection(formula.text.length)
            status.text = "候選：${guess.symbol}（相似度 ${(guess.score * 100).toInt()}%，非機率）；請人工確認"
        }
    }
    private fun confirmCloudRecognition() {
        if (mode == "完全離線") { status.text = "完全離線模式禁止雲端上傳"; return }
        if (busy) return
        if (!ink.hasInk()) { status.text = "請先手寫公式"; return }
        val key = getSharedPreferences("mathime", MODE_PRIVATE).getString("api_key", "").orEmpty()
        if (key.isBlank()) { status.text = "請開啟 MathIME 主程式設定 Gemini API Key"; return }
        AlertDialog.Builder(this)
            .setTitle("確認上傳手寫公式？")
            .setMessage("這次會將目前手寫區域的圖片傳送給 Google Gemini API 辨識。僅上傳手寫區域，不上傳 ChatGPT 對話內容。可能產生 API 費用。")
            .setNegativeButton("取消", null)
            .setPositiveButton("同意上傳") { _, _ -> runCloudRecognition(key) }
            .show()
    }

    private fun runCloudRecognition(key: String) {
        val bitmap = ink.exportInkBitmap() ?: run { status.text = "沒有可辨識的筆跡"; return }
        busy = true
        status.text = "雲端辨識中…"
        Thread {
            val result = runCatching { CloudMathRecognizer.recognize(bitmap, key) }
            bitmap.recycle()
            handler.post {
                busy = false
                if (!::formula.isInitialized) return@post
                result.onSuccess { latex ->
                    // Never overwrite the user's existing formula silently.
                    formula.setText(latex)
                    formula.setSelection(latex.length)
                    status.text = "雲端候選已產生；請核對公式再按輸入（原始筆跡保留）"
                }.onFailure { error -> status.text = "雲端辨識失敗：${error.message?.take(120)}" }
            }
        }.start()
    }

    override fun onDestroy() { handler.removeCallbacks(pending); super.onDestroy() }
}
