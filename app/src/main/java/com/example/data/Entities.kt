package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val queryType: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "hotkeys")
data class CustomHotkeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyChar: String,
    val actionName: String,
    val promptTemplate: String
)

@Dao
interface KeyboardDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    @Query("SELECT * FROM hotkeys")
    fun getAllHotkeys(): Flow<List<CustomHotkeyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHotkey(hotkey: CustomHotkeyEntity)

    @Query("DELETE FROM hotkeys WHERE id = :id")
    suspend fun deleteHotkey(id: Long)
}
