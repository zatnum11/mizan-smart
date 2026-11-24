package com.mizan.smart

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class MainActivity : Activity() {
    private val deep = Color.rgb(18,60,53)
    private val green = Color.rgb(29,106,86)
    private val pale = Color.rgb(237,245,239)
    private val ink = Color.rgb(23,37,33)
    private lateinit var baseWeight: EditText
    private lateinit var basePrice: EditText
    private lateinit var wantedWeight: EditText
    private lateinit var currency: EditText
    private lateinit var unitValue: TextView
    private lateinit var hundredValue: TextView
    private lateinit var kiloValue: TextView
    private lateinit var targetValue: TextView
    private lateinit var historyBox: LinearLayout
    private lateinit var activeLabel: TextView
    private var activeInput: EditText? = null
    private val history = mutableListOf<JSONObject>()
    private val fmt = DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale.US))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = deep
            window.navigationBarColor = deep
            loadHistory()
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
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = bg(Color.WHITE, 20f)
        elevation = dp(2).toFloat()
    }

    private fun title(s: String): TextView = text(s, 16f, deep, true).apply {
        gravity = Gravity.RIGHT
        setPadding(0, 0, 0, dp(12))
    }

    private fun field(label: String, hint: String, decimal: Boolean = true): EditText {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelView = text(label, 12f, Color.DKGRAY, false)
        labelView.gravity = Gravity.RIGHT
        wrap.addView(labelView)
        val e = EditText(this).apply {
            this.hint = hint
            textSize = 18f
            setTextColor(ink)
            setHintTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        else InputType.TYPE_CLASS_TEXT
            background = bg(Color.rgb(250,252,250), 12f)
            setPadding(dp(8), dp(9), dp(8), dp(9))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isSingleLine = true
        }
        e.onFocusChangeListener = View.OnFocusChangeListener { _, has ->
            if (has) {
                activeInput = e
                activeLabel.text = label
            }
        }
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) { calculate() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        wrap.addView(e, LinearLayout.LayoutParams(-1, dp(49)))
        return e
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(239,243,240))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(deep, Color.rgb(36,107,85))).apply {
                cornerRadii = floatArrayOf(0f,0f,0f,0f,dp(25).toFloat(),dp(25).toFloat(),dp(25).toFloat(),dp(25).toFloat())
            }
        }
        header.addView(text("⚖️  الميزان الذكي", 23f, Color.WHITE, true))
        header.addView(text("حاسبة أسعار وأوزان — مثل الميزان التجاري", 12f, Color.rgb(218,237,226)))
        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(25))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val baseCard = card()
        baseCard.addView(title("① بيانات الوزن والسعر الأساسي"))
        val baseRow = horizontal()
        val col1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val col2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        baseWeight = field("الوزن الإجمالي (جرام)", "مثلاً 1000")
        basePrice = field("السعر الإجمالي", "مثلاً 5000")
        col1.addView(baseWeight); col2.addView(basePrice)
        baseRow.addView(col1, LinearLayout.LayoutParams(0, -2, 1f))
        baseRow.addView(col2, LinearLayout.LayoutParams(0, -2, 1f))
        baseCard.addView(baseRow)
        content.addView(baseCard, marginParams())

        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(deep, Color.rgb(27,93,76))).apply { cornerRadius = dp(18).toFloat() }
        }
        display.addView(text("سعر الجرام الواحد", 13f, Color.rgb(213,235,222)).apply { gravity = Gravity.CENTER })
        unitValue = text("—", 38f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        display.addView(unitValue)
        display.addView(text("سعر 100 جرام                 سعر الكيلو", 11f, Color.rgb(213,235,222)).apply { gravity = Gravity.CENTER })
        val stats = horizontal()
        hundredValue = text("—", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        kiloValue = text("—", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        stats.addView(hundredValue, LinearLayout.LayoutParams(0, dp(35), 1f))
        stats.addView(kiloValue, LinearLayout.LayoutParams(0, dp(35), 1f))
        display.addView(stats)
        content.addView(display, marginParams())

        val targetCard = card()
        targetCard.addView(title("② حساب سعر وزن محدد"))
        val row = horizontal()
        val qcol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val ccol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wantedWeight = field("الوزن المطلوب (جرام)", "مثلاً 250")
        currency = field("العملة (اختياري)", "ريال", false)
        qcol.addView(wantedWeight); ccol.addView(currency)
        row.addView(qcol, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(ccol, LinearLayout.LayoutParams(0,-2,1f))
        targetCard.addView(row)
        val result = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10)); background = bg(pale, 14f)
        }
        result.addView(text("السعر المستحق لهذا الوزن", 12f, green).apply { gravity = Gravity.CENTER })
        targetValue = text("—", 30f, deep, true).apply { gravity = Gravity.CENTER }
        result.addView(targetValue)
        targetCard.addView(result, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val add = button("＋ أضف إلى قائمة الحسابات", green, Color.WHITE)
        add.setOnClickListener { addCurrent() }
        targetCard.addView(add, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        val direct = button("بيع مباشر: اعتماد الوزن وإضافة العملية", deep, Color.WHITE)
        direct.setOnClickListener { addCurrent(); wantedWeight.setText(""); wantedWeight.requestFocus() }
        targetCard.addView(direct, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        content.addView(targetCard, marginParams())

        val padCard = card()
        padCard.addView(title("③ لوحة الأرقام السريعة"))
        activeLabel = text("الحقل المحدد: الوزن الإجمالي", 12f, green, true)
        activeLabel.gravity = Gravity.RIGHT
        padCard.addView(activeLabel)
        val keys = listOf("7","8","9","4","5","6","1","2","3","0",".","⌫","مسح الحقل")
        keys.chunked(3).forEach { chunk ->
            val r = horizontal()
            chunk.forEach { key ->
                val b = button(key, if (key=="⌫" || key=="مسح الحقل") Color.rgb(255,240,231) else Color.rgb(237,243,239),
                    if (key=="⌫" || key=="مسح الحقل") Color.rgb(166,77,34) else deep)
                b.textSize = if (key=="مسح الحقل") 12f else 19f
                b.setOnClickListener { keypad(key) }
                r.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3),dp(4),dp(3),dp(4)) })
            }
            padCard.addView(r)
        }
        content.addView(padCard, marginParams())

        val historyCard = card()
        historyCard.addView(title("④ سجل العمليات"))
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyCard.addView(historyBox)
        val controls = horizontal()
        val clear = button("مسح السجل", Color.rgb(237,243,239), deep)
        clear.setOnClickListener { history.clear(); saveHistory(); renderHistory() }
        val share = button("مشاركة النتائج", Color.rgb(237,243,239), deep)
        share.setOnClickListener { shareHistory() }
        controls.addView(clear, LinearLayout.LayoutParams(0, dp(45), 1f).apply { setMargins(dp(3),dp(8),dp(3),0) })
        controls.addView(share, LinearLayout.LayoutParams(0, dp(45), 1f).apply { setMargins(dp(3),dp(8),dp(3),0) })
        historyCard.addView(controls)
        content.addView(historyCard, marginParams())

        val reset = button("تصفير العملية الحالية", Color.rgb(255,240,231), Color.rgb(166,77,34))
        reset.setOnClickListener {
            baseWeight.setText(""); basePrice.setText(""); wantedWeight.setText("")
            calculate(); baseWeight.requestFocus()
        }
        content.addView(reset, marginParams())
        setContentView(root)
        baseWeight.requestFocus()
    }

    private fun horizontal() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun button(label: String, color: Int, fg: Int) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false; setTextColor(fg)
        background = bg(color, 12f); stateListAnimator = null
        minimumHeight = 0; minHeight = 0; setPadding(dp(7),0,dp(7),0)
    }

    private fun marginParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun number(e: EditText): Double? = e.text.toString().trim().replace(',', '.').toDoubleOrNull()

    private fun calculate() {
        val w = number(baseWeight); val p = number(basePrice); val q = number(wantedWeight)
        val unit = if (w != null && p != null && w > 0 && p >= 0) p / w else null
        unitValue.text = unit?.let { fmt.format(it) } ?: "—"
        hundredValue.text = unit?.let { fmt.format(it * 100) } ?: "—"
        kiloValue.text = unit?.let { fmt.format(it * 1000) } ?: "—"
        targetValue.text = if (unit != null && q != null && q >= 0) fmt.format(unit*q) else "—"
    }

    private fun keypad(key: String) {
        val e = activeInput ?: baseWeight
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

    private fun addCurrent() {
        val w = number(baseWeight); val p = number(basePrice); val q = number(wantedWeight)
        if (w == null || p == null || q == null || w <= 0 || p < 0 || q < 0) {
            Toast.makeText(this, "أدخل الوزن والسعر الإجمالي والوزن المطلوب بشكل صحيح", Toast.LENGTH_LONG).show()
            return
        }
        val unit = p/w
        val item = JSONObject().put("weight",q).put("price",unit*q).put("unit",unit)
            .put("currency",currency.text.toString().trim())
        history.add(0,item)
        saveHistory(); renderHistory()
        Toast.makeText(this, "تمت إضافة العملية إلى السجل", Toast.LENGTH_SHORT).show()
    }

    private fun renderHistory() {
        if (!::historyBox.isInitialized) return
        historyBox.removeAllViews()
        if (history.isEmpty()) {
            historyBox.addView(text("لا توجد عمليات مسجلة بعد.", 12f, Color.GRAY).apply {
                gravity = Gravity.CENTER; setPadding(0,dp(12),0,dp(12))
            })
            return
        }
        history.take(50).forEachIndexed { index, item ->
            val weight = item.optDouble("weight")
            val price = item.optDouble("price")
            val cur = item.optString("currency")
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10),dp(9),dp(10),dp(9)); background = bg(Color.rgb(248,250,248),12f)
            }
            val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            left.addView(text("${fmt.format(weight)} جرام",14f,deep,true))
            left.addView(text("سعر الجرام: ${fmt.format(item.optDouble("unit"))}",10f,Color.GRAY))
            line.addView(left, LinearLayout.LayoutParams(0,-2,1f))
            line.addView(text("${fmt.format(price)} $cur",15f,deep,true))
            historyBox.addView(line, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) })
        }
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.forEach { arr.put(it) }
        getSharedPreferences("mizan", Context.MODE_PRIVATE).edit().putString("history",arr.toString()).apply()
    }
    private fun loadHistory() {
        val raw = getSharedPreferences("mizan", Context.MODE_PRIVATE).getString("history","[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) history.add(arr.getJSONObject(i))
        } catch (_: Exception) {}
    }
    private fun shareHistory() {
        if (history.isEmpty()) {
            Toast.makeText(this,"السجل فارغ",Toast.LENGTH_SHORT).show(); return
        }
        val body = history.take(50).joinToString("\n") {
            "${fmt.format(it.optDouble("weight"))} جرام = ${fmt.format(it.optDouble("price"))} ${it.optString("currency")}"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT,"سجل الميزان الذكي")
            putExtra(Intent.EXTRA_TEXT,"نتائج الميزان الذكي\n$body")
        }
        startActivity(Intent.createChooser(intent,"مشاركة النتائج"))
    }
}
