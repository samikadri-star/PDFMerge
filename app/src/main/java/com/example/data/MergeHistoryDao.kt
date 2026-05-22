package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MergeHistoryDao {
    @Query("SELECT * FROM merge_history ORDER BY timestamp DESC")
    fun getAllHistory(): kotlinx.coroutines.flow.Flow<List<MergeHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: MergeHistory): Long

    @Query("DELETE FROM merge_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)
}
