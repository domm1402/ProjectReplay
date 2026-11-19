package com.domm.projectreplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReplayApp() }
    }
}

@Composable
private fun ReplayApp() {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var theme by remember { mutableStateOf(ReplayTheme.VICE) }
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
                    Tab.CAPTURE -> Capture(t)
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
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(option.bg).clickable { theme = option; themePicker = false }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun Capture(t: ReplayTheme) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Heading("SAVE THE MOMENT", "Capture", "Your best play shouldn't disappear.", t)
        Spacer(Modifier.height(15.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(t.secondary.copy(alpha = .3f), t.panel, t.accent.copy(alpha = .14f)))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(t.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = t.accent, modifier = Modifier.size(60.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("CLIP THAT.", color = t.ink, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("Save the last 30 seconds of gameplay.", color = t.ink.copy(alpha = .65f), fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Surface(color = t.accent, shape = RoundedCornerShape(12.dp)) { Text("CAPTURE SETUP", Modifier.padding(horizontal = 25.dp, vertical = 13.dp), color = Color(0xFF17131D), fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(18.dp))
        DetailRow("Replay length", "15 / 30 / 60 / 120 seconds", t)
        DetailRow("Video quality", "Optimised for your device", t)
        DetailRow("Audio", "System audio and mic options", t)
        Spacer(Modifier.height(12.dp))
        Text("Android will ask for screen-recording permission before capture. The replay buffer is not active in this preview build.", color = t.ink.copy(alpha = .48f), fontSize = 11.sp, lineHeight = 16.sp)
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
    Column {
        Heading("YOUR PERSONAL HIGHLIGHT REEL", "Library", "Every clutch, close call and ridiculous moment.", t)
        Column(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.panel).padding(20.dp)) {
            Text("NO CLIPS YET", color = t.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Your next favourite clip starts here.", color = t.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Saved recordings will appear here. Organise them into game folders and favourites.", color = t.ink.copy(alpha = .58f), fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
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
