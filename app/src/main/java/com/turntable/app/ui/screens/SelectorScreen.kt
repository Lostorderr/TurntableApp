package com.turntable.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.data.model.FlowWithStages
import com.turntable.app.data.model.TurntableWithSegments
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun SelectorScreen(viewModel: TurntableViewModel) {
    val spinMode by viewModel.spinMode.collectAsState()
    val turntables by viewModel.turntables.collectAsState()
    val flows by viewModel.flows.collectAsState()
    val selectedWheelId by viewModel.selectedWheelId.collectAsState()
    val selectedFlowId by viewModel.selectedFlowId.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        if (spinMode == 0) {
            if (turntables.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有转盘", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            viewModel.openNewWheelForm()
                            viewModel.setTab(0)
                            viewModel.closeSelector()
                        }) { Text("+ 创建转盘") }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(turntables, key = { it.turntable.id }) { w ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (w.turntable.id == selectedWheelId)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onClick = {
                                viewModel.selectWheel(w.turntable.id)
                                viewModel.closeSelector()
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(w.turntable.name, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                                val preview = w.segments.joinToString(" · ") { it.name }
                                Text(preview, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            if (flows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有流程", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(flows, key = { it.flow.id }) { f ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (f.flow.id == selectedFlowId)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onClick = {
                                viewModel.selectFlow(f.flow.id)
                                viewModel.closeSelector()
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(f.flow.name, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                                val preview = f.stages.joinToString(" → ") { it.stageName }
                                Text(preview, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // Flow hint at bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Text(
                    "流程将多个转盘串连，完成后自动切换下一个转盘",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
