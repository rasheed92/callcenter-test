package com.mizeej.callrec

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة إمكانية الوصول.
 *
 * وجودها مفعّلاً هو ما يرفع أولوية التطبيق في نظام مشاركة المايك بدءاً من
 * Android 10، فيستطيع التقاط صوت المكالمة بدل أن يُسكّته النظام. التسجيل
 * الفعلي يجري في RecordingService باستخدام مصدر VOICE_RECOGNITION.
 *
 * لا نقرأ هنا أي محتوى من الشاشة؛ الخدمة مُعلنة للصوت فقط، وهذا ما يظهر
 * للموظف عند التفعيل. الهدف الوحيد: صلاحية التقاط الصوت أثناء المكالمة.
 *
 * ملاحظة توافق: هذا الأسلوب يعمل على معظم أجهزة أندرويد، لكن بعض الشركات
 * المصنّعة تغلق التقاط صوت المكالمة حتى مع إمكانية الوصول. لذلك نجرّب على
 * الجهاز الفعلي أولاً. على أجهزة سامسونج يُفضّل التسجيل المدمج لمكالمات الخط.
 */
class CallAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لا نحتاج معالجة أحداث الشاشة. الخدمة موجودة فقط لرفع أولوية الصوت.
    }

    override fun onInterrupt() {
        // لا شيء
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
