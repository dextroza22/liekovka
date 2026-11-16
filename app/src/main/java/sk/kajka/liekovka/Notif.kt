package sk.kajka.liekovka

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notif {
    private const val CHANNEL = "zmeny"
    const val EXTRA_TAB = "tab"

    fun vytvorKanal(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.channel_changes), NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = ctx.getString(R.string.channel_changes_desc)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun moze(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ukaz(ctx: Context, titulok: String, text: String) {
        if (!moze(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_TAB, "upozornenia")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(titulok)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (_: SecurityException) {
        }
    }
}
