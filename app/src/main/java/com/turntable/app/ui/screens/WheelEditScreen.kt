package com.turntable.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.data.model.TurntableSegmentEntity
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun WheelEditScreen(viewModel: TurntableViewModel) {
    val turntables by viewModel.turntables.collectAsState()
    val editingId by viewModel.editingWheelId.collectAsState()

    var formName by remember { mutableStateOf("") }
    var formSegments by remember { mutableStateOf(listOf(SegmentFormData("", "", 1))) }
    var dirty by remember { mutableStateOf(false) }
    LaunchedEffect(editingId) {
        if (editingId != null) {
            val w = turntables.find { it.turntable.id == editingId }
            if (w != null) {
                formName = w.turntable.name
                formSegments = w.segments.map { SegmentFormData(it.name, it.description, it.weight) }
            }
        } else {
            formName = ""
            formSegments = listOf(SegmentFormData("", "", 1))
        }
    }

    // Auto-save every 5 seconds, only when form has changed
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            if (dirty && formName.isNotBlank() && formSegments.any { it.name.isNotBlank() }) {
                val segs = formSegments.filter { it.name.isNotBlank() }.map {
                    TurntableSegmentEntity(turntableId = 0, name = it.name, description = it.desc, weight = it.weight)
                }
                viewModel.autoSaveWheel(formName, "", segs)
                dirty = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = formName,
            onValueChange = { formName = it; dirty = true },
            label = { Text("转盘名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("选项列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        formSegments.forEachIndexed { i, seg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = seg.name,
                            onValueChange = { v ->
                                formSegments = formSegments.toMutableList().apply {
                                    set(i, seg.copy(name = v))
                                    dirty = true
                                }
                            },
                            label = { Text("名称") },
                            modifier = Modifier.weight(3f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = seg.weight.toString(),
                            onValueChange = { v ->
                                formSegments = formSegments.toMutableList().apply {
                                    set(i, seg.copy(weight = v.toIntOrNull() ?: 1))
                                    dirty = true
                                }
                            },
                            label = { Text("权重") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        if (formSegments.size > 1) {
                            TextButton(onClick = {
                                formSegments = formSegments.toMutableList().apply { removeAt(i) }
                            }) {
                                Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = seg.desc,
                        onValueChange = { v ->
                            formSegments = formSegments.toMutableList().apply {
                                set(i, seg.copy(desc = v))
                                dirty = true
                            }
                        },
                        label = { Text("描述（选填）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { formSegments = formSegments + SegmentFormData("", "", 1) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ 添加选项")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val segs = formSegments.filter { it.name.isNotBlank() }.map {
                    TurntableSegmentEntity(turntableId = 0, name = it.name, description = it.desc, weight = it.weight)
                }
                if (segs.isEmpty()) {
                    viewModel.toast("至少添加一个选项", "error")
                    return@Button
                }
                if (formName.isBlank()) {
                    viewModel.toast("请输入转盘名称", "error")
                    return@Button
                }
                viewModel.saveWheel(formName, "", segs)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存", fontSize = 16.sp)
        }
    }
}
