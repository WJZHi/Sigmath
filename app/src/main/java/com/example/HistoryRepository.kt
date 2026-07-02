package com.example

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()

    suspend fun insert(item: HistoryItem) {
        historyDao.insert(item)
    }

    suspend fun delete(item: HistoryItem) {
        historyDao.delete(item)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
