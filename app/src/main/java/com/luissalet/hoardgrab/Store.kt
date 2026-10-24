package com.luissalet.hoardgrab

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Live state shared by the service and the screen (same process). */
object GrabState {
    data class Current(
        val jobId: String,
        val url: String,
        val platform: String,
        val progress: Float,
        val line: String,
    )

    val current = MutableStateFlow<Current?>(null)
    val pending = MutableStateFlow(0)
    val historyTick = MutableStateFlow(0L)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
}

data class Saved(val uri: Uri, val name: String, val mime: String)

/** Copies finished files into the public gallery: Movies/Hoard Grab (photos to Pictures/Hoard Grab). */
object MediaSaver {
    private const val FOLDER = "Hoard Grab"
    private val MEDIA_EXT = setOf("mp4", "m4v", "mov", "webm", "mkv", "m4a", "mp3", "aac", "opus", "jpg", "jpeg", "png", "webp", "gif")

    fun isMedia(f: File) = f.isFile && f.extension.lowercase() in MEDIA_EXT

    fun save(ctx: Context, file: File): Saved {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
        val (collection, dir) = when {
            mime.startsWith("image/") ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_PICTURES
            mime.startsWith("audio/") ->
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MUSIC
            else ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to Environment.DIRECTORY_MOVIES
        }
        val cr = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(collection, values) ?: error("No se pudo crear el archivo en la galería")
        try {
            cr.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { cr.delete(uri, null, null) }
            throw e
        }
        return Saved(uri, file.name, mime)
    }
}

/** The list of finished downloads shown on the main screen. */
object History {
    data class Item(
        val uri: String,
        val name: String,
        val mime: String,
        val title: String,
        val author: String,
        val platform: String,
        val source: String,
        val ts: Long,
    )

    private const val PREFS = "history"
    private const val MAX = 300

    fun load(ctx: Context): List<Item> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString("items", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Item(
                o.optString("uri"), o.optString("name"), o.optString("mime"), o.optString("title"),
                o.optString("author"), o.optString("platform"), o.optString("source"), o.optLong("ts"),
            )
        }
    }

    private fun store(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.take(MAX).forEach {
            arr.put(
                JSONObject().put("uri", it.uri).put("name", it.name).put("mime", it.mime).put("title", it.title)
                    .put("author", it.author).put("platform", it.platform).put("source", it.source).put("ts", it.ts),
            )
        }
        ctx.getSharedPreferences(PREFS, 0).edit().putString("items", arr.toString()).apply()
        GrabState.historyTick.value = System.nanoTime()
    }

    @Synchronized
    fun add(ctx: Context, new: List<Item>) = store(ctx, new + load(ctx))

    @Synchronized
    fun remove(ctx: Context, uri: String) = store(ctx, load(ctx).filterNot { it.uri == uri })
}
