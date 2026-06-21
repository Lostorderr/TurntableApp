package com.turntable.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.turntable.app.data.model.BoxWithTurntables
import com.turntable.app.data.model.TurntableWithSegments
import com.turntable.app.viewmodel.TurntableViewModel
import kotlin.math.roundToInt

data class SegmentFormData(val name: String, val desc: String, val weight: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelManageScreen(viewModel: TurntableViewModel) {
    val turntables by viewModel.turntables.collectAsState()
    val boxes by viewModel.boxes.collectAsState()
    val showBoxDialog by viewModel.showBoxDialog.collectAsState()
    val editingBoxId by viewModel.editingBoxId.collectAsState()

    var draggedWheelId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragStartRootY by remember { mutableStateOf(0f) }

    val boxRegions = remember { mutableStateMapOf<Long?, Pair<Float, Float>>() }

    if (showBoxDialog) {
        val editingBox = boxes.find { it.box.id == editingBoxId }
        var boxName by remember(editingBoxId) { mutableStateOf(editingBox?.box?.name ?: "") }
        AlertDialog(
            onDismissRequest = { viewModel.closeBoxDialog() },
            title = { Text(if (editingBoxId != null) "编辑盒子" else "新建盒子") },
            text = {
                OutlinedTextField(value = boxName, onValueChange = { boxName = it },
                    label = { Text("盒子名称") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { viewModel.saveBox(boxName) }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { viewModel.closeBoxDialog() }) { Text("取消") } }
        )
    }

    val unboxed = remember(turntables) { turntables.filter { it.turntable.boxId == null } }

    fun onDragEnd() {
        val dropY = dragStartRootY + dragOffsetY
        var target: Long? = null
        boxRegions[null]?.let { if (dropY in it.first..it.second) target = null }
        for ((id, r) in boxRegions) {
            if (id != null && dropY in r.first..r.second) target = id
        }
        draggedWheelId?.let { viewModel.moveTurntableToBox(it, target) }
        draggedWheelId = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewModel.openNewWheelForm() }) { Text("+ 新建转盘") }
            OutlinedButton(onClick = { viewModel.openNewBoxDialog() }) { Text("+ 盒子") }
            OutlinedButton(onClick = { viewModel.openAiGenerateOrSettings() }) { Text("AI 生成") }
        }

        if (turntables.isEmpty()) {
            EmptyState(icon = "", title = "还没有转盘", desc = "创建转盘或使用 AI 生成吧！")
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                boxes.forEach { box ->
                    BoxSection(
                        box = box, allWheels = turntables,
                        draggedWheelId = draggedWheelId, dragOffsetY = dragOffsetY,
                        onRegionY = { yMin, yMax -> boxRegions[box.box.id] = yMin to yMax },
                        onEditBox = { viewModel.openEditBoxDialog(box.box.id) },
                        onDeleteBox = { viewModel.deleteBox(box.box.id) },
                        onDragStart = { id, rootY -> draggedWheelId = id; dragStartRootY = rootY; dragOffsetY = 0f },
                        onDrag = { dragOffsetY += it },
                        onDragEnd = { onDragEnd() },
                        onWheelClick = { viewModel.openEditWheelForm(it.turntable.id) },
                        onWheelDelete = { viewModel.deleteWheel(it.turntable.id) },
                        viewModel = viewModel
                    )
                }

                if (unboxed.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                boxRegions[null] = pos.y to pos.y + coords.size.height
                            }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("未归类", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        unboxed.forEach { wheel ->
                            WheelRow(
                                wheel = wheel,
                                isDragging = draggedWheelId == wheel.turntable.id,
                                dragOffsetY = if (draggedWheelId == wheel.turntable.id) dragOffsetY else 0f,
                                onDragStart = { rootY ->
                                    draggedWheelId = wheel.turntable.id; dragStartRootY = rootY; dragOffsetY = 0f
                                },
                                onDrag = { dragOffsetY += it },
                                onDragEnd = { onDragEnd() },
                                onClick = { viewModel.openEditWheelForm(wheel.turntable.id) },
                                onDelete = { viewModel.deleteWheel(wheel.turntable.id) },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoxSection(
    box: BoxWithTurntables, allWheels: List<TurntableWithSegments>,
    draggedWheelId: Long?, dragOffsetY: Float,
    onRegionY: (Float, Float) -> Unit,
    onEditBox: () -> Unit, onDeleteBox: () -> Unit,
    onDragStart: (Long, Float) -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
    onWheelClick: (TurntableWithSegments) -> Unit, onWheelDelete: (TurntableWithSegments) -> Unit,
    viewModel: TurntableViewModel
) {
    val boxWheels = allWheels.filter { it.turntable.boxId == box.box.id }
    var expanded by remember { mutableStateOf(true) }

    val hasDragged = draggedWheelId != null && boxWheels.any { it.turntable.id == draggedWheelId }

    // background(shape) only clips the background, not children — allows drag overflow
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (hasDragged) 100f else 0f)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                onRegionY(pos.y, pos.y + coords.size.height)
            }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "▾" else "▸", fontSize = 16.sp)
            }
            Text(box.box.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${boxWheels.size}个", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onEditBox) { Text("编辑", fontSize = 13.sp) }
            TextButton(onClick = onDeleteBox) {
                Text("删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        }

        if (expanded && boxWheels.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                boxWheels.forEach { wheel ->
                    WheelRow(
                        wheel = wheel,
                        isDragging = draggedWheelId == wheel.turntable.id,
                        dragOffsetY = if (draggedWheelId == wheel.turntable.id) dragOffsetY else 0f,
                        onDragStart = { rootY -> onDragStart(wheel.turntable.id, rootY) },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onClick = { onWheelClick(wheel) },
                        onDelete = { onWheelDelete(wheel) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun WheelRow(
    wheel: TurntableWithSegments, isDragging: Boolean,
    dragOffsetY: Float,
    onDragStart: (Float) -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
    onClick: () -> Unit, onDelete: () -> Unit,
    viewModel: TurntableViewModel
) {
    val segText = wheel.segments.joinToString(", ") { "${it.name}(${it.weight})" }
    var showMoveMenu by remember { mutableStateOf(false) }
    val allBoxes by viewModel.boxes.collectAsState()
    var handleRootY by remember { mutableStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .zIndex(if (isDragging) 999f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragOffsetY
                    shadowElevation = 8f
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .onGloballyPositioned { coords -> handleRootY = coords.positionInRoot().y }
                    .pointerInput(wheel.turntable.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(handleRootY) },
                            onDrag = { change, dragAmount ->
                                change.consume(); onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            ) {
                Text("⋮⋮", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(wheel.turntable.name, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(segText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (allBoxes.isNotEmpty()) {
                Box {
                    TextButton(onClick = { showMoveMenu = true }) { Text("移动", fontSize = 12.sp) }
                    DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                        if (wheel.turntable.boxId != null) {
                            DropdownMenuItem(text = { Text("移出盒子") }, onClick = {
                                viewModel.moveTurntableToBox(wheel.turntable.id, null); showMoveMenu = false
                            })
                        }
                        allBoxes.filter { it.box.id != wheel.turntable.boxId }.forEach { b ->
                            DropdownMenuItem(text = { Text(b.box.name) }, onClick = {
                                viewModel.moveTurntableToBox(wheel.turntable.id, b.box.id); showMoveMenu = false
                            })
                        }
                    }
                }
            }

            TextButton(onClick = onClick) { Text("编辑", fontSize = 12.sp) }
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
