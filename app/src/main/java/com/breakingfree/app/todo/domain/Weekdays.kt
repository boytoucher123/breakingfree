package com.breakingfree.app.todo.domain

import java.time.DayOfWeek

/** Weekday sets stored as a bitmask: Monday = bit 0 … Sunday = bit 6. 0 means "any day". */
object Weekdays {
    const val ANY = 0

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun allows(mask: Int, day: DayOfWeek): Boolean = mask == ANY || mask and bit(day) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int {
        val next = mask xor bit(day)
        return if (next == ALL) ANY else next // all seven ticked is the same as "any day"
    }

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask != ANY && mask and bit(day) != 0

    fun label(mask: Int): String =
        if (mask == ANY) "ANY DAY" else DayOfWeek.entries.filter { contains(mask, it) }.joinToString(" ") { short(it) }

    fun short(day: DayOfWeek): String = day.name.take(3)

    private const val ALL = 0b1111111
}
