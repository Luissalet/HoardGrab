package com.luissalet.hoardgrab

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Sign in to X or Instagram inside the app; the session cookies stay in the WebView store. */
class LoginActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_SITE = "site"
        fun open(ctx: Context, site: Cookies.Site) =
            ctx.startActivity(Intent(ctx, LoginActivity::class.java).putExtra(EXTRA_SITE, site.name))
    }

    private lateinit var site: Cookies.Site
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        findViewById<android.view.View>(R.id.loginRoot).padForSystemBars()
        site = Cookies.Site.valueOf(intent.getStringExtra(EXTRA_SITE) ?: Cookies.Site.X.name)
        findViewById<TextView>(R.id.loginTitle).text = getString(R.string.login_title, site.label)
        status = findViewById(R.id.loginStatus)
        findViewById<MaterialButton>(R.id.loginDone).setOnClickListener {
            CookieManager.getInstance().flush()
            finish()
        }

        val web = findViewById<WebView>(R.id.web)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Present as the regular mobile browser; some login pages refuse embedded WebViews.
        web.settings.userAgentString = web.settings.userAgentString
            .replace("; wv", "")
            .replace(Regex("""Version/\d+\.\d+ """), "")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) = refresh()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        refresh()
        web.loadUrl(site.loginUrl)
    }

    private fun refresh() {
        val ok = Cookies.isLoggedIn(site)
        status.text = getString(if (ok) R.string.login_ok else R.string.login_wait)
        status.setTextColor(getColor(if (ok) R.color.ok else R.color.text_dim))
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }
}
