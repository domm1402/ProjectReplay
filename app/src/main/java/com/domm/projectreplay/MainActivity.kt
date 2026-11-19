package com.domm.projectreplay

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.StatFs
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Tab(val title: String) { HOME("Home"), DISCOVER("Discover"), CAPTURE("Capture"), LIBRARY("Library"), PROFILE("Profile") }
private enum class ReplayTheme(val title: String, val bg: Color, val panel: Color, val accent: Color, val secondary: Color, val ink: Color) {
    VICE("Vice City", Color(0xFF100E19), Color(0xFF1D1928), Color(0xFFFFD34E), Color(0xFFB66CFF), Color(0xFFF8F4FF)),
    CHRISTMAS("Christmas", Color(0xFF101713), Color(0xFF1A2821), Color(0xFFEF5757), Color(0xFF55C98B), Color(0xFFF2FFF6)),
    MIDNIGHT("Midnight", Color(0xFF101216), Color(0xFF1D222A), Color(0xFF8EA8FF), Color(0xFF58D6C7), Color(0xFFF4F6FA)),
    GRAPHITE("Graphite", Color(0xFF111111), Color(0xFF202020), Color(0xFFE5E5E5), Color(0xFF999999), Color(0xFFF7F7F7))
}
private data class Clip(val title: String, val creator: String, val game: String, val views: String, val likes: String, val duration: String, val tint: Color)
private data class LocalClipMetadata(val thumbnail: Bitmap?, val durationMs: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReplayApp() }
    }
}

@Composable
private fun ReplayApp() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("project_replay", Context.MODE_PRIVATE) }
    var theme by remember { mutableStateOf(ReplayTheme.entries.firstOrNull { it.name == preferences.getString("theme", "VICE") } ?: ReplayTheme.VICE) }
    var replaySeconds by remember { mutableIntStateOf(preferences.getInt("replay_seconds", 30)) }
    var quality by remember { mutableStateOf(preferences.getString("quality", "Balanced") ?: "Balanced") }
    var recording by remember { mutableStateOf(preferences.getBoolean("recording", false)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recording = preferences.getBoolean("recording", false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            recording = preferences.getBoolean("recording", false)
            delay(750)
        }
    }
    var themePicker by remember { mutableStateOf(false) }
    val t = theme
    MaterialTheme(colorScheme = darkColorScheme(primary = t.accent, secondary = t.secondary, background = t.bg, surface = t.panel, onBackground = t.ink, onSurface = t.ink)) {
        Scaffold(
            containerColor = t.bg,
            bottomBar = {
                NavigationBar(containerColor = t.panel) {
                    Tab.entries.forEach { item ->
                        val icon = when (item) {
                            Tab.HOME -> Icons.Default.Home
                            Tab.DISCOVER -> Icons.Default.Explore
                            Tab.CAPTURE -> Icons.Default.PlayCircle
                            Tab.LIBRARY -> Icons.Default.Bookmarks
                            Tab.PROFILE -> Icons.Default.Person
                        }
                        NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(icon, item.title) }, label = { Text(item.title, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = t.accent, selectedTextColor = t.accent, indicatorColor = t.accent.copy(alpha = .14f), unselectedIconColor = t.ink.copy(alpha = .5f), unselectedTextColor = t.ink.copy(alpha = .5f)))
                    }
                }
            }
        ) { inset ->
            Column(Modifier.fillMaxSize().padding(inset)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("PROJECT", color = t.ink.copy(alpha = .5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("REPLAY", color = t.ink, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(7.dp).clip(CircleShape).background(t.accent))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(onClick = { themePicker = true }, color = t.panel, shape = RoundedCornerShape(13.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, null, tint = t.accent, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(t.title, color = t.ink, fontSize = 12.sp)
                        }
                    }
                }
                when (tab) {
                    Tab.HOME -> Home(t)
                    Tab.DISCOVER -> Discover(t)
                    Tab.CAPTURE -> Capture(
                        t = t,
                        replaySeconds = replaySeconds,
                        onReplaySecondsChange = { replaySeconds = it; preferences.edit().putInt("replay_seconds", it).apply() },
                        quality = quality,
                        onQualityChange = { quality = it; preferences.edit().putString("quality", it).apply() },
                        recording = recording,
                        onStartRecording = { context.startActivity(Intent(context, CapturePermissionActivity::class.java)) },
                        onStopRecording = { context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)); recording = false },
                        onClipReplay = { context.startService(Intent(context, CaptureService::class.java).setAction(CaptureService.ACTION_CLIP)) }
                    )
                    Tab.LIBRARY -> Library(t)
                    Tab.PROFILE -> Profile(t)
                }
            }
        }
        if (themePicker) AlertDialog(
            onDismissRequest = { themePicker = false }, containerColor = t.panel,
            title = { Text("Choose your look", color = t.ink, fontWeight = FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplayTheme.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(option.bg).clickable { theme = option; preferences.edit().putString("theme", option.name).apply(); themePicker = false }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(option.accent))
                        Spacer(Modifier.width(12.dp))
                        Text(option.title, color = option.ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (option == t) Text("ACTIVE", color = option.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { themePicker = false }) { Text("Done", color = t.accent) } }
        )
    }
}

@Composable
private fun Heading(kicker: String, title: String, subtitle: String, t: ReplayTheme) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(kicker, color = t.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(title, color = t.ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = t.ink.copy(alpha = .56f), fontSize = 13.sp)
    }
}

@Composable
private fun Home(t: ReplayTheme) {
    val clips = listOf(
        Clip("1 HP. Somehow survived.", "pixelpanic", "MINECRAFT", "18.2K", "2.4K", "0:27", Color(0xFF286D55)),
        Clip("That last-second clutch was unreal", "vexx", "FORTNITE", "9,841", "1,203", "0:31", Color(0xFF59418B)),
        Clip("The cleanest goal I've ever scored", "domplays", "ROCKET LEAGUE", "6,302", "891", "0:19", Color(0xFF315D86))
    )
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Column {
                Text("THE COMMUNITY", color = t.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("The moments worth keeping.", color = t.ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Fresh plays from across the gaming world.", color = t.ink.copy(alpha = .55f), fontSize = 13.sp)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("For You", "Following", "Trending", "Minecraft", "Fortnite").forEachIndexed { index, label ->
                    Surface(color = if (index == 0) t.accent else t.panel, shape = RoundedCornerShape(30.dp)) {
                        Text(label, Modifier.padding(horizontal = 15.dp, vertical = 9.dp), color = if (index == 0) Color(0xFF17131D) else t.ink.copy(alpha = .8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        items(clips) { ClipCard(it, t) }
        item { Text("You're all caught up for now.", Modifier.fillMaxWidth().padding(12.dp), color = t.ink.copy(alpha = .4f), fontSize = 12.sp) }
    }
}

@Composable
private fun ClipCard(clip: Clip, t: ReplayTheme) {
    Card(colors = CardDefaults.cardColors(containerColor = t.panel), shape = RoundedCornerShape(20.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(188.dp).background(Brush.linearGradient(listOf(clip.tint, clip.tint.copy(alpha = .5f), t.panel))), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(54.dp))
                    Text(clip.game, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), color = Color.Black.copy(alpha = .55f), shape = RoundedCornerShape(7.dp)) {
                    Text(clip.duration, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("SAMPLE CLIP", Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White.copy(alpha = .7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Column(Modifier.padding(14.dp)) {
                Text(clip.title, color = t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("@${clip.creator}  ·  ${clip.game}", color = t.ink.copy(alpha = .5f), fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♥  ${clip.likes}", color = t.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    Text("◉  ${clip.views} views", color = t.ink.copy(alpha = .55f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Share  ↗", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Discover(t: ReplayTheme) {
    Column {
        Heading("FIND YOUR NEXT FAVOURITE", "Discover", "Games, creators and clips worth your time.", t)
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            items(listOf("Minecraft", "Fortnite", "Roblox", "Rocket League", "Call of Duty", "Grand Theft Auto")) { game ->
                Surface(color = t.panel, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(t.secondary.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text(game.take(1), color = t.secondary, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                        Spacer(Modifier.width(13.dp))
                        Text(game, color = t.ink, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("Explore  ›", color = t.accent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Capture(
    t: ReplayTheme,
    replaySeconds: Int,
    onReplaySecondsChange: (Int) -> Unit,
    quality: String,
    onQualityChange: (String) -> Unit,
    recording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClipReplay: () -> Unit
) {
    val captureContext = androidx.compose.ui.platform.LocalContext.current
    val capturePrefs = remember { captureContext.getSharedPreferences(CaptureService.PREFS, Context.MODE_PRIVATE) }
    var captureError by remember { mutableStateOf(capturePrefs.getString("capture_error", null)) }
    var bufferSeconds by remember { mutableIntStateOf(capturePrefs.getInt("buffer_seconds", 0)) }
    LaunchedEffect(recording) {
        while (recording) {
            captureError = capturePrefs.getString("capture_error", null)
            bufferSeconds = capturePrefs.getInt("buffer_seconds", 0)
            delay(750)
        }
        captureError = capturePrefs.getString("capture_error", null)
        bufferSeconds = capturePrefs.getInt("buffer_seconds", 0)
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Heading("SAVE THE MOMENT", "Capture", "Your best play shouldn't disappear.", t)
        captureError?.takeIf { it.isNotBlank() }?.let { errorMessage ->
            Surface(color = Color(0xFF542D35), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFFB3B3))
                    Spacer(Modifier.width(8.dp))
                    Text(errorMessage, color = Color(0xFFFFDADA), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(t.secondary.copy(alpha = .3f), t.panel, t.accent.copy(alpha = .14f)))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(t.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = t.accent, modifier = Modifier.size(60.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("RECORD SESSION.", color = t.ink, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("Capture your screen from now until you stop.", color = t.ink.copy(alpha = .65f), fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            if (recording) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(onClick = onClipReplay, color = t.accent, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, null, tint = Color(0xFF17131D), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("CLIP THAT", color = Color(0xFF17131D), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                    Surface(onClick = onStopRecording, color = Color(0xFFB93F4B), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("STOP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
                Text("Buffer ready: ${bufferSeconds.coerceAtMost(replaySeconds)}s / ${replaySeconds}s. Clip That saves this recent window.", color = t.ink.copy(alpha = .65f), fontSize = 11.sp)
            } else {
                Surface(onClick = onStartRecording, color = t.accent, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("START REPLAY BUFFER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
                Text("Android will ask you to approve screen sharing. Once approved, Project Replay continuously buffers recent gameplay until you stop it.", color = t.ink.copy(alpha = .55f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("REPLAY CLIP LENGTH", color = t.ink.copy(alpha = .55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 120).forEach { seconds ->
                Surface(
                    onClick = { onReplaySecondsChange(seconds) },
                    color = if (replaySeconds == seconds) t.accent else t.panel,
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("${seconds}s", Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                        color = if (replaySeconds == seconds) Color(0xFF17131D) else t.ink,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("VIDEO QUALITY", color = t.ink.copy(alpha = .55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Data saver", "Balanced", "High").forEach { preset ->
                Surface(onClick = { onQualityChange(preset) },
                    color = if (quality == preset) t.secondary.copy(alpha = .24f) else t.panel,
                    shape = RoundedCornerShape(11.dp)) {
                    Text(preset, Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = if (quality == preset) t.secondary else t.ink.copy(alpha = .75f),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        DetailRow("Audio capture", "Permission-controlled", t)
        DetailRow("Storage", "Local device first", t)
        Spacer(Modifier.height(12.dp))
        Text("Clip That saves the most recent selected window from the rolling buffer. Capture stays on-device in temporary segments until you save a clip or stop. Audio is not captured in this version.", color = t.ink.copy(alpha = .48f), fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun DetailRow(label: String, value: String, t: ReplayTheme) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.ink.copy(alpha = .7f), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = t.ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = t.ink.copy(alpha = .08f))
}

@Composable
private fun Library(t: ReplayTheme) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("project_replay", Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val clips = remember { mutableStateListOf<File>() }
    var search by remember { mutableStateOf("") }
    var favouritesOnly by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var sortMode by remember { mutableStateOf("Newest") }
    var sortExpanded by remember { mutableStateOf(false) }
    var storageDialog by remember { mutableStateOf(false) }
    var storageRefresh by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        val folder = File(context.getExternalFilesDir(null), "Movies/ProjectReplay")
        clips.clear()
        clips.addAll(folder.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }?.sortedByDescending { it.lastModified() } ?: emptyList())
    }
    LaunchedEffect(refreshKey) { refresh() }
    LaunchedEffect(storageRefresh, refreshKey) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshKey++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val visibleClips = clips.filter { file ->
        val matchesSearch = file.nameWithoutExtension.contains(search.trim(), ignoreCase = true)
        val isFavourite = preferences.getBoolean("favourite:${file.name}", false)
        matchesSearch && (!favouritesOnly || isFavourite)
    }.let { filtered ->
        when (sortMode) {
            "Oldest" -> filtered.sortedBy { it.lastModified() }
            "Largest" -> filtered.sortedByDescending { it.length() }
            "Smallest" -> filtered.sortedBy { it.length() }
            else -> filtered.sortedByDescending { it.lastModified() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Heading("YOUR PERSONAL HIGHLIGHT REEL", "Library", "${clips.size} saved recording${if (clips.size == 1) "" else "s"} on this device.", t)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search recordings") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = t.ink, unfocusedTextColor = t.ink,
                    focusedBorderColor = t.accent, unfocusedBorderColor = t.ink.copy(alpha = .2f),
                    focusedLeadingIconColor = t.accent, unfocusedLeadingIconColor = t.ink.copy(alpha = .5f),
                    focusedPlaceholderColor = t.ink.copy(alpha = .4f), unfocusedPlaceholderColor = t.ink.copy(alpha = .4f)
                )
            )
            Spacer(Modifier.width(8.dp))
            Box {
                IconButton(onClick = { sortExpanded = true }) { Icon(Icons.Default.Sort, "Sort recordings", tint = t.ink.copy(alpha = .7f)) }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    listOf("Newest", "Oldest", "Largest", "Smallest").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { sortMode = option; sortExpanded = false })
                    }
                }
            }
            IconButton(onClick = { favouritesOnly = !favouritesOnly }) {
                Icon(Icons.Default.Star, "Toggle favourites only", tint = if (favouritesOnly) t.accent else t.ink.copy(alpha = .45f))
            }
            IconButton(onClick = { storageDialog = true; storageRefresh++ }) { Icon(Icons.Default.Storage, "Storage manager", tint = t.ink.copy(alpha = .7f)) }
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "Refresh library", tint = t.ink.copy(alpha = .7f)) }
        }
        if (visibleClips.isEmpty()) {
            Column(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.panel).padding(20.dp)) {
                Icon(if (clips.isEmpty()) Icons.Default.VideoLibrary else Icons.Default.SearchOff, null, tint = t.accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text(if (clips.isEmpty()) "NO RECORDINGS YET" else "NO MATCHES", color = t.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (clips.isEmpty()) "Your highlight reel starts here." else "Try another search or turn off the favourites filter.", color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (clips.isEmpty()) "Start a screen recording from Capture. Finished MP4s appear here and stay on this device." else "Your recordings are still safe on this device.", color = t.ink.copy(alpha = .58f), fontSize = 12.sp, lineHeight = 18.sp)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleClips, key = { it.absolutePath }) { file ->
                    val favourite = preferences.getBoolean("favourite:${file.name}", false)
                    val metadata by produceState(LocalClipMetadata(null, 0L), file.absolutePath, file.lastModified()) {
                        value = withContext(Dispatchers.IO) {
                            runCatching {
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(file.absolutePath)
                                    LocalClipMetadata(retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC),
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
                                } finally { retriever.release() }
                            }.getOrNull() ?: LocalClipMetadata(null, 0L)
                        }
                    }
                    Surface(color = t.panel, shape = RoundedCornerShape(16.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable {
                                runCatching {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                            }.padding(start = 14.dp, top = 14.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(62.dp, 48.dp).clip(RoundedCornerShape(10.dp)).background(t.secondary.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                    if (metadata.thumbnail != null) {
                                        Image(metadata.thumbnail.asImageBitmap(), "Clip thumbnail", modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Default.PlayArrow, null, tint = t.secondary, modifier = Modifier.size(28.dp))
                                    }
                                    Surface(color = Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)) {
                                        Text(formatDuration(metadata.durationMs), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(file.nameWithoutExtension, color = t.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${String.format(java.util.Locale.getDefault(), "%.1f", file.length() / (1024f * 1024f))} MB  ·  ${java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))}", color = t.ink.copy(alpha = .5f), fontSize = 10.sp)
                                }
                                IconButton(onClick = {
                                    preferences.edit().putBoolean("favourite:${file.name}", !favourite).apply()
                                    refreshKey++
                                }) { Icon(if (favourite) Icons.Default.Star else Icons.Default.StarBorder, "Favourite", tint = if (favourite) t.accent else t.ink.copy(alpha = .5f)) }
                                IconButton(onClick = { renameTarget = file; renameText = file.nameWithoutExtension }) { Icon(Icons.Default.Edit, "Rename", tint = t.ink.copy(alpha = .65f)) }
                                IconButton(onClick = {
                                    runCatching {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share recording"))
                                    }
                                }) { Icon(Icons.Default.Share, "Share", tint = t.accent) }
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 72.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { deleteTarget = file }) {
                                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(15.dp), tint = t.ink.copy(alpha = .55f))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", color = t.ink.copy(alpha = .6f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        val target = deleteTarget
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = t.panel,
            title = { Text("Delete this clip?", color = t.ink, fontWeight = FontWeight.Bold) },
            text = { Text("This permanently deletes the saved video from this device. This cannot be undone.", color = t.ink.copy(alpha = .7f)) },
            confirmButton = {
                TextButton(onClick = {
                    if (target != null && target.delete()) {
                        preferences.edit().remove("favourite:" + target.name).apply()
                        clips.remove(target)
                    }
                    deleteTarget = null
                }) { Text("Delete permanently", color = Color(0xFFFF7777)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = t.ink.copy(alpha = .7f)) } }
        )
    }

    if (storageDialog) {
        val savedBytes = clips.sumOf { it.length() }
        val bufferFolder = File(context.cacheDir, "ReplayBuffer")
        val temporaryBytes = folderSize(bufferFolder)
        val freeBytes = runCatching { StatFs(context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath).availableBytes }.getOrDefault(0L)
        val bufferActive = preferences.getBoolean("recording", false)
        AlertDialog(
            onDismissRequest = { storageDialog = false },
            containerColor = t.panel,
            title = { Text("Storage manager", color = t.ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageLine("Saved clips", formatBytes(savedBytes), t)
                    StorageLine("Temporary replay buffer", formatBytes(temporaryBytes), t)
                    StorageLine("Available device storage", formatBytes(freeBytes), t)
                    Text(
                        if (bufferActive) "The replay buffer is active. Temporary segments are protected and cannot be cleared right now."
                        else "Temporary segments are only used for replay capture. Saved clips are kept separately.",
                        color = t.ink.copy(alpha = .6f), fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !bufferActive && temporaryBytes > 0L,
                    onClick = {
                        bufferFolder.listFiles()?.forEach { it.deleteRecursively() }
                        storageDialog = false
                        storageRefresh++
                    }
                ) { Text("Clear temporary buffer", color = if (!bufferActive && temporaryBytes > 0L) t.accent else t.ink.copy(alpha = .3f)) }
            },
            dismissButton = { TextButton(onClick = { storageDialog = false }) { Text("Done", color = t.ink.copy(alpha = .7f)) } }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = t.panel,
            title = { Text("Rename recording", color = t.ink, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(64).filter { ch -> ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '_' } },
                    singleLine = true,
                    label = { Text("Recording name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = t.ink, unfocusedTextColor = t.ink, focusedBorderColor = t.accent, unfocusedBorderColor = t.ink.copy(alpha = .3f))
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val old = renameTarget
                    val clean = renameText.trim()
                    if (old != null && clean.isNotBlank()) {
                        val renamed = File(old.parentFile, "$clean.mp4")
                        if (!renamed.exists() && old.renameTo(renamed)) {
                            val wasFavourite = preferences.getBoolean("favourite:${old.name}", false)
                            preferences.edit().remove("favourite:${old.name}").putBoolean("favourite:${renamed.name}", wasFavourite).apply()
                            refreshKey++
                        }
                    }
                    renameTarget = null
                }) { Text("Save", color = t.accent) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = t.ink.copy(alpha = .7f)) } }
        )
    }
}
private fun folderSize(folder: File): Long {
    if (!folder.exists()) return 0L
    return folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[unit])
}

@Composable
private fun StorageLine(label: String, value: String, t: ReplayTheme) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.ink.copy(alpha = .65f), fontSize = 12.sp)
        Text(value, color = t.ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return String.format(java.util.Locale.getDefault(), "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun Profile(t: ReplayTheme) {
    Column {
        Heading("YOUR CORNER OF THE COMMUNITY", "Profile", "Make your clips recognisably yours.", t)
        Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(22.dp)).background(t.panel).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(t.secondary.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = t.secondary, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.height(12.dp))
            Text("Your player name", color = t.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Create an account to publish clips and follow players.", color = t.ink.copy(alpha = .55f), fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("0", "Clips", t); Stat("0", "Followers", t); Stat("0", "Following", t)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, t: ReplayTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = t.ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(label, color = t.ink.copy(alpha = .5f), fontSize = 11.sp)
    }
}
