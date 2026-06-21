package com.turntable.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.data.model.FlowWithStages
import com.turntable.app.data.model.TurntableFlowStageEntity
import com.turntable.app.viewmodel.TurntableViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowManageScreen(viewModel: TurntableViewModel) {
    val flows by viewModel.flows.collectAsState()
    val turntables by viewModel.turntables.collectAsState()
    val showForm by viewModel.showFlowForm.collectAsState()

    var formName by remember { mutableStateOf("") }
    var formDesc by remember { mutableStateOf("") }
    var formStages by remember { mutableStateOf(listOf(StageFormData("", null))) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                formName = ""; formDesc = ""
                formStages = listOf(StageFormData("", null))
                viewModel.openNewFlowForm()
            }) { Text("+ 新建流程") }
            OutlinedButton(onClick = { viewModel.openAiFlowGenerateOrSettings() }) {
                Text("AI 生成")
            }
        }

        if (flows.isEmpty()) {
            val hasWheels = turntables.isNotEmpty()
            EmptyState(
                icon = "🔗",
                title = "还没有流程",
                desc = if (hasWheels) "你已经有了转盘，可以创建流程了！"
                else "需要先创建至少一个转盘才能组建流程。\n请先切换到「转盘管理」创建转盘。"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(flows, key = { it.flow.id }) { flow ->
                    var showDelDlg by remember { mutableStateOf(false) }
                    var showDelConfirm by remember { mutableStateOf(false) }
                    FlowCard(flow = flow, onClick = { viewModel.openFlowDetail(flow.flow.id) }, onDelete = { showDelDlg = true })
                    if (showDelDlg) AlertDialog(
                        onDismissRequest = { showDelDlg = false }, title = { Text("删除流程") },
                        text = { Text("是否将流程「${flow.flow.name}」中的转盘一同删除？") },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showDelDlg = false; viewModel.deleteFlow(flow.flow.id) }) { Text("仅删流程") }
                                Button(onClick = { showDelDlg = false; showDelConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("一并删除") }
                            }
                        },
                        dismissButton = { TextButton(onClick = { showDelDlg = false }) { Text("取消") } })
                    if (showDelConfirm) AlertDialog(
                        onDismissRequest = { showDelConfirm = false }, title = { Text("二次确认") },
                        text = { Text("确定要删除流程「${flow.flow.name}」及其所有关联转盘吗？此操作不可撤销。") },
                        confirmButton = { TextButton(onClick = { showDelConfirm = false }) { Text("取消") } },
                        dismissButton = { Button(onClick = { showDelConfirm = false; viewModel.deleteFlow(flow.flow.id, deleteTurntables = true) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确定删除") } })
                }
            }
        }
    }

    if (showForm) {
        val wheelOptions = turntables
        AlertDialog(
            onDismissRequest = { viewModel.closeFlowForm() },
            title = { Text("新建流程") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = formName,
                        onValueChange = { formName = it },
                        label = { Text("流程名称") },
                        placeholder = { Text("如：咒术回战角色卡") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = formDesc,
                        onValueChange = { formDesc = it },
                        label = { Text("描述") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("阶段列表", style = MaterialTheme.typography.labelMedium)
                    formStages.forEachIndexed { i, stage ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = stage.stageName,
                                onValueChange = { v ->
                                    formStages = formStages.toMutableList().apply {
                                        set(i, stage.copy(stageName = v))
                                    }
                                },
                                placeholder = { Text("阶段名") },
                                modifier = Modifier.weight(2f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            // Wheel selector
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(2f)) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val selName = wheelOptions.find { it.turntable.id == stage.turntableId }?.turntable?.name ?: "选择转盘"
                                    Text(selName, fontSize = 12.sp, maxLines = 1)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    wheelOptions.forEach { w ->
                                        DropdownMenuItem(
                                            text = { Text(w.turntable.name) },
                                            onClick = {
                                                formStages = formStages.toMutableList().apply {
                                                    set(i, stage.copy(turntableId = w.turntable.id))
                                                }
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = {
                                formStages = formStages.toMutableList().apply { removeAt(i) }
                            }) { Text("×", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    TextButton(onClick = {
                        formStages = formStages + StageFormData("", null)
                    }) { Text("+ 添加阶段") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val stages = formStages.mapNotNull { s ->
                        s.turntableId?.let {
                            TurntableFlowStageEntity(
                                flowId = 0, turntableId = it,
                                stageName = s.stageName.ifBlank { "阶段${formStages.indexOf(s) + 1}" }
                            )
                        }
                    }
                    if (stages.isEmpty()) {
                        viewModel.toast("至少添加一个有效阶段", "error")
                        return@TextButton
                    }
                    viewModel.saveFlow(formName, formDesc, stages)
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeFlowForm() }) { Text("取消") }
            }
        )
    }
}

data class StageFormData(val stageName: String, val turntableId: Long?)

@Composable
fun FlowCard(flow: FlowWithStages, onClick: () -> Unit, onDelete: () -> Unit) {
    val stagesText = flow.stages.joinToString(" → ") { it.stageName }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(flow.flow.name, fontWeight = FontWeight.Bold)
                if (flow.flow.description.isNotBlank())
                    Text(flow.flow.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp)
                Text(stagesText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp)
            }
            Column {
                TextButton(onClick = onClick) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
