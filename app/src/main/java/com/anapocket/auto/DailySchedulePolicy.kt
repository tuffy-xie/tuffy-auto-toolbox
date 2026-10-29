package com.anapocket.auto

import java.util.Calendar
import java.util.TimeZone

object DailySchedulePolicy {
    fun next(hour: Int, minute: Int, nowMs: Long): Long {
        val target = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= nowMs) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }
    fun due(hour: Int, minute: Int, nowMs: Long): Boolean {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply { timeInMillis = nowMs }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) >= hour * 60 + minute
    }
}
