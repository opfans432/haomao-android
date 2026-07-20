package com.catshelf.reader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class FavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences("haomao_library", Context.MODE_PRIVATE)

    fun load(): List<Comic> {
        val raw = preferences.getString(KEY_FAVORITES, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val title = item.optString("title")
                    val imageUrl = item.optString("imageUrl")
                    if (id.isBlank() || title.isBlank()) continue
                    add(
                        Comic(
                            id = id,
                            title = title,
                            imageUrl = imageUrl,
                            section = item.optString("section"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(comics: List<Comic>) {
        val array = JSONArray()
        comics.distinctBy { it.id }.forEach { comic ->
            array.put(
                JSONObject()
                    .put("id", comic.id)
                    .put("title", comic.title)
                    .put("imageUrl", comic.imageUrl)
                    .put("section", comic.section),
            )
        }
        preferences.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
    }
}
