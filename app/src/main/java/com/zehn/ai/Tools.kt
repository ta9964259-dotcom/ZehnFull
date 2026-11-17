package com.zehn.ai
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object Tools {
  val PK = mapOf("whatsapp" to "com.whatsapp", "youtube" to "com.google.android.youtube", "chrome" to "com.android.chrome",
    "maps" to "com.google.android.apps.maps", "settings" to "com.android.settings")
  fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("zehn", 0)
  fun split2(s: String): Pair<String, String> { val i = s.indexOf('|'); return if (i < 0) Pair(s, "") else Pair(s.substring(0, i), s.substring(i + 1)) }
  fun http(u: String): String? = try {
    val cn = URL(u).openConnection() as HttpURLConnection
    cn.connectTimeout = 15000; cn.readTimeout = 15000
    cn.inputStream.bufferedReader().readText()
  } catch (e: Exception) { null }
  fun number(ctx: Context, who: String): String? {
    if (Regex("[+\\d ]{6,}").matches(who.trim())) return who.replace(" ", "")
    try {
      val c = ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%" + who.trim() + "%"), null)
      c?.use { if (it.moveToFirst()) return it.getString(0).replace(" ", "") }
    } catch (e: Exception) {}
    return null
  }
  private fun view(a: Activity, url: String) { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  private fun todos(p: SharedPreferences): JSONArray = try { JSONArray(p.getString("todo", "[]")) } catch (e: Exception) { JSONArray() }
  private fun weather(a: Activity, city: String): String {
    val c = city.ifBlank { prefs(a).getString("city", "") ?: "" }
    return http("https://wttr.in/" + Uri.encode(c) + "?format=3")?.trim() ?: "موسم نہیں ملا"
  }

  fun run(a: Activity, name: String, arg: String): String {
    return try { exec(a, name, arg) } catch (e: Exception) { "یہ کام نہیں ہو سکا" }
  }

  private fun exec(a: Activity, name: String, arg: String): String {
    val p = prefs(a)
    when (name) {
      "workspace_new_project" -> {
        val (n, type) = split2(arg)
        return Workspace.newProject(a, n, type.ifBlank { "code" })
      }
      "workspace_mkdir" -> return Workspace.mkdir(a, arg)
      "workspace_list" -> return Workspace.list(a, arg)
      "workspace_read" -> return Workspace.read(a, arg)
      "workspace_write" -> {
        val (path, content) = split2(arg)
        return Workspace.write(a, path, content)
      }
      "workspace_preview" -> {
        val f = Workspace.resolve(a, arg) ?: return "غلط فائل کا راستہ"
        if (!f.isFile || !f.name.endsWith(".html", true)) return "صرف HTML فائل Preview ہو سکتی ہے"
        a.startActivity(Intent(a, PreviewActivity::class.java).putExtra("file", arg))
        return "Preview کھول دیا"
      }
      "workspace_delete" -> {
        val f = Workspace.resolve(a, arg) ?: return "غلط راستہ"
        if (!f.exists()) return "فائل یا فولڈر نہیں ملا"
        a.runOnUiThread {
          AlertDialog.Builder(a)
            .setMessage("کیا واقعی یہ فائل یا فولڈر حذف کرنا ہے؟\\n${f.name}")
            .setPositiveButton("حذف کریں") { _, _ -> a.runOnUiThread { android.widget.Toast.makeText(a, Workspace.delete(a, arg), android.widget.Toast.LENGTH_LONG).show() } }
            .setNegativeButton("منسوخ", null)
            .show()
        }
        return "حذف کرنے کی تصدیق درکار ہے"
      }
      "open_app" -> {
        val pm = a.packageManager
        var pk: String? = PK[arg.lowercase().trim()]
        if (pk == null) pk = pm.getInstalledApplications(0).firstOrNull { pm.getApplicationLabel(it).toString().contains(arg, true) }?.packageName
        val i = pk?.let { pm.getLaunchIntentForPackage(it) } ?: return "ایپ نہیں ملی"
        a.startActivity(i); return "کھول دی"
      }
      "youtube" -> { view(a, "https://www.youtube.com/results?search_query=" + Uri.encode(arg)); return "کھول دیا" }
      "search" -> { view(a, "https://www.google.com/search?q=" + Uri.encode(arg)); return "کھول دیا" }
      "open_url" -> { view(a, arg); return "کھول دیا" }
      "call" -> {
        val n = number(a, arg) ?: return "نمبر نہیں ملا"
        val ok = ContextCompat.checkSelfPermission(a, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        a.startActivity(Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))
        return if (ok) "کال لگا دی" else "ڈائلر کھول دیا"
      }
      "sms" -> {
        val (who, text) = split2(arg)
        val n = number(a, who) ?: return "نمبر نہیں ملا"
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return "SMS کی اجازت نہیں (Settings ٹیب میں Permissions)"
        a.runOnUiThread {
          AlertDialog.Builder(a).setMessage("SMS بھیجوں؟\n" + who + " (" + n + "):\n" + text)
            .setPositiveButton("ہاں") { _, _ ->
              try { val sm = SmsManager.getDefault(); sm.sendMultipartTextMessage(n, null, sm.divideMessage(text), null, null) } catch (e: Exception) {}
            }.setNegativeButton("نہیں", null).show()
        }
        return "تصدیق کا انتظار ہے"
      }
      "whatsapp" -> {
        val (who, text) = split2(arg)
        val n = number(a, who) ?: return "نمبر نہیں ملا"
        view(a, "https://wa.me/" + n.trimStart('+') + "?text=" + Uri.encode(text))
        return "واٹس ایپ کھل گیا، Send دبائیں"
      }
      "torch" -> {
        val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.setTorchMode(cm.cameraIdList[0], arg.contains("on", true)); return "ٹھیک ہے"
      }
      "battery" -> {
        val bm = a.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return "بیٹری " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%"
      }
      "clipboard" -> {
        a.runOnUiThread { (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("z", arg)) }
        return "کاپی ہو گیا"
      }
      "vibrate" -> {
        (a.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)); return "ہو گیا"
      }
      "remember" -> { p.edit().putString("facts", (p.getString("facts", "") ?: "") + arg + "\n").apply(); return "یاد کر لیا" }
      "websearch" -> return Gem.search(a, arg) ?: "سرچ نہیں ہو سکی"
      "weather" -> return weather(a, arg)
      "alarm" -> {
        val (t, lab) = split2(arg); val hm = t.trim().split(":")
        a.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hm[0].trim().toInt())
          .putExtra(AlarmClock.EXTRA_MINUTES, hm[1].trim().toInt()).putExtra(AlarmClock.EXTRA_MESSAGE, lab.ifBlank { "Zehn" })
          .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "الارم " + t.trim() + " پر لگا دیا"
      }
      "timer" -> {
        val (s, lab) = split2(arg)
        a.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, s.trim().toDouble().toInt())
          .putExtra(AlarmClock.EXTRA_MESSAGE, lab.ifBlank { "Zehn" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "ٹائمر لگا دیا"
      }
      "remind" -> {
        val (m, t) = split2(arg)
        val ms = (m.trim().toDouble() * 60000).toLong()
        val pi = PendingIntent.getBroadcast(a, (System.currentTimeMillis() % 100000).toInt(),
          Intent(a, RemindReceiver::class.java).putExtra("t", t), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        (a.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms, pi)
        return m.trim() + " منٹ بعد یاد دلاؤں گا"
      }
      "note" -> { p.edit().putString("notes", (p.getString("notes", "") ?: "") + arg + "\n").apply(); return "نوٹ محفوظ" }
      "todo_add" -> { val L = todos(p); L.put(arg); p.edit().putString("todo", L.toString()).apply(); return "ٹو ڈو میں شامل" }
      "todo_done" -> {
        val L = todos(p); val i = arg.trim().toInt() - 1
        if (i < 0 || i >= L.length()) return "نمبر غلط ہے"
        L.remove(i); p.edit().putString("todo", L.toString()).apply(); return "مکمل"
      }
      "todo_list" -> {
        val L = todos(p); val sb = StringBuilder()
        for (i in 0 until L.length()) sb.append(i + 1).append(". ").append(L.getString(i)).append("\n")
        return sb.toString().trim().ifEmpty { "ٹو ڈو خالی ہے" }
      }
      "briefing" -> {
        val L = todos(p); val sb = StringBuilder(weather(a, "")).append("\n")
        for (i in 0 until L.length()) sb.append("- ").append(L.getString(i)).append("\n")
        return Gem.ask(a, "Make a short friendly morning briefing in Urdu.", listOf("user" to sb.toString())) ?: sb.toString()
      }
    }
    return "نامعلوم ٹول"
  }
}
