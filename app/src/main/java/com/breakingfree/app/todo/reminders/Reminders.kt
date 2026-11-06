package com.breakingfree.app.todo.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.breakingfree.app.MainActivity
import com.breakingfree.app.R
import com.breakingfree.app.todo.TodoGraph
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.domain.ReminderPlanner
import com.breakingfree.app.todo.util.formatDue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Per-task reminder notifications, driven by AlarmManager.
 *
 * Each task with a reminder has at most one pending alarm: its next firing. When it fires the
 * notification is shown and the following one is scheduled. Random-window times are remembered
 * so that rescheduling (which happens after every edit) doesn't keep moving them.
 */
object Reminders {
    const val CHANNEL_ID = "task_reminders"
    const val EXTRA_OPEN_TASK = "openTask"
    private const val ACTION_FIRE = "com.breakingfree.app.todo.REMINDER_FIRE"
    private const val ACTION_DONE = "com.breakingfree.app.todo.REMINDER_DONE"
    private const val EXTRA_TASK_ID = "taskId"

    private fun prefs(context: Context) = context.getSharedPreferences("todo_reminders", Context.MODE_PRIVATE)

    /** Schedule (or cancel) every task's reminder to match the data. */
    fun rescheduleAll(context: Context, tasks: List<Task>) {
        val ids = tasks.map { it.id }.toSet()
        tasks.forEach { reschedule(context, it) }
        // Forget tasks that no longer exist.
        val p = prefs(context)
        val stale = p.all.keys.mapNotNull { k -> k.substringAfter(':', "").takeIf { it.isNotEmpty() && it !in ids } }.toSet()
        if (stale.isNotEmpty()) {
            val e = p.edit()
            stale.forEach { id ->
                cancel(context, id)
                e.remove("plan:$id").remove("fired:$id")
            }
            e.apply()
        }
    }

    fun reschedule(context: Context, task: Task) {
        if (task.isCompleted || task.isSystem || !task.reminder.isOn) {
            cancel(context, task.id)
            prefs(context).edit().remove("plan:${task.id}").apply()
            return
        }
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val signature = task.reminder.toString()
        val firedOn = p.getString("fired:${task.id}", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // Reuse a planned time if the settings haven't changed and it's still ahead.
        val planned = p.getString("plan:${task.id}", null)?.let { s ->
            val (sig, at) = s.substringBeforeLast('|') to s.substringAfterLast('|').toLongOrNull()
            at?.takeIf { sig == signature && it > now }
        }
        val at = planned ?: ReminderPlanner.next(task.reminder, now, firedOn) ?: run {
            cancel(context, task.id)
            return
        }
        p.edit().putString("plan:${task.id}", "$signature|$at").apply()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = fireIntent(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, taskId: String) {
        fireIntent(context, taskId, PendingIntent.FLAG_NO_CREATE)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    private fun fireIntent(context: Context, taskId: String, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .setData(Uri.parse("todo://reminder/$taskId")) // distinct per task
                .putExtra(EXTRA_TASK_ID, taskId),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

    internal suspend fun handle(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val graph = TodoGraph.get(context)
        when (intent.action) {
            ACTION_FIRE -> {
                val task = graph.repository.getTasks().firstOrNull { it.id == taskId } ?: return
                prefs(context).edit()
                    .putString("fired:$taskId", Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toString())
                    .remove("plan:$taskId")
                    .apply()
                if (!task.isCompleted) show(context, task)
                reschedule(context, task)
            }
            ACTION_DONE -> {
                NotificationManagerCompat.from(context).cancel(taskId.hashCode())
                graph.repository.completeTask(taskId)
            }
        }
    }

    private fun show(context: Context, task: Task) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(Uri.parse("todo://task/${task.id}"))
                .putExtra(EXTRA_OPEN_TASK, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_DONE)
                .setData(Uri.parse("todo://done/${task.id}"))
                .putExtra(EXTRA_TASK_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = listOfNotNull(
            if (task.isInProgress) "IN PROGRESS" else null,
            task.dueAt?.let { formatDue(it) },
            task.description.takeIf { it.isNotBlank() },
        ).joinToString("  //  ")
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC4001A.toInt())
            .setContentTitle(task.titleForList)
            .setContentText(text.ifEmpty { "Reminder" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifEmpty { "Reminder" }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "DONE", done)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), n)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}

/** Fires a reminder, or handles its DONE action. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        TodoGraph.get(context).scope.launch {
            try {
                Reminders.handle(context, intent)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms don't survive a reboot or an app update: put them back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val graph = TodoGraph.get(context)
        graph.scope.launch {
            try {
                Reminders.rescheduleAll(context, graph.repository.getTasks())
            } finally {
                pending.finish()
            }
        }
    }
}
