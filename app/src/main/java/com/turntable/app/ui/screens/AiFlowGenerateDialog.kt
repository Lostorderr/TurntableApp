package com.turntable.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun AiFlowGenerateDialog(viewModel: TurntableViewModel) {
    val aiGenerating by viewModel.aiGenerating.collectAsState()

    var topic by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!aiGenerating) viewModel.closeAiFlowGenerate() },
        title = { Text("AI 生成流程", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("描述你想要生成的流程") },
                    placeholder = { Text("例如：帮我生成一个「周末决策」的流程") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !aiGenerating,
                    minLines = 2
                )

                if (aiGenerating) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("AI 正在生成...", fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.generateFlowByAi(topic) },
                enabled = !aiGenerating && topic.isNotBlank()
            ) {
                Text("生成")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeAiFlowGenerate() }, enabled = !aiGenerating) {
                Text("取消")
            }
        }
    )
}
