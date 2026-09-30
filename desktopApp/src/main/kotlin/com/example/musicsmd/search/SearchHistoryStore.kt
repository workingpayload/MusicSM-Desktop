package com.example.musicsmd.search

import com.example.musicsmd.settings.AppPaths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Small JSON-backed MRU lists for the dedicated Search screen: the queries typed, and the songs,
 * albums and artists opened from their results.
 */
class SearchHistoryStore(
    private val file: File = File(AppPaths.dataDir, "search_history.json"),
    private val itemsFile: File = File(file.parentFile, "search_recent_items.json"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val querySerializer = ListSerializer(String.serializer())
    private val itemSerializer = ListSerializer(RecentSearchItem.serializer())

    private val _history = MutableStateFlow(load(file, querySerializer))
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _items = MutableStateFlow(load(itemsFile, itemSerializer))
    val items: StateFlow<List<RecentSearchItem>> = _items.asStateFlow()

    fun add(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + _history.value.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_HISTORY)
        setHistory(updated)
    }

    fun remove(query: String) = setHistory(_history.value.filterNot { it == query })

    /** Moves [item] to the front, so the latest pick leads the shelf. */
    fun addItem(item: RecentSearchItem) =
        setItems((listOf(item) + _items.value.filterNot { it.key == item.key }).take(MAX_ITEMS))

    fun removeItem(item: RecentSearchItem) = setItems(_items.value.filterNot { it.key == item.key })

    /** Clears both the typed queries and the opened results. */
    fun clear() {
        setHistory(emptyList())
        setItems(emptyList())
    }

    private fun setHistory(value: List<String>) {
        _history.value = value
        persist(file, querySerializer, value)
    }

    private fun setItems(value: List<RecentSearchItem>) {
        _items.value = value
        persist(itemsFile, itemSerializer, value)
    }

    private fun <T> load(from: File, serializer: KSerializer<List<T>>): List<T> = runCatching {
        if (!from.exists()) emptyList() else json.decodeFromString(serializer, from.readText().removePrefix("\uFEFF"))
    }.getOrDefault(emptyList())

    @Synchronized
    private fun <T> persist(to: File, serializer: KSerializer<List<T>>, value: List<T>) {
        runCatching {
            to.parentFile?.mkdirs()
            val tmp = File(to.parentFile, to.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            if (!tmp.renameTo(to)) {
                to.delete()
                tmp.renameTo(to)
            }
        }
    }

    private companion object {
        const val MAX_HISTORY = 20
        const val MAX_ITEMS = 20
    }
}
