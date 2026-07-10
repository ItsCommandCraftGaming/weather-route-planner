package com.example.myapplication.data.interfaces

interface ISearchHistoryRepository {
    fun getHistory(): List<String>
    fun saveQuery(query: String): List<String>
    fun clearHistory()
}
