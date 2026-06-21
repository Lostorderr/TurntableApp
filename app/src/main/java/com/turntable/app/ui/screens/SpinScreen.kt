package com.turntable.app.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.ui.components.WheelCanvas
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun SpinScreen(viewModel: TurntableViewModel) {
    val turntables by viewModel.turntables.collectAsState()
    val flows by viewModel.flows.collectAsState()
    val spinMode by viewModel.spinMode.collectAsState()
    val selectedWheelId by viewModel.selectedWheelId.collectAsState()
    val selectedFlowId by viewModel.selectedFlowId.collectAsState()
    val flowPreview by viewModel.selectedFlowPreview.collectAsState()
    val spinResult by viewModel.spinResult.collectAsState()
    val isSpinning by viewModel.isSpinning.collectAsState()
    val flowSession by viewModel.flowSession.collectAsState()
    val currentStageName by viewModel.currentStageName.collectAsState()
    val autoFlow by viewModel.autoFlow.collectAsState()
    val autoFlowDelay by viewModel.autoFlowDelay.collectAsState()
    val isReSpin by viewModel.isReSpinFlow.collectAsState()

    val singleGuideDone by viewModel.singleGuideDone.collectAsState()
    val flowGuideDone by viewModel.flowGuideDone.collectAsState()

    Log.d("Turntable", "SpinScreen render")

    // Separate segment computations for each mode
    val singleSegments = remember(turntables, selectedWheelId, spinResult, isSpinning) {
        val result = spinResult
        if (isSpinning && spinMode == 0 && result != null) {
            turntables.find { it.turntable.id == result.turntableId }?.segments ?: emptyList()
        } else {
            turntables.find { it.turntable.id == selectedWheelId }?.segments ?: emptyList()
        }
    }

    val flowSegments = remember(turntables, flowSession, flows, spinResult, isSpinning, flowPreview) {
        val result = spinResult
        if (isSpinning && spinMode == 1 && result != null) {
            turntables.find { it.turntable.id == result.turntableId }?.segments ?: emptyList()
        } else {
            flowSession?.let { session ->
                val flow = flows.find { it.flow.id == session.flowId }
                val displayIdx = when {
                    session.status == 1 -> (session.currentStage - 1).coerceIn(0, (flow?.stages?.size ?: 1) - 1)
                    !isSpinning && session.currentStage > 0 -> session.currentStage - 1
                    else -> session.currentStage
                }
                val stage = flow?.stages?.getOrNull(displayIdx)
                turntables.find { it.turntable.id == stage?.turntableId }?.segments
            } ?: flowPreview?.stages?.firstOrNull()?.let { firstStage ->
                turntables.find { it.turntable.id == firstStage.turntableId }?.segments
            } ?: emptyList()
        }
    }

    val onSpinEnd: () -> Unit = {
        if (spinMode == 1) viewModel.commitFlowSpin()
        else viewModel.finishSpin()
    }

    // ---- Swipe state ----
    var isDragging by remember { mutableStateOf(false) }
    var rawDrag by remember { mutableFloatStateOf(0f) }

    // ---- Guide overlay state ----
    // 0:"select wheel"  1:hidden  2:"swipe hint"  3:single-mode done
    // 4:"select flow"  5:"flow preview hint"  6:flow-mode done
    var guideStep by remember {
        mutableStateOf(
            when {
                flowGuideDone -> 6
                singleGuideDone -> 3
                else -> 0
            }
        )
    }

    // SINGLE MODE GUIDE
    // 0→1: dismiss when user selects a wheel
    LaunchedEffect(selectedWheelId) {
        if (guideStep == 0 && selectedWheelId != null) {
            guideStep = 1
        }
    }
    // 1→2: after spin + 1s, show swipe hint (only in single mode)
    LaunchedEffect(spinResult, isSpinning) {
        if (guideStep == 1 && spinMode == 0 && spinResult != null && !isSpinning) {
            kotlinx.coroutines.delay(500)
            if (guideStep == 1) guideStep = 2
        }
    }
    // 2→4 / any→4: user enters flow mode
    LaunchedEffect(spinMode) {
        if (spinMode == 1 && flows.isNotEmpty()) {
            when {
                guideStep == 2 -> guideStep = 4
                guideStep in 0..3 -> guideStep = 4
                guideStep == 3 -> guideStep = 4  // single done but flow not done
            }
        }
    }
    // 4→5: flow selected
    LaunchedEffect(selectedFlowId) {
        if (guideStep == 4 && selectedFlowId != null) {
            guideStep = 5
        }
    }
    // 5→6: flow session started
    LaunchedEffect(flowSession) {
        if (guideStep == 5 && flowSession != null) {
            guideStep = 6
        }
    }

    // Mark guide complete when done
    LaunchedEffect(guideStep) {
        if (guideStep >= 3 && !singleGuideDone) viewModel.markSingleGuideDone()
        if (guideStep >= 6 && !flowGuideDone) viewModel.markFlowGuideDone()
    }

    val effectiveStep = when {
        guideStep == 0 && turntables.isNotEmpty() -> 0
        guideStep == 0 && turntables.isEmpty() -> -1
        // Step 2 only visible in single mode
        guideStep == 2 && spinMode != 0 -> 3
        // Step 4/5 only visible in flow mode
        guideStep in 4..5 && spinMode != 1 -> 6
        else -> guideStep
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // ===== Top: Flow stage indicator (flow mode only) =====
        if (spinMode == 1) {
            flowPreview?.let { preview ->
                val stages = preview.stages
                val sessionStage = flowSession?.currentStage ?: 0
                val centerIdx = when {
                    flowSession?.status == 1 -> (stages.size - 1).coerceAtLeast(0)
                    isReSpin -> (sessionStage - 1).coerceAtLeast(0)
                    isSpinning -> sessionStage.coerceIn(0, stages.size - 1)
                    sessionStage > 0 -> (sessionStage - 1).coerceIn(0, stages.size - 1)
                    else -> 0
                }

                val slideSpec = { size: Int ->
                    (slideInHorizontally { size } + fadeIn()) togetherWith
                        (slideOutHorizontally { -size } + fadeOut())
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Left slot
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val key = if (centerIdx > 0) stages[centerIdx - 1].stageName else "empty"
                        AnimatedContent(targetState = key, transitionSpec = { slideSpec(80) }, label = "left") { name ->
                            if (name != "empty") {
                                Text(name, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    // Center slot
                    Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                        val centerKey = stages[centerIdx].stageName
                        AnimatedContent(targetState = centerKey, transitionSpec = { slideSpec(80) }, label = "center") { name ->
                            Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary, maxLines = 2, textAlign = TextAlign.Center)
                        }
                    }

                    // Right slot
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val rightKey = if (centerIdx < stages.size - 1) stages[centerIdx + 1].stageName else "empty"
                        AnimatedContent(targetState = rightKey, transitionSpec = { slideSpec(80) }, label = "right") { name ->
                            if (name != "empty") {
                                Text(name, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        // ===== Middle: Wheel + result overlay =====
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 44.dp)
                .graphicsLayer { translationX = if (isDragging) rawDrag else 0f }
                .pointerInput(spinMode) {
                    var dragOffset = 0f
                    val threshold = 120f
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffset = 0f; isDragging = true },
                        onDragEnd = {
                            isDragging = false; rawDrag = 0f
                            if (dragOffset > threshold && spinMode == 1) {
                                viewModel.setSpinMode(0)
                                viewModel.toast("已切换至单转盘模式", "info")
                            } else if (dragOffset < -threshold && spinMode == 0) {
                                viewModel.setSpinMode(1)
                                viewModel.toast("已切换至流程模式", "info")
                            }
                        },
                        onHorizontalDrag = { _, amount ->
                            dragOffset += amount
                            rawDrag = dragOffset
                        },
                        onDragCancel = { isDragging = false; rawDrag = 0f }
                    )
                }
        ) {
            val segments = if (spinMode == 0) singleSegments else flowSegments
            val spinning = isSpinning
            WheelCanvas(
                segments = segments,
                isSpinning = spinning,
                targetResult = spinResult,
                onSpinEnd = onSpinEnd,
                modifier = Modifier.fillMaxSize()
            )

            // Result overlay - between top bar and wheel
            if (spinResult != null && !isSpinning) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        spinResult!!.segmentName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (spinResult!!.segmentDescription.isNotBlank()) {
                        Text(
                            spinResult!!.segmentDescription,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // ===== Bottom: Controls =====
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (spinMode == 0) {
                    SingleSpinControls(viewModel, selectedWheelId, isSpinning)
                } else {
                    FlowSpinControls(
                        viewModel = viewModel,
                        flowPreview = flowPreview,
                        session = flowSession,
                        isSpinning = isSpinning,
                        autoFlow = autoFlow,
                        autoFlowDelay = autoFlowDelay,
                        selectedFlowId = selectedFlowId
                    )
                }
            }
        }
    }

        // ===== Guide overlay =====
        // Step 0: select wheel hint with scrim
        if (effectiveStep == 0) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
                // Tip pointing to top selector
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 46.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("▲", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Text("点击这里", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("选取要抽取的转盘内容",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Step 2: swipe hint - no scrim so swipe works
        if (effectiveStep == 2) {
            val infiniteTransition = rememberInfiniteTransition(label = "arrows")
            val arrow1Alpha by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "a1"
            )
            val arrow2Alpha by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = 400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "a2"
            )
            val arrow3Alpha by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, delayMillis = 800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "a3"
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("<", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = arrow3Alpha))
                            Text("<", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = arrow2Alpha))
                            Text("<", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = arrow1Alpha))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("左滑进入流程模式",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("按阶段依次抽取多个转盘",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Step 4: flow mode - select flow
        if (effectiveStep == 4) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 46.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("▲", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Text("点击这里", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("选择要抽取的流程",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Step 5: highlight flow preview, dim rest, tap anywhere to dismiss
        if (effectiveStep == 5) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { guideStep = 6 }
            ) {
                // Flow preview area: kept bright
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color.Transparent)
                )
                // Card explaining the preview, positioned in dimmed area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .widthIn(max = 300.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("▲ 上方的流程预览", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text("每个阶段抽完后自动切换下一个转盘",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        // Step -1: no turntables
        if (effectiveStep == -1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("还没有转盘",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("创建转盘后即可开始抽取",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            viewModel.setTab(0)
                            viewModel.openNewWheelForm()
                            guideStep = -1
                        }) {
                            Text("+ 创建转盘", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleSpinControls(viewModel: TurntableViewModel, selectedWheelId: Long?, isSpinning: Boolean) {
    Button(
        onClick = { viewModel.singleSpin() },
        modifier = Modifier.fillMaxWidth(),
        enabled = selectedWheelId != null && !isSpinning
    ) {
        Text(if (isSpinning) "转动中…" else "开始抽取", fontSize = 18.sp)
    }
}

@Composable
private fun FlowSpinControls(
    viewModel: TurntableViewModel,
    flowPreview: com.turntable.app.data.model.FlowWithStages?,
    session: com.turntable.app.data.model.TurntableSessionEntity?,
    isSpinning: Boolean,
    autoFlow: Boolean,
    autoFlowDelay: Int,
    selectedFlowId: Long?
) {
    if (session == null) {
        Button(
            onClick = { viewModel.startFlowSession("user") },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedFlowId != null
        ) {
            Text("开始流程", fontSize = 18.sp)
        }
    } else {
        val sessionComplete = session.status == 1

        if (sessionComplete) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.reSpinCurrentStage() }, modifier = Modifier.weight(1f), enabled = !isSpinning) {
                    Text("重新旋转", fontSize = 14.sp)
                }
                Button(
                    onClick = { viewModel.resetFlowSession() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("重置", fontSize = 14.sp)
                }
            }
        } else if (!autoFlow) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.reSpinCurrentStage() }, modifier = Modifier.weight(1f), enabled = !isSpinning && session.currentStage > 0) {
                    Text("重新旋转", fontSize = 14.sp)
                }
                Button(onClick = { viewModel.flowSpin() }, modifier = Modifier.weight(1f), enabled = !isSpinning) {
                    Text(if (isSpinning) "转动中…" else "下一阶段", fontSize = 14.sp)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { }, modifier = Modifier.weight(1f), enabled = false) {
                    Text("自动进行中…", fontSize = 14.sp)
                }
            }
        }
    }

    if (selectedFlowId == null) {
        Text(
            "按阶段顺序依次抽取，每个阶段独立转动。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
