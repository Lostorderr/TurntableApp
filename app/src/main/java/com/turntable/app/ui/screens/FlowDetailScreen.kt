package com.turntable.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.turntable.app.data.model.TurntableFlowStageEntity
import com.turntable.app.data.model.TurntableSegmentEntity
import com.turntable.app.data.model.TurntableWithSegments
import com.turntable.app.viewmodel.TurntableViewModel
import kotlin.math.roundToInt

@Composable
fun FlowDetailScreen(
    flowId: Long,
    viewModel: TurntableViewModel
) {
    val flowDetail by viewModel.flowDetail.collectAsState()
    val turntables by viewModel.turntables.collectAsState()

    LaunchedEffect(flowId) {
        viewModel.openFlowDetail(flowId)
    }

    val turntableMap = remember(turntables) {
        turntables.associate { it.turntable.id to it.turntable.name }
    }

    // Local reorderable state - synced from ViewModel
    var localStages by remember { mutableStateOf<List<TurntableFlowStageEntity>>(emptyList()) }

    // Sync local state when flowDetail changes (but not during drag)
    val currentStages = remember(flowDetail) {
        flowDetail?.stages?.sortedBy { it.stageOrder } ?: emptyList()
    }

    LaunchedEffect(currentStages) {
        localStages = currentStages
    }

    // Drag state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightDp = 76.dp
    val density = LocalDensity.current
    val itemHeightPx = remember { with(density) { itemHeightDp.toPx() } }

    if (flowDetail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val flow = flowDetail!!.flow

    Column(modifier = Modifier.fillMaxSize()) {
        // Flow info header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var editingName by remember { mutableStateOf(false) }
                var editName by remember(flow) { mutableStateOf(flow.name) }
                var editDesc by remember(flow) { mutableStateOf(flow.description) }

                if (editingName) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("流程名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("流程描述") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.updateFlowInfo(flow.id, editName, editDesc)
                            editingName = false
                        }) { Text("保存") }
                        TextButton(onClick = { editingName = false }) { Text("取消") }
                    }
                } else {
                    Text(
                        flow.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (flow.description.isNotBlank()) {
                        Text(
                            flow.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { editingName = true }) {
                        Text("编辑名称和描述")
                    }
                }
            }
        }

        // Stage count & drag hint
        Text(
            "阶段列表 (${localStages.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "长按拖拽可调整顺序",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // Stage list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            localStages.forEachIndexed { index, stage ->
                val isDragged = draggedIndex == index
                val elevation by animateDpAsState(
                    if (isDragged) 8.dp else 0.dp,
                    label = "elevation"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationY = dragOffsetY
                                shadowElevation = 8f
                            }
                        }
                        .pointerInput(stage.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, offset ->
                                    change.consume()
                                    dragOffsetY += offset.y

                                    // Reorder locally as we drag
                                    val moveSteps = (dragOffsetY / itemHeightPx).roundToInt()
                                    val targetIndex = (index + moveSteps).coerceIn(0, localStages.size - 1)

                                    if (targetIndex != index && draggedIndex == index) {
                                        val newList = localStages.toMutableList()
                                        val item = newList.removeAt(index)
                                        newList.add(targetIndex, item)
                                        localStages = newList
                                        draggedIndex = targetIndex
                                        // Adjust offset since the item moved in the list
                                        dragOffsetY -= moveSteps * itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggedIndex = null
                                    dragOffsetY = 0f
                                    // Commit the new order
                                    viewModel.reorderStages(flowId, localStages)
                                },
                                onDragCancel = {
                                    draggedIndex = null
                                    dragOffsetY = 0f
                                    // Revert to server state
                                    localStages = currentStages
                                }
                            )
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragged)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.background
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Order number badge
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            var editStageName by remember { mutableStateOf(false) }
                            var stageNameText by remember(stage) { mutableStateOf(stage.stageName) }
                            if (editStageName) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = stageNameText,
                                        onValueChange = { stageNameText = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                                    )
                                    IconButton(onClick = {
                                        viewModel.updateStageName(stage, stageNameText)
                                        editStageName = false
                                    }) { Text("✓", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { editStageName = false }) { Text("✕", fontSize = 14.sp) }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { editStageName = true }
                                ) {
                                    Text(stage.stageName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(" ✎", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                "转盘: ${turntableMap[stage.turntableId] ?: "未知"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Up/Down buttons
                        Column {
                            IconButton(
                                onClick = {
                                    viewModel.moveStageUp(flowId, stage.stageOrder)
                                },
                                modifier = Modifier.size(28.dp),
                                enabled = index > 0
                            ) {
                                Text("▲", fontSize = 14.sp)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.moveStageDown(flowId, stage.stageOrder)
                                },
                                modifier = Modifier.size(28.dp),
                                enabled = index < localStages.size - 1
                            ) {
                                Text("▼", fontSize = 14.sp)
                            }
                        }

                        // Edit button - navigate to turntable edit
                        IconButton(
                            onClick = {
                                viewModel.openEditWheelForm(stage.turntableId)
                                viewModel.setTab(0)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("✎", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        // Delete button
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                        }

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("确认删除") },
                                text = { Text("确定要移除阶段「${stage.stageName}」吗？") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.removeStageFromFlow(stage)
                                        showDeleteConfirm = false
                                    }) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("取消")
                                    }
                                }
                            )
                        }

                    }
                }
            }

            // Add stage button
            var showAddStage by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showAddStage = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("+ 添加阶段")
            }

            if (showAddStage) {
                AddStageDialog(
                    turntables = turntables,
                    viewModel = viewModel,
                    onDismiss = { showAddStage = false },
                    onConfirm = { turntableId, stageName ->
                        viewModel.addStageToFlow(flowId, turntableId, stageName)
                        showAddStage = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AddStageDialog(
    turntables: List<TurntableWithSegments>,
    viewModel: TurntableViewModel,
    onDismiss: () -> Unit,
    onConfirm: (turntableId: Long, stageName: String) -> Unit
) {
    var selectedTurntableId by remember { mutableStateOf<Long?>(null) }
    var stageName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var creatingWheel by remember { mutableStateOf(false) }
    var newWheelName by remember { mutableStateOf("") }
    var newSegments by remember { mutableStateOf(listOf(SegmentFormData("", "", 1))) }

    // Watch turntables for newly created one
    val turntableFlow by viewModel.turntables.collectAsState()
    var lastCreatedId by remember { mutableStateOf<Long?>(null) }

    // When a new turntable appears, select it
    LaunchedEffect(turntableFlow.size) {
        if (creatingWheel && turntableFlow.isNotEmpty()) {
            val newest = turntableFlow.maxByOrNull { it.turntable.id }
            if (newest != null && newest.turntable.id != lastCreatedId) {
                lastCreatedId = newest.turntable.id
                selectedTurntableId = newest.turntable.id
                if (stageName.isBlank()) stageName = newWheelName
                creatingWheel = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加阶段") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (creatingWheel) {
                    // Inline wheel creation
                    Text("新建转盘", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = newWheelName,
                        onValueChange = { newWheelName = it },
                        label = { Text("转盘名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("选项", style = MaterialTheme.typography.labelMedium)
                    newSegments.forEachIndexed { i, seg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = seg.name,
                                onValueChange = { v ->
                                    newSegments = newSegments.toMutableList().apply {
                                        set(i, seg.copy(name = v))
                                    }
                                },
                                placeholder = { Text("名称") },
                                modifier = Modifier.weight(3f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            OutlinedTextField(
                                value = seg.weight.toString(),
                                onValueChange = { v ->
                                    newSegments = newSegments.toMutableList().apply {
                                        set(i, seg.copy(weight = v.toIntOrNull() ?: 1))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            if (newSegments.size > 1) {
                                TextButton(onClick = {
                                    newSegments = newSegments.toMutableList().apply { removeAt(i) }
                                }) { Text("×", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    TextButton(onClick = {
                        newSegments = newSegments + SegmentFormData("", "", 1)
                    }) { Text("+ 添加选项") }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (newWheelName.isNotBlank() && newSegments.any { it.name.isNotBlank() }) {
                                val segs = newSegments.filter { it.name.isNotBlank() }.map { s ->
                                    TurntableSegmentEntity(
                                        turntableId = 0,
                                        name = s.name,
                                        description = s.desc,
                                        weight = s.weight.coerceIn(1, 100)
                                    )
                                }
                                viewModel.saveWheel(newWheelName, "", segs)
                            }
                        }, enabled = newWheelName.isNotBlank() && newSegments.any { it.name.isNotBlank() }) { Text("创建") }
                        TextButton(onClick = { creatingWheel = false }) { Text("取消") }
                    }
                } else {
                    Text("选择转盘", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val name = selectedTurntableId?.let { id ->
                                turntables.find { it.turntable.id == id }?.turntable?.name
                            } ?: "-- 请选择转盘 --"
                            Text(name, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("＋ 新建转盘", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    newWheelName = ""
                                    newSegments = listOf(SegmentFormData("", "", 1))
                                    creatingWheel = true
                                    expanded = false
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            turntables.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(w.turntable.name) },
                                    onClick = {
                                        selectedTurntableId = w.turntable.id
                                        if (stageName.isBlank()) stageName = w.turntable.name
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = stageName,
                        onValueChange = { stageName = it },
                        label = { Text("阶段名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (!creatingWheel) {
                TextButton(
                    onClick = {
                        val id = selectedTurntableId ?: return@TextButton
                        onConfirm(id, stageName.ifBlank { "阶段" })
                    },
                    enabled = selectedTurntableId != null
                ) { Text("添加") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

