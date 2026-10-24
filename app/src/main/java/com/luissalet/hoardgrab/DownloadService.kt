package com.luissalet.hoardgrab

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.UUID

/** Runs the queue of links one after another in the foreground, so a share keeps going with the app closed. */
class DownloadService : Service() {
    companion object {
        const val EXTRA_URL = "url"
        const val ACTION_CANCEL = "com.luissalet.hoardgrab.CANCEL"

        fun enqueue(ctx: Context, url: String) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java).putExtra(EXTRA_URL, url))
        }

        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, DownloadService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val queue = ArrayDeque<String>()
    private var worker: Job? = null
    private var lastStartId = 0
    @Volatile private var currentId: String? = null
    @Volatile private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        ServiceCompat.startForeground(
            this, Notifs.ID_PROGRESS,
            Notifs.progress(this, "Hoard Grab", "Preparando…", -1f, queue.size + 1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (intent?.action == ACTION_CANCEL) {
            currentId?.let { Engine.cancel(it) }
            if (worker == null) finish()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        synchronized(lock) {
            if (url != null) queue.addLast(url)
            GrabState.pending.value = queue.size + if (currentId != null) 1 else 0
            if (worker == null && queue.isNotEmpty()) worker = scope.launch { runQueue() }
        }
        if (url == null && worker == null) finish()
        return START_NOT_STICKY
    }

    private suspend fun runQueue() {
        while (true) {
            val url = synchronized(lock) {
                queue.pollFirst().also { if (it == null) worker = null }
            } ?: break
            GrabState.pending.value = queue.size + 1
            runCatching { process(url) }.onFailure {
                GrabState.messages.tryEmit("Error: ${it.message}")
            }
        }
        currentId = null
        GrabState.current.value = null
        GrabState.pending.value = 0
        withContext(Dispatchers.Main) { finish() }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private fun report(jobId: String, url: String, platform: String, progress: Float, line: String, force: Boolean = false) {
        GrabState.current.value = GrabState.Current(jobId, url, platform, progress, line)
        val now = System.currentTimeMillis()
        if (!force && now - lastNotify < 700) return
        lastNotify = now
        val text = if (progress >= 0f) "${progress.toInt()} %" else line.take(80)
        runCatching {
            NotificationManagerCompat.from(this).notify(
                Notifs.ID_PROGRESS,
                Notifs.progress(this, "Descargando de $platform", text, progress, GrabState.pending.value),
            )
        }
    }

    private fun process(url: String) {
        val jobId = UUID.randomUUID().toString()
        val notifId = (jobId.hashCode() and 0x7fffffff) or 2
        val platform = Links.platform(url)
        currentId = jobId
        report(jobId, url, platform, -1f, "Preparando el motor…", force = true)
        Engine.ensureInit(this)
        if (System.currentTimeMillis() - Engine.lastUpdate(this) > 24L * 3600 * 1000) {
            report(jobId, url, platform, -1f, "Actualizando yt-dlp…", force = true)
            Engine.updateIfStale(this)
        }

        val work = File(cacheDir, "grab/$jobId")
        var updated = false
        while (true) {
            work.deleteRecursively()
            work.mkdirs()
            report(jobId, url, platform, -1f, "Buscando el vídeo…", force = true)
            try {
                Engine.download(this, url, jobId, work) { p, line ->
                    report(jobId, url, platform, if (p > 0f) p else -1f, line.trim())
                }
                break
            } catch (e: YoutubeDL.CanceledException) {
                work.deleteRecursively()
                GrabState.messages.tryEmit("Descarga cancelada")
                return
            } catch (e: Exception) {
                if (currentId == null) return
                val failure = Errors.classify(e.message, platform)
                if (failure.retryAfterUpdate && !updated) {
                    updated = true
                    report(jobId, url, platform, -1f, "Fallo; actualizando yt-dlp y reintentando…", force = true)
                    runCatching { Engine.update(this) }
                    continue
                }
                work.deleteRecursively()
                Notifs.failed(this, notifId, failure.message)
                GrabState.messages.tryEmit(failure.message)
                return
            }
        }

        report(jobId, url, platform, 100f, "Guardando en la galería…", force = true)
        val meta = File(work, "meta.tsv").takeIf { it.exists() }?.readLines().orEmpty()
            .mapNotNull { line -> line.split('\t').takeIf { it.size >= 3 } }
            .associate { File(it[0]).name to (it[1] to it[2]) }
        val files = work.walkTopDown().filter { MediaSaver.isMedia(it) }.sortedBy { it.name }.toList()
        if (files.isEmpty()) {
            work.deleteRecursively()
            val msg = "No se ha encontrado vídeo en ese enlace."
            Notifs.failed(this, notifId, msg)
            GrabState.messages.tryEmit(msg)
            return
        }
        val now = System.currentTimeMillis()
        val saved = files.map { f -> f to MediaSaver.save(this, f) }
        History.add(this, saved.map { (f, s) ->
            val (author, title) = meta[f.name] ?: ("" to "")
            History.Item(s.uri.toString(), s.name, s.mime, title, author, platform, url, now)
        })
        work.deleteRecursively()
        val author = meta[files.first().name]?.first.orEmpty()
        Notifs.done(this, notifId, saved.first().second, saved.size, author)
        GrabState.messages.tryEmit(if (saved.size > 1) "${saved.size} archivos guardados" else "Guardado en Movies/Hoard Grab")
        currentId = null
    }

    override fun onDestroy() {
        currentId?.let { Engine.cancel(it) }
        scope.cancel()
        super.onDestroy()
    }
}
