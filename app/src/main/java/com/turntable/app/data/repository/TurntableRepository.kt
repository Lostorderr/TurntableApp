package com.turntable.app.data.repository

import com.turntable.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TurntableRepository(private val dao: TurntableDao) {

    // ---- Turntable CRUD ----

    fun getAllTurntables(): Flow<List<TurntableWithSegments>> = dao.getAllTurntablesWithSegments()

    suspend fun getTurntable(id: Long): TurntableWithSegments? = dao.getTurntableWithSegments(id)

    suspend fun getTurntableCount(): Int = dao.getTurntableCount()
    suspend fun getFlowCount(): Int = dao.getFlowCount()
    suspend fun getAllTurntablesList(): List<TurntableEntity> = dao.getAllTurntablesList()
    suspend fun getAllFlowsList(): List<TurntableFlowEntity> = dao.getAllFlowsList()

    // ---- Box CRUD ----

    fun getAllBoxes(): Flow<List<BoxWithTurntables>> = dao.getAllBoxesWithTurntables()

    suspend fun createBox(name: String, description: String = ""): Long {
        return dao.insertBox(TurntableBoxEntity(name = name, description = description))
    }

    suspend fun updateBox(id: Long, name: String, description: String) {
        dao.updateBox(TurntableBoxEntity(id = id, name = name, description = description, updateTime = System.currentTimeMillis()))
    }

    suspend fun deleteBox(id: Long) {
        val now = System.currentTimeMillis()
        dao.unboxAllByBoxId(id, now)
        dao.deleteBox(TurntableBoxEntity(id = id))
    }

    suspend fun moveTurntableToBox(turntableId: Long, boxId: Long?) {
        dao.setTurntableBoxId(turntableId, boxId, System.currentTimeMillis())
    }

    // ---- Turntable CRUD ----

    suspend fun getUnboxedTurntables(): List<TurntableWithSegments> {
        val unboxedIds = dao.getUnboxedTurntables().map { it.id }
        return unboxedIds.mapNotNull { dao.getTurntableWithSegments(it) }
    }

    suspend fun createTurntable(name: String, description: String, segments: List<TurntableSegmentEntity>, boxId: Long? = null): Long {
        val id = dao.insertTurntable(
            TurntableEntity(name = name, description = description, status = 1, boxId = boxId)
        )
        val sortedSegments = segments.mapIndexed { i, s ->
            s.copy(turntableId = id, sortOrder = i)
        }
        dao.insertSegments(sortedSegments)
        return id
    }

    suspend fun updateTurntable(id: Long, name: String, description: String, segments: List<TurntableSegmentEntity>) {
        dao.updateTurntable(
            TurntableEntity(id = id, name = name, description = description,
                status = 1, updateTime = System.currentTimeMillis())
        )
        dao.deleteSegmentsByTurntableId(id)
        val sortedSegments = segments.mapIndexed { i, s ->
            s.copy(id = 0, turntableId = id, sortOrder = i)
        }
        dao.insertSegments(sortedSegments)
    }

    suspend fun deleteTurntable(id: Long) {
        val entity = dao.getTurntableById(id) ?: return
        dao.deleteSegmentsByTurntableId(id)
        dao.deleteTurntable(entity)
    }

    // ---- Flow CRUD ----

    fun getAllFlows(): Flow<List<FlowWithStages>> = dao.getAllFlowsWithStages()

    suspend fun getFlow(id: Long): FlowWithStages? = dao.getFlowWithStages(id)

    suspend fun createFlow(name: String, description: String, stages: List<TurntableFlowStageEntity>): Long {
        val flowId = dao.insertFlow(
            TurntableFlowEntity(name = name, description = description, status = 1)
        )
        val sortedStages = stages.mapIndexed { i, s ->
            s.copy(flowId = flowId, stageOrder = i)
        }
        dao.insertStages(sortedStages)
        return flowId
    }

    suspend fun deleteFlow(id: Long) {
        val entity = dao.getFlowById(id) ?: return
        dao.deleteStagesByFlowId(id)
        dao.deleteFlow(entity)
    }

    suspend fun addStageToFlow(flowId: Long, turntableId: Long, stageName: String) {
        val stages = dao.getStagesByFlowId(flowId)
        val nextOrder = (stages.maxOfOrNull { it.stageOrder } ?: -1) + 1
        dao.insertStage(
            TurntableFlowStageEntity(
                flowId = flowId,
                turntableId = turntableId,
                stageName = stageName,
                stageOrder = nextOrder
            )
        )
    }

    suspend fun removeStageFromFlow(stage: TurntableFlowStageEntity) {
        dao.deleteStage(stage)
        val remaining = dao.getStagesByFlowId(stage.flowId)
        remaining.forEachIndexed { index, s ->
            if (s.stageOrder != index) {
                dao.updateStage(s.copy(stageOrder = index))
            }
        }
    }

    suspend fun updateStagesOrder(flowId: Long, stages: List<TurntableFlowStageEntity>) {
        stages.forEachIndexed { index, stage ->
            if (stage.stageOrder != index) {
                dao.updateStage(stage.copy(stageOrder = index))
            }
        }
    }

    suspend fun swapStagesOrder(flowId: Long, stageOrder: Int, direction: Int) {
        val stages = dao.getStagesByFlowId(flowId).sortedBy { it.stageOrder }
        val targetOrder = stageOrder + direction
        if (targetOrder < 0 || targetOrder >= stages.size) return
        val a = stages[stageOrder]
        val b = stages[targetOrder]
        dao.updateStage(a.copy(stageOrder = targetOrder))
        dao.updateStage(b.copy(stageOrder = stageOrder))
    }

    suspend fun updateStageTurntable(stage: TurntableFlowStageEntity, newTurntableId: Long) {
        dao.updateStage(stage.copy(turntableId = newTurntableId))
    }

    suspend fun updateStageName(stage: TurntableFlowStageEntity, newName: String) {
        dao.updateStage(stage.copy(stageName = newName))
    }

    suspend fun updateFlowInfo(id: Long, name: String, description: String) {
        val entity = dao.getFlowById(id) ?: return
        dao.updateFlow(entity.copy(name = name, description = description, updateTime = System.currentTimeMillis()))
    }

    // ---- Spin ----

    suspend fun spin(turntableId: Long): SpinResult {
        val turntable = dao.getTurntableById(turntableId) ?: throw IllegalStateException("转盘不存在")
        val segments = dao.getSegmentsByTurntableId(turntableId)
        if (segments.isEmpty()) throw IllegalStateException("转盘没有选项")
        val totalWeight = segments.sumOf { it.weight }
        require(totalWeight > 0) { "总权重必须大于0" }
        val random = (0 until totalWeight).random()
        var cumulative = 0
        for (seg in segments) {
            cumulative += seg.weight
            if (random < cumulative) {
                return SpinResult(
                    turntableId = turntableId,
                    turntableName = turntable.name,
                    segmentId = seg.id,
                    segmentName = seg.name,
                    segmentDescription = seg.description,
                    weight = seg.weight
                )
            }
        }
        throw IllegalStateException("抽取失败")
    }

    // ---- Session ----

    suspend fun createSession(flowId: Long, userId: String): TurntableSessionEntity {
        val sessionId = UUID.randomUUID().toString().replace("-", "").take(16)
        val session = TurntableSessionEntity(
            sessionId = sessionId,
            flowId = flowId,
            userId = userId,
            currentStage = 0,
            status = 0
        )
        dao.insertSession(session)
        return session
    }

    suspend fun updateSession(session: TurntableSessionEntity) {
        dao.updateSession(session)
    }

    suspend fun insertSpinRecord(record: TurntableSpinRecordEntity) {
        dao.insertSpinRecord(record)
    }
}
