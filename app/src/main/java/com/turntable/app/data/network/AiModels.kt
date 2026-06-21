package com.turntable.app.data.network

data class TurntableData(
    val name: String,
    val description: String = "",
    val segments: List<SegmentData>
)

data class SegmentData(
    val name: String,
    val description: String = "",
    val weight: Int
)

data class FlowData(
    val name: String,
    val description: String = "",
    val stages: List<FlowStageData>
)

data class FlowStageData(
    val stageName: String,
    val turntable: TurntableData
)
