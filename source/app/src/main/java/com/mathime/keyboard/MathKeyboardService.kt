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
import android.widget.FrameLayout
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.text.TextWatcher
import android.text.Editable

/** Offline math IME: CoMER recognition, candidate selection and formula preview. */
class MathKeyboardService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var auto = false
    private var recognizer: OnnxMathRecognizer? = null
    private val symbolRecognizer = StrokeRecognizer()
    private var recognizing = false
    private var nextPrecise = false
    private var candidateIndex = 0
    private var candidateResults: List<OnnxMathRecognizer.Candidate> = emptyList()
    private lateinit var ink: HandwritingView
    private lateinit var formula: EditText
    private lateinit var status: TextView
    private lateinit var preview: WebView
    private var previewReady = false
    private var previewLatex = ""
    private lateinit var previewState: TextView
    private lateinit var candidateLabel: TextView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var editorToggle: Button
    private var editorVisible = false
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
            text = "MathIME v1.0.8 · 手動辨識"
            textSize = 12f
            setTextColor(Color.rgb(60, 70, 86))
        }
        root.addView(status)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "公式預覽 · 離線"
            textSize = 14f
            setTextColor(Color.rgb(32, 48, 72))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        candidateLabel = TextView(this).apply { text = "候選 0/0"; textSize = 12f }
        header.addView(candidateLabel)
        editorToggle = Button(this).apply {
            text = "✎ 編輯"; textSize = 11f; isAllCaps = false
            setOnClickListener {
                editorVisible = !editorVisible
                formula.visibility = if (editorVisible) View.VISIBLE else View.GONE
                text = if (editorVisible) "收合" else "✎ 編輯"
            }
        }
        header.addView(editorToggle)
        root.addView(header)
        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        prevButton = Button(this).apply {
            text = "‹"; textSize = 18f; isAllCaps = false
            setOnClickListener { selectCandidate(-1) }
        }
        nextButton = Button(this).apply {
            text = "›"; textSize = 18f; isAllCaps = false
            setOnClickListener { selectCandidate(1) }
        }
        previewRow.addView(prevButton, LinearLayout.LayoutParams((density * 38).toInt(), (density * 66).toInt()))
        previewReady = false
        preview = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.setSupportZoom(false)
            isVerticalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    previewReady = true
                    updatePreview(previewLatex)
                    refreshPreviewState()
                }
            }
            loadUrl("file:///android_asset/preview.html")
        }
        previewRow.addView(preview, LinearLayout.LayoutParams(0, (density * 86).toInt(), 1f))
        previewRow.addView(nextButton, LinearLayout.LayoutParams((density * 38).toInt(), (density * 66).toInt()))
        root.addView(previewRow)
        previewState = TextView(this).apply { text = "預覽引擎載入中…"; textSize = 11f }
        root.addView(previewState)
        formula = EditText(this).apply {
            hint = "LaTeX 輸入／修正區"
            textSize = 15f
            setSingleLine(true)
            showSoftInputOnFocus = false
            visibility = View.GONE
        }
        root.addView(formula)
        formula.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        refreshCandidateButtons()
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
        row("辨識", "復原", "重寫", "自動/手動", "輸入", "⌫") { label ->
            when (label) {
                "辨識" -> recognize()
                "復原" -> { handler.removeCallbacks(pending); ink.undo(); status.text = "已復原上一筆" }
                "重寫" -> clearHandwriting()
                "自動/手動" -> {
                    auto = !auto
                    handler.removeCallbacks(pending)
                    status.text = if (auto) "MathIME v1.0.8 · 自動辨識" else "MathIME v1.0.8 · 手動辨識"
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
            }
        }
        row("精準辨識", "清空全部", "診斷分享", "🌐") { label ->
            when (label) {
                "精準辨識" -> { nextPrecise = true; recognize() }
                "清空全部" -> { clearHandwriting(); formula.setText(""); candidateResults = emptyList(); candidateIndex = 0; refreshCandidateButtons(); status.text = "已清空筆跡與公式" }
                "診斷分享" -> shareDiagnostics()
                "🌐" -> switchKeyboard()
            }
        }
        return root
    }


    private fun clearHandwriting() {
        handler.removeCallbacks(pending)
        ink.clear()
        status.text = "已清除筆跡，保留公式"
    }

    private fun refreshCandidateButtons() {
        if (!::candidateLabel.isInitialized) return
        candidateLabel.text = "候選 ${if (candidateResults.isEmpty()) 0 else candidateIndex + 1}/${candidateResults.size}"
        prevButton.isEnabled = candidateResults.size > 1
        nextButton.isEnabled = candidateResults.size > 1
        prevButton.alpha = if (prevButton.isEnabled) 1f else .35f
        nextButton.alpha = if (nextButton.isEnabled) 1f else .35f
    }

    private fun selectCandidate(delta: Int) {
        if (candidateResults.size <= 1) return
        candidateIndex = (candidateIndex + delta + candidateResults.size) % candidateResults.size
        val latex = candidateResults[candidateIndex].latex
        formula.setText(latex)
        formula.setSelection(latex.length)
        refreshCandidateButtons()
        status.text = "候選 ${candidateIndex + 1}/${candidateResults.size} · ${candidateResults[candidateIndex].source}，請核對預覽"
    }

    private fun refreshPreviewState() {
        if (!previewReady || !::preview.isInitialized) return
        preview.evaluateJavascript("window.previewStatus ? window.previewStatus() : 'loading'", { value ->
            if (!::previewState.isInitialized) return@evaluateJavascript
            previewState.text = when (value?.trim('"')) {
                "ok", "ready" -> ""
                "error" -> "公式預覽失敗，請展開編輯或分享診斷"
                else -> "預覽引擎載入中…"
            }
        })
    }

    private fun switchKeyboard() {
        val switched = try { switchToNextInputMethod(false) } catch (_: Exception) { false }
        if (!switched) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun shareDiagnostics() {
        val file = recognizer?.diagnosticFile()
        if (file == null || !file.exists()) { status.text = "請先辨識，再分享診斷"; return }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "MathIME CoMER diagnostics")
            putExtra(android.content.Intent.EXTRA_TEXT, file.readText().takeLast(45000))
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(android.content.Intent.createChooser(intent, "分享辨識診斷").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun updatePreview(latex: String) {
        previewLatex = latex
        if (!previewReady || !::preview.isInitialized) return
        val json = org.json.JSONObject.quote(latex)
        preview.evaluateJavascript("render($json);", null)
        handler.postDelayed({ if (::preview.isInitialized) refreshPreviewState() }, 350L)
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
        val precise = nextPrecise
        nextPrecise = false
        val variants = if (precise) ink.exportVariants() else listOfNotNull(ink.exportInkBitmap()?.let { "原始筆跡" to it })
        if (variants.isEmpty()) { status.text = "沒有可辨識的筆跡"; return }
        recognizing = true
        val startMs = android.os.SystemClock.elapsedRealtime()
        status.text = if (precise) "正在精準辨識…" else "正在快速辨識…"
        val engine = try {
            recognizer ?: OnnxMathRecognizer(applicationContext).also { recognizer = it }
        } catch (e: Throwable) {
            recognizing = false
            variants.forEach { it.second.recycle() }
            status.text = "辨識引擎無法啟動：${e.message ?: e.javaClass.simpleName}"
            return
        }
        engine.setPrecisionMode(precise)
        engine.recognizeVariants(variants) { result ->
            val elapsed = android.os.SystemClock.elapsedRealtime() - startMs
            recognizing = false
            result.onSuccess { latex ->
                candidateResults = engine.candidates()
                candidateIndex = 0
                refreshCandidateButtons()
                formula.setText(latex)
                formula.setSelection(latex.length)
                status.text = "候選 1/${candidateResults.size} · ${candidateResults.firstOrNull()?.source ?: ""}，耗時 ${elapsed}ms，請核對預覽"
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

    override fun onDestroy() { handler.removeCallbacks(pending); deleteRepeating = false; handler.removeCallbacks(deleteRepeat); holdRunnable?.let(handler::removeCallbacks); recognizer?.close(); if (::preview.isInitialized) preview.destroy(); super.onDestroy() }
}
