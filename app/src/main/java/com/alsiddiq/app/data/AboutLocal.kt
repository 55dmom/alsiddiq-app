package com.alsiddiq.app.data

import android.content.Context
import com.alsiddiq.app.domain.AboutButton
import com.alsiddiq.app.domain.AboutPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * v3.4 «عن المركز» on the phone: the last copy fetched (works offline), its photos, and the text and
 * photo bundled with the app (his own words, unchanged) shown until the admin saves anything.
 */
class AboutLocal(private val ctx: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(ctx.filesDir, "about_cache.json")
    private val dir = File(ctx.filesDir, "about").apply { mkdirs() }

    val flow = MutableStateFlow(runCatching { json.decodeFromString(AboutPage.serializer(), file.readText()) }.getOrDefault(AboutPage()))
    val page: AboutPage get() = flow.value

    fun set(p: AboutPage) {
        flow.value = p
        runCatching { file.writeText(json.encodeToString(AboutPage.serializer(), p)) }
    }

    /** His text exactly as he sent it (assets/about/about_text_ar.txt). */
    fun bundledText(): String = runCatching { ctx.assets.open("about/about_text_ar.txt").use { it.readBytes().toString(Charsets.UTF_8) } }.getOrDefault("")

    fun cached(path: String): ByteArray? {
        if (path.startsWith("bundled:")) return runCatching { ctx.assets.open("about/" + path.removePrefix("bundled:") + ".jpg").use { it.readBytes() } }.getOrNull()
        val f = File(dir, path.replace('/', '_'))
        return if (f.exists() && f.length() > 0) runCatching { f.readBytes() }.getOrNull() else null
    }

    fun cache(path: String, bytes: ByteArray) { runCatching { File(dir, path.replace('/', '_')).writeBytes(bytes) } }

    fun forget(path: String) { runCatching { File(dir, path.replace('/', '_')).delete() } }

    /** The text to start editing from: the saved one, else the bundled one. */
    fun currentText(): String = page.body ?: bundledText()

    companion object {
        fun sorted(b: List<AboutButton>) = b.sortedWith(compareBy({ it.sort }, { it.label }))
        fun move(list: List<AboutButton>, id: String, up: Boolean): List<AboutButton> {
            val l = sorted(list).toMutableList()
            val i = l.indexOfFirst { it.id == id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in l.indices) return l
            val a = l[i]; l[i] = l[j]; l[j] = a
            return l.mapIndexed { k, b -> b.copy(sort = k) }
        }
    }
}
