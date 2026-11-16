package com.mizeej.callrec

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * تخزين دائم لجلسات المكالمات باستخدام SharedPreferences (JSON).
 * يبقى بعد إغلاق التطبيق أو إعادة تشغيل العملية، بعكس القائمة في الذاكرة.
 * في النظام الحقيقي يُستبدل باستدعاء API على سيرفر Mizeej.
 */
object Store {
    private const val PREF = "callrec_store"
    private const val KEY = "sessions"

    fun append(ctx: Context, s: CallSession) {
        val arr = readArray(ctx)
        val o = JSONObject().apply {
            put("orderId", s.orderId)
            put("phone", s.phone)
            put("channel", s.channel.name)
            put("startedAt", s.startedAt)
            put("endedAt", s.endedAt)
            put("filePath", s.filePath)
            put("answered", s.answered)
            put("durationSec", s.durationSec)
        }
        arr.put(o)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** الأحدث أولاً */
    fun load(ctx: Context): List<CallSession> {
        val arr = readArray(ctx)
        val list = mutableListOf<CallSession>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val s = CallSession(
                orderId = o.optString("orderId", "?"),
                phone = o.optString("phone", ""),
                channel = runCatching {
                    CallSession.Channel.valueOf(o.optString("channel", "SIM"))
                }.getOrDefault(CallSession.Channel.SIM),
                startedAt = o.optLong("startedAt", 0L)
            ).apply {
                endedAt = o.optLong("endedAt", 0L)
                filePath = o.optString("filePath", "")
                answered = o.optBoolean("answered", false)
                durationSec = o.optInt("durationSec", 0)
            }
            list.add(0, s)
        }
        return list
    }

    private fun readArray(ctx: Context): JSONArray {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }
}
