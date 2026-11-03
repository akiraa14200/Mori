package com.ryko.collection

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray

private val Ink = Color(0xFF090B10)
private val Panel = Color(0xFF151923)
private val Gold = Color(0xFFE6C36A)
private val Muted = Color(0xFF9BA3B4)
private val White = Color(0xFFF4F5F7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backend = Backend(this)
        RykoAppContext.current = this
        setContent { RykoApp(backend) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RykoApp(api: Backend) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Home") }
    var mediaMode by remember { mutableStateOf("Anime") }
    var titles by remember { mutableStateOf<List<RykoTitle>>(emptyList()) }
    var library by remember { mutableStateOf<List<RykoTitle>>(emptyList()) }
    var selected by remember { mutableStateOf<RykoTitle?>(null) }
    var search by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(api.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf("Sign in") }
    var showAuth by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            busy = true
            error = ""
            try {
                titles = api.loadTitles(search)
                if (api.signedIn) {
                    library = api.loadLibrary()
                    isAdmin = api.isAdmin()
                } else {
                    library = emptyList()
                    isAdmin = false
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not connect to Ryko Collection"
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(tab) {
        if (tab == "Profile" && api.signedIn) {
            try {
                val p = api.profile()
                profile = p.optString("display_name").ifBlank { p.optString("username") }
            } catch (_: Exception) {
                profile = api.email.orEmpty()
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, onPrimary = Ink, background = Ink, surface = Panel,
        onSurface = White, onBackground = White, secondary = Gold
    )) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RYKO COLLECTION", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Gold)
                            Text("DISCOVER • TRACK • CONNECT", fontSize = 9.sp, color = Muted, letterSpacing = 1.5.sp)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Ink)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Gold) {
                    listOf("Home", "Browse", "Video", "Library", "Account").forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item; if (item == "Library" || item == "Home" || item == "Video") refresh() },
                            icon = {
                                Icon(when (item) {
                                    "Home" -> Icons.Default.Home
                                    "Browse" -> Icons.Default.Explore
                                    "Video" -> Icons.Default.PlayCircle
                                    "Library" -> Icons.Default.Bookmarks
                                    "Account" -> Icons.Default.Person
                                    else -> Icons.Default.Home
                                }, contentDescription = item)
                            },
                            label = { Text(item, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Gold, selectedTextColor = Gold,
                                indicatorColor = Gold.copy(alpha = .13f),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    "Browse" -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.End) { listOf("Anime", "Manga").forEach { mode -> FilterChip(selected = mediaMode == mode, onClick = { mediaMode = mode }, label = { Text(mode) }, modifier = Modifier.padding(start = 6.dp)) } }
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search anime, manga, manhwa…", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Gold) },
                            trailingIcon = {
                                if (search.isNotEmpty()) IconButton(onClick = { search = ""; refresh() }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            },
                            singleLine = true, shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold, unfocusedBorderColor = Panel,
                                focusedTextColor = White, unfocusedTextColor = White
                            )
                        )
                        Button(onClick = { refresh() }, modifier = Modifier.padding(top = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                            Text("Search catalogue")
                        }
                        Catalogue(titles.filter { it.type.contains(mediaMode, ignoreCase = true) || (mediaMode == "Anime" && it.type.contains("TV", true)) }, busy, error, { selected = it })
                    }
                    "Library" -> {
                        if (!api.signedIn) EmptyState(
                            "Your library is waiting",
                            "Sign in to save titles and sync your collection."
                        ) { authMode = "Sign in"; showAuth = true }
                        else Catalogue(library, busy, error, { selected = it },
                            empty = "No saved titles yet. Open a title and tap Add to library.")
                    }
                    "Account" -> {
                        if (!api.signedIn) EmptyState(
                            "Welcome to Ryko",
                            "Sign in or create an account to sync your library, profile and community activity."
                        ) { authMode = "Sign in"; showAuth = true }
                        else Column(Modifier.fillMaxSize().padding(20.dp)) {
                            Text("YOUR PROFILE", color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Panel),
                                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp)) {
                                    Icon(Icons.Default.AccountCircle, null, tint = Gold, modifier = Modifier.size(54.dp))
                                    Text(profile.ifBlank { "Ryko member" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = White)
                                    Text(api.email.orEmpty(), color = Muted, fontSize = 13.sp)
                                    Text(if (isAdmin) "Administrator access" else "Member account",
                                        color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("Your saved library: ${library.size} titles", color = White)
                            Spacer(Modifier.height(18.dp))
                            OutlinedButton(onClick = {
                                api.signOut(); library = emptyList(); isAdmin = false; profile = ""; refresh()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
                        }
                    }
                    "Video" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text("VIDEO", color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("Trailers & previews", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Discover video previews for titles in the Ryko catalogue.", color = Muted, fontSize = 13.sp)
                        }
                        items(titles) { title ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (title.cover.isNotBlank()) AsyncImage(model = title.cover, contentDescription = title.name, modifier = Modifier.width(76.dp).height(96.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(title.name, color = White, fontWeight = FontWeight.Bold)
                                        Text("Official trailer search", color = Muted, fontSize = 11.sp)
                                        TextButton(onClick = { openVideo(title.name) }) { Text("Find video ▶", color = Gold) }
                                    }
                                }
                            }
                        }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().background(
                                    Brush.linearGradient(listOf(Color(0xFF292417), Panel, Color(0xFF171B25))),
                                    RoundedCornerShape(26.dp)
                                ).padding(22.dp)
                            ) {
                                Column {
                                    Text("YOUR NEXT FAVOURITE", color = Gold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                    Text("Stories worth\nkeeping close.", color = White, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("Explore anime, manga, manhwa and more.", color = Muted, fontSize = 13.sp)
                                    Spacer(Modifier.height(14.dp))
                                    Button(onClick = { tab = "Browse" },
                                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                                        Text("Explore catalogue")
                                    }
                                }
                            }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Popular in Ryko", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { tab = "Browse" }) { Text("See all", color = Gold) }
                            }
                        }
                        items(titles) { title -> TitleCard(title, { selected = title }, Modifier.fillMaxWidth()) }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Gold, trackColor = Panel)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF9C91), fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp))
            }
        }

        if (showAuth) AlertDialog(
            onDismissRequest = { showAuth = false },
            title = { Text(authMode, color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true)
                    TextButton(onClick = {
                        authMode = if (authMode == "Sign in") "Create account" else "Sign in"
                        error = ""
                    }) {
                        Text(if (authMode == "Sign in") "Create a new account" else "Already registered? Sign in", color = Gold)
                    }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF9C91), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && email.contains("@") && password.length >= 6, onClick = {
                    scope.launch {
                        busy = true; error = ""
                        try {
                            if (authMode == "Sign in") api.signIn(email, password) else api.signUp(email, password)
                            if (api.signedIn) {
                                showAuth = false
                                refresh()
                                tab = "Account"
                            } else {
                                error = "Check your email to confirm your account, then sign in."
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Authentication failed"
                        }
                        busy = false
                    }
                }) { Text(if (busy) "Please wait…" else authMode, color = Gold) }
            },
            dismissButton = { TextButton(onClick = { showAuth = false }) { Text("Cancel") } },
            containerColor = Panel
        )

        selected?.let { title ->
            var comments by remember(title.id) { mutableStateOf<JSONArray?>(null) }
            var comment by remember(title.id) { mutableStateOf("") }
            var saved by remember(title.id) { mutableStateOf(library.any { it.id == title.id }) }
            LaunchedEffect(title.id) { try { comments = api.comments(title.id) } catch (_: Exception) { } }
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(title.name, color = White, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                        item {
                            if (title.cover.isNotBlank()) AsyncImage(
                                model = title.cover, contentDescription = title.name,
                                modifier = Modifier.fillMaxWidth().height(190.dp)
                            )
                            Text("${title.type} • ${title.year ?: "—"} • ${title.status}", color = Gold, fontSize = 11.sp)
                            if (!title.rating.isNullOrBlank()) Text("Rating  ${title.rating}/10", color = Muted, fontSize = 12.sp)
                            Text(title.synopsis.ifBlank { "No synopsis available." }, color = White, fontSize = 13.sp)
                            Text(title.genres.joinToString(" • "), color = Gold, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        if (saved) {
                                            api.removeFromLibrary(title.id)
                                            library = library.filterNot { it.id == title.id }
                                        } else {
                                            api.saveToLibrary(title.id)
                                            library = library + title
                                        }
                                        saved = !saved
                                    } catch (e: Exception) { error = e.message ?: "Could not update library" }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (saved) "Remove from library" else "Add to library")
                            }
                            Text("COMMUNITY", color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        }
                        if (comments == null) item { Text("Loading comments…", color = Muted) }
                        else items((0 until (comments?.length() ?: 0)).map { comments!!.getJSONObject(it) }) { c ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(c.optString("body"), color = White, fontSize = 13.sp)
                                    Text(c.optString("created_at").take(10), color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                        if (api.signedIn) item {
                            OutlinedTextField(comment, { comment = it }, modifier = Modifier.fillMaxWidth(),
                                label = { Text("Write a comment") })
                            TextButton(enabled = comment.isNotBlank(), onClick = {
                                scope.launch {
                                    try {
                                        api.postComment(title.id, comment)
                                        comment = ""
                                        comments = api.comments(title.id)
                                    } catch (e: Exception) { error = e.message ?: "Could not post comment" }
                                }
                            }) { Text("Post comment", color = Gold) }
                        } else item { Text("Sign in to join the discussion.", color = Muted, fontSize = 12.sp) }
                    }
                },
                confirmButton = { TextButton(onClick = { selected = null }) { Text("Close", color = Gold) } },
                containerColor = Panel
            )
        }
    }
}

@Composable
private fun Catalogue(
    data: List<RykoTitle>, busy: Boolean, error: String,
    onSelect: (RykoTitle) -> Unit, empty: String = "No titles found."
) {
    if (data.isEmpty() && !busy) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (error.isBlank()) empty else "Catalogue unavailable. Try again.", color = Muted)
        }
    } else LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        gridItems(data) { title -> TitleCard(title, { onSelect(title) }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun TitleCard(title: RykoTitle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box {
            if (title.cover.isNotBlank()) AsyncImage(model = title.cover, contentDescription = title.name,
                modifier = Modifier.fillMaxWidth().height(218.dp).background(Panel, RoundedCornerShape(16.dp)))
            else Box(Modifier.fillMaxWidth().height(218.dp).background(Panel, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CollectionsBookmark, null, tint = Gold, modifier = Modifier.size(36.dp))
            }
            Surface(color = Color(0xDD171923), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.TopStart).padding(7.dp)) {
                Text(title.type.uppercase(), color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
            if (!title.rating.isNullOrBlank()) Surface(color = Color(0xDD171923), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)) {
                Text("★ ${title.rating}", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
        Text(title.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${title.year ?: "Year unknown"}  ·  ${title.status}", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CollectionsBookmark, null, tint = Gold, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
            Text("Sign in / Create account")
        }
    }
}

private fun openVideo(title: String) { val c = RykoAppContext.current ?: return; val u = "https://www.youtube.com/results?search_query=" + Uri.encode("$title official trailer"); c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
private object RykoAppContext { var current: android.content.Context? = null }
