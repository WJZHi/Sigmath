package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "math_history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val expression: String,
    val result: String,
    val type: String, // "calculation" or "equation"
    val timestamp: Long = System.currentTimeMillis()
)
