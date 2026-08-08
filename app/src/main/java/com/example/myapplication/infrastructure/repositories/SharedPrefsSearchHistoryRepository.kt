package com.example.myapplication.infrastructure.repositories

import android.content.Context
import com.example.myapplication.application.interfaces.ISearchHistoryRepository

class SharedPrefsSearchHistoryRepository(private val context: Context) : ISearchHistoryRepository {
    private val prefs = context.getSharedPreferences("MapAppPrefs", Context.MODE_PRIVATE)
    private val key = "istoric_cautari"

    override fun getHistory(): List<String> {
        val historyString = prefs.getString(key, "") ?: ""
        return if (historyString.isEmpty()) emptyList() else historyString.split("|||")
    }

    override fun saveQuery(query: String): List<String> {
        if (query.isBlank()) return getHistory()
        val currentHistory = getHistory().toMutableList()

        currentHistory.remove(query)
        currentHistory.add(0, query)
        if (currentHistory.size > 5) currentHistory.removeLast()

        prefs.edit().putString(key, currentHistory.joinToString("|||")).apply()
        return currentHistory
    }

    override fun clearHistory() {
        prefs.edit().remove(key).apply()
    }
}
