package com.turntable.app.ui.screens

import androidx.compose.foundation.clickable
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
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun FlowSettingsPage(viewModel: TurntableViewModel) {
    val autoFlow by viewModel.autoFlow.collectAsState()
    val autoFlowDelay by viewModel.autoFlowDelay.collectAsState()
    val flowHistory by viewModel.flowHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Auto-flow toggle card
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("自动进行流程", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("完成后自动跳转下一阶段", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoFlow, onCheckedChange = { viewModel.setAutoFlow(it) })
            }
        }

        // Interval settings
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("停留间隔", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("-5", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.setAutoFlowDelay(autoFlowDelay - 5) }.padding(8.dp))
                    Text("-1", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.setAutoFlowDelay(autoFlowDelay - 1) }.padding(8.dp))
                    Text("${autoFlowDelay}s", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 16.dp))
                    Text("+1", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.setAutoFlowDelay(autoFlowDelay + 1) }.padding(8.dp))
                    Text("+5", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.setAutoFlowDelay(autoFlowDelay + 5) }.padding(8.dp))
                }
            }
        }

        // Flow records
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("流程记录", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (flowHistory.isEmpty()) {
                    Text("暂无记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    flowHistory.forEach { (stageName, result) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("$stageName: ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(result.segmentName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
