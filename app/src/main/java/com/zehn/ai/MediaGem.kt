package com.zehn.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MediaGem {

    fun ask(
        ctx: Context,
        sys: String,
        h: List<Pair<String, String>>,
        uri: Uri,
        prompt: String
    ): String {
        val prefs = ctx.getSharedPreferences("zehn", Context.MODE_PRIVATE)
        val key = prefs.getString("key", "")?.trim().orEmpty()

        if (key.isBlank()) return "پہلے Settings میں Gemini API Key درج کریں۔"

        val resolver = ctx.contentResolver
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use {
                    if (it.moveToFirst()) it.getString(0) else ""
                } ?: ""
        } catch (_: Exception) { "" }

        var mime = resolver.getType(uri).orEmpty()
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
            mime = when (name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4", "m4v" -> "video/mp4"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                else -> ""
            }
        }

        if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
            return "یہ فائل تصویر یا ویڈیو نہیں ہے۔"
        }

        val bytes = try {
            val input = resolver.openInputStream(uri)
                ?: return "فائل نہیں کھل سکی۔"
            input.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > 14 * 1024 * 1024) {
                        return "فائل 14 MB سے بڑی ہے۔ چھوٹی ویڈیو یا تصویر منتخب کریں۔"
                    }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            return "فائل پڑھنے میں مسئلہ: ${e.message}"
        }

        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val mediaPart = JSONObject().put(
            "inline_data",
            JSONObject()
                .put("mime_type", mime)
                .put("data", encoded)
        )

        val contents = JSONArray()
        val history = h.takeLast(24)

        for ((index, item) in history.withIndex()) {
            val role = if (
                item.first.equals("user", true)
            ) "user" else "model"

            val parts = JSONArray().put(
                JSONObject().put("text", item.second)
            )

            if (index == history.lastIndex && role == "user") {
                parts.put(mediaPart)
            }

            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", parts)
            )
        }

        if (history.isEmpty() || !history.last().first.equals("user", true)) {
            val parts = JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(mediaPart)

            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", parts)
            )
        }

        val configured = prefs.getString("models", "").orEmpty()
            .split(",")
            .map { it.trim().removePrefix("models/") }
            .filter { it.startsWith("gemini-") }

        val models = (
            configured + listOf(
                "gemini-3.8-flash",
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite"
            )
        ).distinct()

        var lastError = "Gemini جواب نہیں دے سکا۔"

        for (model in models) {
            var connection: HttpURLConnection? = null
            try {
                val body = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", sys))
                        )
                    )
                    .put("contents", contents)
                    .put(
                        "generationConfig",
                        JSONObject().put("temperature", 0.7)
                    )

                connection = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 120000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("x-goog-api-key", key)

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    lastError = try {
                        JSONObject(raw).optJSONObject("error")
                            ?.optString("message").orEmpty()
                    } catch (_: Exception) { raw }
                    continue
                }

                val root = JSONObject(raw)
                val parts = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")

                val answer = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            append(parts.optJSONObject(i)?.optString("text").orEmpty())
                        }
                    }
                }.trim()

                if (answer.isNotBlank()) return answer
                lastError = "AI نے اس میڈیا کا جواب نہیں دیا۔"

            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
            } finally {
                connection?.disconnect()
            }
        }

        return "Media analysis failed: $lastError"
    }
}
