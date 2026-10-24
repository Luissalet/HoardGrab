package com.luissalet.hoardgrab

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Target of "Share → Hoard Grab": queues the link and gets out of the way. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val url = Links.extract(text)
        if (url == null) {
            Toast.makeText(this, "No hay ningún enlace en lo compartido", Toast.LENGTH_SHORT).show()
        } else {
            DownloadService.enqueue(this, Links.normalize(url))
            Toast.makeText(this, "Descargando de ${Links.platform(url)}…", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
