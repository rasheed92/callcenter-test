package com.mizeej.callrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File

/**
 * خدمة أمامية تسجّل صوت المكالمة إلى ملف.
 *
 * من Android 14 يجب أن تبدأ خدمة المايك بينما التطبيق في المقدّمة، ويجب
 * الإعلان عن foregroundServiceType=microphone. لذلك نبدأها من MainActivity
 * قبل فتح المكالمة مباشرة.
 *
 * نستخدم مصدر VOICE_RECOGNITION لأنه — مع تفعيل خدمة إمكانية الوصول —
 * يحصل على الصوت بدل أن يُسكّت أثناء المكالمة. VOICE_COMMUNICATION يُسكّت
 * عادةً للتطبيقات العادية.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "callrec_recording"
        private const val NOTIF_ID = 1001

        const val ACTION_START = "com.mizeej.callrec.START"
        const val ACTION_STOP = "com.mizeej.callrec.STOP"
        const val EXTRA_ORDER_ID = "order_id"

        @Volatile
        var lastFilePath: String = ""
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotif()
                val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: "unknown"
                startRecording(orderId)
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotif() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "تسجيل المكالمات",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("جاري تسجيل المكالمة")
            .setContentText("المكالمة مسجّلة لأغراض الجودة")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startRecording(orderId: String) {
        try {
            val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
            val file = File(dir, "order_${orderId}_${System.currentTimeMillis()}.m4a")
            outputFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()

            rec.apply {
                // VOICE_RECOGNITION: يبقى غير مُسكّت مع خدمة إمكانية الوصول
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            Log.i(TAG, "started recording -> ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording failed", e)
        }
        recorder = null
        outputFile?.let { lastFilePath = it.absolutePath }
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
