package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rykocollection.app.data.CatalogueRepository
import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.launch

private enum class MainTab(val label: String) {
    HOME("Home"), BROWSE("Browse"), LIBRARY("Library"), COMMUNITY("Community"), PROFILE("Profile")
}

@Composable
fun RykoCollectionApp() {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var selectedTitle by remember { mutableStateOf<CatalogueTitle?>(null) }
    var search by remember { mutableStateOf("") }
    var titles by remember { mutableStateOf<List<CatalogueTitle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val repository = remember { CatalogueRepository() }
    val scope = rememberCoroutineScope()

    fun loadCatalogue(term: String = "") {
        scope.launch {
            loading = true
            error = null
            repository.getTitles(term).onSuccess {
                titles = it
            }.onFailure {
                error = it.message ?: "Couldn't load the catalogue."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadCatalogue() }

    Scaffold(
        containerColor = Color(0xFF0C0D10),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF14161B), contentColor = Color(0xFFF4F1E8)) {
                MainTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        MainTab.HOME -> Icons.Rounded.Home
                        MainTab.BROWSE -> Icons.Rounded.Explore
                        MainTab.LIBRARY -> Icons.Rounded.BookmarkBorder
                        MainTab.COMMUNITY -> Icons.Rounded.Groups
                        MainTab.PROFILE -> Icons.Rounded.PersonOutline
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFE8C547),
                            selectedTextColor = Color(0xFFE8C547),
                            indicatorColor = Color(0xFF34301E),
                            unselectedIconColor = Color(0xFF92939A),
                            unselectedTextColor = Color(0xFF92939A)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .background(Color(0xFF0C0D10))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("RYKO", color = Color(0xFFE8C547), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("COLLECTION", color = Color(0xFFF4F1E8), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = Color(0xFF202127)) {
                    Icon(Icons.Rounded.Search, null, tint = Color(0xFFE8C547), modifier = Modifier.padding(11.dp))
                }
            }

            when (selectedTab) {
                MainTab.HOME, MainTab.BROWSE -> {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        placeholder = { Text("Search manga, manhwa, anime...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE8C547),
                            unfocusedBorderColor = Color(0xFF30323A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFE8C547)
                        )
                    )
                    LaunchedEffect(search) {
                        kotlinx.coroutines.delay(350)
                        if (selectedTab == MainTab.BROWSE || search.isNotBlank()) loadCatalogue(search)
                    }
                    if (selectedTab == MainTab.HOME) {
                        Text("Discover", modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Your next favourite story starts here.", modifier = Modifier.padding(start = 20.dp, bottom = 14.dp), color = Color(0xFF9B9CA4), fontSize = 13.sp)
                    } else {
                        Text("Browse catalogue", modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 12.dp), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFE8C547)) }
                        error != null -> Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error ?: "", color = Color(0xFFB8B8BE))
                            TextButton(onClick = { loadCatalogue(search) }) { Text("Try again", color = Color(0xFFE8C547)) }
                        }
                        titles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No titles found.", color = Color(0xFF9B9CA4)) }
                        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(titles, key = { it.id }) { title ->
                                TitleCard(title = title, onClick = { selectedTitle = title })
                            }
                        }
                    }
                }
                MainTab.LIBRARY -> EmptyNativeSection("Your library", "Sign in to sync your saved titles and reading progress.")
                MainTab.COMMUNITY -> EmptyNativeSection("Community", "Reader posts, reviews, comments and follows will live here.")
                MainTab.PROFILE -> EmptyNativeSection("Your profile", "Sign in to manage your profile, avatar and activity.")
            }
        }
    }

    selectedTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { selectedTitle = null },
            containerColor = Color(0xFF191B21),
            title = { Text(title.name, color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${title.mediaType}  •  ${title.status}", color = Color(0xFFE8C547), fontSize = 12.sp)
                    if (title.coverUrl.isNotBlank()) AsyncImage(title.coverUrl, null, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Text(title.synopsis.ifBlank { "No synopsis available yet." }, color = Color(0xFFD0D0D5), maxLines = 7, overflow = TextOverflow.Ellipsis)
                    if (title.genres.isNotEmpty()) Text(title.genres.joinToString(" • "), color = Color(0xFFB9A45A), fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selectedTitle = null }) { Text("Close", color = Color(0xFFE8C547)) } }
        )
    }
}

@Composable
private fun TitleCard(title: CatalogueTitle, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF17191F)).clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        AsyncImage(
            model = title.coverUrl.ifBlank { null },
            contentDescription = title.name,
            modifier = Modifier.width(94.dp).height(128.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF292B31)),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title.trending || title.featured) {
                Text(if (title.trending) "TRENDING" else "FEATURED", color = Color(0xFFE8C547), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Text(title.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${title.mediaType}  •  ${title.status}", color = Color(0xFF9FA0A7), fontSize = 11.sp)
            if (title.genres.isNotEmpty()) Text(title.genres.take(3).joinToString("  ·  "), color = Color(0xFFB9A45A), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            title.rating?.let { Text("★  %.1f".format(it), color = Color(0xFFE8C547), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun EmptyNativeSection(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, color = Color(0xFF9B9CA4), fontSize = 14.sp)
    }
}
