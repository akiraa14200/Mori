package com.rykocollection.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.rykocollection.app.data.VideoRepository
import com.rykocollection.app.model.RelatedVideo
import com.rykocollection.app.video.VideoLinkParser
import kotlinx.coroutines.flow.collect

@Composable
fun VideosScreen() {
    val context = LocalContext.current
    val repository = remember { VideoRepository() }
    val listState = rememberLazyListState()
    var videos by remember { mutableStateOf<List<RelatedVideo>>(emptyList()) }
    var initialLoading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var offset by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        initialLoading = true
        loadingMore = false
        hasMore = true
        offset = 0
        error = null
        videos = emptyList()
        val firstPage = repository.getVideos(limit = 20, offset = 0)
        firstPage.onSuccess { page ->
            videos = page
            offset = page.size
            hasMore = page.size == 20
        }.onFailure { failure ->
            error = failure.message ?: "Couldn't load videos."
            hasMore = false
        }
        initialLoading = false

        if (firstPage.isSuccess) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible ->
                    if (hasMore && !loadingMore && videos.isNotEmpty() && lastVisible >= videos.size - 4) {
                        loadingMore = true
                        repository.getVideos(limit = 20, offset = offset)
                            .onSuccess { page ->
                                val knownIds = videos.asSequence().map { it.id }.toHashSet()
                                videos = videos + page.filterNot { it.id in knownIds }
                                offset += page.size
                                hasMore = page.size == 20
                            }
                            .onFailure { failure ->
                                error = failure.message ?: "Couldn't load more videos."
                            }
                        loadingMore = false
                    }
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Videos",
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Trailers, edits and community picks",
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 14.dp),
            color = Color(0xFF9B9CA4),
            fontSize = 12.sp
        )

        when {
            initialLoading -> VideoSkeletonList()
            error != null && videos.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error.orEmpty(), color = Color(0xFFB8B8BE))
                TextButton(onClick = { retryKey++ }) { Text("Try again", color = Color(0xFFE8C547)) }
            }
            videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PlayCircle, null, tint = Color(0xFFE8C547), modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No videos yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("New trailers and community videos will appear here.", color = Color(0xFF9B9CA4), fontSize = 12.sp)
                }
            }
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoCard(video = video, onWatch = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.videoUrl))
                        runCatching { context.startActivity(intent) }
                            .onFailure { Toast.makeText(context, "No app can open this video link.", Toast.LENGTH_SHORT).show() }
                    })
                }
                if (loadingMore) item(key = "videos-loading-more") {
                    Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE8C547), strokeWidth = 2.dp)
                    }
                }
                if (error != null && videos.isNotEmpty()) item(key = "videos-page-error") {
                    TextButton(onClick = { retryKey++ }) {
                        Text("Couldn't load more videos. Tap to retry.", color = Color(0xFFE8C547))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSkeletonList() {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(4) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF17191F)).padding(10.dp)
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF292B31)))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(0.78f).height(13.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292B31)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.42f).height(10.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292B31)))
            }
        }
    }
}

@Composable
private fun VideoCard(video: RelatedVideo, onWatch: () -> Unit) {
    val context = LocalContext.current
    val thumbnailUrl = remember(video.videoUrl, video.platform) {
        when (video.platform.lowercase()) {
            "youtube" -> VideoLinkParser.youtubeThumbnail(video.videoUrl)
            else -> null
        }
    }
    val imageRequest = remember(thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .crossfade(false)
            .build()
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF17191F))
            .clickable(onClick = onWatch).padding(10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF292B31)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = video.videoTitle,
                    placeholder = ColorPainter(Color(0xFF292B31)),
                    error = ColorPainter(Color(0xFF292B31)),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Rounded.PlayCircle, null, tint = Color(0xFFB9A45A), modifier = Modifier.size(54.dp))
            }
            Surface(color = Color(0xB8000000), shape = RoundedCornerShape(50)) {
                Icon(Icons.Rounded.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.padding(8.dp).size(25.dp))
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(9.dp),
                color = Color(0xD9000000),
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(video.platform.uppercase(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(11.dp))
        Text(
            video.videoTitle,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (video.creatorName.isNotBlank()) {
            Text(video.creatorName, modifier = Modifier.padding(top = 5.dp), color = Color(0xFF9B9CA4), fontSize = 11.sp)
        }
    }
}
