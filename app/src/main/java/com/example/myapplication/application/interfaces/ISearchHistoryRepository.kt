package com.example.myapplication.application.interfaces

interface ISearchHistoryRepository {
    fun getHistory(): List<String>
    fun saveQuery(query: String): List<String>
    fun clearHistory()
}
