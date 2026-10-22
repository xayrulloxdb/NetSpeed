package uz.netspeed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.util.Locale

/**
 * Ҳар сонияда умумий трафикни ўлчаб, статус-бардаги иконкага тезликни ёзади.
 */
class SpeedService : Service() {

    companion object {
        // Янги канал: Samsung "овозсиз" (LOW) билдиришноманинг иконкасини статус-барда яширади
        const val CHANNEL_ID = "speed_live"
        const val OLD_CHANNEL_ID = "speed_channel"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "uz.netspeed.STOP"
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SpeedService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpeedService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager

    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L

    // Кунлик жами (иловани ишга туширгандан бери)
    private var sessionRx = 0L
    private var sessionTx = 0L

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        val first = buildNotification(0, 0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, first)
        }

        if (!isRunning) {
            isRunning = true
            lastRx = TrafficStats.getTotalRxBytes()
            lastTx = TrafficStats.getTotalTxBytes()
            lastTime = SystemClock.elapsedRealtime()
            handler.post(tick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        isRunning = false
        super.onDestroy()
    }

    private fun update() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()

        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return

        val dt = (now - lastTime).coerceAtLeast(1)
        // Тармоқ алмашганда ҳисоблагич нолланиши мумкин — манфий қийматни ташлаймиз
        val dRx = (rx - lastRx).coerceAtLeast(0)
        val dTx = (tx - lastTx).coerceAtLeast(0)
        val down = dRx * 1000 / dt   // байт/сония
        val up = dTx * 1000 / dt

        sessionRx += dRx
        sessionTx += dTx
        lastRx = rx; lastTx = tx; lastTime = now

        nm.notify(NOTIF_ID, buildNotification(down, up))
    }

    // ---------- Билдиришнома ----------

    private fun createChannel() {
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        // DEFAULT — иконка статус-барда кўринади; овоз ва вибрация ўчирилган
        val ch = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(down: Long, up: Long): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, SpeedService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "↓ ${formatSpeed(down)}   ↑ ${formatSpeed(up)}"
        val text = getString(R.string.session_total, formatBytes(sessionRx), formatBytes(sessionTx))

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(speedIcon(down + up))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stopIntent).build())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // ---------- Статус-бар иконкаси ----------

    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.NORMAL)
    }

    /** Иконкада икки қатор: юқорида рақам, пастда бирлик (KB/s ёки MB/s). */
    private fun speedIcon(bytesPerSec: Long): Icon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val (num, unit) = splitSpeed(bytesPerSec)

        numPaint.textSize = if (num.length >= 4) 50f else 60f
        unitPaint.textSize = 36f

        c.drawText(num, size / 2f, 54f, numPaint)
        c.drawText(unit, size / 2f, 92f, unitPaint)
        return Icon.createWithBitmap(bmp)
    }

    private fun splitSpeed(b: Long): Pair<String, String> {
        val kb = b / 1024.0
        return when {
            kb < 1 -> "0" to "KB/s"
            kb < 100 -> String.format(Locale.US, if (kb < 10) "%.1f" else "%.0f", kb) to "KB/s"
            kb < 1000 -> String.format(Locale.US, "%.0f", kb) to "KB/s"
            else -> {
                val mb = kb / 1024.0
                String.format(Locale.US, if (mb < 10) "%.1f" else "%.0f", mb) to "MB/s"
            }
        }
    }

    private fun formatSpeed(b: Long): String {
        val (n, u) = splitSpeed(b)
        return "$n $u"
    }

    private fun formatBytes(b: Long): String {
        val kb = b / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
