package com.breakingfree.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakingfree.app.chaos.ChaosScreen
import com.breakingfree.app.countdown.CountdownScreen
import com.breakingfree.app.lattice.LatticeScreen
import com.breakingfree.app.roadmap.RoadmapScreen
import com.breakingfree.app.todo.TodoNav
import com.breakingfree.app.todo.TodoRoute
import com.breakingfree.app.todo.TodoTab
import com.breakingfree.app.todo.reminders.Reminders
import com.breakingfree.app.ui.BreakingFreeTheme
import com.breakingfree.app.ui.LatticeIcon
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel

enum class Tab(val label: String, val icon: ImageVector) {
    COUNTDOWN("COUNTDOWN", Icons.Filled.DateRange),
    CHAOS("CHAOS", Icons.Filled.Warning),
    TASKS("TASKS", Icons.Filled.CheckCircle),
    ROADMAP("ROADMAP", Icons.Filled.Place),
    LATTICE("LATTICE", LatticeIcon),
}

class MainActivity : ComponentActivity() {
    /** A task the widget or a reminder asked to open: an id, "" for "add a task", or null. */
    private val openTask = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            BreakingFreeApp(openTask = openTask.value, onOpenTaskHandled = { openTask.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(Reminders.EXTRA_OPEN_TASK)?.let { openTask.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Restart the widgets' ticking if anything (e.g. a force-stop) interrupted it.
        com.breakingfree.app.widget.CountdownWidgets.kick(this)
    }
}

@Composable
fun BreakingFreeApp(openTask: String? = null, onOpenTaskHandled: () -> Unit = {}) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    // Kept up here so the Tasks tab remembers where you were when you switch tabs.
    val todoNav = rememberSaveable(saver = TodoNav.saver) { TodoNav() }
    LaunchedEffect(openTask) {
        if (openTask != null) {
            tabIndex = Tab.TASKS.ordinal
            todoNav.resetTo(if (openTask.isEmpty()) TodoRoute.Edit(null) else TodoRoute.Detail(openTask))
            onOpenTaskHandled()
        }
    }
    BreakingFreeTheme(quiet = tab == Tab.LATTICE) {
        val p = LocalPalette.current
        Column(
            Modifier
                .fillMaxSize()
                .background(p.background)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Crossfade(targetState = tab, animationSpec = tween(350), label = "tab") { t ->
                    when (t) {
                        Tab.COUNTDOWN -> CountdownScreen()
                        Tab.CHAOS -> ChaosScreen()
                        Tab.TASKS -> TodoTab(todoNav)
                        Tab.ROADMAP -> RoadmapScreen()
                        Tab.LATTICE -> LatticeScreen()
                    }
                }
            }
            BottomBar(tab) { tabIndex = it.ordinal }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.accentDim)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(if (selected) 28.dp else 0.dp)
                            .height(2.dp)
                            .background(p.accent)
                    )
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        t.icon, contentDescription = t.label,
                        tint = if (selected) p.accent else p.textDim,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    MonoLabel(
                        t.label,
                        color = if (selected) p.text else p.textDim,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

