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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun SettingsScreen(viewModel: TurntableViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val spinMode by viewModel.spinMode.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Theme ----
        Text("主题色调", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        var themeExpanded by remember { mutableStateOf(false) }
        val themeLabel = when (themeMode) {
            0 -> "跟随系统"
            1 -> "浅色模式"
            else -> "深色模式"
        }
        Box {
            OutlinedButton(
                onClick = { themeExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(themeLabel, fontSize = 15.sp, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface)
                Text(" ▾", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(
                expanded = themeExpanded,
                onDismissRequest = { themeExpanded = false }
            ) {
                listOf("跟随系统", "浅色模式", "深色模式").forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (themeMode == i) "● $label" else "○ $label",
                                fontWeight = if (themeMode == i) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp
                            )
                        },
                        onClick = {
                            viewModel.setTheme(i)
                            themeExpanded = false
                        }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ---- Spin mode ----
        Text("抽取模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { viewModel.setSpinMode(0) })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (spinMode == 0) "● 单转盘" else "○ 单转盘",
                        fontWeight = if (spinMode == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { viewModel.setSpinMode(1) })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (spinMode == 1) "● 流程" else "○ 流程",
                        fontWeight = if (spinMode == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ---- Manage ----
        Text("管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { viewModel.setTab(0); viewModel.closeSettings() })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("转盘管理", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("→", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { viewModel.setTab(1); viewModel.closeSettings() })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("流程管理", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("→", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ---- AI Settings ----
        Text("AI 设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var apiKey by remember(aiConfig) { mutableStateOf(aiConfig.apiKey) }
                var baseUrl by remember(aiConfig) { mutableStateOf(aiConfig.baseUrl) }
                var model by remember(aiConfig) { mutableStateOf(aiConfig.model) }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    placeholder = { Text("https://api.deepseek.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称") },
                    placeholder = { Text("deepseek-chat") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                Button(
                    onClick = {
                        viewModel.saveAiConfig(
                            enabled = true,
                            apiKey = apiKey,
                            baseUrl = baseUrl.ifBlank { "https://api.deepseek.com" },
                            model = model.ifBlank { "deepseek-chat" }
                        )
                        viewModel.toast("AI 配置已保存", "success")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存 AI 配置")
                }
            }
        }
    }
}
