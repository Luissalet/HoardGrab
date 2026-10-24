package com.luissalet.hoardgrab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifs {
    const val CH_PROGRESS = "progress"
    const val CH_DONE = "done"
    const val ID_PROGRESS = 1

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Descarga en curso", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(ctx: Context, title: String, text: String, progress: Float, pending: Int): Notification {
        val cancel = PendingIntent.getService(
            ctx, 1, Intent(ctx, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_grab)
            .setContentTitle(if (pending > 1) "$title (+${pending - 1} en cola)" else title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Cancelar", cancel)
        if (progress in 0f..100f) b.setProgress(100, progress.toInt(), false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun canPost(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    @Suppress("MissingPermission")
    fun done(ctx: Context, id: Int, first: Saved, count: Int, author: String) {
        if (!canPost(ctx)) return
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(first.uri, first.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, id, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val what = if (count > 1) "$count archivos guardados" else "Vídeo guardado"
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_grab)
            .setContentTitle(what)
            .setContentText(if (author.isNotBlank()) "$author · Movies/Hoard Grab" else "Movies/Hoard Grab")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    @Suppress("MissingPermission")
    fun failed(ctx: Context, id: Int, message: String) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_grab)
            .setContentTitle("No se pudo descargar")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}
