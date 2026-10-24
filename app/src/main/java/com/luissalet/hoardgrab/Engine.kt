package com.luissalet.hoardgrab

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/** The embedded yt-dlp (Python + ffmpeg inside the APK). */
object Engine {
    private const val PREFS = "engine"
    private const val STALE_MS = 24L * 3600 * 1000

    /** H.264 + AAC first so the file plays in any gallery, then whatever is best. */
    const val FORMAT = "bv*[vcodec^=avc]+ba[ext=m4a]/b[ext=mp4][vcodec^=avc]/bv*+ba/b"

    @Volatile private var initDone = false

    @Synchronized
    fun ensureInit(ctx: Context) {
        if (initDone) return
        val app = ctx.applicationContext
        YoutubeDL.getInstance().init(app)
        FFmpeg.getInstance().init(app)
        initDone = true
    }

    /** The library only records a version after an update; otherwise ask yt-dlp itself and remember it. */
    fun version(ctx: Context): String? = runCatching {
        ensureInit(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, 0)
        YoutubeDL.getInstance().version(ctx.applicationContext)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("version", null)
            ?: YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList()).addOption("--version"))
                .out.trim().lines().lastOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?.also { prefs.edit().putString("version", it).apply() }
    }.getOrNull()

    fun lastUpdate(ctx: Context): Long = ctx.getSharedPreferences(PREFS, 0).getLong("lastUpdate", 0)

    /** Pulls the latest stable yt-dlp from its releases; X and Instagram change often. */
    fun update(ctx: Context): String {
        ensureInit(ctx)
        val status = YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        ctx.getSharedPreferences(PREFS, 0).edit().putLong("lastUpdate", System.currentTimeMillis()).remove("version").apply()
        return status?.name ?: "DONE"
    }

    fun updateIfStale(ctx: Context) {
        if (System.currentTimeMillis() - lastUpdate(ctx) > STALE_MS) runCatching { update(ctx) }
    }

    fun download(
        ctx: Context,
        url: String,
        jobId: String,
        workDir: File,
        onProgress: (Float, String) -> Unit,
    ) {
        ensureInit(ctx)
        val req = YoutubeDLRequest(url)
        req.addOption("--no-mtime")
        req.addOption("-f", FORMAT)
        req.addOption("--merge-output-format", "mp4")
        req.addOption("-o", File(workDir, "%(uploader_id,uploader,extractor)s_%(id)s%(playlist_index&_{}|)s.%(ext)s").absolutePath)
        req.addCommands(
            listOf("--print-to-file", "after_move:%(filepath)s\t%(uploader,uploader_id|)s\t%(title|)s", File(workDir, "meta.tsv").absolutePath),
        )
        req.addOption("--retries", "3")
        req.addOption("--extractor-retries", "3")
        Cookies.export(ctx)?.let { req.addOption("--cookies", it.absolutePath) }
        YoutubeDL.getInstance().execute(req, jobId) { progress, _, line -> onProgress(progress, line) }
    }

    fun cancel(jobId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(jobId) }
    }
}

/** yt-dlp errors turned into something readable, and whether a fresh yt-dlp might fix it. */
object Errors {
    data class Failure(val message: String, val retryAfterUpdate: Boolean)

    fun classify(raw: String?, platform: String): Failure {
        val text = raw.orEmpty()
        val low = text.lowercase()
        val account = if (platform == "Instagram") "Instagram" else "X"
        return when {
            "unable to resolve host" in low || "network is unreachable" in low || "timed out" in low ||
                "connection reset" in low || "failed to establish" in low ->
                Failure("Sin conexión o la red ha fallado. Prueba otra vez.", false)
            "no video could be found" in low || "no video in this" in low || "there is no video" in low ||
                "no media found" in low ->
                Failure("Ese post no tiene vídeo.", false)
            "unsupported url" in low ->
                Failure("Ese enlace no es de un vídeo compatible.", false)
            "login" in low || "log in" in low || "logged-in" in low || "cookies" in low ||
                "authentication" in low || "sensitive" in low || "nsfw" in low || "age-restricted" in low ||
                "private" in low || "not available" in low && platform == "Instagram" ->
                Failure("Hace falta la sesión de $account (cuenta privada, contenido sensible o límite de la plataforma). Inicia sesión en «Cuentas» y repite.", true)
            "http error 404" in low || "does not exist" in low || "has been deleted" in low ->
                Failure("El post no existe o lo han borrado.", false)
            "http error 429" in low || "rate" in low && "limit" in low ->
                Failure("$account está limitando las descargas. Espera un poco o inicia sesión.", true)
            "http error 403" in low ->
                Failure("La plataforma ha rechazado la descarga (403).", true)
            else -> Failure(lastLine(text).ifEmpty { "Error desconocido de yt-dlp." }, true)
        }
    }

    private fun lastLine(text: String): String =
        text.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.removePrefix("ERROR:")?.trim()?.take(240) ?: ""
}
