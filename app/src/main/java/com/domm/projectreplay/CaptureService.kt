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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil

class CaptureService : Service() {
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var currentSegment: File? = null
    private val segments = mutableListOf<File>()
    private val pinnedSegments = mutableSetOf<File>()
    private val handler = Handler(Looper.getMainLooper())
    private val exportExecutor = Executors.newSingleThreadExecutor()
    private var stopping = false
    private var assembling = false
    private var width = 0
    private var height = 0
    private var densityDpi = 0
    private var outputFolder: File? = null
    private val rotateTask = object : Runnable {
        override fun run() {
            if (stopping || projection == null) return
            rotateSegment()
            handler.postDelayed(this, SEGMENT_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopCapture(); return START_NOT_STICKY }
            ACTION_CLIP -> { saveReplayClip(); return START_STICKY }
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForegroundCompat()
        try {
            beginReplayBuffer(resultCode, resultData)
        } catch (error: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("recording", false)
                .putString("capture_error", error.message ?: "Replay capture could not start. Check storage and try again.")
                .apply()
            stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun beginReplayBuffer(resultCode: Int, data: Intent) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(resultCode, data)
        stopping = false
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, handler)

        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
        val scale = minOf(1f, 1920f / maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1))
        width = ((metrics.widthPixels * scale).toInt().coerceAtLeast(2) / 2) * 2
        height = ((metrics.heightPixels * scale).toInt().coerceAtLeast(2) / 2) * 2
        densityDpi = metrics.densityDpi

        outputFolder = File(getExternalFilesDir(null), "Movies/ProjectReplay").apply { mkdirs() }
        if (StatFs(outputFolder!!.absolutePath).availableBytes < MIN_FREE_BYTES) {
            throw IllegalStateException("Not enough free storage. Project Replay needs at least 500 MB available.")
        }
        val bufferFolder = File(cacheDir, "ReplayBuffer").apply { mkdirs() }
        bufferFolder.listFiles()?.forEach { it.delete() }
        segments.clear()
        prefs.edit().remove("capture_error").putBoolean("recording", true).putString("capture_mode", "rolling_buffer").apply()
        startSegment()
        handler.postDelayed(rotateTask, SEGMENT_MS)
        updateNotification()
    }

    private fun createRecorder(): MediaRecorder {
        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        val quality = getSharedPreferences(PREFS, MODE_PRIVATE).getString("quality", "Balanced") ?: "Balanced"
        val bitrate = when (quality) { "Data saver" -> 2_500_000; "High" -> 9_000_000; else -> 5_000_000 }
        media.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        media.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        media.setVideoSize(width, height)
        media.setVideoFrameRate(30)
        media.setVideoEncodingBitRate(bitrate)
        val file = File(cacheDir, "ReplayBuffer/segment_${System.currentTimeMillis()}_${segments.size}.mp4")
        currentSegment = file
        media.setOutputFile(file.absolutePath)
        media.prepare()
        return media
    }

    private fun startSegment() {
        val media = createRecorder()
        recorder = media
        if (display == null) {
            display = projection!!.createVirtualDisplay(
                "ProjectReplayRollingBuffer", width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, media.surface, null, handler
            )
        } else {
            display?.setSurface(media.surface)
        }
        media.start()
    }

    private fun finishSegment(): File? {
        val oldRecorder = recorder ?: return null
        val file = currentSegment
        recorder = null
        currentSegment = null
        try {
            oldRecorder.stop()
        } catch (_: RuntimeException) {
            file?.delete()
        } finally {
            try { oldRecorder.reset(); oldRecorder.release() } catch (_: Exception) {}
        }
        return file?.takeIf { it.exists() && it.length() > 1024L }
    }

    private fun rotateSegment() {
        val completed = finishSegment()
        if (completed != null) segments.add(completed)
        pruneBuffer()
        try {
            if (!stopping && projection != null) startSegment()
        } catch (error: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("capture_error", "Replay buffer paused: ${error.message ?: "encoder error"}")
                .apply()
            stopCapture()
        }
    }

    private fun pruneBuffer() {
        val keepCount = ceil(MAX_BUFFER_SECONDS.toDouble() / SEGMENT_SECONDS).toInt() + 2
        while (segments.size > keepCount) {
            val removable = segments.firstOrNull { it !in pinnedSegments } ?: break
            segments.remove(removable)
            removable.delete()
        }
    }

    private fun saveReplayClip() {
        if (projection == null || stopping || assembling) return
        assembling = true
        rotateSegment()
        if (projection == null) { assembling = false; return }
        val seconds = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("replay_seconds", 30).coerceIn(15, 120)
        val count = ceil(seconds.toDouble() / SEGMENT_SECONDS).toInt()
        val selected = segments.takeLast(count).toList()
        pinnedSegments.addAll(selected)
        if (selected.isEmpty()) {
            assembling = false
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("capture_error", "The replay buffer is still warming up. Try again in a few seconds.").apply()
            return
        }
        val destination = File(outputFolder, "Replay_Clip_${System.currentTimeMillis()}.mp4")
        exportExecutor.execute {
            val result = runCatching { ClipAssembler.assemble(selected, destination) }
            handler.post {
                assembling = false
                pinnedSegments.removeAll(selected.toSet())
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                if (stopping) {
                    segments.forEach { it.delete() }
                    segments.clear()
                } else {
                    pruneBuffer()
                }
                if (result.isSuccess) {
                    prefs.edit().remove("capture_error").putString("last_saved_clip", destination.absolutePath).apply()
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification("Replay clip saved to your library"))
                } else {
                    destination.delete()
                    prefs.edit().putString("capture_error", "Couldn't assemble that clip. Keep the buffer running and try again.").apply()
                }
            }
        }
    }

    private fun stopCapture() {
        if (stopping) return
        stopping = true
        handler.removeCallbacks(rotateTask)
        finishSegment()?.delete()
        if (!assembling) {
            segments.forEach { it.delete() }
            segments.clear()
        }
        val activeProjection = projection
        projection = null
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { activeProjection?.stop() } catch (_: Exception) {}
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit().putBoolean("recording", false).remove("capture_mode").apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Replay capture", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(message: String = "Rolling replay buffer is active"): Notification {
        val clipIntent = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_CLIP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 2, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Project Replay")
            .setContentText(message)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Clip That", clipIntent).build())
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        if (!stopping) stopCapture()
        exportExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.domm.projectreplay.STOP_CAPTURE"
        const val ACTION_CLIP = "com.domm.projectreplay.SAVE_REPLAY_CLIP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val PREFS = "project_replay"
        private const val CHANNEL = "replay_capture"
        private const val NOTIFICATION_ID = 7301
        private const val MIN_FREE_BYTES = 500L * 1024L * 1024L
        private const val SEGMENT_SECONDS = 4
        private const val SEGMENT_MS = SEGMENT_SECONDS * 1000L
        private const val MAX_BUFFER_SECONDS = 120
    }
}
