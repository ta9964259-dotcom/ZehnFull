package com.zehn.ai
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Gem {
  const val DEF = "gemini-3.8-flash,gemini-3.5-flash-lite,gemini-3.1-flash-lite"
  val MODE_NAMES = arrayOf("Normal", "Boss", "Friend", "Girlfriend", "Boyfriend", "Ideas", "Story")
  val MODE_TXT = arrayOf(
    "Be a helpful, friendly assistant.",
    "Address the user respectfully as Boss. Be short, sharp and efficient.",
    "Talk like a warm, caring close friend, casual and supportive.",
    "Talk like a sweet, caring, affectionate girlfriend: warm, playful and respectful. Never possessive or controlling; encourage the user's real-life friends, family and wellbeing.",
    "Talk in a sweet, caring, romantic but respectful tone.",
    "Brainstorm: give creative, practical ideas in short numbered points.",
    "You are a gifted Urdu storyteller and YouTube script writer. Write vivid simple Urdu with scene-by-scene structure, narration and dialogues.")
  const val TOOLS = """You can control the phone. To use a tool, output a separate line exactly like:
ACTION: {"tool":"NAME","arg":"VALUE"}
Tools: open_app(app name), youtube(search text), search(search text), open_url(url), call(contact name or number), sms("contact|text"), whatsapp("contact|text"), torch(on/off), battery(""), clipboard(text), vibrate(""), remember(fact), websearch(question; for current info), weather(city), alarm("HH:MM|label" in 24h), timer("seconds|label"), remind("minutes|text"), note(text), todo_add(text), todo_list(""), todo_done(number), briefing("").
After the ACTION line(s) add a short natural reply."""

  private fun post(key: String, url: String, body: JSONObject?): String? = try {
    val cn = URL(url).openConnection() as HttpURLConnection
    cn.connectTimeout = 15000; cn.readTimeout = 40000
    cn.setRequestProperty("x-goog-api-key", key)
    if (body != null) {
      cn.requestMethod = "POST"; cn.doOutput = true
      cn.setRequestProperty("Content-Type", "application/json")
      cn.outputStream.use { it.write(body.toString().toByteArray()) }
    }
    cn.inputStream.bufferedReader().readText()
  } catch (e: Exception) { null }

  private fun call(key: String, model: String, sys: String, h: List<Pair<String, String>>, tools: JSONArray?): String? = try {
    val c = JSONArray()
    h.forEach { c.put(JSONObject().put("role", it.first).put("parts", JSONArray().put(JSONObject().put("text", it.second)))) }
    val body = JSONObject()
      .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
      .put("contents", c)
    if (tools != null) body.put("tools", tools)
    val t = post(key, "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body)
    val parts = JSONObject(t ?: "").getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
    val sb = StringBuilder()
    for (i in 0 until parts.length()) { val o = parts.getJSONObject(i); if (!o.optBoolean("thought")) sb.append(o.optString("text")) }
    sb.toString().trim().ifEmpty { null }
  } catch (e: Exception) { null }

  fun refresh(ctx: Context): Boolean = try {
    val p = ctx.getSharedPreferences("zehn", 0)
    val t = post(p.getString("key", "") ?: "", "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", null)
    val arr = JSONObject(t ?: "").getJSONArray("models")
    val bad = listOf("image", "tts", "audio", "live", "embed", "robotics", "imagen", "veo", "computer", "research", "omni", "aqa", "banana")
    val l = ArrayList<String>()
    for (i in 0 until arr.length()) {
      val o = arr.getJSONObject(i); val n = o.getString("name").substringAfterLast("/")
      val ok = o.optJSONArray("supportedGenerationMethods")?.toString()?.contains("generateContent") == true
      if (n.startsWith("gemini") && ok && bad.none { n.contains(it) }) l.add(n)
    }
    fun ver(n: String) = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDouble() ?: 0.0
    l.sortWith(compareBy<String>({ -ver(it) }, { it.contains("pro") }, { it.contains("lite") }, { it.contains("preview") || it.contains("exp") }, { it }))
    if (l.isNotEmpty()) { p.edit().putString("models", l.take(14).joinToString(",")).apply(); true } else false
  } catch (e: Exception) { false }

  fun ask(ctx: Context, sys: String, h: List<Pair<String, String>>, tools: JSONArray? = null): String? {
    val p = ctx.getSharedPreferences("zehn", 0)
    val key = p.getString("key", "") ?: ""
    for (attempt in 0..1) {
      if (attempt == 1 && !refresh(ctx)) break
      for (m in (p.getString("models", DEF) ?: DEF).split(",")) {
        call(key, m.trim(), sys, h, tools)?.let { return it }
      }
    }
    return null
  }

  fun search(ctx: Context, q: String): String? =
    ask(ctx, "Answer using Google Search results. Reply briefly in the user's language.", listOf("user" to q),
      JSONArray().put(JSONObject().put("google_search", JSONObject())))
      ?: ask(ctx, "Reply briefly in the user's language.", listOf("user" to q))

  fun chatSys(ctx: Context): String {
    val p = ctx.getSharedPreferences("zehn", 0)
    var s = "You are ${p.getString("name", "Zehn")}, a smart AI assistant of ${p.getString("owner", "Tanveer Abbas")}. Reply in the same language/script the user writes (Urdu by default).\n"
    s += MODE_TXT[p.getInt("mode", 0).coerceIn(0, MODE_TXT.size - 1)] + "\n"
    s += "Current time: " + SimpleDateFormat("EEEE dd MMMM yyyy hh:mm a", Locale.US).format(Date()) + "\n"
    val f = p.getString("facts", "") ?: ""
    if (f.isNotBlank()) s += "Known facts about the user: " + f.trim().replace("\n", "; ") + "\n"
    return s + TOOLS
  }
}
