package com.breakingfree.app.todo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

enum class ReminderMode(val code: Int) {
    OFF(0),
    /** Every allowed day at [Reminder.startMinute]. */
    AT_TIME(1),
    /** Once on every allowed day, at a random moment between [Reminder.startMinute] and [Reminder.endMinute]. */
    RANDOM_WINDOW(2);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: OFF
    }
}

/** A task's notification settings. Minutes are minutes after local midnight. */
data class Reminder(
    val mode: ReminderMode = ReminderMode.OFF,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
    /** [Weekdays] mask, 0 = every day. */
    val days: Int = Weekdays.ANY,
) {
    val isOn get() = mode != ReminderMode.OFF
}

object ReminderPlanner {

    /**
     * When the reminder should next fire, or null if it never will (off, or an empty window).
     *
     * @param firedOn the last local date it fired; at most one reminder per day, so that date is skipped.
     */
    fun next(
        reminder: Reminder,
        now: Long,
        firedOn: LocalDate? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        random: Random = Random.Default,
    ): Long? {
        if (!reminder.isOn) return null
        if (reminder.mode == ReminderMode.RANDOM_WINDOW && reminder.endMinute <= reminder.startMinute) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            if (firedOn != null && !date.isAfter(firedOn)) continue
            if (!Weekdays.allows(reminder.days, date.dayOfWeek)) continue
            val start = at(date, reminder.startMinute, zone)
            when (reminder.mode) {
                ReminderMode.AT_TIME -> if (start > now) return start
                ReminderMode.RANDOM_WINDOW -> {
                    val end = at(date, reminder.endMinute, zone)
                    val from = maxOf(start, now + 60_000) // never "right now": at least a minute out
                    if (from < end) return from + (random.nextDouble() * (end - from)).toLong()
                }
                ReminderMode.OFF -> return null
            }
        }
        return null
    }

    private fun at(date: LocalDate, minute: Int, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
