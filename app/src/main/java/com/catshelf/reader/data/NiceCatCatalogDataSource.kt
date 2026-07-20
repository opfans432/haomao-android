package com.catshelf.reader.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class NiceCatCatalogDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build(),
) : CatalogDataSource {
    private val webBase = "https://web.nicecat.cc"

    override suspend fun home(): CatalogHome = withContext(Dispatchers.IO) {
        val payload = pageData(fetch("$webBase/"))
        val data = payload.entries.firstOrNull { it.key.startsWith("HomeFeed.randomFeed") }
            ?.value?.jsonObject ?: error("首页数据不存在")

        val featured = data["recommend"]?.asComic("推荐")
        val tags = data["tagData"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("uid") ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            Tag(id, name)
        }
        val sections = data["homeData"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj.string("ViewName").orEmpty().ifBlank { "最新内容" }
            val comics = obj["ViewDataArray"]?.jsonArray.orEmpty()
                .mapNotNull { it.asComic(title) }
                .distinctBy { it.id }
            if (comics.isEmpty()) null else HomeSection(title, comics)
        }
        CatalogHome(featured, tags, sections)
    }

    override suspend fun search(query: String): List<Comic> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
        val root = NuxtPayloadParser.parse(fetch("$webBase/search/all/content/$encoded"))
        collectComics(root).distinctBy { it.id }
    }

    override suspend fun detail(comicId: String): ComicDetail = withContext(Dispatchers.IO) {
        val payload = pageData(fetch("$webBase/comic/info/id.$comicId"))
        val holder = payload.entries.firstOrNull { it.key.startsWith("ComicInfo.info.") }
            ?.value?.jsonObject ?: error("作品详情不存在")
        val comic = holder["comicData"]?.jsonObject ?: error("作品详情格式无效")

        val tags = comic["tagData"]?.jsonObject?.values.orEmpty()
            .flatMap { value -> (value as? JsonArray).orEmpty() }
            .mapNotNull { (it as? JsonObject)?.string("name") }
            .distinct()

        val recommendations = comic["recommendComic"]?.jsonArray.orEmpty()
            .mapNotNull { it.asComic("相关作品") }
            .distinctBy { it.id }

        ComicDetail(
            id = comic.string("uid") ?: comicId,
            title = comic.string("name_two") ?: comic.string("name_one") ?: "未命名作品",
            alternateTitle = comic.string("name_one").orEmpty(),
            imageUrl = comic.string("image").orEmpty(),
            uploader = comic["uploader"]?.jsonObject?.string("name").orEmpty(),
            updatedAt = comic.string("update_time").orEmpty(),
            rating = comic.string("rating").orEmpty(),
            tags = tags,
            recommendations = recommendations,
        )
    }

    override fun readerUrl(comicId: String): String =
        "$webBase/comic/book/reader/id.$comicId"

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("服务器返回 ${response.code}")
            return response.body?.string() ?: error("服务器返回空内容")
        }
    }

    private fun pageData(html: String): JsonObject {
        val root = NuxtPayloadParser.parse(html)
        return root["data"]?.jsonObject ?: error("页面数据不存在")
    }

    private fun JsonElement.asComic(section: String): Comic? {
        val obj = this as? JsonObject ?: return null
        val id = obj.string("uid") ?: return null
        val image = obj.string("image") ?: return null
        val title = obj.string("name") ?: obj.string("en_name") ?: return null
        return Comic(id, title, image, section)
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun collectComics(element: JsonElement): List<Comic> {
        val output = mutableListOf<Comic>()
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    node.asComic("搜索结果")?.let(output::add)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
        return output
    }
}
