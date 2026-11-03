package com.rykocollection.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoLinkParserTest {
    @Test
    fun extractsIdFromStandardYouTubeUrl() {
        assertEquals("abc123XYZ_0", VideoLinkParser.youtubeVideoId("https://www.youtube.com/watch?v=abc123XYZ_0"))
    }

    @Test
    fun extractsIdFromShortYouTubeUrl() {
        assertEquals("abc123XYZ_0", VideoLinkParser.youtubeVideoId("https://youtu.be/abc123XYZ_0"))
    }

    @Test
    fun extractsIdFromYouTubeShortsUrl() {
        assertEquals("abc123XYZ_0", VideoLinkParser.youtubeVideoId("https://youtube.com/shorts/abc123XYZ_0"))
    }

    @Test
    fun rejectsNonYouTubeAndMalformedLinks() {
        assertNull(VideoLinkParser.youtubeVideoId("https://www.instagram.com/reel/example"))
        assertNull(VideoLinkParser.youtubeVideoId("https://youtube.com/watch"))
    }

    @Test
    fun buildsThumbnailOnlyForValidYouTubeLinks() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123XYZ_0/hqdefault.jpg",
            VideoLinkParser.youtubeThumbnail("https://youtu.be/abc123XYZ_0")
        )
        assertNull(VideoLinkParser.youtubeThumbnail("https://www.dailymotion.com/video/x123"))
    }
}
