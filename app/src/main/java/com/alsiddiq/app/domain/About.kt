package com.alsiddiq.app.domain

import kotlinx.serialization.Serializable

/**
 * v3.4 «عن المركز»: open to everyone without a password. The admin edits the text, the photos
 * and the contact buttons; until he saves anything, the app shows the text and photo bundled with it.
 * A photo entry "bundled:about_photo_1" means the photo shipped inside the app.
 */
@Serializable
data class AboutButton(val id: String, val label: String, val kind: String, val value: String, val sort: Int = 0) {
    companion object {
        /** call | whatsapp | link | email | map, with the Arabic names the admin picks from. */
        val KINDS = listOf("call" to "اتصال", "whatsapp" to "واتساب", "link" to "رابط", "email" to "إيميل", "map" to "موقع على الخريطة")
        fun kindName(k: String) = KINDS.firstOrNull { it.first == k }?.second ?: k
    }
}

@Serializable
data class AboutPage(
    /** null = no row on the server yet: the bundled text is shown. */
    val body: String? = null,
    val photos: List<String> = listOf(BUNDLED_PHOTO),
    val buttons: List<AboutButton> = emptyList(),
    val updatedAt: Long = 0,
    /** true once a copy came from the server (else: the bundled default or the offline copy). */
    val fromServer: Boolean = false
) {
    companion object { const val BUNDLED_PHOTO = "bundled:about_photo_1" }
}

/** v3.4: the address is its own card: the lines after «العنوان:» in the text. */
object AboutText {
    fun split(text: String): Pair<String, String?> {
        val i = text.indexOf("العنوان:")
        if (i < 0) return text.trim() to null
        val addr = text.substring(i + "العنوان:".length).trim().lines().joinToString(" ") { it.trim() }.trim()
        return text.substring(0, i).trim() to addr.ifBlank { null }
    }

    /** The intent target for a button. */
    fun uri(kind: String, value: String): String {
        val v = value.trim()
        val digits = v.filter { it.isDigit() || it == '+' }.map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.joinToString("")
        return when (kind) {
            "call" -> "tel:$digits"
            "whatsapp" -> "https://wa.me/" + digits.trimStart('+').let { if (it.startsWith("00")) it.drop(2) else it }
            "email" -> "mailto:$v"
            "map" -> if (v.startsWith("http")) v else "geo:0,0?q=" + java.net.URLEncoder.encode(v, "UTF-8")
            else -> if (v.startsWith("http://") || v.startsWith("https://")) v else "https://$v"
        }
    }
}
