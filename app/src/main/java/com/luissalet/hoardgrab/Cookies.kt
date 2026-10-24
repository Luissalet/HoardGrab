package com.luissalet.hoardgrab

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/**
 * Sessions live in the WebView cookie store (the user signs in inside the app); before every
 * download they are written as a Netscape cookies.txt for yt-dlp, the same way the Links hoard
 * hands a browser's cookies to it.
 */
object Cookies {
    enum class Site(
        val label: String,
        val loginUrl: String,
        val url: String,
        val domains: List<String>,
        val sessionCookie: String,
    ) {
        X("X", "https://x.com/i/flow/login", "https://x.com", listOf(".x.com", ".twitter.com"), "auth_token"),
        INSTAGRAM(
            "Instagram", "https://www.instagram.com/accounts/login/", "https://www.instagram.com",
            listOf(".instagram.com"), "sessionid",
        ),
    }

    private fun parse(raw: String?): List<Pair<String, String>> =
        raw.orEmpty().split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
        }.filter { it.first.isNotEmpty() }

    private fun cookiesOf(site: Site) = parse(CookieManager.getInstance().getCookie(site.url))

    fun isLoggedIn(site: Site): Boolean = cookiesOf(site).any { it.first == site.sessionCookie && it.second.isNotEmpty() }

    fun logout(site: Site) {
        val cm = CookieManager.getInstance()
        val names = cookiesOf(site).map { it.first }
        for (name in names) {
            cm.setCookie(site.url, "$name=; Max-Age=0; Path=/")
            for (d in site.domains) cm.setCookie(site.url, "$name=; Max-Age=0; Path=/; Domain=$d")
        }
        cm.flush()
    }

    /** cookies.txt with every signed-in site, or null when there is no session at all. */
    fun export(ctx: Context): File? {
        val far = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val lines = StringBuilder("# Netscape HTTP Cookie File\n")
        var any = false
        for (site in Site.entries) {
            val cookies = cookiesOf(site)
            if (cookies.none { it.first == site.sessionCookie }) continue
            any = true
            for (domain in site.domains) for ((name, value) in cookies) {
                lines.append(domain).append("\tTRUE\t/\tTRUE\t").append(far).append('\t')
                    .append(name).append('\t').append(value).append('\n')
            }
        }
        val file = File(ctx.filesDir, "cookies.txt")
        if (!any) {
            file.delete()
            return null
        }
        file.writeText(lines.toString())
        return file
    }
}
