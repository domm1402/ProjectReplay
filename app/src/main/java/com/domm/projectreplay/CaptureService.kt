package com.domm.projectreplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

class CaptureService : Service() {
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var output: File? = null
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >=  Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        try {
            beginCapture(resultCode, resultData)
        } catch (error: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("recording", false).apply()
            stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun beginCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        stopping = false
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, null)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val scale = minOf(1f, 1920f / maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1))
        val width = ((metrics.widthPixels * scale).toInt().coerceAtLeast(2) / 2) * 2
        val height = ((metrics.heightPixels * scale).toInt().coerceAtLeast(2) / 2) * 2
        val folder = File(getExternalFilesDir(null), "Movies/ProjectReplay").apply { mkdirs() }
        val freeBytes = StatFs(folder.absolutePath).availableBytes
        if (freeBytes < MIN_FREE_BYTES) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("capture_error", "Not enough free storage. Project Replay needs at least 500 MB available.")
                .apply()
            throw IllegalStateException("Insufficient free storage")
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("capture_error").apply()
        output = File(folder, "Replay_${System.currentTimeMillis()}.mp4")
        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = media
        media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        media.setVideoSize(width, height)
        val quality = getSharedPreferences(PREFS, MODE_PRIVATE).getString("quality", "Balanced") ?: "Balanced"
        val bitrate = when (quality) { "Data saver" -> 2_500_000; "High" -> 9_000_000; else -> 5_000_000 }
        media.setVideoFrameRate(30)
        media.setVideoEncodingBitRate(bitrate)
        media.setOutputFile(output!!.absolutePath)
        media.prepare()
        display = projection!!.createVirtualDisplay(
            "ProjectReplayCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, media.surface, null, null
        )
        media.start()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("recording", true).putString("active_file", output!!.absolutePath).apply()
    }

    private fun stopCapture() {
        if (stopping) return
        stopping = true
        val activeProjection = projection
        projection = null
        try { recorder?.stop() } catch (_: RuntimeException) { output?.delete() }
        try { display?.release() } catch (_: Exception) {}
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
        try { activeProjection?.stop() } catch (_: Exception) {}
        recorder = null
        display = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("recording", false).remove("active_file").apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Replay capture", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(this, 2, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Project Replay is recording").setContentText("Your screen is being recorded. Tap Stop when finished.")
                .setOngoing(true).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Project Replay is recording").setContentText("Tap Stop when finished.")
                .setOngoing(true).addAction(android.R.drawable.ic_media_pause, "Stop", stop).build()
        }
    }

    override fun onDestroy() {
        if (recorder != null || projection != null) stopCapture()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.domm.projectreplay.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val PREFS = "project_replay"
        private const val CHANNEL = "replay_capture"
        private const val NOTIFICATION_ID = 7301
        private const val Q = 29
        private const val MIN_FREE_BYTES = 500L * 1024L * 1024L
    }
}
