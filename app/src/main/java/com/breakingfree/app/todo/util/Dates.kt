package com.breakingfree.app.todo.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val dateYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** "TODAY 17:00", "TOMORROW", "FRI 14 NOV", "3D OVERDUE"… Upper-case to suit the mono labels. */
fun formatWhen(millis: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(today, dt.toLocalDate())
    val time = if (dt.hour == 23 && dt.minute == 59) "" else " " + dt.format(timeFmt)
    val day = when {
        days == 0L -> "TODAY"
        days == 1L -> "TOMORROW"
        days == -1L -> "YESTERDAY"
        dt.year == today.year -> dt.format(dateFmt).uppercase()
        else -> dt.format(dateYearFmt).uppercase()
    }
    return day + time
}

fun formatDue(millis: Long, now: Long = System.currentTimeMillis()): String =
    if (millis < now) "OVERDUE // " + formatWhen(millis, now) else "DUE " + formatWhen(millis, now)
