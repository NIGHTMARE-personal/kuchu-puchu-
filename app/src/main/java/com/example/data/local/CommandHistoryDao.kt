package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommandHistoryEntity): Long

    @Update
    suspend fun update(entity: CommandHistoryEntity)

    @Query("DELETE FROM command_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}
