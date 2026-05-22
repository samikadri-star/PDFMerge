package com.example.data

import kotlinx.coroutines.flow.Flow

class MergeHistoryRepository(private val dao: MergeHistoryDao) {
    val allHistory: Flow<List<MergeHistory>> = dao.getAllHistory()

    suspend fun insert(history: MergeHistory): Long {
        return dao.insertHistory(history)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteHistoryById(id)
    }
}
