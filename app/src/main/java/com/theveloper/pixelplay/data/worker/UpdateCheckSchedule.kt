package com.theveloper.pixelplay.data.worker

import android.content.Context
import java.util.Calendar

/** User-controlled hours for automatic update checks. Manual checks always bypass this gate. */
object UpdateCheckSchedule {
    private const val PREFS = "vybe_update_schedule"
    private const val KEY_ANYTIME = "anytime"
    private const val KEY_START = "start_hour"
    private const val KEY_END = "end_hour"

    fun isAnytime(context: Context): Boolean = prefs(context).getBoolean(KEY_ANYTIME, false)
    fun startHour(context: Context): Int = prefs(context).getInt(KEY_START, 20).coerceIn(0, 23)
    fun endHour(context: Context): Int = prefs(context).getInt(KEY_END, 6).coerceIn(0, 23)

    fun save(context: Context, anytime: Boolean, startHour: Int = 20, endHour: Int = 6) {
        prefs(context).edit()
            .putBoolean(KEY_ANYTIME, anytime)
            .putInt(KEY_START, startHour.coerceIn(0, 23))
            .putInt(KEY_END, endHour.coerceIn(0, 23))
            .apply()
    }

    fun allowsAutomaticCheck(context: Context, nowHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Boolean {
        if (isAnytime(context)) return true
        val start = startHour(context)
        val end = endHour(context)
        return if (start == end) true else if (start < end) nowHour in start until end else nowHour >= start || nowHour < end
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
