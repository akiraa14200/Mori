package com.rykocollection.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
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
import com.rykocollection.app.data.CatalogueRepository
import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive

@Composable
fun CatalogueScreen(
    search: String,
    onSearchChange: (String) -> Unit,
    onTitleSelected: (CatalogueTitle) -> Unit
) {
    val repository = remember { CatalogueRepository() }
    val listState = rememberLazyListState()
    var titles by remember { mutableStateOf<List<CatalogueTitle>>(emptyList()) }
    var initialLoading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var offset by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(search, retryKey) {
        if (search.isNotBlank()) delay(320)
        listState.scrollToItem(0)
        initialLoading = true
        loadingMore = false
        hasMore = true
        offset = 0
        error = null
        titles = emptyList()
        val firstPage = repository.getTitles(query = search, limit = 30, offset = 0)
        firstPage.onSuccess { page ->
            titles = page
            offset = page.size
            hasMore = page.size == 30
        }.onFailure { failure ->
            error = failure.message ?: "Couldn't load the catalogue."
            hasMore = false
        }
        initialLoading = false

        if (firstPage.isSuccess) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            }.collect { lastVisible ->
                if (hasMore && !loadingMore && titles.isNotEmpty() && lastVisible >= titles.size - 5) {
                    loadingMore = true
                    repository.getTitles(query = search, limit = 30, offset = offset)
                        .onSuccess { page ->
                            val knownIds = titles.asSequence().map { it.id }.toHashSet()
                            val uniquePage = page.filterNot { it.id in knownIds }
                            titles = titles + uniquePage
                            offset += page.size
                            hasMore = page.size == 30
                        }
                        .onFailure { failure ->
                            error = failure.message ?: "Couldn't load more titles."
                        }
                    loadingMore = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            placeholder = { Text("Search manga, manhwa, anime...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFE8C547),
                unfocusedBorderColor = Color(0xFF30323A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Text(
            "Browse catalogue",
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 12.dp),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        when {
            initialLoading -> CatalogueSkeleton()
            error != null && titles.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error.orEmpty(), color = Color(0xFFB8B8BE))
                TextButton(onClick = { retryKey++ }) { Text("Try again", color = Color(0xFFE8C547)) }
            }
            titles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No titles found.", color = Color(0xFF9B9CA4))
            }
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(titles, key = { it.id }) { title ->
                    TitleCard(title = title, onClick = { onTitleSelected(title) })
                }
                if (loadingMore) item(key = "catalogue-loading-more") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE8C547), strokeWidth = 2.dp)
                    }
                }
                if (error != null && titles.isNotEmpty()) item(key = "catalogue-page-error") {
                    TextButton(onClick = { retryKey++ }) {
                        Text("Couldn't load more. Tap to retry.", color = Color(0xFFE8C547))
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(5) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF17191F)).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Box(Modifier.size(width = 94.dp, height = 128.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF292B31)))
                Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292B31)))
                    Box(Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292B31)))
                    Box(Modifier.fillMaxWidth(0.68f).height(10.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292B31)))
                }
            }
        }
    }
}

@Composable
private fun TitleCard(title: CatalogueTitle, onClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(title.coverUrl) {
        ImageRequest.Builder(context)
            .data(title.coverUrl.takeIf(String::isNotBlank))
            .crossfade(false)
            .build()
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF17191F)).clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = title.name,
            placeholder = ColorPainter(Color(0xFF292B31)),
            error = ColorPainter(Color(0xFF292B31)),
            modifier = Modifier.width(94.dp).height(128.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (title.trending || title.featured) {
                Text(
                    if (title.trending) "TRENDING" else "FEATURED",
                    color = Color(0xFFE8C547),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            Text(title.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${title.mediaType}  •  ${title.status}", color = Color(0xFF9FA0A7), fontSize = 11.sp)
            if (title.genres.isNotEmpty()) {
                Text(title.genres.take(3).joinToString("  ·  "), color = Color(0xFFB9A45A), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            title.rating?.let { Text("★  %.1f".format(it), color = Color(0xFFE8C547), fontSize = 11.sp) }
        }
    }
}
