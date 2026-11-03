package com.rykocollection.app.model

data class RelatedVideo(
    val id: String,
    val titleId: String,
    val videoUrl: String,
    val platform: String,
    val videoTitle: String,
    val creatorName: String,
    val creatorAvatarUrl: String,
    val actionType: String,
    val actionUrl: String,
    val sortOrder: Int
)
