package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rykocollection.app.model.CatalogueTitle

private enum class MainTab(val label: String) {
    HOME("Home"),
    BROWSE("Browse"),
    VIDEOS("Videos"),
    LIBRARY("Library"),
    COMMUNITY("Community"),
    PROFILE("Profile")
}

@Composable
fun RykoCollectionApp() {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var selectedTitle by remember { mutableStateOf<CatalogueTitle?>(null) }
    var search by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color(0xFF0C0D10),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF14161B),
                contentColor = Color(0xFFF4F1E8)
            ) {
                MainTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        MainTab.HOME -> Icons.Rounded.Home
                        MainTab.BROWSE -> Icons.Rounded.Explore
                        MainTab.VIDEOS -> Icons.Rounded.PlayCircle
                        MainTab.LIBRARY -> Icons.Rounded.BookmarkBorder
                        MainTab.COMMUNITY -> Icons.Rounded.Groups
                        MainTab.PROFILE -> Icons.Rounded.PersonOutline
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 9.sp, maxLines = 1) },
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
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFF0C0D10))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("RYKO", color = Color(0xFFE8C547), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("COLLECTION", color = Color(0xFFF4F1E8), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }

            when (selectedTab) {
                MainTab.HOME -> {
                    Text(
                        "Discover",
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your next favourite story starts here.",
                        modifier = Modifier.padding(start = 20.dp, bottom = 14.dp),
                        color = Color(0xFF9B9CA4),
                        fontSize = 13.sp
                    )
                    CatalogueScreen(search = search, onSearchChange = { search = it }, onTitleSelected = { selectedTitle = it })
                }
                MainTab.BROWSE -> {
                    CatalogueScreen(search = search, onSearchChange = { search = it }, onTitleSelected = { selectedTitle = it })
                }
                MainTab.VIDEOS -> VideosScreen()
                MainTab.LIBRARY -> EmptyNativeSection("Your library", "Sign in to sync your saved titles and reading progress.")
                MainTab.COMMUNITY -> EmptyNativeSection("Community", "Reader posts, reviews, comments and follows will live here.")
                MainTab.PROFILE -> EmptyNativeSection("Your profile", "Sign in to manage your profile, avatar and activity.")
            }
        }
    }

    selectedTitle?.let { title ->
        val context = LocalContext.current
        val imageRequest = remember(title.coverUrl) {
            ImageRequest.Builder(context)
                .data(title.coverUrl.takeIf(String::isNotBlank))
                .crossfade(false)
                .build()
        }
        AlertDialog(
            onDismissRequest = { selectedTitle = null },
            containerColor = Color(0xFF191B21),
            title = { Text(title.name, color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${title.mediaType}  •  ${title.status}", color = Color(0xFFE8C547), fontSize = 12.sp)
                    if (title.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = title.name,
                            placeholder = ColorPainter(Color(0xFF292B31)),
                            error = ColorPainter(Color(0xFF292B31)),
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        title.synopsis.ifBlank { "No synopsis available yet." },
                        color = Color(0xFFD0D0D5),
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (title.genres.isNotEmpty()) {
                        Text(title.genres.joinToString(" • "), color = Color(0xFFB9A45A), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTitle = null }) {
                    Text("Close", color = Color(0xFFE8C547))
                }
            }
        )
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
