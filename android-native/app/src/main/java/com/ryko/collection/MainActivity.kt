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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.compose.ui.window.Dialog
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
    var relatedVideos by remember { mutableStateOf<List<RykoRelatedVideo>>(emptyList()) }
    var library by remember { mutableStateOf<List<RykoTitle>>(emptyList()) }
    var selected by remember { mutableStateOf<RykoTitle?>(null) }
    var playingVideo by remember { mutableStateOf<RykoRelatedVideo?>(null) }
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
                relatedVideos = api.loadRelatedVideos()
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
                    "Video" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text("RYKO VIDEO", color = Color(0xFFB9A5FF), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Text("Watch & discover.", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                                Text("${relatedVideos.size} videos from your entries", color = Muted, fontSize = 12.sp)
                            }
                        }
                        items(relatedVideos, key = { it.id }) { video ->
                            val parent = titles.firstOrNull { it.id == video.titleId }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).background(Panel, RoundedCornerShape(18.dp))
                                .clickable { playingVideo = video }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(190.dp).background(Ink, RoundedCornerShape(13.dp))) {
                                    val thumb = videoThumbnail(video)
                                    if (thumb != null) AsyncImage(model = thumb, contentDescription = video.videoTitle, modifier = Modifier.fillMaxSize())
                                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(if (video.platform == "instagram") Icons.Default.CameraAlt else Icons.Default.PlayCircle, null, tint = Color(0xFFB9A5FF), modifier = Modifier.size(52.dp))
                                    }
                                    Surface(color = Color(0xD9000000), shape = RoundedCornerShape(30.dp), modifier = Modifier.align(Alignment.Center)) {
                                        Icon(Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.padding(10.dp).size(28.dp))
                                    }
                                    Surface(color = Color(0xDD171923), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.TopStart).padding(9.dp)) {
                                        Text(video.platform.uppercase(), color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                    }
                                }
                                Text(video.videoTitle.ifBlank { parent?.name ?: "Ryko video" }, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(parent?.name ?: "Ryko Collection", color = Color(0xFFB9A5FF), fontSize = 11.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(video.creatorName.ifBlank { video.platform }, color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { FeaturedCarousel(titles.filter { it.featured }.ifEmpty { titles }.take(5), { selected = it }) }
                        item { HomeSection("Top rated", "Highest-rated stories", titles.sortedByDescending { it.rating?.toFloatOrNull() ?: 0f }.take(15), { selected = it }, { tab = "Browse" }) }
                        item { HomeSection("Trending now", "Popular this week", titles.filter { it.trending }.ifEmpty { titles.sortedByDescending { it.popularity } }.take(15), { selected = it }, { tab = "Browse" }) }
                        item { HomeSection("Popular picks", "Stories readers are discovering", titles.sortedByDescending { it.popularity }.take(15), { selected = it }, { tab = "Browse" }) }
                        item { HomeSection("Manga & manhwa", "Explore illustrated stories", titles.filter { it.type.contains("manga", true) || it.type.contains("manhwa", true) || it.type.contains("manhua", true) }.take(15), { selected = it }, { mediaMode = "Manga"; tab = "Browse" }) }
                        item { HomeSection("Anime & movies", "Series and films to explore", titles.filter { it.type.contains("anime", true) || it.type.contains("tv", true) || it.type.contains("movie", true) }.take(15), { selected = it }, { mediaMode = "Anime"; tab = "Browse" }) }
                        item { HomeSection("Latest additions", "Recently added to Ryko", titles.take(15), { selected = it }, { tab = "Browse" }) }
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

        playingVideo?.let { video ->
            Dialog(onDismissRequest = { playingVideo = null }) {
                Column(Modifier.fillMaxWidth().background(Color(0xFF090B10), RoundedCornerShape(18.dp)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(video.videoTitle.ifBlank { "Ryko video" }, color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { playingVideo = null }) { Icon(Icons.Default.Close, null, tint = White) }
                    }
                    AndroidView(factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webChromeClient = WebChromeClient()
                            setBackgroundColor(android.graphics.Color.BLACK)
                            loadDataWithBaseURL("https://www.youtube-nocookie.com", playerHtml(video.videoUrl, video.platform), "text/html", "UTF-8", null)
                        }
                    }, modifier = Modifier.fillMaxWidth().height(240.dp))
                    Text(video.platform.replaceFirstChar { it.uppercase() } + " • Plays inside Ryko", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }

        selected?.let { title ->
            var detailTab by remember(title.id) { mutableStateOf("Overview") }
            var characters by remember(title.id) { mutableStateOf<List<RykoCharacter>>(emptyList()) }
            var comments by remember(title.id) { mutableStateOf<JSONArray?>(null) }
            var comment by remember(title.id) { mutableStateOf("") }
            var saved by remember(title.id) { mutableStateOf(library.any { it.id == title.id }) }
            LaunchedEffect(title.id) {
                try { characters = api.loadCharacters(title.id) } catch (_: Exception) { }
                try { comments = api.comments(title.id) } catch (_: Exception) { }
            }
            Dialog(onDismissRequest = { selected = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                Column(Modifier.fillMaxSize().background(Ink)) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 22.dp)) {
                        item {
                            Box(Modifier.fillMaxWidth().height(260.dp)) {
                                val hero = title.banner.ifBlank { title.cover }
                                if (hero.isNotBlank()) AsyncImage(model = hero, contentDescription = title.name, modifier = Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Ink))))
                                IconButton(onClick = { selected = null }, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) { Icon(Icons.Default.ArrowBack, null, tint = White) }
                                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                                    Text(title.type.uppercase() + (title.year?.let { " • $it" } ?: ""), color = Color(0xFFB9A5FF), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                                    Text(title.name, color = White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
                                    if (!title.rating.isNullOrBlank()) Text("★  ${title.rating} / 10     •     ${title.status}", color = Gold, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { playingVideo = relatedVideos.firstOrNull { it.titleId == title.id } }, enabled = relatedVideos.any { it.titleId == title.id } || title.trailerUrl.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B83F5), contentColor = Ink)) { Icon(Icons.Default.PlayArrow, null); Text(" Play trailer") }
                                        OutlinedButton(onClick = {
                                            scope.launch { try { if (saved) { api.removeFromLibrary(title.id); library = library.filterNot { it.id == title.id } } else { api.saveToLibrary(title.id); library = library + title }; saved = !saved } catch (e: Exception) { error = e.message ?: "Could not update library" } }
                                        }) { Icon(Icons.Default.Bookmark, null); Text(if (saved) " Saved" else " Add to list") }
                                    }
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Overview", "Characters", "Details", "Comments").forEach { name ->
                                    val icon = when (name) { "Overview" -> Icons.Default.Info; "Characters" -> Icons.Default.Groups; "Details" -> Icons.Default.Description; else -> Icons.Default.ChatBubbleOutline }
                                    Surface(onClick = { detailTab = name }, color = if (detailTab == name) Color(0xFF29213D) else Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                        Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(icon, null, tint = if (detailTab == name) Color(0xFFB9A5FF) else Muted, modifier = Modifier.size(19.dp))
                                            Text(name, color = if (detailTab == name) White else Muted, fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        when (detailTab) {
                            "Overview" -> {
                                item {
                                    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("THE STORY", color = Color(0xFFB9A5FF), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                                        Text("Overview", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                        Text(title.synopsis.ifBlank { "No description available." }, color = Muted, fontSize = 14.sp, lineHeight = 23.sp)
                                        Text("OFFICIAL PREVIEW", color = Color(0xFFB9A5FF), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                                        Text("Trailer", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        val trailer = title.trailerUrl.ifBlank { relatedVideos.firstOrNull { it.titleId == title.id && it.platform == "youtube" }?.videoUrl.orEmpty() }
                                        if (trailer.isNotBlank()) VideoPreview(trailer, "youtube", { url, platform -> playingVideo = RykoRelatedVideo("trailer-${title.id}", title.id, url, platform, "Official trailer", "", "", "watch", url, 0) })
                                        else Text("No trailer added for this entry.", color = Muted, fontSize = 12.sp)
                                        Text("RELATED VIDEOS", color = Color(0xFFB9A5FF), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                                        relatedVideos.filter { it.titleId == title.id }.forEach { v ->
                                            VideoPreview(v.videoUrl, v.platform, { url, platform -> playingVideo = v })
                                            Text(v.videoTitle.ifBlank { v.platform }, color = White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            "Characters" -> {
                                if (characters.isEmpty()) item { Text("No characters added yet.", color = Muted, modifier = Modifier.padding(20.dp)) }
                                else item {
                                    Column(Modifier.padding(horizontal = 14.dp)) {
                                        Text("${characters.size} characters", color = Muted, fontSize = 12.sp)
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 900.dp), userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            gridItems(characters) { ch ->
                                                var show by remember(ch.id) { mutableStateOf(false) }
                                                Column(Modifier.clickable { show = true }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    AsyncImage(model = ch.image, contentDescription = ch.name, modifier = Modifier.fillMaxWidth().height(132.dp).background(Panel, RoundedCornerShape(12.dp)))
                                                    Text(ch.name, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                                    Text(ch.role, color = Muted, fontSize = 10.sp)
                                                }
                                                if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text(ch.name, color = White) }, text = { Column { AsyncImage(model = ch.image, contentDescription = ch.name, modifier = Modifier.fillMaxWidth().height(210.dp)); Text(ch.role, color = Gold); Text(ch.description, color = Muted) } }, confirmButton = { TextButton(onClick = { show = false }) { Text("Close") } }, containerColor = Panel)
                                            }
                                        }
                                    }
                                }
                            }
                            "Details" -> item {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("INFORMATION", color = Color(0xFFB9A5FF), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                    DetailRow("Name", title.name)
                                    DetailRow("Alternative names", title.alternativeNames.joinToString(", "))
                                    DetailRow("Status", title.status)
                                    DetailRow("Category", title.type)
                                    DetailRow("Genres", title.genres.joinToString(", "))
                                    DetailRow("Themes", title.themes.joinToString(", "))
                                    DetailRow("Studio", title.studio)
                                    DetailRow("Artist", title.artist)
                                    DetailRow("Author", title.author)
                                    DetailRow("Release", listOfNotNull(title.season.takeIf { it.isNotBlank() }, title.year?.toString(), title.country.takeIf { it.isNotBlank() }).joinToString(" • "))
                                    DetailRow("Chapters", title.chapters)
                                    DetailRow("Episodes", title.episodes)
                                    DetailRow("Source", title.sourceUrl)
                                }
                            }
                            else -> {
                                if (comments == null) item { Text("Loading comments…", color = Muted, modifier = Modifier.padding(18.dp)) }
                                else items((0 until (comments?.length() ?: 0)).map { comments!!.getJSONObject(it) }) { c ->
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp).background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                        Text(c.optString("body"), color = White, fontSize = 13.sp)
                                        Text(c.optString("created_at").take(10), color = Muted, fontSize = 10.sp)
                                    }
                                }
                                if (api.signedIn) item {
                                    Column(Modifier.padding(16.dp)) {
                                        OutlinedTextField(comment, { comment = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Write a comment") })
                                        Button(enabled = comment.isNotBlank(), onClick = { scope.launch { try { api.postComment(title.id, comment); comment = ""; comments = api.comments(title.id) } catch (e: Exception) { error = e.message ?: "Could not post comment" } } }) { Text("Post comment") }
                                    }
                                } else item { Text("Sign in to join the discussion.", color = Muted, modifier = Modifier.padding(18.dp)) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().background(Panel).padding(10.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("Home", "Browse", "Video", "Library", "Account").forEach { name ->
                            TextButton(onClick = { selected = null; tab = name }) { Text(name, color = if (name == "Home") Color(0xFFB9A5FF) else Muted, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedCarousel(data: List<RykoTitle>, onSelect: (RykoTitle) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(data.size) {
        if (data.size > 1) while (true) { delay(3500); val next = (state.firstVisibleItemIndex + 1) % data.size; state.animateScrollToItem(next) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(state = state, contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(data, key = { it.id }) { title ->
                Box(Modifier.fillParentMaxWidth(0.92f).height(230.dp).clickable { onSelect(title) }.background(Panel, RoundedCornerShape(20.dp))) {
                    val image = title.banner.ifBlank { title.cover }
                    if (image.isNotBlank()) AsyncImage(model = image, contentDescription = title.name, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text("FEATURED • ${title.type.uppercase()}", color = Color(0xFFD0B8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(title.name, color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                        Text("${title.status}  •  ${title.year ?: "Year unknown"}", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            data.forEachIndexed { index, _ -> Box(Modifier.padding(horizontal = 3.dp).size(if (index == state.firstVisibleItemIndex) 18.dp else 6.dp, 6.dp).background(if (index == state.firstVisibleItemIndex) Color(0xFFB9A5FF) else Muted, RoundedCornerShape(20.dp))) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = White, fontSize = 14.sp)
        Divider(color = Panel)
    }
}

@Composable
private fun VideoPreview(url: String, platform: String, onPlay: (String, String) -> Unit) {
    val video = RykoRelatedVideo(url, "", url, platform, "", "", "", "watch", url, 0)
    Box(Modifier.fillMaxWidth().height(190.dp).clickable { onPlay(url, platform) }.background(Panel, RoundedCornerShape(14.dp))) {
        val thumb = videoThumbnail(video)
        if (thumb != null) AsyncImage(model = thumb, contentDescription = "Video preview", modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, null, tint = White, modifier = Modifier.size(48.dp)) }
        Surface(color = Color(0xD9000000), shape = RoundedCornerShape(30.dp), modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.padding(10.dp).size(30.dp))
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
    } else LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
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

private fun openVideoUrl(url: String) { val c = RykoAppContext.current ?: return; if (url.startsWith("https://") || url.startsWith("http://")) c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

@Composable
private fun HomeSection(title: String, subtitle: String, data: List<RykoTitle>, onSelect: (RykoTitle) -> Unit, onSeeAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp) }
            TextButton(onClick = onSeeAll) { Text("View all  ›", color = Color(0xFFB9A5FF)) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(data, key = { it.id }) { item ->
                Column(Modifier.width(132.dp).clickable { onSelect(item) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(184.dp).background(Panel, RoundedCornerShape(13.dp))) {
                        if (item.cover.isNotBlank()) AsyncImage(model = item.cover, contentDescription = item.name, modifier = Modifier.fillMaxSize())
                        Surface(color = Color(0xDD171923), shape = RoundedCornerShape(7.dp), modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                            Text(item.type.uppercase(), color = White, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                    }
                    Text(item.name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.status, color = Muted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}
private fun youtubeId(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    return when (uri.host?.lowercase()) {
        "youtu.be" -> uri.lastPathSegment
        "youtube.com", "www.youtube.com", "m.youtube.com", "www.youtube-nocookie.com" ->
            uri.getQueryParameter("v") ?: uri.pathSegments.firstOrNull { it == "embed" || it == "shorts" }?.let { marker ->
                val index = uri.pathSegments.indexOf(marker); uri.pathSegments.getOrNull(index + 1)
            }
        else -> null
    }?.takeIf { it.isNotBlank() }
}
private fun videoThumbnail(video: RykoRelatedVideo): String? =
    youtubeId(video.videoUrl)?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
private fun playerHtml(url: String, platform: String): String {
    val yt = youtubeId(url)
    val src = if (yt != null) "https://www.youtube-nocookie.com/embed/$yt?autoplay=1&playsinline=1"
        else if (platform == "instagram") {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase().orEmpty()
            if (host.endsWith("instagram.com") || host.endsWith("instagr.am")) "https://www.instagram.com${uri.path.orEmpty().trimEnd('/')}/embed" else url
        } else url
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"><style>html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}iframe{border:0;width:100%;height:100%}</style></head><body><iframe src="$src" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>"""
}
