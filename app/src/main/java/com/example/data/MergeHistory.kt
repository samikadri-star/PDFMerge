package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merge_history")
data class MergeHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val filesCount: Int
)
