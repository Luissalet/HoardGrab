package com.luissalet.hoardgrab

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var urlField: TextInputEditText
    private lateinit var jobCard: MaterialCardView
    private lateinit var jobTitle: TextView
    private lateinit var jobLine: TextView
    private lateinit var jobProgress: LinearProgressIndicator
    private lateinit var btnX: MaterialButton
    private lateinit var btnIg: MaterialButton
    private lateinit var engineText: TextView
    private lateinit var empty: TextView
    private lateinit var adapter: HistoryAdapter
    private var lastAutoPaste: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).padForSystemBars()
        urlField = findViewById(R.id.url)
        jobCard = findViewById(R.id.jobCard)
        jobTitle = findViewById(R.id.jobTitle)
        jobLine = findViewById(R.id.jobLine)
        jobProgress = findViewById(R.id.jobProgress)
        btnX = findViewById(R.id.btnX)
        btnIg = findViewById(R.id.btnIg)
        engineText = findViewById(R.id.engine)
        empty = findViewById(R.id.empty)

        findViewById<MaterialButton>(R.id.btnPaste).setOnClickListener { pasteClipboard(force = true) }
        findViewById<MaterialButton>(R.id.btnGo).setOnClickListener { go() }
        urlField.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) { go(); true } else false
        }
        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { DownloadService.cancel(this) }
        btnX.setOnClickListener { account(Cookies.Site.X) }
        btnIg.setOnClickListener { account(Cookies.Site.INSTAGRAM) }
        findViewById<MaterialButton>(R.id.btnUpdate).setOnClickListener { updateEngine() }

        adapter = HistoryAdapter(lifecycleScope, ::openItem, ::itemMenu)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { GrabState.current.collect(::showJob) }
                launch {
                    GrabState.historyTick.collect {
                        reloadHistory()
                        refreshEngine()
                    }
                }
                launch { GrabState.messages.collect { snack(it) } }
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString ?: return
        Links.extract(text)?.let { urlField.setText(it) }
    }

    override fun onResume() {
        super.onResume()
        refreshAccounts()
        refreshEngine()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android only lets the focused app read the clipboard.
        if (hasFocus) pasteClipboard(force = false)
    }

    private fun pasteClipboard(force: Boolean) {
        val cb = getSystemService(ClipboardManager::class.java)
        val text = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
        val url = Links.extract(text)
        if (url == null) {
            if (force) toast("No hay ningún enlace copiado")
            return
        }
        if (!force && (url == lastAutoPaste || !Links.isSupported(url) || !urlField.text.isNullOrBlank())) return
        // Don't offer again a link that is already downloaded.
        if (!force && History.load(this).any { it.source == Links.normalize(url) }) {
            lastAutoPaste = url
            return
        }
        lastAutoPaste = url
        urlField.setText(url)
        urlField.setSelection(url.length)
    }

    private fun go() {
        val url = Links.extract(urlField.text)
        if (url == null) {
            toast("Pega un enlace de X o Instagram")
            return
        }
        DownloadService.enqueue(this, Links.normalize(url))
        urlField.setText("")
    }

    private fun showJob(job: GrabState.Current?) {
        jobCard.visibility = if (job == null) View.GONE else View.VISIBLE
        if (job == null) return
        val pending = GrabState.pending.value
        jobTitle.text = buildString {
            append("Descargando de ").append(job.platform)
            if (pending > 1) append("  ·  ").append(pending - 1).append(" en cola")
        }
        jobLine.text = job.line
        if (job.progress >= 0f) {
            jobProgress.isIndeterminate = false
            jobProgress.setProgressCompat(job.progress.toInt(), true)
        } else if (!jobProgress.isIndeterminate) {
            jobProgress.visibility = View.INVISIBLE
            jobProgress.isIndeterminate = true
            jobProgress.visibility = View.VISIBLE
        }
    }

    private fun refreshAccounts() {
        val x = Cookies.isLoggedIn(Cookies.Site.X)
        val ig = Cookies.isLoggedIn(Cookies.Site.INSTAGRAM)
        btnX.text = getString(if (x) R.string.acc_on else R.string.acc_off, "X")
        btnIg.text = getString(if (ig) R.string.acc_on else R.string.acc_off, "Instagram")
        btnX.isChecked = x
        btnIg.isChecked = ig
    }

    private fun account(site: Cookies.Site) {
        if (!Cookies.isLoggedIn(site)) {
            LoginActivity.open(this, site)
            return
        }
        refreshAccounts()
        MaterialAlertDialogBuilder(this)
            .setTitle("Cuenta de ${site.label}")
            .setMessage("La sesión está iniciada y se usa para descargar.")
            .setPositiveButton("Abrir") { _, _ -> LoginActivity.open(this, site) }
            .setNegativeButton("Cerrar sesión") { _, _ ->
                Cookies.logout(site)
                refreshAccounts()
            }
            .setNeutralButton("Volver", null)
            .show()
    }

    private fun refreshEngine() {
        lifecycleScope.launch {
            val v = withContext(Dispatchers.IO) { Engine.version(this@MainActivity) }
            engineText.text = getString(R.string.engine, v ?: "preparando…")
        }
    }

    private fun updateEngine() {
        engineText.text = getString(R.string.engine, "actualizando…")
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Engine.update(this@MainActivity) } }
            r.onSuccess { snack(if (it == "ALREADY_UP_TO_DATE") "yt-dlp ya estaba al día" else "yt-dlp actualizado") }
                .onFailure { snack("No se pudo actualizar: ${it.message}") }
            refreshEngine()
        }
    }

    private fun reloadHistory() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { History.load(this@MainActivity) }
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openItem(item: History.Item) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.uri), item.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(i) }.onFailure { toast("El archivo ya no está") }
    }

    private fun itemMenu(item: History.Item, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Compartir")
            menu.add(0, 2, 1, "Abrir el post original")
            menu.add(0, 3, 2, "Copiar enlace")
            menu.add(0, 4, 3, "Quitar de la lista")
            menu.add(0, 5, 4, "Borrar el archivo")
            setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    1 -> startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType(item.mime)
                                .putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            null,
                        ),
                    )
                    2 -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.source))) }
                    3 -> {
                        getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(android.content.ClipData.newPlainText("enlace", item.source))
                        lastAutoPaste = item.source
                        toast("Enlace copiado")
                    }
                    4 -> History.remove(this@MainActivity, item.uri)
                    5 -> {
                        val ok = runCatching { contentResolver.delete(Uri.parse(item.uri), null, null) }.getOrDefault(0) > 0
                        History.remove(this@MainActivity, item.uri)
                        toast(if (ok) "Archivo borrado" else "Quitado de la lista (bórralo desde la galería)")
                    }
                }
                true
            }
        }.show()
    }

    private fun snack(msg: String) = Snackbar.make(findViewById(R.id.root), msg, Snackbar.LENGTH_LONG).show()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
