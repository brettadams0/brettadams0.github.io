package dev.sift.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dev.sift.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Foreground progress for the grading batch (§9.2). */
object GradeNotifications {

    private const val CHANNEL_ID = "sift-grading"
    private const val NOTIFICATION_ID = 1001

    fun foregroundInfo(context: Context, completed: Int, total: Int): ForegroundInfo {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Grading photos")
            .setContentText("$completed of $total")
            .setSmallIcon(R.drawable.ic_stat_sift)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), completed, false)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Grading", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while Sift grades your keepers"
                setShowBadge(false)
            },
        )
    }
}

/**
 * §12 — "Log failures locally — no network, so write to a rotating file in app
 * storage and expose it in Settings."
 */
object GradeLog {

    private const val FILE_NAME = "sift-failures.log"
    private const val MAX_BYTES = 256 * 1024

    @Volatile
    private var directory: File? = null

    fun install(context: Context) {
        directory = context.filesDir
    }

    fun record(assetId: Long, error: Throwable) {
        val dir = directory ?: return
        runCatching {
            val file = File(dir, FILE_NAME)
            if (file.length() > MAX_BYTES) {
                // Rotate by keeping the newest half, so the log cannot grow
                // without bound on a device with no network to ship it off.
                val kept = file.readLines().takeLast(200)
                file.writeText(kept.joinToString("\n") + "\n")
            }
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("$stamp asset=$assetId ${error::class.simpleName}: ${error.message}\n")
        }
    }

    fun read(): String {
        val dir = directory ?: return ""
        val file = File(dir, FILE_NAME)
        return if (file.exists()) file.readText() else ""
    }

    fun clear() {
        directory?.let { File(it, FILE_NAME).delete() }
    }
}
