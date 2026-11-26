package com.mizan.smart

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (name.contains("..") || name.contains("/")) throw FileNotFoundException()
        val dir = File(ctx.cacheDir, "exports")
        val file = File(dir, name)
        if (!file.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val ctx = context!!
        val name = uri.lastPathSegment ?: "report"
        val file = File(File(ctx.cacheDir, "exports"), name)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        val row = cols.map { col ->
            when (col) {
                OpenableColumns.DISPLAY_NAME -> name
                OpenableColumns.SIZE -> if (file.exists()) file.length() else 0L
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val name = uri.lastPathSegment?.lowercase(Locale.US) ?: ""
        return when {
            name.endsWith(".csv") -> "text/csv"
            name.endsWith(".pdf") -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

class MainActivity : Activity() {
    private val deep = Color.rgb(18, 60, 53)
    private val green = Color.rgb(29, 106, 86)
    private val pale = Color.rgb(237, 245, 239)
    private val ink = Color.rgb(23, 37, 33)
    private val accentOrange = Color.rgb(166, 77, 34)
    private val softOrange = Color.rgb(255, 240, 231)

    private lateinit var baseWeight: EditText
    private lateinit var basePrice: EditText
    private lateinit var calcInput: EditText
    private lateinit var calcInputLabel: TextView
    private lateinit var tareInput: EditText
    private lateinit var discountInput: EditText
    private lateinit var currencyInput: EditText

    private lateinit var unitValue: TextView
    private lateinit var hundredValue: TextView
    private lateinit var kiloValue: TextView
    private lateinit var resultTitle: TextView
    private lateinit var targetValue: TextView
    private lateinit var targetSubInfo: TextView
    private lateinit var swapButton: Button
    private lateinit var discountTypeBtn: Button
    private lateinit var quickModeBtn: Button
    private lateinit var activeLabel: TextView

    private lateinit var baseCard: LinearLayout
    private lateinit var displayCard: LinearLayout
    private lateinit var historyCard: LinearLayout
    private lateinit var resetButton: Button

    private lateinit var historySummary: TextView
    private lateinit var historyBox: LinearLayout
    private var activeInput: EditText? = null

    private var reverseMode = false
    private var discountIsPercent = true
    private var quickMode = false
    private var currentProductName = ""

    private val history = mutableListOf<JSONObject>()
    private val savedProducts = mutableListOf<JSONObject>()
    private val fmt = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
    private val timeFmt = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = deep
            window.navigationBarColor = deep
            loadData()
            buildScreen()
            calculate()
            renderHistory()
        } catch (e: Throwable) {
            val err = TextView(this).apply {
                text = "خطأ عند التشغيل:\n${e::class.java.simpleName}: ${e.message}\n\n${e.stackTraceToString().take(900)}"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(120, 20, 20))
                setPadding(32, 48, 32, 32)
                textSize = 13f
            }
            setContentView(err)
        }
    }

    private fun bg(color: Int, radius: Float = 18f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = bg(Color.WHITE, 20f)
        elevation = dp(2).toFloat()
    }

    private fun title(s: String): TextView = text(s, 15f, deep, true).apply {
        gravity = Gravity.RIGHT
        setPadding(0, 0, 0, dp(10))
    }

    private fun field(
        label: String,
        hint: String,
        target: (EditText) -> Unit,
        labelRef: ((TextView) -> Unit)? = null,
        decimal: Boolean = true,
        defaultText: String = ""
    ): LinearLayout {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelView = text(label, 11.5f, Color.DKGRAY, false)
        labelView.gravity = Gravity.RIGHT
        labelRef?.invoke(labelView)
        wrap.addView(labelView)
        val e = EditText(this).apply {
            this.hint = hint
            if (defaultText.isNotEmpty()) setText(defaultText)
            textSize = 17f
            setTextColor(ink)
            setHintTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else InputType.TYPE_CLASS_TEXT
            background = bg(Color.rgb(250, 252, 250), 12f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isSingleLine = true
        }
        e.onFocusChangeListener = View.OnFocusChangeListener { _, has ->
            if (has) {
                activeInput = e
                if (::activeLabel.isInitialized) {
                    activeLabel.text = "الحقل المحدد: ${labelView.text}"
                }
            }
        }
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) { calculate() }
            override fun afterTextChanged(s: Editable?) {}
        })
        target(e)
        wrap.addView(e, LinearLayout.LayoutParams(-1, dp(46)))
        return wrap
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(239, 243, 240))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(deep, Color.rgb(36, 107, 85))
            ).apply {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat())
            }
        }

        val topBar = horizontal()
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("⚖️ الميزان الذكي", 21f, Color.WHITE, true))
        titleBox.addView(text("حاسبة تجارية ذكية", 11f, Color.rgb(218, 237, 226)))
        topBar.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))

        val presetsBtn = button("📦 الأصناف", Color.rgb(42, 122, 98), Color.WHITE).apply {
            textSize = 12f
            setOnClickListener { showProductsDialog() }
        }
        topBar.addView(presetsBtn, LinearLayout.LayoutParams(-2, dp(36)).apply { setMargins(dp(4), 0, dp(4), 0) })

        quickModeBtn = button("⚡ وضع سريع", Color.rgb(42, 122, 98), Color.WHITE).apply {
            textSize = 12f
            setOnClickListener { toggleQuickMode() }
        }
        topBar.addView(quickModeBtn, LinearLayout.LayoutParams(-2, dp(36)))

        header.addView(topBar)
        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(25))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        baseCard = card()
        val baseHeader = horizontal()
        baseHeader.addView(title("① التسعير الأساسي والعملة"), LinearLayout.LayoutParams(0, -2, 1f))
        val saveProdBtn = button("＋ حفظ كصنف", pale, deep).apply {
            textSize = 11.5f
            setOnClickListener { promptSaveProduct() }
        }
        baseHeader.addView(saveProdBtn, LinearLayout.LayoutParams(-2, dp(32)))
        baseCard.addView(baseHeader)

        val baseRow = horizontal()
        val col1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val col2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col1.addView(field("الوزن الأساسي (جرام)", "1000", { baseWeight = it }, defaultText = "1000"))
        col2.addView(field("السعر الأساسي", "5000", { basePrice = it }))
        baseRow.addView(col1, LinearLayout.LayoutParams(0, -2, 1f))
        baseRow.addView(col2, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(8), 0) })
        baseCard.addView(baseRow)

        val curRow = horizontal().apply { setPadding(0, dp(8), 0, 0) }
        listOf("ر.ي", "ر.س", "$").forEach { c ->
            val cb = button(c, pale, deep).apply {
                textSize = 12f
                setOnClickListener { currencyInput.setText(c); calculate() }
            }
            curRow.addView(cb, LinearLayout.LayoutParams(dp(44), dp(38)).apply { setMargins(dp(3), dp(14), dp(3), 0) })
        }
        val curFieldWrap = field("العملة", "ر.ي", { currencyInput = it }, decimal = false, defaultText = "ر.ي")
        curRow.addView(curFieldWrap, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        baseCard.addView(curRow)

        content.addView(baseCard, marginParams())

        displayCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(deep, Color.rgb(27, 93, 76))
            ).apply { cornerRadius = dp(18).toFloat() }
        }
        displayCard.addView(text("سعر الجرام الواحد", 12f, Color.rgb(213, 235, 222)).apply { gravity = Gravity.CENTER })
        unitValue = text("—", 32f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        displayCard.addView(unitValue)
        displayCard.addView(text("سعر 100 جرام                 سعر الكيلو", 11f, Color.rgb(213, 235, 222)).apply { gravity = Gravity.CENTER })
        val stats = horizontal()
        hundredValue = text("—", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        kiloValue = text("—", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        stats.addView(hundredValue, LinearLayout.LayoutParams(0, dp(30), 1f))
        stats.addView(kiloValue, LinearLayout.LayoutParams(0, dp(30), 1f))
        displayCard.addView(stats)
        content.addView(displayCard, marginParams())

        val targetCard = card()
        targetCard.addView(title("② حاسبة البيع"))

        val swapBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = bg(Color.rgb(245, 249, 246), 14f)
        }
        swapBar.addView(text("الوزن", 14f, deep, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        swapButton = button("⇄ تبديل", pale, deep).apply {
            textSize = 12.5f
            setOnClickListener { toggleMode() }
        }
        swapBar.addView(swapButton, LinearLayout.LayoutParams(-2, dp(34)).apply { setMargins(dp(6), 0, dp(6), 0) })
        swapBar.addView(text("السعر", 14f, deep, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        targetCard.addView(swapBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        targetCard.addView(field(
            "الوزن الإجمالي الموزون (جرام)",
            "مثلاً 520",
            { calcInput = it },
            { calcInputLabel = it }
        ))

        val extrasRow = horizontal().apply { setPadding(0, dp(8), 0, 0) }
        val tareCol = field("وزن العبوة (جرام)", "0", { tareInput = it })
        extrasRow.addView(tareCol, LinearLayout.LayoutParams(0, -2, 1f))

        val discCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val discSubRow = horizontal()
        val discField = field("الخصم", "0", { discountInput = it })
        discSubRow.addView(discField, LinearLayout.LayoutParams(0, -2, 1f))
        discountTypeBtn = button("%", pale, deep).apply {
            textSize = 13f
            setOnClickListener {
                discountIsPercent = !discountIsPercent
                text = if (discountIsPercent) "%" else "مبلغ"
                calculate()
            }
        }
        discSubRow.addView(discountTypeBtn, LinearLayout.LayoutParams(dp(48), dp(46)).apply {
            setMargins(0, dp(16), dp(4), 0)
        })
        discCol.addView(discSubRow)
        extrasRow.addView(discCol, LinearLayout.LayoutParams(0, -2, 1.15f).apply { setMargins(0, 0, dp(8), 0) })
        targetCard.addView(extrasRow)

        val result = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10)); background = bg(pale, 14f)
        }
        resultTitle = text("السعر المستحق", 12f, green).apply { gravity = Gravity.CENTER }
        result.addView(resultTitle)
        targetValue = text("—", 29f, deep, true).apply { gravity = Gravity.CENTER }
        result.addView(targetValue)
        targetSubInfo = text("", 11f, green).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        result.addView(targetSubInfo)
        targetCard.addView(result, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val actionRow = horizontal().apply { setPadding(0, dp(10), 0, 0) }
        val add = button("＋ أضف للسجل", green, Color.WHITE).apply {
            setOnClickListener { addCurrent() }
        }
        val direct = button("✓ بيع وتصفير", deep, Color.WHITE).apply {
            setOnClickListener {
                if (addCurrent()) {
                    calcInput.setText("")
                    calcInput.requestFocus()
                }
            }
        }
        actionRow.addView(add, LinearLayout.LayoutParams(0, dp(46), 1f))
        actionRow.addView(direct, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(8), 0) })
        targetCard.addView(actionRow)
        content.addView(targetCard, marginParams())

        val padCard = card()
        padCard.addView(title("③ لوحة الأرقام السريعة"))
        activeLabel = text("الحقل المحدد: الوزن الإجمالي الموزون", 11.5f, green, true).apply {
            gravity = Gravity.RIGHT
        }
        padCard.addView(activeLabel)
        val keys = listOf("7", "8", "9", "4", "5", "6", "1", "2", "3", "0", ".", "⌫", "مسح الحقل")
        keys.chunked(3).forEach { chunk ->
            val r = horizontal()
            chunk.forEach { key ->
                val isAction = (key == "⌫" || key == "مسح الحقل")
                val b = button(
                    key,
                    if (isAction) softOrange else Color.rgb(237, 243, 239),
                    if (isAction) accentOrange else deep
                )
                b.textSize = if (key == "مسح الحقل") 12f else 19f
                b.setOnClickListener { keypad(key) }
                r.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            padCard.addView(r)
        }
        content.addView(padCard, marginParams())

        historyCard = card()
        historyCard.addView(title("④ سجل المبيعات"))
        historySummary = text("العمليات: 0 | الإجمالي: 0", 12.5f, deep, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = bg(pale, 10f)
        }
        historyCard.addView(historySummary, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyCard.addView(historyBox)

        val controls = horizontal()
        val exportCsv = button("تصدير Excel", pale, deep).apply {
            textSize = 12f
            setOnClickListener { exportToCsv() }
        }
        val exportPdf = button("تصدير PDF", pale, deep).apply {
            textSize = 12f
            setOnClickListener { exportToPdf() }
        }
        val clear = button("مسح الكل", softOrange, accentOrange).apply {
            textSize = 12f
            setOnClickListener { confirmClearHistory() }
        }
        controls.addView(exportCsv, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(8), dp(2), 0) })
        controls.addView(exportPdf, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(8), dp(2), 0) })
        controls.addView(clear, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(8), dp(2), 0) })
        historyCard.addView(controls)
        content.addView(historyCard, marginParams())

        resetButton = button("تصفير الحقول الحالية", softOrange, accentOrange).apply {
            setOnClickListener {
                calcInput.setText(""); tareInput.setText(""); discountInput.setText("")
                calculate(); calcInput.requestFocus()
            }
        }
        content.addView(resetButton, marginParams())

        setContentView(root)
        calcInput.requestFocus()
    }

    private fun toggleQuickMode() {
        quickMode = !quickMode
        val vis = if (quickMode) View.GONE else View.VISIBLE
        baseCard.visibility = vis
        displayCard.visibility = vis
        historyCard.visibility = vis
        resetButton.visibility = vis
        quickModeBtn.text = if (quickMode) "🔓 إظهار الكل" else "⚡ وضع سريع"
        quickModeBtn.background = bg(if (quickMode) accentOrange else Color.rgb(42, 122, 98), 12f)
    }

    private fun toggleMode() {
        reverseMode = !reverseMode
        if (reverseMode) {
            calcInputLabel.text = "المبلغ المطلوب"
            calcInput.hint = "مثلاً 500"
            resultTitle.text = "الوزن الصافي المقابل (جرام)"
            swapButton.background = bg(green, 12f)
            swapButton.setTextColor(Color.WHITE)
        } else {
            calcInputLabel.text = "الوزن الإجمالي الموزون (جرام)"
            calcInput.hint = "مثلاً 520"
            resultTitle.text = "السعر المستحق"
            swapButton.background = bg(pale, 12f)
            swapButton.setTextColor(deep)
        }
        if (activeInput == calcInput) {
            activeLabel.text = "الحقل المحدد: ${calcInputLabel.text}"
        }
        calculate()
    }

    private fun horizontal() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun button(label: String, color: Int, fg: Int) = Button(this).apply {
        text = label; textSize = 13.5f; isAllCaps = false; setTextColor(fg)
        background = bg(color, 12f); stateListAnimator = null
        minimumHeight = 0; minHeight = 0; setPadding(dp(6), 0, dp(6), 0)
    }

    private fun marginParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun number(e: EditText): Double? = e.text.toString().trim().replace(',', '.').toDoubleOrNull()

    private fun calculate() {
        val w = number(baseWeight)
        val p = number(basePrice)
        val x = number(calcInput)
        val tare = number(tareInput) ?: 0.0
        val disc = number(discountInput) ?: 0.0
        val cur = currencyInput.text.toString().trim()

        val unit = if (w != null && p != null && w > 0 && p >= 0) p / w else null
        unitValue.text = unit?.let { fmt.format(it) } ?: "—"
        hundredValue.text = unit?.let { fmt.format(it * 100) } ?: "—"
        kiloValue.text = unit?.let { fmt.format(it * 1000) } ?: "—"

        if (unit == null || x == null || x < 0) {
            targetValue.text = "—"
            targetSubInfo.visibility = View.GONE
            return
        }

        val infoParts = mutableListOf<String>()

        if (!reverseMode) {
            val netWeight = (x - tare).coerceAtLeast(0.0)
            if (tare > 0) infoParts.add("الوزن الصافي: ${fmt.format(netWeight)} جم")
            val rawPrice = netWeight * unit
            val finalPrice = if (discountIsPercent) {
                (rawPrice * (1.0 - (disc / 100.0))).coerceAtLeast(0.0)
            } else {
                (rawPrice - disc).coerceAtLeast(0.0)
            }
            if (disc > 0) infoParts.add("قبل الخصم: ${fmt.format(rawPrice)}")
            targetValue.text = if (cur.isNotEmpty()) "${fmt.format(finalPrice)} $cur" else fmt.format(finalPrice)
        } else {
            if (unit <= 0) {
                targetValue.text = "—"
                targetSubInfo.visibility = View.GONE
                return
            }
            val effectiveAmount = if (discountIsPercent) {
                if (disc < 100) x / (1.0 - (disc / 100.0)) else x
            } else {
                x + disc
            }
            val netWeight = effectiveAmount / unit
            val grossWeight = netWeight + tare
            targetValue.text = "${fmt.format(netWeight)} جرام"
            if (tare > 0) infoParts.add("الوزن الإجمالي مع العبوة: ${fmt.format(grossWeight)} جم")
            if (disc > 0) infoParts.add("شامل خصم: ${fmt.format(disc)}${if (discountIsPercent) "%" else " $cur"}")
        }

        if (infoParts.isNotEmpty()) {
            targetSubInfo.text = infoParts.joinToString("  •  ")
            targetSubInfo.visibility = View.VISIBLE
        } else {
            targetSubInfo.visibility = View.GONE
        }
    }

    private fun keypad(key: String) {
        val e = activeInput ?: calcInput
        val old = e.text.toString()
        when (key) {
            "مسح الحقل" -> e.setText("")
            "⌫" -> if (old.isNotEmpty()) e.setText(old.dropLast(1))
            "." -> if (!old.contains(".")) e.setText(if (old.isEmpty()) "0." else old + ".")
            else -> e.setText(old + key)
        }
        e.setSelection(e.text.length)
        e.requestFocus()
    }

    private fun addCurrent(): Boolean {
        val w = number(baseWeight)
        val p = number(basePrice)
        val x = number(calcInput)
        val tare = number(tareInput) ?: 0.0
        val disc = number(discountInput) ?: 0.0
        val cur = currencyInput.text.toString().trim()

        if (w == null || p == null || x == null || w <= 0 || p < 0 || x < 0) {
            Toast.makeText(this, "أدخل البيانات الأساسية والقيمة المطلوبة بشكل صحيح", Toast.LENGTH_SHORT).show()
            return false
        }
        val unit = p / w
        val netWeight: Double
        val finalPrice: Double

        if (!reverseMode) {
            netWeight = (x - tare).coerceAtLeast(0.0)
            val rawPrice = netWeight * unit
            finalPrice = if (discountIsPercent) {
                (rawPrice * (1.0 - (disc / 100.0))).coerceAtLeast(0.0)
            } else {
                (rawPrice - disc).coerceAtLeast(0.0)
            }
        } else {
            if (unit <= 0) return false
            val effectiveAmount = if (discountIsPercent) {
                if (disc < 100) x / (1.0 - (disc / 100.0)) else x
            } else {
                x + disc
            }
            netWeight = effectiveAmount / unit
            finalPrice = x
        }

        val item = JSONObject()
            .put("product", currentProductName)
            .put("weight", netWeight)
            .put("price", finalPrice)
            .put("unit", unit)
            .put("currency", cur)
            .put("time", timeFmt.format(Date()))

        history.add(0, item)
        saveData()
        renderHistory()
        Toast.makeText(this, "تم تسجيل العملية", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun renderHistory() {
        if (!::historyBox.isInitialized) return
        historyBox.removeAllViews()

        val totalSales = history.sumOf { it.optDouble("price", 0.0) }
        val mainCur = currencyInput.text.toString().trim()
        historySummary.text = "عدد العمليات: ${history.size}   |   الإجمالي: ${fmt.format(totalSales)} $mainCur"

        if (history.isEmpty()) {
            historyBox.addView(text("لا توجد عمليات مسجلة بعد.", 12f, Color.GRAY).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
            })
            return
        }

        history.take(100).forEachIndexed { index, item ->
            val weight = item.optDouble("weight")
            val price = item.optDouble("price")
            val cur = item.optString("currency")
            val prod = item.optString("product", "")
            val timeStr = item.optString("time", "")

            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8)); background = bg(Color.rgb(248, 250, 248), 12f)
            }

            val infoCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val headerTitle = if (prod.isNotEmpty()) "$prod — ${fmt.format(weight)} جم" else "${fmt.format(weight)} جرام"
            infoCol.addView(text(headerTitle, 13.5f, deep, true))
            infoCol.addView(text(timeStr, 10f, Color.GRAY))
            line.addView(infoCol, LinearLayout.LayoutParams(0, -2, 1f))

            line.addView(text("${fmt.format(price)} $cur", 14.5f, green, true).apply {
                setPadding(dp(8), 0, dp(8), 0)
            })

            val delBtn = button("✕", softOrange, accentOrange).apply {
                textSize = 12f
                setOnClickListener {
                    history.removeAt(index)
                    saveData()
                    renderHistory()
                }
            }
            line.addView(delBtn, LinearLayout.LayoutParams(dp(32), dp(32)))

            historyBox.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
    }

    private fun promptSaveProduct() {
        val w = number(baseWeight)
        val p = number(basePrice)
        if (w == null || p == null || w <= 0) {
            Toast.makeText(this, "أدخل الوزن والسعر الأساسي أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = "اسم الصنف (مثلاً: بن خولاني، هيل، عسل...)"
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("حفظ تسعيرة الصنف")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val obj = JSONObject()
                        .put("name", name)
                        .put("weight", w)
                        .put("price", p)
                        .put("currency", currencyInput.text.toString().trim())
                    savedProducts.removeAll { it.optString("name") == name }
                    savedProducts.add(0, obj)
                    currentProductName = name
                    saveData()
                    Toast.makeText(this, "تم حفظ الصنف: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showProductsDialog() {
        if (savedProducts.isEmpty()) {
            Toast.makeText(this, "لا توجد أصناف محفوظة بعد. اضغط (＋ حفظ كصنف) لإضافة صنف.", Toast.LENGTH_LONG).show()
            return
        }
        val names = savedProducts.map {
            "${it.optString("name")} (${fmt.format(it.optDouble("weight"))} جم = ${fmt.format(it.optDouble("price"))} ${it.optString("currency")})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("اختر صنفاً من الذاكرة")
            .setItems(names) { _, which ->
                val selected = savedProducts[which]
                currentProductName = selected.optString("name")
                baseWeight.setText(fmt.format(selected.optDouble("weight")).replace(",", ""))
                basePrice.setText(fmt.format(selected.optDouble("price")).replace(",", ""))
                val c = selected.optString("currency")
                if (c.isNotEmpty()) currencyInput.setText(c)
                calculate()
                Toast.makeText(this, "تم تحميل: $currentProductName", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("حذف الأصناف المحفوظة") { _, _ ->
                savedProducts.clear()
                currentProductName = ""
                saveData()
                Toast.makeText(this, "تم مسح ذاكرة الأصناف", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun exportToCsv() {
        if (history.isEmpty()) {
            Toast.makeText(this, "السجل فارغ", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val sb = StringBuilder("\uFEFF")
            sb.append("الصنف,الوزن الصافي (جرام),السعر النهائي,العملة,الوقت والتاريخ\n")
            for (item in history) {
                val prod = item.optString("product").ifEmpty { "عام" }
                val w = item.optDouble("weight")
                val p = item.optDouble("price")
                val c = item.optString("currency")
                val t = item.optString("time")
                sb.append("\"$prod\",$w,$p,\"$c\",\"$t\"\n")
            }
            val dir = File(cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "MizanSmart_Sales.csv")
            file.writeText(sb.toString(), Charsets.UTF_8)
            shareFile(file, "text/csv", "تصدير سجل المبيعات (Excel)")
        } catch (e: Exception) {
            Toast.makeText(this, "فشل التصدير: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToPdf() {
        if (history.isEmpty()) {
            Toast.makeText(this, "السجل فارغ", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 14f
            }
            var y = 50f
            paint.isFakeBoldText = true
            paint.textSize = 18f
            canvas.drawText("MizanSmart Sales Report", 60f, y, paint)
            y += 28f
            paint.textSize = 13f
            paint.isFakeBoldText = false
            val total = history.sumOf { it.optDouble("price", 0.0) }
            canvas.drawText("Operations: ${history.size}  |  Total: ${fmt.format(total)}", 60f, y, paint)
            y += 25f
            canvas.drawLine(40f, y, 555f, y, paint)
            y += 22f

            for (item in history.take(32)) {
                val prod = item.optString("product").ifEmpty { "Item" }
                val lineText = "$prod | ${fmt.format(item.optDouble("weight"))} g | ${fmt.format(item.optDouble("price"))} ${item.optString("currency")} | ${item.optString("time")}"
                canvas.drawText(lineText, 60f, y, paint)
                y += 22f
            }
            pdf.finishPage(page)

            val dir = File(cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "MizanSmart_Report.pdf")
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()
            shareFile(file, "application/pdf", "تصدير تقرير المبيعات (PDF)")
        } catch (e: Exception) {
            Toast.makeText(this, "فشل إنشاء PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File, mime: String, title: String) {
        val uri = Uri.parse("content://${packageName}.provider/${file.name}")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun confirmClearHistory() {
        if (history.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("مسح السجل")
            .setMessage("هل تريد حذف جميع العمليات المسجلة؟")
            .setPositiveButton("نعم") { _, _ ->
                history.clear()
                saveData()
                renderHistory()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun saveData() {
        val hArr = JSONArray()
        history.forEach { hArr.put(it) }
        val pArr = JSONArray()
        savedProducts.forEach { pArr.put(it) }
        getSharedPreferences("mizan", Context.MODE_PRIVATE).edit()
            .putString("history", hArr.toString())
            .putString("products", pArr.toString())
            .apply()
    }

    private fun loadData() {
        val prefs = getSharedPreferences("mizan", Context.MODE_PRIVATE)
        try {
            val hArr = JSONArray(prefs.getString("history", "[]") ?: "[]")
            for (i in 0 until hArr.length()) history.add(hArr.getJSONObject(i))
        } catch (_: Exception) {}
        try {
            val pArr = JSONArray(prefs.getString("products", "[]") ?: "[]")
            for (i in 0 until pArr.length()) savedProducts.add(pArr.getJSONObject(i))
        } catch (_: Exception) {}
    }
}
