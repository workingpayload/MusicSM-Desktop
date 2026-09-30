package com.example.musicsmd.search

import com.example.musicsmd.settings.AppPaths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Small JSON-backed MRU list for the dedicated Search screen. */
class SearchHistoryStore(
    private val file: File = File(AppPaths.dataDir, "search_history.json"),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun add(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + _history.value.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_HISTORY)
        set(updated)
    }

    fun remove(query: String) = set(_history.value.filterNot { it == query })

    fun clear() = set(emptyList())

    private fun set(value: List<String>) {
        _history.value = value
        persist(value)
    }

    private fun load(): List<String> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString(ListSerializer(String.serializer()), file.readText())
    }.getOrDefault(emptyList())

    @Synchronized
    private fun persist(value: List<String>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(String.serializer()), value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val MAX_HISTORY = 20
    }
}
