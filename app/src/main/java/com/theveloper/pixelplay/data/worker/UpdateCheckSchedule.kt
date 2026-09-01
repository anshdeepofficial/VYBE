package com.theveloper.pixelplay.data.worker

import android.content.Context
import java.util.Calendar

/** User-controlled hours for automatic update checks. Manual checks always bypass this gate. */
object UpdateCheckSchedule {
    private const val PREFS = "vybe_update_schedule"
    private const val KEY_START = "start_hour"
    private const val KEY_END = "end_hour"
    private const val KEY_START_MINUTE = "start_minute"
    private const val KEY_END_MINUTE = "end_minute"
    private const val KEY_PROMPTED_VERSION = "prompted_version"

    @Deprecated("Automatic checks always use the selected quiet update window")
    fun isAnytime(context: Context): Boolean = false
    fun startHour(context: Context): Int = prefs(context).getInt(KEY_START, 20).coerceIn(0, 23)
    fun endHour(context: Context): Int = prefs(context).getInt(KEY_END, 6).coerceIn(0, 23)
    fun startMinute(context: Context): Int = prefs(context).getInt(KEY_START_MINUTE, 0).coerceIn(0, 59)
    fun endMinute(context: Context): Int = prefs(context).getInt(KEY_END_MINUTE, 0).coerceIn(0, 59)

    fun save(context: Context, anytime: Boolean, startHour: Int = 20, endHour: Int = 6) {
        save(context, startHour, 0, endHour, 0)
    }

    fun save(context: Context, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        prefs(context).edit()
            .putInt(KEY_START, startHour.coerceIn(0, 23))
            .putInt(KEY_END, endHour.coerceIn(0, 23))
            .putInt(KEY_START_MINUTE, startMinute.coerceIn(0, 59))
            .putInt(KEY_END_MINUTE, endMinute.coerceIn(0, 59))
            .apply()
    }

    fun allowsAutomaticCheck(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = startHour(context) * 60 + startMinute(context)
        val end = endHour(context) * 60 + endMinute(context)
        return if (start == end) true else if (start < end) current in start until end else current >= start || current < end
    }

    /** Compatibility helper for older callers and tests that only supply an hour. */
    fun allowsAutomaticCheck(context: Context, nowHour: Int): Boolean =
        allowsAutomaticCheck(
            context,
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, nowHour.coerceIn(0, 23))
                set(Calendar.MINUTE, 0)
            }
        )

    fun shouldPromptForVersion(context: Context, versionCode: Int): Boolean =
        prefs(context).getInt(KEY_PROMPTED_VERSION, 0) < versionCode

    fun markPrompted(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_PROMPTED_VERSION, versionCode).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
