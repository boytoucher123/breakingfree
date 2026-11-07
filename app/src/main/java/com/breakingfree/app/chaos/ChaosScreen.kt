package com.breakingfree.app.chaos

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import com.breakingfree.app.ui.ShiftingTitle
import com.breakingfree.app.ui.TagChip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChaosState(
    val items: List<NewsItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val fetchedAt: Long = 0L,
    val region: Region? = null,
    val category: Category? = null,
) {
    val visible: List<NewsItem>
        get() = items.filter { (region == null || it.region == region) && (category == null || category in it.categories) }
}

class ChaosViewModel(app: Application) : AndroidViewModel(app) {
    private val cacheFile = File(app.cacheDir, "chaos_cache.json")
    private val _state = MutableStateFlow(ChaosState())
    val state: StateFlow<ChaosState> = _state

    init {
        viewModelScope.launch {
            NewsEngine.loadCache(cacheFile)?.let { (at, items) ->
                _state.update { it.copy(items = items, fetchedAt = at) }
            }
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val items = NewsEngine.fetchAll()
                val at = System.currentTimeMillis()
                _state.update { it.copy(items = items, fetchedAt = at, loading = false) }
                NewsEngine.saveCache(cacheFile, items, at)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Signal lost") }
            }
        }
    }

    fun setRegion(r: Region?) = _state.update { it.copy(region = r) }
    fun setCategory(c: Category?) = _state.update { it.copy(category = c) }
}

@Composable
fun ChaosScreen(vm: ChaosViewModel = viewModel()) {
    val p = LocalPalette.current
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    ShiftingTitle("Chaos Phase", "CHAOS PHASE", fontSize = 34.sp, startGothic = false, textAlign = TextAlign.Start)
                    MonoLabel(
                        if (s.fetchedAt > 0) "INTERCEPT // " + DateUtils.getRelativeTimeSpanString(s.fetchedAt)
                            .toString().uppercase()
                        else "INTERCEPT // AWAITING SIGNAL",
                        color = p.accent,
                    )
                }
                if (s.loading) {
                    CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), color = p.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = p.accent)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagChip("ALL", s.region == null, { vm.setRegion(null) })
                Region.entries.forEach { r -> TagChip(r.label, s.region == r, { vm.setRegion(r) }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TagChip("ANY", s.category == null, { vm.setCategory(null) })
                Category.entries.forEach { c -> TagChip(c.label, s.category == c, { vm.setCategory(c) }) }
            }
            Spacer(Modifier.height(10.dp))
            s.error?.let {
                MonoLabel("// ${it.uppercase()}", color = p.accent, modifier = Modifier.padding(horizontal = 20.dp))
            }

            val visible = s.visible
            if (visible.isEmpty() && !s.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (s.items.isEmpty()) "Silence.\nPull the signal again." else "Nothing matches this filter.",
                        style = TextStyle(fontFamily = Fonts.GothicAlt, fontSize = 22.sp),
                        color = p.textDim,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    val grouped = visible.groupBy { it.region }
                    Region.entries.forEach { region ->
                        val list = grouped[region] ?: return@forEach
                        item(key = "h-$region") { RegionHeader(region) }
                        items(list, key = { it.link + region }) { n ->
                            NewsCard(n) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link)))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RegionHeader(region: Region) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            region.label,
            style = TextStyle(fontFamily = Fonts.Oppressive, fontSize = 18.sp, letterSpacing = 3.sp),
            color = if (region == Region.AUSTRALIA) p.accent else p.text,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(p.line)
        )
    }
}

@Composable
private fun NewsCard(n: NewsItem, onClick: () -> Unit) {
    val p = LocalPalette.current
    val heat = (n.intensity.coerceAtMost(6) / 6f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .border(1.dp, p.line)
            .background(p.surface)
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            Modifier
                .width((3 + heat * 5).dp)
                .fillMaxHeight()
                .background(p.accent.copy(alpha = 0.45f + heat * 0.55f))
        )
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                n.categories.forEach { c -> MonoLabel("[${c.label}]", color = p.accent, fontSize = 10.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                n.title,
                style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 18.sp, lineHeight = 21.sp),
                color = p.text,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (n.summary.isNotBlank() && n.summary != n.title) {
                Spacer(Modifier.height(4.dp))
                Text(
                    n.summary,
                    style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 15.sp, lineHeight = 17.sp),
                    color = p.textDim,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            MonoLabel(
                buildString {
                    append(n.source.uppercase())
                    n.published?.let { append("  //  "); append(DateUtils.getRelativeTimeSpanString(it).toString().uppercase()) }
                },
                fontSize = 10.sp,
            )
        }
    }
}
