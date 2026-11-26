package com.alpa.galeriku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alpa.galeriku.GalleryViewModel
import com.alpa.galeriku.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Data tampilan

enum class MediaFilter(val label: String) {
    ALL("Semua"),
    PHOTO("Foto"),
    VIDEO("Video"),
    SHOT("Screenshot"),
    BIG("Besar (>10 MB)"),
}

sealed class GridEntry {
    abstract val key: Any
    abstract val month: String
}

class HeaderEntry(val label: String, override val month: String, dayKey: Long) : GridEntry() {
    override val key: Any = "h$dayKey"
}

class MediaEntry(val item: MediaItem, val index: Int, override val month: String) : GridEntry() {
    override val key: Any = item.id
}

class DisplayData(val entries: List<GridEntry>, val media: List<MediaItem>) {
    companion object {
        val EMPTY = DisplayData(emptyList(), emptyList())
    }
}

private const val BIG_BYTES = 10L * 1024 * 1024

private fun isScreenshot(m: MediaItem): Boolean =
    m.folder.contains("screenshot", ignoreCase = true) ||
        m.relativePath.contains("screenshot", ignoreCase = true) ||
        m.name.startsWith("Screenshot", ignoreCase = true)

private fun dayLabel(d: LocalDate, today: LocalDate, same: DateTimeFormatter, other: DateTimeFormatter): String =
    when {
        d == today -> "Hari ini"
        d == today.minusDays(1) -> "Kemarin"
        d.year == today.year -> same.format(d)
        else -> other.format(d)
    }

/** Filter, cari, urutkan, dan kelompokkan per hari. Dijalankan di thread background. */
private fun buildDisplay(
    items: List<MediaItem>,
    query: String,
    filter: MediaFilter,
    sortBy: Int,
    asc: Boolean,
    sortable: Boolean,
): DisplayData {
    var list: List<MediaItem> = items

    if (filter != MediaFilter.ALL) {
        list = list.filter {
            when (filter) {
                MediaFilter.PHOTO -> !it.isVideo
                MediaFilter.VIDEO -> it.isVideo
                MediaFilter.SHOT -> isScreenshot(it)
                MediaFilter.BIG -> it.size >= BIG_BYTES
                MediaFilter.ALL -> true
            }
        }
    }

    val q = query.trim()
    if (q.isNotEmpty()) {
        val fLong = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val fShort = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val fIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = Date()
        list = list.filter { m ->
            if (m.name.contains(q, ignoreCase = true) || m.folder.contains(q, ignoreCase = true)) {
                true
            } else {
                date.time = m.takenMs
                fLong.format(date).contains(q, ignoreCase = true) ||
                    fShort.format(date).contains(q, ignoreCase = true) ||
                    fIso.format(date).contains(q, ignoreCase = true)
            }
        }
    }

    if (sortable && !(sortBy == 0 && !asc)) {
        val cmp: Comparator<MediaItem> = when (sortBy) {
            1 -> Comparator { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            2 -> Comparator { a, b -> a.size.compareTo(b.size) }
            else -> Comparator { a, b -> a.takenMs.compareTo(b.takenMs) }
        }
        list = list.sortedWith(if (asc) cmp else cmp.reversed())
    }

    val entries = ArrayList<GridEntry>(list.size + 128)
    if (sortable && sortBy == 0) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val locale = Locale.getDefault()
        val same = DateTimeFormatter.ofPattern("EEEE, d MMM", locale)
        val other = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        var lastDay = Long.MIN_VALUE
        var lastMonth = -1
        var month = ""
        list.forEachIndexed { i, m ->
            val d = Instant.ofEpochMilli(m.takenMs).atZone(zone).toLocalDate()
            val day = d.toEpochDay()
            if (day != lastDay) {
                lastDay = day
                val mk = d.year * 12 + d.monthValue
                if (mk != lastMonth) {
                    lastMonth = mk
                    month = monthFmt.format(d)
                }
                entries.add(HeaderEntry(dayLabel(d, today, same, other), month, day))
            }
            entries.add(MediaEntry(m, i, month))
        }
    } else {
        list.forEachIndexed { i, m ->
            val tag = if (sortable && sortBy == 1) m.name.take(1).uppercase() else ""
            entries.add(MediaEntry(m, i, tag))
        }
    }
    return DisplayData(entries, list)
}

private class DragSel {
    var base: Set<Long> = emptySet()
    var anchor: Int = -1
    var pointer: Offset = Offset.Zero
}

// ---------------------------------------------------------------- Layar

/**
 * Layar grid serbaguna: dipakai untuk tab Foto, Favorit, Sampah, dan isi album.
 * Tekan lama lalu geser untuk memilih banyak item sekaligus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    title: String,
    items: List<MediaItem>,
    vm: GalleryViewModel,
    actions: GalleryActions,
    trashMode: Boolean = false,
    customAlbum: String? = null,
    subtitle: String? = null,
    emptyText: String = "Belum ada foto",
    onBack: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.ALL) }
    var moreMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selecting = selected.isNotEmpty()
    val focus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current

    val sortBy = vm.settings.sortBy
    val asc = vm.settings.sortAsc

    val display by produceState(DisplayData.EMPTY, items, query, filter, sortBy, asc, trashMode) {
        value = withContext(Dispatchers.Default) {
            buildDisplay(items, query, filter, sortBy, asc, !trashMode)
        }
    }

    fun currentSelected(): List<MediaItem> = display.media.filter { it.id in selected }

    fun closeSearch() {
        searching = false
        query = ""
        filter = MediaFilter.ALL
    }

    LaunchedEffect(query, filter, sortBy, asc) { gridState.scrollToItem(0) }
    LaunchedEffect(searching) {
        if (searching) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // fokus belum siap: abaikan
            }
        }
    }

    BackHandler(enabled = searching) { closeSearch() }
    BackHandler(enabled = selecting) { selected = emptySet() }

    // ---- Pilih dengan menggeser ----
    val entriesNow by rememberUpdatedState(display.entries)
    val drag = remember { DragSel() }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val autoScrolling by remember { derivedStateOf { autoScroll != 0f } }

    fun indexAt(pos: Offset): Int {
        for (info in gridState.layoutInfo.visibleItemsInfo) {
            val o = info.offset
            val s = info.size
            if (pos.x >= o.x && pos.x < o.x + s.width && pos.y >= o.y && pos.y < o.y + s.height) {
                return info.index
            }
        }
        return -1
    }

    fun applyRange(cur: Int) {
        val es = entriesNow
        val lo = minOf(drag.anchor, cur)
        val hi = maxOf(drag.anchor, cur)
        val ids = HashSet<Long>(drag.base)
        for (i in lo..hi) {
            val e = es.getOrNull(i)
            if (e is MediaEntry) ids.add(e.item.id)
        }
        selected = ids
    }

    LaunchedEffect(autoScrolling) {
        while (autoScroll != 0f) {
            gridState.scrollBy(autoScroll)
            val i = indexAt(drag.pointer)
            if (i >= 0) applyRange(i)
            withFrameNanos { }
        }
    }

    if (showJump) {
        val months = remember(display) {
            val out = ArrayList<Pair<String, Int>>()
            var last = ""
            display.entries.forEachIndexed { i, e ->
                if (e is HeaderEntry && e.month != last) {
                    last = e.month
                    out.add(e.month to i)
                }
            }
            out
        }
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("Lompat ke bulan") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (months.isEmpty()) Text("Belum ada foto")
                    months.forEach { (label, index) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showJump = false
                                    scope.launch { gridState.scrollToItem(index) }
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showJump = false }) { Text("Tutup") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            TopAppBar(
                title = { Text("${selected.size} dipilih") },
                navigationIcon = {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Filled.Close, "Batal pilih")
                    }
                },
                actions = {
                    if (trashMode) {
                        IconButton(onClick = { selected = display.media.map { it.id }.toSet() }) {
                            Icon(Icons.Filled.SelectAll, "Pilih semua")
                        }
                        IconButton(onClick = {
                            vm.restore(currentSelected())
                            selected = emptySet()
                        }) { Icon(Icons.Filled.Restore, "Pulihkan") }
                        IconButton(onClick = {
                            actions.deleteForever(currentSelected())
                            selected = emptySet()
                        }) { Icon(Icons.Filled.DeleteForever, "Hapus permanen") }
                    } else {
                        val allFav = selected.all { it in vm.favorites }
                        IconButton(onClick = {
                            vm.setFavorite(currentSelected(), !allFav)
                            selected = emptySet()
                        }) {
                            Icon(
                                if (allFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favorit",
                            )
                        }
                        IconButton(onClick = {
                            actions.delete(currentSelected())
                            selected = emptySet()
                        }) { Icon(Icons.Filled.Delete, "Hapus") }
                        Box {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Filled.MoreVert, "Lainnya")
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Pilih semua") },
                                    onClick = {
                                        moreMenu = false
                                        selected = display.media.map { it.id }.toSet()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Tambah ke album") },
                                    onClick = {
                                        moreMenu = false
                                        actions.addToAlbum(currentSelected())
                                        selected = emptySet()
                                    },
                                )
                                if (customAlbum != null) {
                                    DropdownMenuItem(
                                        text = { Text("Keluarkan dari album") },
                                        onClick = {
                                            moreMenu = false
                                            vm.removeFromAlbum(customAlbum, currentSelected())
                                            selected = emptySet()
                                        },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Pindahkan ke folder") },
                                    onClick = {
                                        moreMenu = false
                                        actions.move(currentSelected())
                                        selected = emptySet()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Salin ke folder") },
                                    onClick = {
                                        moreMenu = false
                                        actions.copy(currentSelected())
                                        selected = emptySet()
                                    },
                                )
                                if (selected.size == 1) {
                                    DropdownMenuItem(
                                        text = { Text("Ganti nama") },
                                        onClick = {
                                            moreMenu = false
                                            val one = currentSelected().firstOrNull()
                                            if (one != null) actions.rename(one)
                                            selected = emptySet()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        } else if (searching) {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Cari nama, folder, atau tanggal") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { closeSearch() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tutup pencarian")
                    }
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Hapus teks") }
                    }
                },
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                    )
                }
            }
            Text(
                "${display.media.size} hasil",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                        }
                    }
                },
                actions = {
                    topActions()
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Filled.Search, "Cari")
                    }
                    if (!trashMode && sortBy == 0) {
                        IconButton(onClick = { showJump = true }) {
                            Icon(Icons.Filled.DateRange, "Lompat ke bulan")
                        }
                    }
                    if (!trashMode) {
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.Filled.SwapVert, "Urutkan")
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf("Tanggal" to 0, "Nama" to 1, "Ukuran" to 2).forEach { (label, v) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            sortMenu = false
                                            vm.updateSettings { it.copy(sortBy = v) }
                                        },
                                        trailingIcon = {
                                            if (sortBy == v) Icon(Icons.Filled.Check, null)
                                        },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (asc) "Urutan: menaik" else "Urutan: menurun") },
                                    onClick = {
                                        sortMenu = false
                                        vm.updateSettings { it.copy(sortAsc = !it.sortAsc) }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }

        if (subtitle != null && !searching) {
            Text(
                subtitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (searching && display.media.isEmpty() && (query.isNotBlank() || filter != MediaFilter.ALL)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tidak ada hasil", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = { vm.refresh(manual = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
              Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(vm.settings.columns),
                    contentPadding = PaddingValues(bottom = LocalBottomInset.current),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(gridState) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                // Tunggu: kalau jari diangkat/bergeser sebelum batas tekan lama, ini bukan tekan lama.
                                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    var stop = false
                                    while (!stop) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed ||
                                            (change.position - down.position).getDistance() >
                                            viewConfiguration.touchSlop
                                        ) {
                                            stop = true
                                        }
                                    }
                                    true
                                }
                                if (ended != null) return@awaitEachGesture

                                val startIdx = indexAt(down.position)
                                if (startIdx < 0) return@awaitEachGesture
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                drag.base = selected
                                drag.anchor = startIdx
                                drag.pointer = down.position
                                applyRange(startIdx)

                                val h = size.height.toFloat()
                                val zone = 96.dp.toPx()
                                var go = true
                                while (go) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        go = false
                                    } else {
                                        change.consume()
                                        drag.pointer = change.position
                                        val idx = indexAt(change.position)
                                        if (idx >= 0) applyRange(idx)
                                        val y = change.position.y
                                        autoScroll = when {
                                            y < zone -> -(zone - y) / zone * 36f
                                            y > h - zone -> (y - (h - zone)) / zone * 36f
                                            else -> 0f
                                        }
                                        if (!change.pressed) go = false
                                    }
                                }
                                autoScroll = 0f
                            }
                        },
                ) {
                    items(
                        display.entries,
                        key = { it.key },
                        span = { e ->
                            if (e is HeaderEntry) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        },
                        contentType = { if (it is HeaderEntry) 0 else 1 },
                    ) { entry ->
                        when (entry) {
                            is HeaderEntry -> DateHeader(entry.label)
                            is MediaEntry -> {
                                val item = entry.item
                                Thumb(
                                    item = item,
                                    favorite = item.id in vm.favorites,
                                    selected = item.id in selected,
                                    selecting = selecting,
                                    onClick = {
                                        if (selected.isNotEmpty()) {
                                            selected = if (item.id in selected) selected - item.id else selected + item.id
                                        } else {
                                            actions.open(display.media, entry.index, trashMode)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                FastScroller(
                    state = gridState,
                    entries = display.entries,
                    modifier = Modifier.fillMaxSize(),
                )
              }
            }
        }
    }
}

@Composable
private fun DateHeader(label: String) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Penggeser cepat: muncul saat scroll. Pegangannya besar dan bereaksi langsung saat disentuh
 * (tanpa menunggu geser). Label bulan tampil di sebelah kiri pegangan saat ditarik.
 */
@Composable
private fun FastScroller(state: LazyGridState, entries: List<GridEntry>, modifier: Modifier = Modifier) {
    if (entries.size < 40) return

    val scope = rememberCoroutineScope()
    val count = entries.size
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackH by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(2500)
            visible = false
        }
    }

    val thumbH = with(LocalDensity.current) { 72.dp.toPx() }
    val firstIdx = state.firstVisibleItemIndex
    val realFraction = (firstIdx.toFloat() / (count - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val fraction = if (dragging) dragFraction else realFraction
    val realFractionNow by rememberUpdatedState(realFraction)
    val countNow by rememberUpdatedState(count)
    val trackNow by rememberUpdatedState(trackH)

    // Kotak ini seluas grid supaya label tidak terpotong; hanya pegangan yang menangkap sentuhan.
    Box(modifier.onSizeChanged { trackH = it.height }) {
        if (visible || dragging) {
            val y = (fraction * (trackH - thumbH).coerceAtLeast(0f)).roundToInt()
            val idx = if (dragging) (dragFraction * (count - 1)).roundToInt() else firstIdx
            val label = entries.getOrNull(idx.coerceIn(0, count - 1))?.month.orEmpty()

            if (dragging && label.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                -76.dp.roundToPx(),
                                y + (thumbH / 2 - 20.dp.toPx()).roundToInt(),
                            )
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, y) }
                    .size(width = 64.dp, height = 72.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            dragFraction = realFractionNow
                            dragging = true
                            var go = true
                            while (go) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    go = false
                                } else {
                                    val dy = change.positionChange().y
                                    change.consume()
                                    val range = (trackNow - thumbH).coerceAtLeast(1f)
                                    dragFraction = (dragFraction + dy / range).coerceIn(0f, 1f)
                                    val target = (dragFraction * (countNow - 1)).roundToInt()
                                    scope.launch { state.scrollToItem(target) }
                                }
                            }
                            dragging = false
                        }
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                        .width(if (dragging) 14.dp else 10.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.7f else 0.45f)),
                )
            }
        }
    }
}
