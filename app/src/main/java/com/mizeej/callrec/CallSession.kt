package com.mizeej.callrec

/**
 * جلسة مكالمة واحدة. في النظام الحقيقي هذه تُنشأ على السيرفر (NestJS)
 * وتحمل order_id والموظف، وتُربط بالطلب بعد رفع التسجيل.
 * هنا نسخة محلية مبسطة للتجربة.
 */
data class CallSession(
    val orderId: String,
    val phone: String,
    val channel: Channel,
    val startedAt: Long,
    var endedAt: Long = 0L,
    var filePath: String = "",
    var answered: Boolean = false,
    var durationSec: Int = 0,
    var staffConfirmedDisclosure: Boolean = false
) {
    enum class Channel { SIM, WHATSAPP }

    val durationMs: Long get() = if (endedAt > 0) endedAt - startedAt else 0L
}

/**
 * مخزن بسيط في الذاكرة للجلسة النشطة، يتشاركه الـ Activity وخدمتا
 * التسجيل وإمكانية الوصول. في النظام الحقيقي يُستبدل باستدعاء API.
 */
object SessionStore {
    @Volatile
    var active: CallSession? = null

    val finished = mutableListOf<CallSession>()

    fun start(session: CallSession) {
        active = session
    }

    fun finishActive(filePath: String, answered: Boolean, durationSec: Int) {
        active?.let {
            it.endedAt = System.currentTimeMillis()
            it.filePath = filePath
            it.answered = answered
            it.durationSec = durationSec
            finished.add(0, it)
        }
        active = null
    }
}
