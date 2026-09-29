package com.treader

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

/** Nhắc đọc sách hằng ngày: 1 báo thức hẹn giờ, tự đặt lại cho ngày hôm sau mỗi khi nổ ra. */
object Reminder {
    const val CHANNEL = "reading_reminder"
    private const val REQ = 1001

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Nhắc đọc sách", NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, REQ, Intent(ctx, ReminderReceiver::class.java), flags())

    fun schedule(ctx: Context, hour: Int, min: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = ctx.getSystemService(AlarmManager::class.java)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi(ctx)) }
    }

    fun cancel(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx)) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminder.createChannel(ctx)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val nb = NotificationCompat.Builder(ctx, Reminder.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Đến giờ đọc sách rồi 📖")
            .setContentText("Dành vài phút đọc tiếp cuốn sách của bạn nhé")
            .setAutoCancel(true).setContentIntent(open).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1, nb) }
        val s = Store(ctx)
        if (s.prefs.remindOn) Reminder.schedule(ctx, s.prefs.remindHour, s.prefs.remindMin)
    }
}

/** Báo thức bị hệ thống xoá khi khởi động lại máy -> đặt lại nếu người dùng đang bật nhắc đọc. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminder.createChannel(ctx)
            val s = Store(ctx)
            if (s.prefs.remindOn) Reminder.schedule(ctx, s.prefs.remindHour, s.prefs.remindMin)
        }
    }
}