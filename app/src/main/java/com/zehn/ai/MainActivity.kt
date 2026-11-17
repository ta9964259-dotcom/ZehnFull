package com.zehn.ai
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
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
  private lateinit var p: SharedPreferences
  private lateinit var tts: TextToSpeech
  private lateinit var msgs: LinearLayout
  private lateinit var scroll: ScrollView
  private val hist = ArrayList<Pair<String, String>>()
  private val BG = 0xFF0B1020.toInt(); private val CARD = 0xFF171E36.toInt()
  private val ACC = 0xFF7C5CFF.toInt(); private val TXT = 0xFFEDEFFA.toInt()
  private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT; private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

  private val speechL = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
    val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    if (t != null) send(t)
  }

  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
  private fun bg(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
  private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
  private fun tv(t: String, size: Float = 15f): TextView {
    val v = TextView(this); v.text = t; v.textSize = size; v.setTextColor(TXT); v.textDirection = View.TEXT_DIRECTION_ANY_RTL; return v
  }
  private fun edit(hint: String, k: String, d: String, lines: Int = 1): EditText {
    val e = EditText(this); e.hint = hint; e.setText(p.getString(k, d)); e.minLines = lines
    e.setTextColor(TXT); e.setHintTextColor(0xFF8A93B2.toInt()); e.background = bg(CARD, 12)
    e.setPadding(dp(14), dp(10), dp(14), dp(10)); e.textDirection = View.TEXT_DIRECTION_ANY_RTL; return e
  }
  private fun btn(t: String, c: Int = ACC, f: () -> Unit): Button {
    val b = Button(this); b.text = t; b.setTextColor(0xFFFFFFFF.toInt()); b.background = bg(c, 14); b.setOnClickListener { f() }; return b
  }
  private fun lp(top: Int = 8) = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }
  private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_LONG).show() }
  private fun page(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }

  private fun bubble(t: String, user: Boolean) {
    runOnUiThread {
      val v = tv(t); v.setPadding(dp(14), dp(10), dp(14), dp(10)); v.background = bg(if (user) ACC else CARD, 16); v.maxWidth = dp(300)
      val l = LinearLayout.LayoutParams(WRAP, WRAP); l.topMargin = dp(6); l.gravity = if (user) Gravity.END else Gravity.START
      msgs.addView(v, l); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
  }

  private fun send(text: String) {
    if (text.isBlank()) return
    bubble(text, true); hist.add(Pair("user", text))
    thread {
      var h: List<Pair<String, String>> = hist.takeLast(20)
      while (h.isNotEmpty() && h[0].first != "user") h = h.drop(1)
      val r = Gem.ask(this, Gem.chatSys(this), h)
      if (r == null) { hist.removeAt(hist.size - 1); bubble("ابھی جواب نہیں مل سکا، دوبارہ کوشش کریں۔", false); return@thread }
      val out = ArrayList<String>()
      for (ln in r.lines()) {
        val m = Regex("^\\s*ACTION:\\s*(\\{.*\\})").find(ln)
        if (m != null) {
          try { val j = JSONObject(m.groupValues[1]); out.add("⚙️ " + Tools.run(this, j.getString("tool"), j.optString("arg"))) } catch (e: Exception) {}
        } else out.add(ln)
      }
      val clean = out.joinToString("\n").trim()
      hist.add(Pair("model", clean)); bubble(clean, false)
      if (p.getBoolean("voice", false)) runOnUiThread {
        tts.setPitch((p.getString("pitch", "1.0") ?: "1.0").toFloatOrNull() ?: 1.0f)
        tts.speak(clean.replace(Regex("⚙️[^\n]*"), ""), TextToSpeech.QUEUE_FLUSH, null, "z")
      }
    }
  }

  private fun logText(): String {
    return try {
      val a = JSONArray(p.getString("log", "[]")); val sb = StringBuilder()
      for (i in a.length() - 1 downTo 0) sb.append(a.getString(i)).append("\n\n")
      sb.toString().ifBlank { "ابھی کوئی لاگ نہیں" }
    } catch (e: Exception) { "" }
  }

  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    p = getSharedPreferences("zehn", MODE_PRIVATE)
    tts = TextToSpeech(this) { st ->
      if (st == TextToSpeech.SUCCESS) {
        val r = tts.setLanguage(Locale("ur", "PK"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale("hi", "IN"))
      }
    }
    // ---- Chat page
    val chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(6)) }
    val orb = tv("🔮", 30f)
    orb.startAnimation(ScaleAnimation(1f, 1.18f, 1f, 1.18f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
      duration = 1300; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE })
    val title = tv("Zehn AI", 18f)
    val modeSp = Spinner(this); modeSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Gem.MODE_NAMES)
    modeSp.setSelection(p.getInt("mode", 0))
    modeSp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) { p.edit().putInt("mode", pos).apply() }
      override fun onNothingSelected(a: AdapterView<*>?) {}
    }
    val vc = CheckBox(this); vc.text = "🔊"; vc.isChecked = p.getBoolean("voice", false)
    vc.setOnCheckedChangeListener { _, c -> p.edit().putBoolean("voice", c).apply() }
    top.addView(orb); top.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(10) }); top.addView(modeSp); top.addView(vc)
    msgs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(6)) }
    scroll = ScrollView(this); scroll.addView(msgs)
    val inp = edit("پیغام لکھیں", "__t", "")
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(10)) }
    row.addView(inp, LinearLayout.LayoutParams(0, WRAP, 1f))
    row.addView(btn("🎤") {
      try {
        speechL.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK"))
      } catch (e: Exception) { toast("مائیک دستیاب نہیں") }
    }, LinearLayout.LayoutParams(WRAP, WRAP))
    row.addView(btn("➤") { val t = inp.text.toString(); inp.setText(""); send(t) }, LinearLayout.LayoutParams(WRAP, WRAP))
    chat.addView(top); chat.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f)); chat.addView(row)

    // ---- WhatsApp page
    val wa = page()
    val sw = Switch(this); sw.text = "WhatsApp auto-reply ON/OFF"; sw.setTextColor(TXT); sw.isChecked = p.getBoolean("on", false)
    val rules = edit("کنٹیکٹ رولز: نام=ہدایت (ہر لائن میں ایک)", "rules", "", 4)
    val only = edit("صرف انہیں جواب دو (کومہ سے، خالی = سب)", "only", "")
    val hours = edit("جواب کے اوقات مثلاً 22:00-07:00 (خالی = ہر وقت)", "hours", "")
    val busy = edit("مصروف پیغام مثلاً: میں گاڑی چلا رہا ہوں", "busy", "")
    val logTv = tv(logText(), 13f)
    wa.addView(sw); wa.addView(rules, lp()); wa.addView(only, lp()); wa.addView(hours, lp()); wa.addView(busy, lp())
    wa.addView(btn("Save") {
      p.edit().putBoolean("on", sw.isChecked).putString("rules", rules.text.toString()).putString("only", only.text.toString())
        .putString("hours", hours.text.toString().trim()).putString("busy", busy.text.toString()).apply(); toast("Saved")
    }, lp(12))
    wa.addView(btn("Notification Access", 0xFF2E7D6B.toInt()) { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }, lp())
    wa.addView(btn("لاگ تازہ کریں", CARD) { logTv.text = logText() }, lp())
    wa.addView(logTv, lp(12))
    val waScroll = ScrollView(this); waScroll.addView(wa)

    // ---- Settings page
    val st = page()
    val key = edit("Gemini API Key", "key", "")
    val owner = edit("آپ کا نام", "owner", "Tanveer Abbas")
    val city = edit("شہر (موسم کے لیے)", "city", "")
    val sys = edit("Personality", "sys", "You are Zehn AI, a polite assistant replying on behalf of the owner. Reply briefly, in the same language as the message.", 3)
    val pitch = edit("آواز کی پچ (0.8 بھاری، 1.0 عام، 1.3 باریک)", "pitch", "1.0")
    val pin = EditText(this); pin.hint = "نیا PIN (خالی = پرانا رکھیں، 0 = ہٹائیں)"; pin.setTextColor(TXT); pin.setHintTextColor(0xFF8A93B2.toInt())
    pin.background = bg(CARD, 12); pin.setPadding(dp(14), dp(10), dp(14), dp(10)); pin.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    st.addView(key); st.addView(owner, lp()); st.addView(city, lp()); st.addView(sys, lp()); st.addView(pitch, lp()); st.addView(pin, lp())
    st.addView(btn("Save") {
      val e = p.edit().putString("key", key.text.toString().trim()).putString("owner", owner.text.toString().trim())
        .putString("city", city.text.toString().trim()).putString("sys", sys.text.toString()).putString("pitch", pitch.text.toString().trim())
      val np = pin.text.toString().trim()
      if (np == "0") e.remove("pin") else if (np.isNotEmpty()) e.putString("pin", sha(np))
      e.apply(); toast("Saved")
    }, lp(12))
    st.addView(btn("Test Gemini", 0xFF2E7D6B.toInt()) {
      toast("ٹیسٹ ہو رہا ہے...")
      thread { val r = Gem.ask(this, "Reply in one short sentence.", listOf(Pair("user", "hi"))); toast(r ?: "جواب نہیں آیا، key اور انٹرنیٹ دیکھیں") }
    }, lp())
    st.addView(btn("ماڈل لسٹ تازہ کریں", CARD) {
      thread { val ok = Gem.refresh(this); toast(if (ok) "ماڈل: " + p.getString("models", "") else "لسٹ نہیں ملی، key چیک کریں") }
    }, lp())
    st.addView(btn("Permissions (کال، SMS، کنٹیکٹس، نوٹیفکیشن)", CARD) {
      ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS), 1)
    }, lp())
    val stScroll = ScrollView(this); stScroll.addView(st)

    // ---- Tabs
    val frame = FrameLayout(this)
    frame.addView(chat); frame.addView(waScroll); frame.addView(stScroll)
    val pages = listOf<View>(chat, waScroll, stScroll)
    fun show(i: Int) { pages.forEachIndexed { k, v -> v.visibility = if (k == i) View.VISIBLE else View.GONE } }
    val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(6)); setBackgroundColor(CARD) }
    val names = listOf("💬 چیٹ", "📲 واٹس ایپ", "⚙️ سیٹنگز")
    names.forEachIndexed { i, n -> tabs.addView(btn(n, 0x00000000) { show(i) }, LinearLayout.LayoutParams(0, WRAP, 1f)) }
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
    root.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f)); root.addView(tabs)
    setContentView(root); show(0)
    bubble("سلام! میں ذہن ہوں۔ Settings میں API key ڈال کر شروع کریں۔", false)

    val ph = p.getString("pin", "") ?: ""
    if (ph.isNotEmpty()) {
      val e = EditText(this); e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      AlertDialog.Builder(this).setTitle("🔒 PIN").setView(e).setCancelable(false)
        .setPositiveButton("OK") { _, _ -> if (sha(e.text.toString()) != ph) finish() }.show()
    }
  }
}
