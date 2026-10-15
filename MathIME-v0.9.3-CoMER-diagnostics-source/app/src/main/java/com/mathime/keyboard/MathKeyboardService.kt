package com.mathime.keyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.MotionEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.os.Build
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Offline-only keyboard. The recognizer is a small template baseline, NOT full formula OCR. */
class MathKeyboardService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var auto = false
    private var recognizer: OnnxMathRecognizer? = null
    private val symbolRecognizer = StrokeRecognizer()
    private var recognizing = false
    private lateinit var ink: HandwritingView
    private lateinit var formula: EditText
    private lateinit var status: TextView
    private val pending = Runnable { recognize() }
    private var deleteRepeating = false
    private var holdRunnable: Runnable? = null
    private var deleteTriggeredByHold = false
    private val deleteRepeat = object : Runnable {
        override fun run() {
            if (!deleteRepeating) return
            deleteCharacter()
            handler.postDelayed(this, 75L)
        }
    }


    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(235, 239, 244))
        }
        status = TextView(this).apply {
            text = "MathIME 0.9.2｜CoMER 空間維度測試"
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
                if (label == "⌫") {
                    // Tap: one character. Hold: start repeating after 450 ms.
                    button.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                deleteTriggeredByHold = false
                                deleteRepeating = false
                                handler.removeCallbacks(deleteRepeat)
                                holdRunnable?.let(handler::removeCallbacks)
                                holdRunnable = Runnable {
                                    if (button.isPressed) {
                                        deleteTriggeredByHold = true
                                        deleteRepeating = true
                                        deleteRepeat.run()
                                    }
                                }.also { handler.postDelayed(it, 450L) }
                                false
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                deleteRepeating = false
                                handler.removeCallbacks(deleteRepeat)
                                holdRunnable?.let(handler::removeCallbacks)
                                false
                            }
                            else -> false
                        }
                    }
                }
                line.addView(button, LinearLayout.LayoutParams(0, (density * 46).toInt(), 1f))
            }
            root.addView(line)
        }
        row("∫", "∂", "∞", "Σ", "√", "π") { addSymbol(it) }
        row("^", "_", "\\frac{}{}", "\\lim_{}", "dx", "=") { addSymbol(it) }
        row("辨識", "復原", "清除", "手動", "輸入", "⌫") { label ->
            when (label) {
                "辨識" -> recognize()
                "復原" -> { handler.removeCallbacks(pending); ink.undo(); status.text = "已復原上一筆" }
                "清除" -> { handler.removeCallbacks(pending); ink.clear(); formula.setText(""); status.text = "已清除" }
                "手動" -> {
                    auto = !auto
                    handler.removeCallbacks(pending)
                    status.text = if (auto) "即時辨識：停筆 0.8 秒" else "手動辨識：按辨識觸發"
                }
                "輸入" -> {
                    val s = formula.text.toString()
                    if (s.isNotBlank()) {
                        val connection = currentInputConnection
                        status.text = if (connection?.commitText(s, 1) == true) "已輸入 LaTeX" else "找不到可輸入的文字框"
                    } else status.text = "請先輸入或編輯 LaTeX"
                }
                "⌫" -> {
                    if (!deleteTriggeredByHold) deleteCharacter()
                    deleteTriggeredByHold = false
                }
                "🌐" -> {
                    // Ask Android to switch to the next enabled IME; picker is a fallback.
                    val switched = try { switchToNextInputMethod(false) } catch (_: Exception) { false }
                    if (!switched) {
                        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    }
                }
            }
        }
        row("重新書寫", "模型狀態", "🌐") { label ->
            if (label == "重新書寫") {
                handler.removeCallbacks(pending)
                ink.clear()
                status.text = "筆跡已清除，LaTeX 保留"
            } else if (label == "🌐") {
                val switched = try { switchToNextInputMethod(false) } catch (_: Exception) { false }
                if (!switched) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            } else status.text = if (hasModelFiles()) "已找到模型檔案；相容性需實際辨識驗證" else "缺少 CoMER ONNX 模型檔案"
        }
        return root
    }

    /** Deletes in the ChatGPT/app text field, not the handwritten canvas.
     * If the user is actively editing the local LaTeX field, delete there instead.
     */
    private fun deleteCharacter() {
        if (formula.hasFocus()) {
            val editable = formula.text
            val start = minOf(formula.selectionStart, formula.selectionEnd).coerceAtLeast(0)
            val end = maxOf(formula.selectionStart, formula.selectionEnd).coerceAtLeast(0)
            if (start != end) {
                editable.delete(start, end)
            } else if (start > 0) {
                val prev = Character.offsetByCodePoints(editable, start, -1)
                editable.delete(prev, start)
            }
            return
        }
        val ic: InputConnection = currentInputConnection ?: run {
            status.text = "找不到可刪除的輸入框"
            return
        }
        try {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingTextInCodePoints(1, 0)
            }
        } catch (_: Exception) {
            status.text = "刪除失敗，請重新點選輸入框"
        }
    }

    private fun addSymbol(s: String) {
        val cursor = formula.selectionStart.coerceAtLeast(0)
        formula.text.insert(cursor, s)
    }

    private fun hasModelFiles(): Boolean = try {
        assets.open("encoder_int8.onnx").close()
        assets.open("decoder_int8.onnx").close()
        true
    } catch (_: java.io.IOException) { false }

    private fun recognize() {
        if (recognizing) return
        if (!ink.hasInk()) { status.text = "請先在白色區域書寫"; return }
        // Prefer actual CoMER inference; fall back only when model assets are missing.
        val hasModel = hasModelFiles()
        if (!hasModel) {
            val strokes = ink.getStrokes()
            val guess = symbolRecognizer.recognize(strokes)
            if (guess == null) {
                status.text = "離線單符號辨識不確定；請使用符號鍵或手動輸入 LaTeX"
            } else {
                val symbol = when (guess.symbol) {
                    "∫" -> "\\int "
                    "∞" -> "\\infty "
                    else -> guess.symbol
                }
                formula.setText(symbol)
                formula.setSelection(symbol.length)
                status.text = "候選：${guess.symbol}（${(guess.score * 100).toInt()}% 相似度，非機率）；請核對"
            }
            return
        }
        val bitmap = ink.exportInkBitmap() ?: run { status.text = "沒有可辨識的筆跡"; return }
        recognizing = true
        status.text = "正在本地模型辨識…"
        val engine = try {
            recognizer ?: OnnxMathRecognizer(applicationContext).also { recognizer = it }
        } catch (e: Throwable) {
            recognizing = false
            bitmap.recycle()
            status.text = "辨識引擎無法啟動：${e.message ?: e.javaClass.simpleName}"
            return
        }
        engine.recognize(bitmap) { result ->
            recognizing = false
            bitmap.recycle()
            result.onSuccess { latex ->
                formula.setText(latex)
                formula.setSelection(latex.length)
                status.text = "本地辨識完成，請核對 LaTeX"
            }.onFailure { error ->
                status.text = error.message ?: "辨識失敗；筆跡已保留"
            }
        }
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        handler.removeCallbacks(pending)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacks(pending)
        deleteRepeating = false
        handler.removeCallbacks(deleteRepeat)
        holdRunnable?.let(handler::removeCallbacks)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() { handler.removeCallbacks(pending); deleteRepeating = false; handler.removeCallbacks(deleteRepeat); holdRunnable?.let(handler::removeCallbacks); recognizer?.close(); super.onDestroy() }
}
