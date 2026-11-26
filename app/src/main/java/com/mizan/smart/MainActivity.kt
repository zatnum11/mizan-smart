package com.mizan.smart

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val deep = Color.rgb(18, 60, 53)
    private val green = Color.rgb(29, 106, 86)
    private val pale = Color.rgb(237, 245, 239)
    private val ink = Color.rgb(23, 37, 33)
    private val accentOrange = Color.rgb(166, 77, 34)
    private val softOrange = Color.rgb(255, 240, 231)

    // حقول الإدخال
    private lateinit var baseWeight: EditText
    private lateinit var basePrice: EditText
    private lateinit var calcInput: EditText
    private lateinit var calcInputLabel: TextView
    private lateinit var tareInput: EditText
    private lateinit var discountInput: EditText
    private lateinit var currencyInput: EditText

    // عناصر العرض
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

    // البطاقات القابلة للإخفاء في الوضع السريع
    private lateinit var baseCard: LinearLayout
    private lateinit var displayCard: LinearLayout
    private lateinit var historyCard: LinearLayout
    private lateinit var resetButton: Button

    // السجل وذاكرة الأسعار
    private lateinit var historySummary: TextView
    private lateinit var historyBox: LinearLayout
    private var activeInput: EditText? = null

    private var reverseMode = false       // false: الوزن -> السعر | true: السعر -> الوزن
    private var discountIsPercent = true  // true: % | false: مبلغ ثابت
    private var quickMode = false         // وضع الاستخدام السريع
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
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) { calculate() }
            override fun afterTextChanged(s: android.text.Editable?) {}
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

        // الشريط العلوي
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

        // ① بطاقة التسعير الأساسي + العملات + حفظ الصنف
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
        val currencies = listOf("ر.ي", "ر.س", "$")
        currencies.forEach { c ->
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

        // لوحة الأسعار المرجعية
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

        // ② بطاقة الحساب (الوزن <-> السعر + العبوة + الخصم)
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

        // ③ لوحة الأرقام السريعة
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

        // ④ سجل المبيعات الاحترافي
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
        background = bg(color, 12f); stateL
