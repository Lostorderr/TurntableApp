package com.turntable.app.data.model

import androidx.room.*

// ==================== Turntable (Wheel) ====================

@Entity(tableName = "turntable")
data class TurntableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val status: Int = 1,          // 0=draft, 1=enabled, 2=disabled
    val boxId: Long? = null,      // FK to turntable_box, null = unboxed
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

// ==================== Box ====================

@Entity(tableName = "turntable_box")
data class TurntableBoxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "turntable_segment")
data class TurntableSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turntableId: Long,
    val name: String = "",
    val description: String = "",
    val weight: Int = 1,
    val sortOrder: Int = 0
)

// ==================== Flow ====================

@Entity(tableName = "turntable_flow")
data class TurntableFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val status: Int = 1,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "turntable_flow_stage")
data class TurntableFlowStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flowId: Long,
    val turntableId: Long,
    val stageName: String = "",
    val stageOrder: Int = 0
)

// ==================== Session & Record ====================

@Entity(tableName = "turntable_session")
data class TurntableSessionEntity(
    @PrimaryKey val sessionId: String,
    val flowId: Long,
    val userId: String = "",
    val currentStage: Int = 0,
    val status: Int = 0,           // 0=in_progress, 1=completed, 2=abandoned
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "turntable_spin_record")
data class TurntableSpinRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val turntableId: Long,
    val segmentId: Long,
    val segmentName: String = "",
    val segmentDescription: String = "",
    val stageOrder: Int = 0,
    val userId: String = "",
    val createTime: Long = System.currentTimeMillis()
)

// ==================== Relationship classes (for queries) ====================

data class TurntableWithSegments(
    @Embedded val turntable: TurntableEntity,
    @Relation(parentColumn = "id", entityColumn = "turntableId")
    val segments: List<TurntableSegmentEntity>
)

data class BoxWithTurntables(
    @Embedded val box: TurntableBoxEntity,
    @Relation(parentColumn = "id", entityColumn = "boxId")
    val turntables: List<TurntableEntity>
)

data class FlowWithStages(
    @Embedded val flow: TurntableFlowEntity,
    @Relation(parentColumn = "id", entityColumn = "flowId")
    val stages: List<TurntableFlowStageEntity>
)

data class SessionWithRecords(
    @Embedded val session: TurntableSessionEntity,
    @Relation(parentColumn = "sessionId", entityColumn = "sessionId")
    val records: List<TurntableSpinRecordEntity>
)

// ==================== Value Objects (non-persisted) ====================

data class SpinResult(
    val turntableId: Long,
    val turntableName: String,
    val segmentId: Long,
    val segmentName: String,
    val segmentDescription: String,
    val weight: Int
)
