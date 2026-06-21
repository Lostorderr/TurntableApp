package com.turntable.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { editingName = true }) { Text("编辑") }
                        TextButton(onClick = { viewModel.openAiEditFlow() }) { Text("AI 编辑", color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            if (viewModel.showAiEditFlow.collectAsState().value) {
                var editInstruction by remember { mutableStateOf("") }
                val aiGenerating by viewModel.aiGenerating.collectAsState()
                AlertDialog(
                    onDismissRequest = { viewModel.closeAiEditFlow() }, title = { Text("AI 编辑流程") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("描述你想要的修改，例如：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("· 把第一阶段和第二阶段顺序调换 / 改名为「周末大冒险」/ 删除最后一个阶段", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(value = editInstruction, onValueChange = { editInstruction = it }, label = { Text("修改指令") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !aiGenerating)
                            if (aiGenerating) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)); Text("AI 正在编辑…", fontSize = 14.sp) }
                        }
                    },
                    confirmButton = { Button(onClick = { viewModel.editFlowByAi(editInstruction) }, enabled = editInstruction.isNotBlank() && !aiGenerating) { Text("执行") } },
                    dismissButton = { TextButton(onClick = { viewModel.closeAiEditFlow() }) { Text("取消") } }
                )
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

        // Stage list — disable scroll during drag
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState, enabled = draggedIndex == null).padding(horizontal = 16.dp)
        ) {
            var stageNameDlg by remember { mutableStateOf<TurntableFlowStageEntity?>(null) }
            var dlgText by remember { mutableStateOf("") }

            // Stage name edit dialog
            if (stageNameDlg != null) {
                AlertDialog(
                    onDismissRequest = { stageNameDlg = null },
                    title = { Text("重命名阶段") },
                    text = { OutlinedTextField(value = dlgText, onValueChange = { dlgText = it }, label = { Text("阶段名称") }, singleLine = true) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.updateStageName(stageNameDlg!!, dlgText); stageNameDlg = null }) { Text("保存") }
                    },
                    dismissButton = { TextButton(onClick = { stageNameDlg = null }) { Text("取消") } }
                )
            }

            localStages.forEachIndexed { index, stage ->
                val isDragged = draggedIndex == index

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .zIndex(if (isDragged) 999f else 0f)
                        .graphicsLayer { if (isDragged) { translationY = dragOffsetY; shadowElevation = 8f } },
                    colors = CardDefaults.cardColors(containerColor = if (isDragged) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Drag handle — manual gesture to prevent scroll interception
                        Box(modifier = Modifier.padding(4.dp).pointerInput(stage.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitPointerEvent(); val dc = down.changes.firstOrNull() ?: continue
                                    if (!dc.pressed) continue
                                    val startTime = System.currentTimeMillis(); var isDragging = false
                                    while (true) {
                                        val event = awaitPointerEvent(); val c = event.changes.firstOrNull() ?: break
                                        c.consume(); if (!c.pressed) break
                                        if (!isDragging && System.currentTimeMillis() - startTime > 300) { isDragging = true; draggedIndex = index; dragOffsetY = 0f }
                                        if (isDragging) {
                                            dragOffsetY += c.position.y - c.previousPosition.y
                                            val steps = (dragOffsetY / itemHeightPx).roundToInt()
                                            val ti = (index + steps).coerceIn(0, localStages.size - 1)
                                            if (ti != index && draggedIndex == index) { val nl = localStages.toMutableList(); val item = nl.removeAt(index); nl.add(ti, item); localStages = nl; draggedIndex = ti; dragOffsetY -= steps * itemHeightPx }
                                        }
                                    }
                                    if (isDragging) viewModel.reorderStages(flowId, localStages)
                                    draggedIndex = null; dragOffsetY = 0f
                                }
                            }
                        }) { Text("⋮⋮", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }

                        // Order badge
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
                        Spacer(modifier = Modifier.width(10.dp))

                        // Name + turntable
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stage.stageName, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { stageNameDlg = stage; dlgText = stage.stageName })
                            Text(turntableMap[stage.turntableId] ?: "未知", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // Up/Down
                        IconButton(onClick = { viewModel.moveStageUp(flowId, stage.stageOrder) }, modifier = Modifier.size(28.dp), enabled = index > 0) { Text("▲", fontSize = 12.sp) }
                        IconButton(onClick = { viewModel.moveStageDown(flowId, stage.stageOrder) }, modifier = Modifier.size(28.dp), enabled = index < localStages.size - 1) { Text("▼", fontSize = 12.sp) }

                        // Edit turntable
                        TextButton(onClick = { viewModel.openEditWheelForm(stage.turntableId); viewModel.setTab(0) }) { Text("编辑", fontSize = 12.sp) }

                        // Delete
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        TextButton(onClick = { showDeleteConfirm = true }) { Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                        if (showDeleteConfirm) AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false }, title = { Text("确认删除") }, text = { Text("确定要移除阶段「${stage.stageName}」吗？") },
                            confirmButton = { TextButton(onClick = { viewModel.removeStageFromFlow(stage); showDeleteConfirm = false }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
                            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } })
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

