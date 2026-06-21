package com.turntable.app.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.ui.screens.AiFlowGenerateDialog
import com.turntable.app.ui.screens.AiGenerateDialog
import com.turntable.app.ui.screens.FlowDetailScreen
import com.turntable.app.ui.screens.SelectorScreen
import com.turntable.app.ui.screens.SettingsScreen
import com.turntable.app.ui.screens.FlowManageScreen
import com.turntable.app.ui.screens.FlowSettingsPage
import com.turntable.app.ui.screens.FlowSettlementScreen
import com.turntable.app.ui.screens.SpinScreen
import com.turntable.app.ui.screens.WheelEditScreen
import com.turntable.app.ui.screens.WheelManageScreen
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun TurntableApp(viewModel: TurntableViewModel) {
    Log.d("Turntable", "TurntableApp render start")
    val currentTab by viewModel.currentTab.collectAsState()
    val flowDetailId by viewModel.flowDetailId.collectAsState()
    val showFlowSettings by viewModel.showFlowSettings.collectAsState()
    val showWheelForm by viewModel.showWheelForm.collectAsState()
    val editingWheelId by viewModel.editingWheelId.collectAsState()
    val toast by viewModel.toastMessage.collectAsState()

    // Auto-dismiss toast after 2 seconds
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearToast()
        }
    }

    val spinMode by viewModel.spinMode.collectAsState()
    val turntables by viewModel.turntables.collectAsState()
    val flows by viewModel.flows.collectAsState()
    val selectedWheelId by viewModel.selectedWheelId.collectAsState()
    val selectedFlowId by viewModel.selectedFlowId.collectAsState()
    val flowHistory by viewModel.flowHistory.collectAsState()
    val flowSession by viewModel.flowSession.collectAsState()

    val showSettings by viewModel.showSettings.collectAsState()
    val showSelector by viewModel.showSelector.collectAsState()
    val showAiGenerate by viewModel.showAiGenerate.collectAsState()
    val showAiFlowGenerate by viewModel.showAiFlowGenerate.collectAsState()
    val isFlowDetail = flowDetailId != null
    val isFlowSettings = showFlowSettings
    val isWheelForm = showWheelForm
    val isSettings = showSettings
    val isSelector = showSelector
    val isAiGenerate = showAiGenerate
    val isAiFlowGenerate = showAiFlowGenerate
    val showSettlement by viewModel.showSettlement.collectAsState()
    val isSettlement = showSettlement
    val isConfigMode = currentTab != 2
    val showBackButton = isConfigMode || isFlowDetail || isFlowSettings || isWheelForm || isSettings || isSelector || isAiGenerate || isAiFlowGenerate || isSettlement

    // System back button: go back through overlays, then to spin screen, then exit
    val canGoBack = showBackButton
    BackHandler(enabled = canGoBack) {
        when {
            isSelector -> viewModel.closeSelector()
            isSettings -> viewModel.closeSettings()
            isWheelForm -> viewModel.closeWheelForm()
            isAiGenerate -> viewModel.closeAiGenerate()
            isAiFlowGenerate -> viewModel.closeAiFlowGenerate()
            isSettlement -> viewModel.closeSettlement()
            isFlowSettings -> viewModel.closeFlowSettings()
            isFlowDetail -> viewModel.closeFlowDetail()
            isConfigMode -> viewModel.setTab(2)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton) {
                        TextButton(onClick = {
                            if (isSelector) viewModel.closeSelector()
                            else if (isSettings) viewModel.closeSettings()
                            else if (isWheelForm) viewModel.closeWheelForm()
                            else if (isFlowSettings) viewModel.closeFlowSettings()
                            else if (isFlowDetail) viewModel.closeFlowDetail()
                            else if (isAiGenerate) viewModel.closeAiGenerate()
                            else if (isAiFlowGenerate) viewModel.closeAiFlowGenerate()
                            else if (isSettlement) viewModel.closeSettlement()
                            else viewModel.setTab(2)
                        }) {
                            Text("← 返回", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        val title = when {
                            isSelector -> if (spinMode == 0) "选择转盘" else "选择流程"
                            isSettings -> "设置"
                            isWheelForm -> if (editingWheelId != null) "编辑转盘" else "新建转盘"
                            isFlowSettings -> "流程设置"
                            isFlowDetail -> "流程详情"
                            isAiGenerate -> "AI 生成转盘"
                            isAiFlowGenerate -> "AI 生成流程"
                            isSettlement -> "流程结算"
                            currentTab == 0 -> "转盘管理"
                            currentTab == 1 -> "流程管理"
                            else -> ""
                        }
                        Text(
                            title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelector) {
                            TextButton(onClick = {
                                viewModel.setTab(if (spinMode == 0) 0 else 1)
                                viewModel.closeSelector()
                            }) {
                                Text("编辑", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(80.dp))
                        }
                    } else {
                        // === Spin screen top bar ===

                        // Left: Open settings page
                        IconButton(onClick = { viewModel.openSettings() }) {
                            Text("⚙", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Center: Wheel/Flow selector → opens SelectorScreen
                        val selectorName = if (spinMode == 0) {
                            turntables.find { it.turntable.id == selectedWheelId }?.turntable?.name
                                ?: "选择转盘 ▾"
                        } else {
                            flows.find { it.flow.id == selectedFlowId }?.flow?.name
                                ?: "选择流程 ▾"
                        }
                        TextButton(
                            onClick = { viewModel.openSelector() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                selectorName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }

                        // Right: Flow history (only when in flow mode)
                        if (spinMode == 1) {
                            IconButton(onClick = { viewModel.openFlowSettings() }) {
                                Text("☰", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant
                )

                // Content
                if (isSelector) {
                    SelectorScreen(viewModel)
                } else if (isSettings) {
                    SettingsScreen(viewModel)
                } else if (isWheelForm) {
                    WheelEditScreen(viewModel)
                } else if (isAiGenerate) {
                    AiGenerateDialog(viewModel)
                } else if (isAiFlowGenerate) {
                    AiFlowGenerateDialog(viewModel)
                } else if (isSettlement) {
                    FlowSettlementScreen(viewModel)
                } else if (isFlowSettings) {
                    FlowSettingsPage(viewModel)
                } else if (isFlowDetail) {
                    FlowDetailScreen(flowId = flowDetailId!!, viewModel = viewModel)
                } else {
                    when (currentTab) {
                        0 -> WheelManageScreen(viewModel)
                        1 -> FlowManageScreen(viewModel)
                        2 -> SpinScreen(viewModel)
                    }
                }
            }

            // Toast (positioned below top bar to avoid covering selector)
            AnimatedContent(
                targetState = toast,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "toast"
            ) { state ->
                state?.let { (msg, type) ->
                    val (bg, fg) = when (type) {
                        "success" -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                        "error" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
                        else -> MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface
                    }
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = bg
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = fg
                        )
                    }
                }
            }
        }
    }
}
