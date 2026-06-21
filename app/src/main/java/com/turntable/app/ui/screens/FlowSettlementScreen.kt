package com.turntable.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turntable.app.viewmodel.TurntableViewModel

@Composable
fun FlowSettlementScreen(viewModel: TurntableViewModel) {
    val flowHistory by viewModel.flowHistory.collectAsState()
    val flowPreview by viewModel.selectedFlowPreview.collectAsState()
    val summary by viewModel.settlementSummary.collectAsState()
    val aiSummarizing by viewModel.aiSummarizing.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("流程结算", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        flowPreview?.let {
            Text(it.flow.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            if (it.flow.description.isNotBlank()) Text(it.flow.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Text("抽取记录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp)) {
                flowHistory.forEachIndexed { index, (stage, result) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                        Text(stage, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("→", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(result.segmentName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (result.segmentDescription.isNotBlank()) Text("    ${result.segmentDescription}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp))
                }
            }
        }
        if (flowHistory.isNotEmpty()) {
            Text("总结", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
                    Text(text = parseBold(summary!!), modifier = Modifier.padding(16.dp), fontSize = 15.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Button(onClick = { viewModel.summarizeFlow() }, modifier = Modifier.fillMaxWidth(), enabled = !aiSummarizing && summary == null) {
                if (aiSummarizing) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(modifier = Modifier.width(8.dp)); Text("正在总结…") }
                else Text("总结", fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun parseBold(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val b = text.indexOf("**", i)
        if (b == -1) { append(text.substring(i)); break }
        append(text.substring(i, b))
        val e = text.indexOf("**", b + 2)
        if (e == -1) { append(text.substring(b)); break }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(b + 2, e)) }
        i = e + 2
    }
}
