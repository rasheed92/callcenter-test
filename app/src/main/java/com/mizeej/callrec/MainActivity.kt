package com.mizeej.callrec

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * شاشة تجريبية:
 *  - تعرض "طلب" واحد فيه رقم زبون.
 *  - زرّان: اتصال عبر الخط / اتصال عبر واتساب.
 *  - عند الضغط: تعرض إقرار "المكالمة مسجّلة"، تنشئ جلسة، تشغّل خدمة
 *    التسجيل، ثم تفتح المكالمة.
 *  - تكتشف نهاية المكالمة وتوقف التسجيل وتربط الملف بالطلب.
 *  - شاشة ثانية: قائمة التسجيلات المحفوظة.
 *
 * هذا نموذج اختبار لجودة الصوت وتدفق العمل فقط، قبل بناء النظام الكامل
 * المربوط بسيرفر Mizeej.
 */
class MainActivity : AppCompatActivity() {

    // طلب تجريبي ثابت
    private val demoOrderId = "1200"
    private var demoPhone = "07700000000"

    private lateinit var tm: TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private var wasInCall = false
    private var pollCount = 0

    private lateinit var ui: MainUi

    private val reqPerms = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* النتيجة تُفحص عند الاتصال */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        ui = MainUi(
            activity = this,
            orderId = demoOrderId,
            phoneProvider = { demoPhone },
            onPhoneChanged = { demoPhone = it },
            onCallSim = { startCall(CallSession.Channel.SIM) },
            onCallWhatsApp = { startCall(CallSession.Channel.WHATSAPP) },
            onOpenRecordings = { startActivity(Intent(this, RecordingsActivity::class.java)) },
            onCheckSetup = { showSetupStatus() }
        )
        setContentView(ui.build())

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) reqPerms.launch(missing.toTypedArray())
    }

    private fun startCall(channel: CallSession.Channel) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "صلاحية المايك غير ممنوحة", Toast.LENGTH_LONG).show()
            requestNeededPermissions()
            return
        }

        // إقرار التسجيل — يُعرض للموظف ويُسجّل مع الجلسة
        AlertDialog.Builder(this)
            .setTitle("تنبيه التسجيل")
            .setMessage(
                "سيتم تسجيل هذه المكالمة لأغراض الجودة والمتابعة.\n" +
                "يُرجى إعلام الزبون بأن المكالمة مسجّلة.\n\n" +
                "الطلب: $demoOrderId\nالرقم: $demoPhone"
            )
            .setPositiveButton("موافق، ابدأ الاتصال") { _, _ ->
                beginSessionAndDial(channel)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun beginSessionAndDial(channel: CallSession.Channel) {
        // 1) أنشئ الجلسة
        val session = CallSession(
            orderId = demoOrderId,
            phone = demoPhone,
            channel = channel,
            startedAt = System.currentTimeMillis(),
            staffConfirmedDisclosure = true
        )
        SessionStore.start(session)

        // 2) شغّل خدمة التسجيل (ونحن في المقدّمة — مطلوب على Android 14+)
        val recIntent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_ORDER_ID, demoOrderId)
        }
        ContextCompat.startForegroundService(this, recIntent)

        // 3) افتح المكالمة
        wasInCall = false
        pollCount = 0
        when (channel) {
            CallSession.Channel.SIM -> dialSim(demoPhone)
            CallSession.Channel.WHATSAPP -> dialWhatsApp(demoPhone)
        }

        // 4) ابدأ مراقبة نهاية المكالمة
        handler.postDelayed(callWatcher, 2000)
    }

    private fun dialSim(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
            startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, "صلاحية الاتصال مرفوضة", Toast.LENGTH_LONG).show()
            stopRecordingNow(answered = false, duration = 0)
        }
    }

    /**
     * واتساب لا يوفّر طريقة رسمية لبدء مكالمة مباشرة. نفتح محادثة الزبون،
     * والموظف يضغط زر الاتصال داخل واتساب. التسجيل يكون قد بدأ مسبقاً.
     */
    private fun dialWhatsApp(phone: String) {
        val intl = phone.replaceFirst(Regex("^0"), "964")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl"))
            intent.setPackage("com.whatsapp")
            startActivity(intent)
            Toast.makeText(this, "اضغط زر الاتصال داخل واتساب", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // جرّب بدون تحديد الحزمة (قد يكون WhatsApp Business)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl")))
            } catch (e2: Exception) {
                Toast.makeText(this, "واتساب غير مثبّت", Toast.LENGTH_LONG).show()
                stopRecordingNow(answered = false, duration = 0)
            }
        }
    }

    /**
     * مراقب نهاية المكالمة. لمكالمة الخط نعتمد على حالة الهاتف. لمكالمة
     * واتساب (التي لا تظهر في حالة الهاتف) نعتمد على خروج النظام من وضع
     * الاتصال الصوتي أو انتهاء مهلة قصوى.
     */
    private val callWatcher = object : Runnable {
        override fun run() {
            pollCount++
            val session = SessionStore.active
            if (session == null) return  // أُنهيت الجلسة فعلاً

            val ended: Boolean = when (session.channel) {
                CallSession.Channel.SIM -> {
                    @Suppress("DEPRECATION")
                    val state = tm.callState
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK) wasInCall = true
                    wasInCall && state == TelephonyManager.CALL_STATE_IDLE
                }
                CallSession.Channel.WHATSAPP -> {
                    val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    @Suppress("DEPRECATION")
                    val inComm = am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION
                    if (inComm) wasInCall = true
                    // انتهت إذا كنا في مكالمة ثم خرجنا، أو تجاوزنا المهلة القصوى
                    (wasInCall && !inComm) || pollCount > 600 // ~20 دقيقة سقف
                }
            }

            if (ended) {
                val answered = wasInCall
                val duration = if (answered)
                    ((System.currentTimeMillis() - session.startedAt) / 1000).toInt() else 0
                stopRecordingNow(answered, duration)
            } else {
                handler.postDelayed(this, 2000)
            }
        }
    }

    private fun stopRecordingNow(answered: Boolean, duration: Int) {
        handler.removeCallbacks(callWatcher)
        val stop = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        startService(stop)

        // امنح الخدمة لحظة لإغلاق الملف ثم اربطه بالطلب
        handler.postDelayed({
            SessionStore.finishActive(
                filePath = RecordingService.lastFilePath,
                answered = answered,
                durationSec = duration
            )
            val msg = if (answered)
                "تم حفظ التسجيل وربطه بالطلب $demoOrderId (المدة ${duration}ث)"
            else
                "لم يُردّ الزبون — سُجّلت المحاولة"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }, 800)
    }

    private fun showSetupStatus() {
        val acc = CallAccessibilityService.isRunning
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val msg = StringBuilder()
        msg.append(if (mic) "✓ صلاحية المايك\n" else "✗ صلاحية المايك\n")
        msg.append(if (acc) "✓ خدمة إمكانية الوصول مفعّلة\n" else "✗ خدمة إمكانية الوصول غير مفعّلة\n")

        val builder = AlertDialog.Builder(this)
            .setTitle("حالة الإعداد")
            .setMessage(msg.toString())
            .setPositiveButton("حسناً", null)
        if (!acc) {
            builder.setNeutralButton("فتح إعدادات إمكانية الوصول") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        builder.show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(callWatcher)
        super.onDestroy()
    }
}
