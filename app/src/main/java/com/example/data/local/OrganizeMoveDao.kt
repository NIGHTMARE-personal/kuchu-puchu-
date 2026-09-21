package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizeMoveDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMove(move: OrganizeMoveEntity): Long

    @Update
    suspend fun updateMove(move: OrganizeMoveEntity)

    @Query("UPDATE organize_moves SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateMoveStatus(id: Long, status: String, errorMessage: String? = null)

    @Query("SELECT * FROM organize_moves WHERE planId = :planId ORDER BY id ASC")
    suspend fun getMovesForPlan(planId: String): List<OrganizeMoveEntity>

    @Query("SELECT * FROM organize_moves WHERE planId = :planId AND status = 'COMPLETED' ORDER BY id DESC")
    suspend fun getCompletedMovesForPlan(planId: String): List<OrganizeMoveEntity>

    @Query("SELECT COUNT(*) FROM organize_moves WHERE planId = :planId AND status = 'COMPLETED'")
    suspend fun countCompletedMoves(planId: String): Int

    @Query("SELECT COUNT(*) FROM organize_moves WHERE planId = :planId AND status = 'UNDONE'")
    suspend fun countUndoneMoves(planId: String): Int

    @Query("SELECT * FROM organize_moves ORDER BY timestamp DESC LIMIT 100")
    fun getAllMovesFlow(): Flow<List<OrganizeMoveEntity>>
}
