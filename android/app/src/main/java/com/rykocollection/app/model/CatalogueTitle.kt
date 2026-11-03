package com.rykocollection.app.model

data class CatalogueTitle(
    val id: String,
    val name: String,
    val mediaType: String,
    val synopsis: String,
    val coverUrl: String,
    val bannerUrl: String,
    val status: String,
    val year: Int?,
    val rating: Double?,
    val genres: List<String>,
    val trending: Boolean,
    val featured: Boolean
)
