package com.catshelf.reader.data

data class Comic(
    val id: String,
    val title: String,
    val imageUrl: String,
    val section: String = "",
)

data class HomeSection(
    val title: String,
    val comics: List<Comic>,
)

data class CatalogHome(
    val featured: Comic?,
    val tags: List<Tag>,
    val sections: List<HomeSection>,
)

data class Tag(val id: String, val name: String)

data class ComicDetail(
    val id: String,
    val title: String,
    val alternateTitle: String,
    val imageUrl: String,
    val uploader: String,
    val updatedAt: String,
    val rating: String,
    val tags: List<String>,
    val recommendations: List<Comic>,
)

interface CatalogDataSource {
    suspend fun home(): CatalogHome
    suspend fun search(query: String): List<Comic>
    suspend fun detail(comicId: String): ComicDetail
    fun readerUrl(comicId: String): String
}
