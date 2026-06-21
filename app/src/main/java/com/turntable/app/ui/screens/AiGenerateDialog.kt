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
fun AiGenerateDialog(viewModel: TurntableViewModel) {
    val aiGenerating by viewModel.aiGenerating.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()

    var topic by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!aiGenerating) viewModel.closeAiGenerate() },
        title = {
            Text("AI 生成转盘", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!aiEnabled || aiConfig.apiKey.isBlank()) {
                    Text(
                        "请先在设置中开启 AI 功能并配置 API Key",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("描述你想要生成的转盘") },
                    placeholder = { Text("例如：帮我生成一个「今天吃什么」的转盘") },
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
                onClick = { viewModel.generateWheelByAi(topic) },
                enabled = !aiGenerating && topic.isNotBlank() && aiEnabled && aiConfig.apiKey.isNotBlank()
            ) {
                Text("生成")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeAiGenerate() }, enabled = !aiGenerating) {
                Text("取消")
            }
        }
    )
}
