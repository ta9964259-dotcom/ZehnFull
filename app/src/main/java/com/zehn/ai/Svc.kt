package com.zehn.ai
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class RemindReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent) {
    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(NotificationChannel("zehn", "Zehn", NotificationManager.IMPORTANCE_HIGH))
    val n = Notification.Builder(c, "zehn").setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("⏰ یاد دہانی").setContentText(i.getStringExtra("t") ?: "").build()
    nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
  }
}

class Svc : NotificationListenerService() {
  private val seen = ArrayList<String>()
  private val hist = HashMap<String, MutableList<Pair<String, String>>>()

  private fun inHours(w: String): Boolean {
    if (w.isBlank()) return true
    return try {
      val parts = w.split("-"); val a = parts[0].trim(); val b = parts[1].trim()
      val n = SimpleDateFormat("HH:mm", Locale.US).format(Date())
      if (a < b) (n >= a && n < b) else (n >= a || n < b)
    } catch (e: Exception) { true }
  }

  override fun onNotificationPosted(s: StatusBarNotification) {
    val p = getSharedPreferences("zehn", MODE_PRIVATE)
    if (!p.getBoolean("on", false) || (s.packageName != "com.whatsapp" && s.packageName != "com.whatsapp.w4b")) return
    val n = s.notification
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
    val ex = n.extras
    if (ex.getBoolean("android.isGroupConversation")) return
    val who = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
    val msg = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
    if (Regex("\\d+ (new )?messages?.*").matches(msg)) return
    if (!inHours(p.getString("hours", "") ?: "")) return
    val only = (p.getString("only", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (only.isNotEmpty() && only.none { who.contains(it, true) }) return
    val id = who + "|" + msg + "|" + n.`when`
    synchronized(seen) {
      if (seen.contains(id)) return
      seen.add(id); if (seen.size > 100) seen.removeAt(0)
    }
    val act = n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return
    thread {
      val h = synchronized(hist) { hist.getOrPut(who) { mutableListOf() } }
      h.add(Pair("user", msg))
      var sys = (p.getString("sys", "") ?: "") + "\n" + Gem.MODE_TXT[p.getInt("mode", 0).coerceIn(0, Gem.MODE_TXT.size - 1)]
      sys += "\nCurrent time: " + Date() + ". You are replying on WhatsApp on behalf of the owner to " + who + ". Reply briefly like a human, in the sender's language."
      val busy = p.getString("busy", "") ?: ""
      if (busy.isNotBlank()) sys += "\nOwner status: " + busy + ". Mention it politely."
      (p.getString("rules", "") ?: "").lines().forEach {
        val i = it.indexOf('=')
        if (i > 0 && who.contains(it.substring(0, i).trim(), true)) sys += "\nSpecial rule for this contact: " + it.substring(i + 1).trim()
      }
      var recent = h.takeLast(8); while (recent.isNotEmpty() && recent[0].first != "user") recent = recent.drop(1)
      val r = Gem.ask(this, sys, recent) ?: return@thread
      h.add(Pair("model", r)); while (h.size > 20) h.removeAt(0)
      try {
        val lg = JSONArray(p.getString("log", "[]")); lg.put(who + ": " + msg + "\n↳ " + r)
        val out = JSONArray(); for (k in maxOf(0, lg.length() - 30) until lg.length()) out.put(lg.get(k))
        p.edit().putString("log", out.toString()).apply()
      } catch (e: Exception) {}
      Thread.sleep(2000)
      val ri = act.remoteInputs
      val bd = Bundle(); bd.putCharSequence(ri[0].resultKey, r)
      val intent = Intent(); RemoteInput.addResultsToIntent(ri, intent, bd)
      try { act.actionIntent.send(this, 0, intent) } catch (e: Exception) {}
    }
  }
}
