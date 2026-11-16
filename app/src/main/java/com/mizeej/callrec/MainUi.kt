package com.mizeej.callrec

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * واجهة الشاشة الرئيسية مبنية بالكود (بدون ملفات layout) لتبسيط المشروع
 * التجريبي. تمثّل بطاقة "طلب" مع زرّي الاتصال.
 */
class MainUi(
    private val activity: Activity,
    private val orderId: String,
    private val phoneProvider: () -> String,
    private val onPhoneChanged: (String) -> Unit,
    private val onCallSim: () -> Unit,
    private val onCallWhatsApp: () -> Unit,
    private val onOpenRecordings: () -> Unit,
    private val onCheckSetup: () -> Unit
) {
    fun build(): View {
        val pad = dp(16)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        root.addView(TextView(activity).apply {
            text = "نظام الكول سنتر — تجربة"
            textSize = 20f
            setTextColor(Color.parseColor("#222222"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        // بطاقة الطلب
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(pad, pad, pad, pad)
        }
        card.addView(TextView(activity).apply {
            text = "الطلب رقم $orderId"
            textSize = 18f
            setTextColor(Color.parseColor("#1565C0"))
        })
        card.addView(TextView(activity).apply {
            text = "رقم هاتف الزبون:"
            setPadding(0, dp(12), 0, dp(4))
            setTextColor(Color.parseColor("#555555"))
        })
        val phoneInput = EditText(activity).apply {
            setText(phoneProvider())
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(Color.parseColor("#222222"))
        }
        card.addView(phoneInput)

        val callSim = Button(activity).apply {
            text = "📞 اتصال عبر الخط"
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                onPhoneChanged(phoneInput.text.toString().trim())
                onCallSim()
            }
        }
        val callWa = Button(activity).apply {
            text = "🟢 اتصال عبر واتساب"
            setBackgroundColor(Color.parseColor("#128C7E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                onPhoneChanged(phoneInput.text.toString().trim())
                onCallWhatsApp()
            }
        }
        card.addView(callSim, lp(top = dp(16)))
        card.addView(callWa, lp(top = dp(8)))
        root.addView(card)

        // أزرار ثانوية
        root.addView(Button(activity).apply {
            text = "التسجيلات المحفوظة"
            setOnClickListener { onOpenRecordings() }
        }, lp(top = dp(16)))

        root.addView(Button(activity).apply {
            text = "فحص الإعداد (المايك / إمكانية الوصول)"
            setOnClickListener { onCheckSetup() }
        }, lp(top = dp(8)))

        root.addView(TextView(activity).apply {
            text = "ملاحظة: شغّل مكبّر الصوت أثناء المكالمة لالتقاط صوت الزبون بوضوح."
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dp(16), 0, 0)
        })

        return root
    }

    private fun dp(v: Int): Int =
        (v * activity.resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = top }
}
