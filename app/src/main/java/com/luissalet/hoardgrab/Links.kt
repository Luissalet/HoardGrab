package com.luissalet.hoardgrab

import android.net.Uri

/** Finding and cleaning the link inside shared text ("Mira esto https://x.com/... vía @app"). */
object Links {
    private val URL_RE = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    private val X_HOSTS = setOf(
        "x.com", "twitter.com", "mobile.twitter.com", "mobile.x.com", "fxtwitter.com", "vxtwitter.com",
        "fixupx.com", "fixvx.com", "twittpr.com", "nitter.net",
    )
    private val IG_HOSTS = setOf("instagram.com", "instagr.am", "ddinstagram.com", "m.instagram.com")

    fun extract(text: CharSequence?): String? {
        val raw = URL_RE.find(text ?: return null)?.value ?: return null
        return raw.trimEnd('.', ',', ')', ']', '!', ';', '»', '"')
    }

    private fun host(url: String): String =
        (Uri.parse(url).host ?: "").lowercase().removePrefix("www.")

    fun platform(url: String): String = when (host(url)) {
        in X_HOSTS, "t.co" -> "X"
        in IG_HOSTS -> "Instagram"
        else -> "Web"
    }

    /** Mirror domains back to the real site and drop tracking query strings. */
    fun normalize(url: String): String {
        val uri = Uri.parse(url)
        val h = host(url)
        val path = uri.encodedPath ?: ""
        return when (h) {
            in X_HOSTS -> "https://x.com$path"
            in IG_HOSTS -> "https://www.instagram.com$path"
            else -> url
        }
    }

    fun isSupported(url: String) = platform(url) != "Web"
}
