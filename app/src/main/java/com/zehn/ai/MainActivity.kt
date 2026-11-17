package com.zehn.ai
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private var pendingMediaUri: android.net.Uri? = null

    private val galleryPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingMediaUri = uri
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            android.widget.Toast.makeText(
                this, "Media منتخب ہو گیا۔ اب سوال لکھ کر Send دبائیں۔",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

  private lateinit var p: SharedPreferences
  private lateinit var tts: TextToSpeech
  private lateinit var msgs: LinearLayout
  private lateinit var scroll: ScrollView
  private lateinit var orb: Orb
  private lateinit var status: TextView
  private val hist = ArrayList<Pair<String, String>>()
  private val CYAN = 0xFF00E5FF.toInt(); private val PINK = 0xFFFF4FA8.toInt()
  private val VIOLET = 0xFF7C5CFF.toInt(); private val TXT = 0xFFEDEFFA.toInt()
  private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT; private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

  private val speechL = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
    val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    if (t != null) send(t)
  }

  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
  private fun grad(c1: Int, c2: Int, r: Int, o: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT, stroke: Int = 0): GradientDrawable {
    val g = GradientDrawable(o, intArrayOf(c1, c2)); g.cornerRadius = dp(r).toFloat()
    if (stroke != 0) g.setStroke(dp(1), stroke); return g
  }
  private fun glass(r: Int, stroke: Int = 0x5500E5FF) = grad(0xCC101A33.toInt(), 0xCC1B1240.toInt(), r, GradientDrawable.Orientation.TL_BR, stroke)
  private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
  private fun tv(t: String, size: Float = 15f, color: Int = TXT): TextView {
    val v = TextView(this); v.text = t; v.textSize = size; v.setTextColor(color); v.textDirection = View.TEXT_DIRECTION_ANY_RTL; return v
  }
  private fun edit(hint: String, k: String, d: String, lines: Int = 1): EditText {
    val e = EditText(this); e.hint = hint; e.setText(p.getString(k, d)); e.minLines = lines
    e.setTextColor(TXT); e.setHintTextColor(0xFF8A93B2.toInt()); e.background = glass(14, 0x3300E5FF)
    e.setPadding(dp(14), dp(10), dp(14), dp(10)); e.textDirection = View.TEXT_DIRECTION_ANY_RTL; return e
  }
  private fun btn(t: String, c1: Int = VIOLET, c2: Int = PINK, f: () -> Unit): Button {
    val b = Button(this); b.text = t; b.setTextColor(0xFFFFFFFF.toInt()); b.background = grad(c1, c2, 16); b.setOnClickListener { f() }; return b
  }
  private fun lp(top: Int = 8) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }
  private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_LONG).show() }
  private fun page(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }

  private fun bubble(t: String, user: Boolean): TextView {
    val v = tv(t); v.setPadding(dp(14), dp(10), dp(14), dp(10)); v.maxWidth = dp(310)
    v.background = if (user) grad(VIOLET, PINK, 18) else glass(18)
    val l = LinearLayout.LayoutParams(WRAP, WRAP); l.topMargin = dp(6); l.gravity = if (user) Gravity.END else Gravity.START
    runOnUiThread { msgs.addView(v, l); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
    return v
  }

  private fun speak(t: String) {
    val say = t.lines().filter { !it.startsWith("⚙️") }.joinToString(" ").take(500)
    if (say.isBlank()) return
    runOnUiThread { orb.speaking = true }
    thread {
      var ok = false
      if (p.getBoolean("gv", true)) ok = Gem.speak(this, say)
      if (!ok) runOnUiThread {
        tts.setPitch((p.getString("pitch", "1.0") ?: "1.0").toFloatOrNull() ?: 1.0f)
        tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "z")
      }
      runOnUiThread { orb.speaking = false }
    }
  }

  private fun send(text: String) {
        val attachedUri = pendingMediaUri
        if (text.isBlank() && attachedUri != null) {
            android.widget.Toast.makeText(
                this, "میڈیا منتخب ہے؛ پہلے سوال لکھیں۔",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (attachedUri != null) pendingMediaUri = null

    if (text.isBlank()) return
    Gem.stopSpeak()
    bubble(text, true); hist.add(Pair("user", text))
    val th = bubble("✨ ...", false)
    thread {
      var h: List<Pair<String, String>> = hist.takeLast(20)
      while (h.isNotEmpty() && h[0].first != "user") h = h.drop(1)
      val r = if (attachedUri != null)
                    MediaGem.ask(this, Gem.chatSys(this), h, attachedUri, text)
                else
                    Gem.ask(this, Gem.chatSys(this), h)
      runOnUiThread { msgs.removeView(th) }
      if (r == null) { hist.removeAt(hist.size - 1); bubble("ابھی جواب نہیں مل سکا، دوبارہ کوشش کریں۔", false); return@thread }
      val out = ArrayList<String>()
      for (ln in r.lines()) {
        val m = Regex("^\\s*ACTION:\\s*(\\{.*\\})").find(ln)
        if (m != null) {
          try { val j = JSONObject(m.groupValues[1]); out.add("⚙️ " + Tools.run(this, j.getString("tool"), j.optString("arg"))) } catch (e: Exception) {}
        } else out.add(ln)
      }
      val clean = out.joinToString("\n").replace("**", "").trim()
      hist.add(Pair("model", clean)); bubble(clean, false)
      if (p.getBoolean("voice", true)) speak(clean)
    }
  }

  private fun logText(): String {
    return try {
      val a = JSONArray(p.getString("log", "[]")); val sb = StringBuilder()
      for (i in a.length() - 1 downTo 0) sb.append(a.getString(i)).append("\n\n")
      sb.toString().ifBlank { "ابھی کوئی لاگ نہیں" }
    } catch (e: Exception) { "" }
  }

  private fun refreshStatus() {
    val bat = try { (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (e: Exception) { -1 }
    val mode = Gem.MODE_NAMES[p.getInt("mode", 0).coerceIn(0, Gem.MODE_NAMES.size - 1)]
    status.text = "● ONLINE   " + mode + "   🔋" + bat + "%   📲 " + (if (p.getBoolean("on", false)) "ON" else "OFF")
  }

  override fun onResume() { super.onResume(); refreshStatus() }

  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    p = getSharedPreferences("zehn", MODE_PRIVATE)
    tts = TextToSpeech(this) { st ->
      if (st == TextToSpeech.SUCCESS) {
        val r = tts.setLanguage(Locale("ur", "PK"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale("hi", "IN"))
      }
    }
    orb = Orb(this)
    val aid = resources.getIdentifier("avatar", "drawable", packageName)
    if (aid != 0) { try { orb.bmp = BitmapFactory.decodeResource(resources, aid) } catch (e: Exception) {} }
    val nameTv = tv((p.getString("name", "Naira") ?: "Naira").uppercase(), 22f); nameTv.letterSpacing = 0.3f; nameTv.gravity = Gravity.CENTER
    status = tv("", 12f, 0xFF9FB0D9.toInt()); status.gravity = Gravity.CENTER

    // ---- Chat page
    val chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(12), dp(10), dp(12), dp(2)) }
    head.addView(orb, LinearLayout.LayoutParams(dp(150), dp(150)))
    head.addView(nameTv, LinearLayout.LayoutParams(MATCH, WRAP)); head.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))
    val modeSp = Spinner(this); modeSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Gem.MODE_NAMES)
    modeSp.setSelection(p.getInt("mode", 0))
    modeSp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) { p.edit().putInt("mode", pos).apply(); refreshStatus() }
      override fun onNothingSelected(a: AdapterView<*>?) {}
    }
    val vc = CheckBox(this); vc.text = "🔊"; vc.setTextColor(TXT); vc.isChecked = p.getBoolean("voice", true)
    vc.setOnCheckedChangeListener { _, c -> p.edit().putBoolean("voice", c).apply() }
    val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) }
    row1.addView(modeSp); row1.addView(vc)
    val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(2), dp(8), dp(6)) }
    val quick = listOf("🔦 ٹارچ" to "ٹارچ آن کرو", "🔋 بیٹری" to "بیٹری کتنی ہے؟", "🌤 موسم" to "آج کا موسم بتاؤ",
      "📰 خبریں" to "آج کی تازہ خبریں بتاؤ", "▶️ یوٹیوب" to "یوٹیوب پر کوئی اچھی اردو غزل سرچ کرو",
      "📝 ٹو ڈو" to "میرا ٹو ڈو دکھاؤ", "☀️ بریفنگ" to "صبح کی بریفنگ دو")
    quick.forEach { (lbl, msg) ->
      val c = tv(lbl, 13f); c.setPadding(dp(14), dp(8), dp(14), dp(8)); c.background = glass(20); c.setOnClickListener { send(msg) }
      chips.addView(c, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) })
    }
    val chipScroll = HorizontalScrollView(this); chipScroll.isHorizontalScrollBarEnabled = false; chipScroll.addView(chips)
    msgs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(6)) }
    scroll = ScrollView(this); scroll.addView(msgs)
    val inp = edit("پیغام لکھیں", "__t", ""); inp.background = glass(26)
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(10)) }
    val galleryButton = android.widget.Button(this).apply {
        text = "＋"
        textSize = 22f
        isAllCaps = false
        minWidth = 0
        setOnClickListener {
            galleryPicker.launch(arrayOf("image/*", "video/*"))
        }
    }
    row.addView(inp, LinearLayout.LayoutParams(0, WRAP, 1f))
    row.addView(
        galleryButton,
        LinearLayout.LayoutParams(
            (46 * resources.displayMetrics.density).toInt(),
            (48 * resources.displayMetrics.density).toInt()
        )
    )

    row.addView(btn("🎤", CYAN, VIOLET) {
      try {
        speechL.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK"))
      } catch (e: Exception) { toast("مائیک دستیاب نہیں") }
    }, LinearLayout.LayoutParams(dp(54), dp(54)).apply { leftMargin = dp(6) })
    row.addView(btn("➤") { val t = inp.text.toString(); inp.setText(""); send(t) }, LinearLayout.LayoutParams(dp(54), dp(54)).apply { leftMargin = dp(6) })
    chat.addView(head); chat.addView(row1); chat.addView(chipScroll); chat.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f)); chat.addView(row)

    // ---- WhatsApp page
    val wa = page()
    val sw = Switch(this); sw.text = "WhatsApp auto-reply ON/OFF"; sw.setTextColor(TXT); sw.isChecked = p.getBoolean("on", false)
    val waMode = Spinner(this); waMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Gem.MODE_NAMES)
    waMode.setSelection(p.getInt("wamode", 0))
    val rules = edit("کنٹیکٹ رولز: نام=ہدایت (ہر لائن میں ایک)", "rules", "", 4)
    val only = edit("صرف انہیں جواب دو (کومہ سے، خالی = سب)", "only", "")
    val hours = edit("جواب کے اوقات مثلاً 22:00-07:00 (خالی = ہر وقت)", "hours", "")
    val busy = edit("مصروف پیغام مثلاً: میں گاڑی چلا رہا ہوں", "busy", "")
    val logTv = tv(logText(), 13f)
    wa.addView(sw); wa.addView(tv("جواب کا انداز (واٹس ایپ کے لیے الگ)", 13f), lp(12)); wa.addView(waMode)
    wa.addView(rules, lp()); wa.addView(only, lp()); wa.addView(hours, lp()); wa.addView(busy, lp())
    wa.addView(btn("Save") {
      p.edit().putBoolean("on", sw.isChecked).putInt("wamode", waMode.selectedItemPosition).putString("rules", rules.text.toString())
        .putString("only", only.text.toString()).putString("hours", hours.text.toString().trim()).putString("busy", busy.text.toString()).apply()
      refreshStatus(); toast("Saved")
    }, lp(12))
    wa.addView(btn("Notification Access", 0xFF1FA67A.toInt(), CYAN) { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }, lp())
    wa.addView(btn("لاگ تازہ کریں", 0xFF2A2F55.toInt(), 0xFF3A2F66.toInt()) { logTv.text = logText() }, lp())
    wa.addView(logTv, lp(12))
    val waScroll = ScrollView(this); waScroll.addView(wa)

    // ---- Settings page
    val st = page()
    val key = edit("Gemini API Key", "key", "")
    val nm = edit("اسسٹنٹ کا نام", "name", "Naira")
    val owner = edit("آپ کا نام", "owner", "Tanveer Abbas")
    val city = edit("شہر (موسم کے لیے)", "city", "")
    val vname = edit("آواز کا نام (Kore، Leda، Aoede، Zephyr)", "vname", "Kore")
    val sys = edit("Personality (واٹس ایپ جوابات کے لیے)", "sys",
      "You are Naira, a polite female assistant replying on behalf of the owner. Use feminine grammar. Reply briefly, in the same language and script as the message.", 3)
    val pitch = edit("پرانی آواز کی پچ (0.8 بھاری، 1.0 عام، 1.3 باریک)", "pitch", "1.0")
    val gv = CheckBox(this); gv.text = "قدرتی Gemini آواز"; gv.setTextColor(TXT); gv.isChecked = p.getBoolean("gv", true)
    val pin = EditText(this); pin.hint = "نیا PIN (خالی = پرانا رکھیں، 0 = ہٹائیں)"; pin.setTextColor(TXT); pin.setHintTextColor(0xFF8A93B2.toInt())
    pin.background = glass(14, 0x3300E5FF); pin.setPadding(dp(14), dp(10), dp(14), dp(10)); pin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    st.addView(key); st.addView(nm, lp()); st.addView(owner, lp()); st.addView(city, lp()); st.addView(vname, lp()); st.addView(sys, lp()); st.addView(pitch, lp()); st.addView(gv, lp()); st.addView(pin, lp())
    st.addView(btn("Save") {
      val e = p.edit().putString("key", key.text.toString().trim()).putString("name", nm.text.toString().trim()).putString("owner", owner.text.toString().trim())
        .putString("city", city.text.toString().trim()).putString("vname", vname.text.toString().trim()).putString("sys", sys.text.toString())
        .putString("pitch", pitch.text.toString().trim()).putBoolean("gv", gv.isChecked)
      val np = pin.text.toString().trim()
      if (np == "0") e.remove("pin") else if (np.isNotEmpty()) e.putString("pin", sha(np))
      e.apply(); nameTv.text = nm.text.toString().trim().uppercase(); toast("Saved")
    }, lp(12))
    st.addView(btn("Test Gemini", 0xFF1FA67A.toInt(), CYAN) {
      toast("ٹیسٹ ہو رہا ہے...")
      thread { val r = Gem.ask(this, "Reply in one short sentence.", listOf(Pair("user", "hi"))); toast(r ?: "جواب نہیں آیا، key اور انٹرنیٹ دیکھیں") }
    }, lp())
    st.addView(btn("آواز ٹیسٹ", 0xFF1FA67A.toInt(), CYAN) {
      toast("آواز بن رہی ہے...")
      thread { val ok = Gem.speak(this, "سلام! میں نایرہ ہوں۔ آپ کی کیا مدد کر سکتی ہوں؟"); toast(if (ok) "آواز ٹھیک چل گئی" else "Gemini آواز نہیں بنی، key چیک کریں") }
    }, lp())
    st.addView(btn("ماڈل لسٹ تازہ کریں", 0xFF2A2F55.toInt(), 0xFF3A2F66.toInt()) {
      thread { val ok = Gem.refresh(this); toast(if (ok) "ماڈل: " + p.getString("models", "") else "لسٹ نہیں ملی، key چیک کریں") }
    }, lp())
    st.addView(btn("Permissions (کال، SMS، کنٹیکٹس، نوٹیفکیشن)", 0xFF2A2F55.toInt(), 0xFF3A2F66.toInt()) {
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS), 1)
    }, lp())
    val stScroll = ScrollView(this); stScroll.addView(st)

    // ---- Tabs
    val frame = FrameLayout(this)
    frame.addView(chat); frame.addView(waScroll); frame.addView(stScroll)
    val pages = listOf<View>(chat, waScroll, stScroll)
    val tabBtns = ArrayList<TextView>()
    fun show(i: Int) {
      pages.forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE }
      tabBtns.forEachIndexed { k, t -> t.setTextColor(if (k == i) CYAN else 0xFF8A93B2.toInt()); t.background = if (k == i) grad(0x2200E5FF, 0x22FF4FA8, 14) else null }
    }
    val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(6)); background = grad(0xFF0C1126.toInt(), 0xFF150F2E.toInt(), 0) }
    listOf("💬 چیٹ", "📲 واٹس ایپ", "⚙️ سیٹنگز").forEachIndexed { i, n ->
      val t = tv(n, 14f); t.gravity = Gravity.CENTER; t.setPadding(0, dp(10), 0, dp(10)); t.setOnClickListener { show(i) }
      tabBtns.add(t); tabs.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
    }
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF07040F.toInt(), 0xFF140A2E.toInt()))
    root.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f)); root.addView(tabs)
    setContentView(root); show(0); refreshStatus()
    bubble("سلام! میں نایرہ ہوں 💜 سیٹنگز میں API key ڈال کر شروع کریں۔", false)

    val ph = p.getString("pin", "") ?: ""
    if (ph.isNotEmpty()) {
      val e = EditText(this); e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      AlertDialog.Builder(this).setTitle("🔒 PIN").setView(e).setCancelable(false)
        .setPositiveButton("OK") { _, _ -> if (sha(e.text.toString()) != ph) finish() }.show()
    }
  }
}
