package com.mizeej.callrec

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * قائمة التسجيلات المحفوظة في هذه الجلسة، مع تشغيل كل تسجيل والتحقق من
 * ربطه بالطلب. في النظام الحقيقي تُقرأ من السيرفر.
 */
class RecordingsActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "التسجيلات المحفوظة"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

        // 1) الجلسات المحفوظة بشكل دائم
        val sessions = Store.load(this).toMutableList()
        val knownPaths = sessions.map { it.filePath }.toHashSet()

        // 2) أي ملفات تسجيل على القرص ليست مرتبطة بجلسة (شبكة أمان إضافية)
        val dir = File(getExternalFilesDir(null), "recordings")
        dir.listFiles()?.filter { it.isFile && it.absolutePath !in knownPaths }?.forEach { f ->
            val orderId = Regex("order_([^_]+)_").find(f.name)?.groupValues?.get(1) ?: "?"
            sessions.add(CallSession(
                orderId = orderId,
                phone = "",
                channel = CallSession.Channel.SIM,
                startedAt = f.lastModified()
            ).apply {
                filePath = f.absolutePath
                answered = f.length() > 0
                durationSec = 0
            })
        }
        sessions.sortByDescending { it.startedAt }

        if (sessions.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "لا توجد تسجيلات بعد.\nاضغط \"اتصال\"، أكمل المكالمة، ثم ارجع هنا."
                setTextColor(Color.GRAY)
            })
        } else {
            for (s in sessions) {
                root.addView(buildRow(s, fmt))
            }
        }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun buildRow(s: CallSession, fmt: SimpleDateFormat): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val channel = if (s.channel == CallSession.Channel.SIM) "خط" else "واتساب"
        val status = if (s.answered) "تم الرد (${s.durationSec}ث)" else "بدون رد"
        row.addView(TextView(this).apply {
            text = "طلب ${s.orderId} • $channel • $status"
            setTextColor(Color.parseColor("#1565C0"))
            textSize = 16f
        })
        row.addView(TextView(this).apply {
            text = "${s.phone} • ${fmt.format(Date(s.startedAt))}"
            setTextColor(Color.parseColor("#666666"))
            textSize = 13f
        })

        val file = File(s.filePath)
        val exists = file.exists() && file.length() > 0
        row.addView(TextView(this).apply {
            text = if (exists) "الملف: ${file.name} (${file.length() / 1024} ك.ب)"
            else "⚠ لا يوجد ملف صوت (قد يكون الجهاز لا يدعم الالتقاط)"
            setTextColor(if (exists) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })

        if (exists) {
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            btns.addView(Button(this).apply {
                text = "▶ تشغيل"
                setOnClickListener { play(file) }
            })
            btns.addView(Button(this).apply {
                text = "مشاركة"
                setOnClickListener { share(file) }
            })
            row.addView(btns)
        }

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        wrap.addView(row)
        return wrap
    }

    private fun play(file: File) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "جاري التشغيل…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر التشغيل", Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "مشاركة التسجيل"))
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّرت المشاركة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
