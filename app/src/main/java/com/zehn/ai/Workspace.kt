package com.zehn.ai

import android.content.Context
import java.io.File

object Workspace {
    fun root(c: Context): File =
        File(c.getExternalFilesDir(null), "NairaWorkspace").apply { mkdirs() }

    fun resolve(c: Context, path: String): File? {
        return try {
            val r = root(c).canonicalFile
            val f = File(r, path.trim()).canonicalFile
            if (f.path.startsWith(r.path + File.separator)) f else null
        } catch (e: Exception) { null }
    }

    fun list(c: Context, path: String): String {
        val d = if (path.isBlank()) root(c) else resolve(c, path)
            ?: return "غلط فولڈر"
        if (!d.isDirectory) return "فولڈر نہیں ملا"
        return d.walkTopDown().filter { it.isFile }.take(100)
            .joinToString("\n") { it.relativeTo(root(c)).path }
            .ifBlank { "فولڈر خالی ہے" }
    }

    fun mkdir(c: Context, path: String): String {
        val f = resolve(c, path) ?: return "غلط فولڈر کا نام"
        return if (f.mkdirs() || f.isDirectory) "فولڈر بن گیا" else "فولڈر نہیں بن سکا"
    }

    fun write(c: Context, path: String, content: String): String {
        val f = resolve(c, path) ?: return "غلط فائل کا راستہ"
        if (content.length > 1000000) return "فائل بہت بڑی ہے"
        return try {
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
            "فائل محفوظ ہو گئی: ${f.name}"
        } catch (e: Exception) { "فائل محفوظ نہیں ہوئی" }
    }

    fun read(c: Context, path: String): String {
        val f = resolve(c, path) ?: return "غلط فائل کا راستہ"
        return try {
            if (!f.isFile) return "فائل نہیں ملی"
            f.readText(Charsets.UTF_8).take(200000)
        } catch (e: Exception) { "فائل نہیں پڑھی جا سکی" }
    }

    fun delete(c: Context, path: String): String {
        val f = resolve(c, path) ?: return "غلط راستہ"
        return try {
            if (!f.exists()) "فائل یا فولڈر نہیں ملا"
            else if (f.deleteRecursively()) "حذف ہو گیا"
            else "حذف نہیں ہو سکا"
        } catch (e: Exception) { "حذف نہیں ہو سکا" }
    }

    fun newProject(c: Context, name: String, type: String): String {
        val n = name.trim()
        if (!n.matches(Regex("[A-Za-z0-9_-]{1,40}"))) return "پروجیکٹ کا نام درست نہیں"
        val f = resolve(c, n) ?: return "غلط نام"
        if (f.exists()) return "اس نام کا پروجیکٹ پہلے موجود ہے"
        return try {
            f.mkdirs()
            write(c, "$n/README.md", "Project: $n\nType: $type\nCreated by Naira AI")
            "پروجیکٹ فولڈر بن گیا۔ اب اس کی فائلیں بنائی جا سکتی ہیں۔"
        } catch (e: Exception) { "پروجیکٹ نہیں بن سکا" }
    }
}
