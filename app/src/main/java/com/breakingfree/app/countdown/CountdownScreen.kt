package com.breakingfree.app.countdown

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakingfree.app.ui.SpinningCoin
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import com.breakingfree.app.ui.Scanlines
import com.breakingfree.app.ui.ShiftingTitle
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

val TARGET: LocalDateTime = LocalDateTime.of(2030, 1, 1, 0, 0, 0)
private val DECADE_START: LocalDateTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0)

data class Remaining(
    val years: Long, val months: Long, val days: Long,
    val hours: Long, val minutes: Long, val seconds: Long,
    val totalSeconds: Long,
)

/** Calendar-accurate breakdown of the time between [now] and [target]. */
fun remaining(now: LocalDateTime, target: LocalDateTime = TARGET): Remaining {
    if (!now.isBefore(target)) return Remaining(0, 0, 0, 0, 0, 0, 0)
    var cursor = now.withNano(0)
    val years = ChronoUnit.YEARS.between(cursor, target); cursor = cursor.plusYears(years)
    val months = ChronoUnit.MONTHS.between(cursor, target); cursor = cursor.plusMonths(months)
    val days = ChronoUnit.DAYS.between(cursor, target); cursor = cursor.plusDays(days)
    val hours = ChronoUnit.HOURS.between(cursor, target); cursor = cursor.plusHours(hours)
    val minutes = ChronoUnit.MINUTES.between(cursor, target); cursor = cursor.plusMinutes(minutes)
    val seconds = ChronoUnit.SECONDS.between(cursor, target)
    return Remaining(years, months, days, hours, minutes, seconds, ChronoUnit.SECONDS.between(now, target))
}

@Composable
fun CountdownScreen() {
    val p = LocalPalette.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val r = remaining(now)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse",
    )
    val decadeTotal = ChronoUnit.SECONDS.between(DECADE_START, TARGET).toFloat()
    val decadeDone = (ChronoUnit.SECONDS.between(DECADE_START, now) / decadeTotal).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(p.accentDim.copy(alpha = 0.35f), p.background, p.background))
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpinningCoin(150.dp)
            Spacer(Modifier.height(18.dp))
            ShiftingTitle("BREAKING FREE", "BREAKING FREE", fontSize = 31.sp)
            Spacer(Modifier.height(4.dp))
            MonoLabel("T-MINUS // ANNO DOMINI MMXXX", color = p.accent)
            Spacer(Modifier.height(28.dp))

            if (r.totalSeconds <= 0) {
                Text(
                    "The Hour Has Come",
                    style = TextStyle(fontFamily = Fonts.Gothic, fontSize = 44.sp, textAlign = TextAlign.Center),
                    color = p.text,
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UnitCell(r.years, "YEARS", Modifier.weight(1f))
                    UnitCell(r.months, "MONTHS", Modifier.weight(1f))
                    UnitCell(r.days, "DAYS", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UnitCell(r.hours, "HOURS", Modifier.weight(1f))
                    UnitCell(r.minutes, "MINUTES", Modifier.weight(1f))
                    UnitCell(r.seconds, "SECONDS", Modifier.weight(1f), live = true)
                }
            }

            Spacer(Modifier.height(30.dp))
            MonoLabel("SECONDS REMAINING")
            Text(
                NumberFormat.getIntegerInstance().format(r.totalSeconds),
                style = TextStyle(fontFamily = Fonts.Technical, fontSize = 30.sp, letterSpacing = 3.sp),
                color = p.accent,
            )
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("MMXX")
                MonoLabel("DECADE CONSUMED ${"%.2f".format(decadeDone * 100)}%")
                MonoLabel("MMXXX")
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .border(1.dp, p.line)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(decadeDone)
                        .height(10.dp)
                        .background(Brush.horizontalGradient(listOf(p.accentDim, p.accent)))
                )
            }
            Spacer(Modifier.height(36.dp))
            Text(
                "Every second is a stone in the wall.\nEvery second is a blow against it.",
                style = TextStyle(fontFamily = Fonts.GothicAlt, fontSize = 20.sp, textAlign = TextAlign.Center),
                color = p.textDim,
            )
            Spacer(Modifier.height(16.dp))
            MonoLabel("● SIGNAL LIVE", color = p.accent, modifier = Modifier.alpha(pulse))
        }
        Scanlines(Modifier.fillMaxSize())
    }
}

@Composable
private fun UnitCell(value: Long, label: String, modifier: Modifier = Modifier, live: Boolean = false) {
    val p = LocalPalette.current
    Column(
        modifier
            .border(1.dp, if (live) p.accent else p.line)
            .background(p.surface.copy(alpha = 0.85f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value.toString().padStart(2, '0'),
            style = TextStyle(fontFamily = Fonts.Oppressive, fontSize = 46.sp, letterSpacing = 1.sp),
            color = if (live) p.accent else p.text,
        )
        MonoLabel(label, fontSize = 11.sp)
    }
}
