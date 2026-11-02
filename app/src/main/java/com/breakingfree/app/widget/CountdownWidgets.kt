package com.breakingfree.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews
import com.breakingfree.app.MainActivity
import com.breakingfree.app.R
import com.breakingfree.app.countdown.remaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDateTime

/**
 * Home-screen countdown widgets.
 *
 * Android only lets widgets tick on their own for clock/stopwatch formats, so the app ticks them:
 * a non-wakeup alarm fires every few seconds (the shortest Android allows), and each firing
 * redraws the widgets on every second boundary until the next one. Non-wakeup alarms never wake
 * the phone, so with the screen off this costs nothing; the moment the phone wakes, the pending
 * alarm fires and the widgets catch up.
 */
object CountdownWidgets {
    private const val ACTION_TICK = "com.breakingfree.app.widget.TICK"
    private const val LOOP_SECONDS = 6

    private fun ids(ctx: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    fun anyPlaced(ctx: Context): Boolean =
        ids(ctx, SecondsWidget::class.java).isNotEmpty() || ids(ctx, UnitsWidget::class.java).isNotEmpty()

    /** Redraw both widget kinds with the current time. */
    fun render(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val r = remaining(LocalDateTime.now())
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ids(ctx, SecondsWidget::class.java).takeIf { it.isNotEmpty() }?.let { idList ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_seconds)
            v.setTextViewText(R.id.widget_seconds, NumberFormat.getIntegerInstance().format(r.totalSeconds))
            v.setOnClickPendingIntent(R.id.widget_root, open)
            mgr.updateAppWidget(idList, v)
        }
        ids(ctx, UnitsWidget::class.java).takeIf { it.isNotEmpty() }?.let { idList ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_units)
            fun two(n: Long) = n.toString().padStart(2, '0')
            v.setTextViewText(R.id.widget_y, two(r.years))
            v.setTextViewText(R.id.widget_mo, two(r.months))
            v.setTextViewText(R.id.widget_d, two(r.days))
            v.setTextViewText(R.id.widget_h, two(r.hours))
            v.setTextViewText(R.id.widget_mi, two(r.minutes))
            v.setTextViewText(R.id.widget_s, two(r.seconds))
            v.setOnClickPendingIntent(R.id.widget_root, open)
            mgr.updateAppWidget(idList, v)
        }
    }

    private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, TickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Schedules the next tick just after a second boundary; returns its wall-clock time. */
    fun scheduleNext(ctx: Context): Long {
        val now = System.currentTimeMillis()
        val delta = LOOP_SECONDS * 1000L - now % 1000L + 40L
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + delta
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactOk) {
            am.setExact(AlarmManager.ELAPSED_REALTIME, at, tickIntent(ctx))
        } else {
            am.setWindow(AlarmManager.ELAPSED_REALTIME, at, 1000L, tickIntent(ctx))
        }
        return now + delta
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(tickIntent(ctx))
    }

    /** Draw now and make sure the ticking loop is running (if any widget is placed). */
    fun kick(ctx: Context) {
        if (!anyPlaced(ctx)) { cancel(ctx); return }
        render(ctx)
        scheduleNext(ctx)
    }

    internal fun isScreenOn(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true
}

/** One alarm firing: redraw every second until the next alarm takes over. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        if (!CountdownWidgets.anyPlaced(ctx)) { CountdownWidgets.cancel(ctx); return }
        val nextAt = CountdownWidgets.scheduleNext(ctx)
        if (!CountdownWidgets.isScreenOn(ctx)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                while (true) {
                    CountdownWidgets.render(ctx)
                    val now = System.currentTimeMillis()
                    val wait = 1000L - now % 1000L + 15L
                    if (now + wait >= nextAt - 250L || !CountdownWidgets.isScreenOn(ctx)) break
                    delay(wait)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

open class CountdownWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CountdownWidgets.kick(context.applicationContext)
    }

    override fun onEnabled(context: Context) = CountdownWidgets.kick(context.applicationContext)

    override fun onDisabled(context: Context) {
        if (!CountdownWidgets.anyPlaced(context)) CountdownWidgets.cancel(context)
    }
}

/** The raw number: every second left until 2030. */
class SecondsWidget : CountdownWidgetProvider()

/** The six boxes: years, months, days, hours, minutes, seconds. */
class UnitsWidget : CountdownWidgetProvider()
