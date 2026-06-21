package com.turntable.app.data.repository

import androidx.room.*
import com.turntable.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TurntableDao {

    // ---- Turntable ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurntable(entity: TurntableEntity): Long

    @Update
    suspend fun updateTurntable(entity: TurntableEntity)

    @Delete
    suspend fun deleteTurntable(entity: TurntableEntity)

    @Query("SELECT COUNT(*) FROM turntable")
    suspend fun getTurntableCount(): Int

    @Query("SELECT * FROM turntable WHERE boxId IS NULL")
    suspend fun getUnboxedTurntables(): List<TurntableEntity>

    @Query("SELECT * FROM turntable")
    suspend fun getAllTurntablesList(): List<TurntableEntity>

    @Query("SELECT * FROM turntable WHERE id = :id")
    suspend fun getTurntableById(id: Long): TurntableEntity?

    @Transaction
    @Query("SELECT * FROM turntable WHERE id = :id")
    suspend fun getTurntableWithSegments(id: Long): TurntableWithSegments?

    @Transaction
    @Query("SELECT * FROM turntable")
    fun getAllTurntablesWithSegments(): Flow<List<TurntableWithSegments>>

    // ---- Box ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBox(entity: TurntableBoxEntity): Long

    @Update
    suspend fun updateBox(entity: TurntableBoxEntity)

    @Delete
    suspend fun deleteBox(entity: TurntableBoxEntity)

    @Query("SELECT * FROM turntable_box ORDER BY createTime")
    fun getAllBoxes(): Flow<List<TurntableBoxEntity>>

    @Transaction
    @Query("SELECT * FROM turntable_box ORDER BY createTime")
    fun getAllBoxesWithTurntables(): Flow<List<BoxWithTurntables>>

    @Query("UPDATE turntable SET boxId = :boxId, updateTime = :updateTime WHERE id = :turntableId")
    suspend fun setTurntableBoxId(turntableId: Long, boxId: Long?, updateTime: Long)

    @Query("UPDATE turntable SET boxId = NULL, updateTime = :updateTime WHERE boxId = :boxId")
    suspend fun unboxAllByBoxId(boxId: Long, updateTime: Long)

    // ---- Segment ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(entities: List<TurntableSegmentEntity>)

    @Query("SELECT * FROM turntable_segment WHERE turntableId = :turntableId ORDER BY sortOrder")
    suspend fun getSegmentsByTurntableId(turntableId: Long): List<TurntableSegmentEntity>

    @Query("DELETE FROM turntable_segment WHERE turntableId = :turntableId")
    suspend fun deleteSegmentsByTurntableId(turntableId: Long)

    // ---- Flow ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlow(entity: TurntableFlowEntity): Long

    @Update
    suspend fun updateFlow(entity: TurntableFlowEntity)

    @Delete
    suspend fun deleteFlow(entity: TurntableFlowEntity)

    @Query("SELECT COUNT(*) FROM turntable_flow")
    suspend fun getFlowCount(): Int

    @Query("SELECT * FROM turntable_flow")
    suspend fun getAllFlowsList(): List<TurntableFlowEntity>

    @Query("SELECT * FROM turntable_flow WHERE id = :id")
    suspend fun getFlowById(id: Long): TurntableFlowEntity?

    @Transaction
    @Query("SELECT * FROM turntable_flow WHERE id = :id")
    suspend fun getFlowWithStages(id: Long): FlowWithStages?

    @Transaction
    @Query("SELECT * FROM turntable_flow")
    fun getAllFlowsWithStages(): Flow<List<FlowWithStages>>

    // ---- Flow Stage ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStage(entity: TurntableFlowStageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStages(entities: List<TurntableFlowStageEntity>)

    @Update
    suspend fun updateStage(entity: TurntableFlowStageEntity)

    @Delete
    suspend fun deleteStage(entity: TurntableFlowStageEntity)

    @Query("DELETE FROM turntable_flow_stage WHERE flowId = :flowId")
    suspend fun deleteStagesByFlowId(flowId: Long)

    @Query("SELECT * FROM turntable_flow_stage WHERE flowId = :flowId ORDER BY stageOrder")
    suspend fun getStagesByFlowId(flowId: Long): List<TurntableFlowStageEntity>

    // ---- Session ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(entity: TurntableSessionEntity)

    @Update
    suspend fun updateSession(entity: TurntableSessionEntity)

    @Query("SELECT * FROM turntable_session WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): TurntableSessionEntity?

    @Transaction
    @Query("SELECT * FROM turntable_session WHERE sessionId = :sessionId")
    suspend fun getSessionWithRecords(sessionId: String): SessionWithRecords?

    // ---- Spin Record ----
    @Insert
    suspend fun insertSpinRecord(entity: TurntableSpinRecordEntity)

}
