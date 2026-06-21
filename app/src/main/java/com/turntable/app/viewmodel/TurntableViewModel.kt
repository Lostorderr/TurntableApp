package com.turntable.app.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turntable.app.data.model.*
import com.turntable.app.data.network.AiService
import com.turntable.app.data.network.FlowData
import com.turntable.app.data.preferences.AiConfig
import com.turntable.app.data.preferences.AiPreferences
import com.turntable.app.data.repository.TurntableDatabase
import com.turntable.app.data.repository.TurntableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TurntableViewModel(application: Application) : AndroidViewModel(application) {
    init {
        Log.d("Turntable", "ViewModel init start")
    }

    private val repository = TurntableRepository(TurntableDatabase.getInstance(application).dao())
    private val prefs = application.getSharedPreferences("turntable_guide", 0)
    private val aiPreferences = AiPreferences(application)

    init {
        Log.d("Turntable", "ViewModel init done, db ready")
        viewModelScope.launch {
            val dataVersion = prefs.getInt("data_version", 0)
            if (repository.getTurntableCount() == 0) {
                createDefaultTurntables()
            }
            if (repository.getFlowCount() == 0) {
                createDefaultFlows()
            } else if (dataVersion < 3) {
                refreshDefaultFlows()
            }
            prefs.edit().putInt("data_version", 3).apply()
        }
    }

    // ---- Persistent guide state ----
    private val _singleGuideDone = MutableStateFlow(prefs.getBoolean("single_guide_done", false))
    val singleGuideDone: StateFlow<Boolean> = _singleGuideDone.asStateFlow()

    private val _flowGuideDone = MutableStateFlow(prefs.getBoolean("flow_guide_done", false))
    val flowGuideDone: StateFlow<Boolean> = _flowGuideDone.asStateFlow()

    fun markSingleGuideDone() {
        _singleGuideDone.value = true
        prefs.edit().putBoolean("single_guide_done", true).apply()
    }

    fun markFlowGuideDone() {
        _flowGuideDone.value = true
        prefs.edit().putBoolean("flow_guide_done", true).apply()
    }

    // ---- UI State ----

    private val _currentTab = MutableStateFlow(2)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Theme: 0=auto, 1=light, 2=dark
    private val _themeMode = MutableStateFlow(0)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setTheme(mode: Int) { _themeMode.value = mode }

    // Settings page
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    fun openSettings() { _showSettings.value = true }
    fun closeSettings() { _showSettings.value = false }

    // AI configuration
    private val _aiEnabled = MutableStateFlow(false)
    val aiEnabled: StateFlow<Boolean> = _aiEnabled.asStateFlow()

    private val _aiGenerating = MutableStateFlow(false)
    val aiGenerating: StateFlow<Boolean> = _aiGenerating.asStateFlow()

    private val _aiConfig = MutableStateFlow(AiConfig())
    val aiConfig: StateFlow<AiConfig> = _aiConfig.asStateFlow()

    private val _showAiGenerate = MutableStateFlow(false)
    val showAiGenerate: StateFlow<Boolean> = _showAiGenerate.asStateFlow()

    fun loadAiConfig() { _aiConfig.value = aiPreferences.load(); _aiEnabled.value = _aiConfig.value.enabled }

    init {
        loadAiConfig()
    }

    fun saveAiConfig(enabled: Boolean, apiKey: String, baseUrl: String, model: String) {
        val config = AiConfig(enabled, apiKey, baseUrl, model)
        aiPreferences.save(config)
        _aiConfig.value = config
        _aiEnabled.value = enabled
    }

    fun openAiGenerate() { _showAiGenerate.value = true }
    fun closeAiGenerate() { _showAiGenerate.value = false }

    fun openAiGenerateOrSettings() {
        if (_aiConfig.value.apiKey.isNotBlank()) {
            _showAiGenerate.value = true
        } else {
            _showSettings.value = true
            toast("请先配置 API Key、Base URL 和模型", "error")
        }
    }

    // Flow AI generation
    private val _showAiFlowGenerate = MutableStateFlow(false)
    val showAiFlowGenerate: StateFlow<Boolean> = _showAiFlowGenerate.asStateFlow()

    fun openAiFlowGenerate() { _showAiFlowGenerate.value = true }
    fun closeAiFlowGenerate() { _showAiFlowGenerate.value = false }

    // Flow settlement
    private val _showSettlement = MutableStateFlow(false)
    val showSettlement: StateFlow<Boolean> = _showSettlement.asStateFlow()
    private val _settlementSummary = MutableStateFlow<String?>(null)
    val settlementSummary: StateFlow<String?> = _settlementSummary.asStateFlow()
    private val _aiSummarizing = MutableStateFlow(false)
    val aiSummarizing: StateFlow<Boolean> = _aiSummarizing.asStateFlow()

    fun openSettlement() { _showSettlement.value = true }
    fun closeSettlement() { _showSettlement.value = false; _settlementSummary.value = null }

    fun summarizeFlow() {
        val config = _aiConfig.value
        if (config.apiKey.isBlank()) { _showSettlement.value = false; _showSettings.value = true; toast("请先配置 API Key", "error"); return }
        val history = _flowHistory.value; if (history.isEmpty()) return
        val flowName = _selectedFlowPreview.value?.flow?.name ?: "流程"
        val historyText = history.joinToString("\n") { (stage, result) ->
            "【$stage】→ ${result.segmentName}${if (result.segmentDescription.isNotBlank()) "（${result.segmentDescription}）" else ""}"
        }
        viewModelScope.launch { _aiSummarizing.value = true
            try { withContext(Dispatchers.IO) { AiService(config.apiKey, config.baseUrl, config.model).summarizeFlow(flowName, historyText) }.fold(onSuccess = { _settlementSummary.value = it }, onFailure = { toast("AI 总结失败: ${it.message}", "error") }) }
            finally { _aiSummarizing.value = false } }
    }

    // AI flow editing
    private val _showAiEditFlow = MutableStateFlow(false)
    val showAiEditFlow: StateFlow<Boolean> = _showAiEditFlow.asStateFlow()
    fun openAiEditFlow() { _showAiEditFlow.value = true }
    fun closeAiEditFlow() { _showAiEditFlow.value = false }

    fun editFlowByAi(instructions: String) {
        val config = _aiConfig.value
        if (config.apiKey.isBlank()) { _showAiEditFlow.value = false; _showSettings.value = true; toast("请先配置 API Key", "error"); return }
        val detail = _flowDetail.value ?: return
        val turntableMap = turntables.value.associate { it.turntable.id to it.turntable.name }
        val sortedStages = detail.stages.sortedBy { it.stageOrder }
        val currentJson = buildString {
            appendLine("{"); appendLine("  \"name\": \"${detail.flow.name}\","); appendLine("  \"description\": \"${detail.flow.description}\",")
            appendLine("  \"stages\": [")
            sortedStages.forEachIndexed { i, s -> appendLine("    { \"stageName\": \"${s.stageName}\", \"turntableName\": \"${turntableMap[s.turntableId] ?: "未知"}\" }${if (i < sortedStages.size - 1) "," else ""}") }
            appendLine("  ]"); append("}")
        }
        viewModelScope.launch { _aiGenerating.value = true
            try {
                val flowId = detail.flow.id
                withContext(Dispatchers.IO) { AiService(config.apiKey, config.baseUrl, config.model).editFlow(currentJson, instructions) }.fold(
                    onSuccess = { fd ->
                        repository.updateFlowInfo(flowId, fd.name, fd.description)
                        val ttByName = turntableMap.entries.associate { (id, name) -> name to id }
                        val newStages = fd.stages.mapIndexed { idx, sd -> TurntableFlowStageEntity(flowId = flowId, turntableId = ttByName[sd.turntable.name] ?: (sortedStages.getOrNull(idx)?.turntableId ?: turntableMap.keys.firstOrNull() ?: 0L), stageName = sd.stageName, stageOrder = idx) }
                        repository.replaceFlowStages(flowId, newStages)
                        _flowDetail.value = repository.getFlow(flowId)
                        _showAiEditFlow.value = false; toast("AI 已编辑流程", "success")
                    }, onFailure = { toast("AI 编辑失败: ${it.message}", "error") })
            } finally { _aiGenerating.value = false } }
    }

    fun openAiFlowGenerateOrSettings() {
        if (_aiConfig.value.apiKey.isNotBlank()) {
            _showAiFlowGenerate.value = true
        } else {
            _showSettings.value = true
            toast("请先配置 API Key、Base URL 和模型", "error")
        }
    }

    fun generateFlowByAi(topic: String) {
        val config = _aiConfig.value
        if (config.apiKey.isBlank()) {
            toast("请先在设置中配置 API Key", "error")
            return
        }
        viewModelScope.launch {
            _aiGenerating.value = true
            try {
                val aiResult = withContext(Dispatchers.IO) {
                    val service = AiService(config.apiKey, config.baseUrl, config.model)
                    service.generateFlow(topic)
                }
                aiResult.fold(
                    onSuccess = { flowData ->
                        val boxId = repository.createBox(flowData.name, "AI 自动生成")
                        val stageEntities = mutableListOf<TurntableFlowStageEntity>()
                        flowData.stages.forEachIndexed { idx, stage ->
                            val turntableId = repository.createTurntable(
                                stage.turntable.name,
                                stage.turntable.description,
                                stage.turntable.segments.map { seg ->
                                    TurntableSegmentEntity(
                                        turntableId = 0,
                                        name = seg.name,
                                        description = seg.description,
                                        weight = seg.weight.coerceIn(1, 100)
                                    )
                                },
                                boxId = boxId
                            )
                            stageEntities.add(
                                TurntableFlowStageEntity(
                                    flowId = 0,
                                    turntableId = turntableId,
                                    stageName = stage.stageName,
                                    stageOrder = idx
                                )
                            )
                        }
                        repository.createFlow(flowData.name, flowData.description, stageEntities)
                        _showAiFlowGenerate.value = false
                        toast("AI 流程已生成: ${flowData.name}（含${flowData.stages.size}个转盘，已归入盒子）", "success")
                    },
                    onFailure = { e ->
                        toast("AI 流程生成失败: ${e.message}", "error")
                    }
                )
            } finally {
                _aiGenerating.value = false
            }
        }
    }

    fun generateWheelByAi(topic: String) {
        val config = _aiConfig.value
        if (config.apiKey.isBlank()) {
            toast("请先在设置中配置 API Key", "error")
            return
        }
        viewModelScope.launch {
            _aiGenerating.value = true
            try {
                val aiResult = withContext(Dispatchers.IO) {
                    val service = AiService(config.apiKey, config.baseUrl, config.model)
                    service.generateWheel(topic)
                }
                aiResult.fold(
                    onSuccess = { data ->
                        val segments = data.segments.map { seg ->
                            TurntableSegmentEntity(
                                turntableId = 0,
                                name = seg.name,
                                description = seg.description,
                                weight = seg.weight.coerceIn(1, 100)
                            )
                        }
                        repository.createTurntable(data.name, data.description, segments)
                        _showAiGenerate.value = false
                        toast("AI 转盘已生成: ${data.name}", "success")
                    },
                    onFailure = { e ->
                        toast("AI 生成失败: ${e.message}", "error")
                    }
                )
            } finally {
                _aiGenerating.value = false
            }
        }
    }

    // Selector page
    private val _showSelector = MutableStateFlow(false)
    val showSelector: StateFlow<Boolean> = _showSelector.asStateFlow()

    fun openSelector() { _showSelector.value = true }
    fun closeSelector() { _showSelector.value = false }

    // Turntable lists
    val turntables: StateFlow<List<TurntableWithSegments>> =
        repository.getAllTurntables().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flows: StateFlow<List<FlowWithStages>> =
        repository.getAllFlows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Box state
    val boxes: StateFlow<List<BoxWithTurntables>> =
        repository.getAllBoxes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showBoxDialog = MutableStateFlow(false)
    val showBoxDialog: StateFlow<Boolean> = _showBoxDialog.asStateFlow()

    private val _editingBoxId = MutableStateFlow<Long?>(null)
    val editingBoxId: StateFlow<Long?> = _editingBoxId.asStateFlow()

    fun openNewBoxDialog() { _editingBoxId.value = null; _showBoxDialog.value = true }
    fun openEditBoxDialog(id: Long) { _editingBoxId.value = id; _showBoxDialog.value = true }
    fun closeBoxDialog() { _showBoxDialog.value = false }

    fun saveBox(name: String) {
        viewModelScope.launch {
            val editingId = _editingBoxId.value
            if (editingId != null) {
                repository.updateBox(editingId, name, "")
                toast("盒子已更新", "success")
            } else {
                repository.createBox(name)
                toast("盒子已创建", "success")
            }
            _showBoxDialog.value = false
        }
    }

    fun deleteBox(id: Long) {
        viewModelScope.launch {
            repository.deleteBox(id)
            toast("盒子已删除（转盘已移出）", "success")
        }
    }

    fun moveTurntableToBox(turntableId: Long, boxId: Long?) {
        viewModelScope.launch {
            repository.moveTurntableToBox(turntableId, boxId)
        }
    }

    // Modal state
    private val _showWheelForm = MutableStateFlow(false)
    val showWheelForm: StateFlow<Boolean> = _showWheelForm.asStateFlow()

    private val _editingWheelId = MutableStateFlow<Long?>(null)
    val editingWheelId: StateFlow<Long?> = _editingWheelId.asStateFlow()

    private val _showFlowForm = MutableStateFlow(false)
    val showFlowForm: StateFlow<Boolean> = _showFlowForm.asStateFlow()

    // Spin state
    private val _spinMode = MutableStateFlow(0)
    val spinMode: StateFlow<Int> = _spinMode.asStateFlow()

    private val _selectedWheelId = MutableStateFlow<Long?>(null)
    val selectedWheelId: StateFlow<Long?> = _selectedWheelId.asStateFlow()

    private val _selectedFlowId = MutableStateFlow<Long?>(null)
    val selectedFlowId: StateFlow<Long?> = _selectedFlowId.asStateFlow()

    private val _selectedFlowPreview = MutableStateFlow<FlowWithStages?>(null)
    val selectedFlowPreview: StateFlow<FlowWithStages?> = _selectedFlowPreview.asStateFlow()

    // Spin result
    private val _spinResult = MutableStateFlow<SpinResult?>(null)
    val spinResult: StateFlow<SpinResult?> = _spinResult.asStateFlow()

    private val _isSpinning = MutableStateFlow(false)
    val isSpinning: StateFlow<Boolean> = _isSpinning.asStateFlow()

    // Auto-flow toggle
    private val _autoFlow = MutableStateFlow(false)
    val autoFlow: StateFlow<Boolean> = _autoFlow.asStateFlow()

    private val _autoFlowDelay = MutableStateFlow(1)
    val autoFlowDelay: StateFlow<Int> = _autoFlowDelay.asStateFlow()

    // Flow settings page
    private val _showFlowSettings = MutableStateFlow(false)
    val showFlowSettings: StateFlow<Boolean> = _showFlowSettings.asStateFlow()

    fun openFlowSettings() { _showFlowSettings.value = true }
    fun closeFlowSettings() { _showFlowSettings.value = false }

    private val _isReSpin = MutableStateFlow(false)
    val isReSpinFlow: StateFlow<Boolean> = _isReSpin.asStateFlow()

    // Flow session
    private val _flowSession = MutableStateFlow<TurntableSessionEntity?>(null)
    val flowSession: StateFlow<TurntableSessionEntity?> = _flowSession.asStateFlow()

    private val _flowHistory = MutableStateFlow<List<Pair<String, SpinResult>>>(emptyList())
    val flowHistory: StateFlow<List<Pair<String, SpinResult>>> = _flowHistory.asStateFlow()

    private val _currentStageName = MutableStateFlow("")
    val currentStageName: StateFlow<String> = _currentStageName.asStateFlow()

    // Flow detail
    private val _flowDetailId = MutableStateFlow<Long?>(null)
    val flowDetailId: StateFlow<Long?> = _flowDetailId.asStateFlow()

    private val _flowDetail = MutableStateFlow<FlowWithStages?>(null)
    val flowDetail: StateFlow<FlowWithStages?> = _flowDetail.asStateFlow()

    // Toast
    private val _toastMessage = MutableStateFlow<Pair<String, String>?>(null)
    val toastMessage: StateFlow<Pair<String, String>?> = _toastMessage.asStateFlow()

    // ---- Actions ----

    fun setTab(tab: Int) { _currentTab.value = tab }

    fun setAutoFlow(enabled: Boolean) {
        _autoFlow.value = enabled
        // If enabling during an active session, kick off next spin immediately
        if (enabled) {
            val session = _flowSession.value
            if (session != null && session.status == 0 && !_isSpinning.value) {
                flowSpin()
            }
        }
    }

    fun setAutoFlowDelay(seconds: Int) { _autoFlowDelay.value = seconds.coerceIn(0, 30) }

    fun finishSpin() { _isSpinning.value = false }

    // Turntable
    fun openNewWheelForm() { _editingWheelId.value = null; _showWheelForm.value = true }
    fun openEditWheelForm(id: Long) { _editingWheelId.value = id; _showWheelForm.value = true }
    fun closeWheelForm() { _showWheelForm.value = false }

    fun saveWheel(name: String, desc: String, segments: List<TurntableSegmentEntity>) {
        viewModelScope.launch {
            try {
                val editingId = _editingWheelId.value
                if (editingId != null) {
                    repository.updateTurntable(editingId, name, desc, segments)
                    toast("转盘已更新", "success")
                } else {
                    repository.createTurntable(name, desc, segments)
                    toast("转盘创建成功", "success")
                }
                _showWheelForm.value = false
            } catch (e: Exception) {
                toast("保存失败: ${e.message}", "error")
            }
        }
    }

    fun autoSaveWheel(name: String, desc: String, segments: List<TurntableSegmentEntity>) {
        viewModelScope.launch {
            try {
                val editingId = _editingWheelId.value
                if (editingId != null) {
                    repository.updateTurntable(editingId, name, desc, segments)
                } else if (name.isNotBlank() && segments.any { it.name.isNotBlank() }) {
                    val newId = repository.createTurntable(name, desc, segments)
                    _editingWheelId.value = newId
                }
            } catch (_: Exception) { }
        }
    }

    fun deleteWheel(id: Long) {
        viewModelScope.launch {
            repository.deleteTurntable(id)
            toast("已删除", "success")
        }
    }

    private suspend fun createDefaultTurntables() {
        repository.createTurntable("今天吃什么", "", listOf(
            TurntableSegmentEntity(turntableId = 0, name = "火锅", weight = 25),
            TurntableSegmentEntity(turntableId = 0, name = "烧烤", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "日料", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "川菜", weight = 20),
            TurntableSegmentEntity(turntableId = 0, name = "麻辣烫", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "家常菜", weight = 10),
        ))
        repository.createTurntable("去哪家电影院", "", listOf(
            TurntableSegmentEntity(turntableId = 0, name = "万达影城", weight = 25),
            TurntableSegmentEntity(turntableId = 0, name = "CGV影城", weight = 20),
            TurntableSegmentEntity(turntableId = 0, name = "博纳影城", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "卢米埃影城", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "百老汇影城", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "金逸影城", weight = 10),
        ))
        repository.createTurntable("看什么电影", "", listOf(
            TurntableSegmentEntity(turntableId = 0, name = "科幻", weight = 20),
            TurntableSegmentEntity(turntableId = 0, name = "喜剧", weight = 20),
            TurntableSegmentEntity(turntableId = 0, name = "悬疑", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "动画", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "爱情", weight = 10),
            TurntableSegmentEntity(turntableId = 0, name = "恐怖", weight = 10),
            TurntableSegmentEntity(turntableId = 0, name = "纪录片", weight = 10),
        ))
        repository.createTurntable("周末去哪玩", "", listOf(
            TurntableSegmentEntity(turntableId = 0, name = "商场逛街", weight = 20),
            TurntableSegmentEntity(turntableId = 0, name = "户外徒步", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "宅家休息", weight = 20),
            TurntableSegmentEntity(turntableId = 0, name = "看电影", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "博物馆", weight = 10),
            TurntableSegmentEntity(turntableId = 0, name = "咖啡馆", weight = 10),
            TurntableSegmentEntity(turntableId = 0, name = "游乐园", weight = 10),
        ))
        repository.createTurntable("今日运势", "", listOf(
            TurntableSegmentEntity(turntableId = 0, name = "大吉", description = "万事顺利，好运连连", weight = 5),
            TurntableSegmentEntity(turntableId = 0, name = "中吉", description = "平稳顺遂，小有收获", weight = 15),
            TurntableSegmentEntity(turntableId = 0, name = "小吉", description = "偶有小惊喜", weight = 25),
            TurntableSegmentEntity(turntableId = 0, name = "末吉", description = "平平淡淡才是真", weight = 25),
            TurntableSegmentEntity(turntableId = 0, name = "凶", description = "今天低调为妙", weight = 20),
            TurntableSegmentEntity(turntableId = 0, name = "大凶", description = "宜宅家不宜出门", weight = 10),
        ))
    }

    private suspend fun createDefaultFlows() {
        var all = repository.getAllTurntablesList()
        // Ensure required turntables exist
        val existingNames = all.map { it.name }.toSet()
        if ("去哪家电影院" !in existingNames) {
            repository.createTurntable("去哪家电影院", "", listOf(
                TurntableSegmentEntity(turntableId = 0, name = "万达影城", weight = 25),
                TurntableSegmentEntity(turntableId = 0, name = "CGV影城", weight = 20),
                TurntableSegmentEntity(turntableId = 0, name = "博纳影城", weight = 15),
                TurntableSegmentEntity(turntableId = 0, name = "卢米埃影城", weight = 15),
                TurntableSegmentEntity(turntableId = 0, name = "百老汇影城", weight = 15),
                TurntableSegmentEntity(turntableId = 0, name = "金逸影城", weight = 10),
            ))
            all = repository.getAllTurntablesList()
        }
        val byName = all.associate { it.name to it.id }
        val eatId = byName["今天吃什么"] ?: return
        val movieId = byName["看什么电影"] ?: return
        val cinemaId = byName["去哪家电影院"] ?: return
        val weekendId = byName["周末去哪玩"] ?: return
        repository.createFlow("周末决策", "先选吃的再选玩的", listOf(
            TurntableFlowStageEntity(flowId = 0, turntableId = eatId, stageName = "今天吃啥", stageOrder = 0),
            TurntableFlowStageEntity(flowId = 0, turntableId = weekendId, stageName = "去哪玩", stageOrder = 1),
        ))
        repository.createFlow("电影之夜", "先选影院再选电影", listOf(
            TurntableFlowStageEntity(flowId = 0, turntableId = cinemaId, stageName = "去哪家电影院", stageOrder = 0),
            TurntableFlowStageEntity(flowId = 0, turntableId = movieId, stageName = "看什么电影", stageOrder = 1),
        ))
    }

    private suspend fun refreshDefaultFlows() {
        // Ensure required turntables exist
        val existingNames = repository.getAllTurntablesList().map { it.name }.toSet()
        if ("去哪家电影院" !in existingNames) {
            repository.createTurntable("去哪家电影院", "", listOf(
                TurntableSegmentEntity(turntableId = 0, name = "万达影城", weight = 25),
                TurntableSegmentEntity(turntableId = 0, name = "CGV影城", weight = 20),
                TurntableSegmentEntity(turntableId = 0, name = "博纳影城", weight = 15),
                TurntableSegmentEntity(turntableId = 0, name = "卢米埃影城", weight = 15),
                TurntableSegmentEntity(turntableId = 0, name = "百老汇影城", weight = 15),
                TurntableSegmentEntity(turntableId = 0, name = "金逸影城", weight = 10),
            ))
        }
        // Delete old default flows
        val allFlows = repository.getAllFlowsList()
        val defaultNames = setOf("周末决策", "电影之夜")
        allFlows.filter { it.name in defaultNames }.forEach {
            repository.deleteFlow(it.id)
        }
        createDefaultFlows()
    }

    // Flow
    fun openNewFlowForm() { _showFlowForm.value = true }
    fun closeFlowForm() { _showFlowForm.value = false }

    fun saveFlow(name: String, desc: String, stages: List<TurntableFlowStageEntity>) {
        viewModelScope.launch {
            try {
                repository.createFlow(name, desc, stages)
                toast("流程创建成功", "success")
                _showFlowForm.value = false
            } catch (e: Exception) {
                toast("创建失败: ${e.message}", "error")
            }
        }
    }

    fun deleteFlow(id: Long, deleteTurntables: Boolean = false) {
        viewModelScope.launch {
            if (deleteTurntables) { repository.deleteFlowWithTurntables(id); toast("流程及转盘已删除", "success") }
            else { repository.deleteFlow(id); toast("流程已删除", "success") }
        }
    }

    // Flow detail
    fun openFlowDetail(id: Long) {
        _flowDetailId.value = id
        viewModelScope.launch {
            _flowDetail.value = repository.getFlow(id)
        }
    }

    fun closeFlowDetail() {
        _flowDetailId.value = null
        _flowDetail.value = null
    }

    fun updateFlowInfo(id: Long, name: String, desc: String) {
        viewModelScope.launch {
            repository.updateFlowInfo(id, name, desc)
            _flowDetail.value = repository.getFlow(id)
            toast("流程信息已更新", "success")
        }
    }

    fun addStageToFlow(flowId: Long, turntableId: Long, stageName: String) {
        viewModelScope.launch {
            repository.addStageToFlow(flowId, turntableId, stageName)
            _flowDetail.value = repository.getFlow(flowId)
            refreshFlowPreview()
            toast("阶段已添加", "success")
        }
    }

    fun removeStageFromFlow(stage: TurntableFlowStageEntity) {
        viewModelScope.launch {
            repository.removeStageFromFlow(stage)
            _flowDetail.value = repository.getFlow(stage.flowId)
            refreshFlowPreview()
            toast("阶段已移除", "success")
        }
    }

    fun moveStageUp(flowId: Long, stageOrder: Int) {
        viewModelScope.launch {
            repository.swapStagesOrder(flowId, stageOrder, -1)
            _flowDetail.value = repository.getFlow(flowId)
            refreshFlowPreview()
        }
    }

    fun moveStageDown(flowId: Long, stageOrder: Int) {
        viewModelScope.launch {
            repository.swapStagesOrder(flowId, stageOrder, 1)
            _flowDetail.value = repository.getFlow(flowId)
            refreshFlowPreview()
        }
    }

    fun updateStageName(stage: TurntableFlowStageEntity, newName: String) {
        viewModelScope.launch {
            repository.updateStageName(stage, newName)
            _flowDetail.value = repository.getFlow(stage.flowId)
            refreshFlowPreview()
        }
    }

    fun updateStageTurntable(stage: TurntableFlowStageEntity, newTurntableId: Long) {
        viewModelScope.launch {
            repository.updateStageTurntable(stage, newTurntableId)
            _flowDetail.value = repository.getFlow(stage.flowId)
            refreshFlowPreview()
            toast("阶段已更新", "success")
        }
    }

    fun reorderStages(flowId: Long, reorderedStages: List<TurntableFlowStageEntity>) {
        viewModelScope.launch {
            repository.updateStagesOrder(flowId, reorderedStages)
            _flowDetail.value = repository.getFlow(flowId)
            refreshFlowPreview()
        }
    }

    fun setSpinMode(mode: Int) {
        _spinMode.value = mode
        _isSpinning.value = false
        _spinResult.value = null
    }

    fun selectWheel(id: Long?) { _selectedWheelId.value = id }

    fun selectFlow(id: Long?) {
        _selectedFlowId.value = id
        if (id != null) {
            viewModelScope.launch {
                _selectedFlowPreview.value = repository.getFlow(id)
            }
        } else {
            _selectedFlowPreview.value = null
        }
    }

    private fun refreshFlowPreview() {
        val fid = _selectedFlowId.value ?: return
        viewModelScope.launch {
            _selectedFlowPreview.value = repository.getFlow(fid)
        }
    }

    // Single spin
    fun singleSpin() {
        val id = _selectedWheelId.value ?: return
        viewModelScope.launch {
            _isSpinning.value = true
            try {
                val result = repository.spin(id)
                _spinResult.value = result
            } catch (e: Exception) {
                toast("抽取失败: ${e.message}", "error")
                _isSpinning.value = false
            }
        }
    }

    // Flow session
    fun startFlowSession(userId: String) {
        val flowId = _selectedFlowId.value ?: return
        viewModelScope.launch {
            try {
                val session = repository.createSession(flowId, userId)
                _flowSession.value = session
                _flowHistory.value = emptyList()
                _spinResult.value = null

                val flow = repository.getFlow(flowId)
                if (flow != null) {
                    val currentStage = flow.stages.getOrNull(session.currentStage)
                    _currentStageName.value = currentStage?.stageName ?: "已完成"
                }
                // Auto-start first spin
                flowSpin()
            } catch (e: Exception) {
                toast("开始失败: ${e.message}", "error")
            }
        }
    }

    fun flowSpin() {
        val session = _flowSession.value ?: run { toast("请先开始流程", "error"); return }
        Log.d("Turntable", "flowSpin: stage=${session.currentStage} status=${session.status}")
        viewModelScope.launch {
            Log.d("Turntable", "flowSpin coroutine: start")
            _isSpinning.value = true
            try {
                val flow = repository.getFlow(session.flowId) ?: throw IllegalStateException("流程不存在")
                Log.d("Turntable", "flowSpin coroutine: got flow, stages=${flow.stages.size}")
                val stages = flow.stages

                if (session.currentStage >= stages.size) {
                    _isSpinning.value = false
                    return@launch
                }

                val currentStage = stages[session.currentStage]
                val result = repository.spin(currentStage.turntableId)
                Log.d("Turntable", "flowSpin coroutine: spin result=${result.segmentName} for stage=${currentStage.stageName}")
                _spinResult.value = result
            } catch (e: Exception) {
                toast("抽取失败: ${e.message}", "error")
                _isSpinning.value = false
            }
        }
    }

    fun commitFlowSpin() {
        val session = _flowSession.value ?: return
        val result = _spinResult.value ?: return
        if (_isReSpin.value) {
            _isReSpin.value = false
            _isSpinning.value = false
            return
        }
        Log.d("Turntable", "commitFlowSpin: session=${session.sessionId} stage=${session.currentStage} auto=${_autoFlow.value}")
        // Defer via Handler to ensure UI events (Switch toggle) are processed first
        Handler(Looper.getMainLooper()).post {
            viewModelScope.launch {
                Log.d("Turntable", "commitFlowSpin coroutine: start, auto=${_autoFlow.value}")
                try {
                val flow = repository.getFlow(session.flowId) ?: return@launch
                val stages = flow.stages

                if (session.currentStage >= stages.size) {
                    _isSpinning.value = false
                    return@launch
                }

                val currentStage = stages[session.currentStage]

                // Save record
                val record = TurntableSpinRecordEntity(
                    sessionId = session.sessionId,
                    turntableId = result.turntableId,
                    segmentId = result.segmentId,
                    segmentName = result.segmentName,
                    segmentDescription = result.segmentDescription,
                    stageOrder = session.currentStage,
                    userId = session.userId
                )
                repository.insertSpinRecord(record)

                // Add to history (after animation completes)
                val newHistory = _flowHistory.value + (currentStage.stageName to result)
                _flowHistory.value = newHistory

                // Advance session (don't exceed last stage for display)
                val newStage = session.currentStage + 1
                val newStatus = if (newStage >= stages.size) 1 else 0
                val updated = session.copy(
                    currentStage = newStage,
                    status = newStatus,
                    updateTime = System.currentTimeMillis()
                )
                repository.updateSession(updated)
                _flowSession.value = updated

                val nextStage = stages.getOrNull(newStage)
                _currentStageName.value = nextStage?.stageName ?: "全部完成！"

                if (newStatus == 1) {
                    // Flow complete - show result, wait for reset
                    _isSpinning.value = false
                } else if (_autoFlow.value) {
                    // Show result during delay, then auto-advance
                    Log.d("Turntable", "commitFlowSpin: auto-advancing, delay=${_autoFlowDelay.value}s")
                    _isSpinning.value = false
                    val delayMs = _autoFlowDelay.value * 1000L
                    if (delayMs > 0) {
                        kotlinx.coroutines.delay(delayMs)
                    }
                    Log.d("Turntable", "commitFlowSpin: delay done, calling flowSpin")
                    flowSpin()
                } else {
                    // Manual mode - show result, wait for user
                    _isSpinning.value = false
                }
            } catch (e: Exception) {
                toast("提交失败: ${e.message}", "error")
            }
        }
        }
    }

    fun reSpinCurrentStage() {
        val session = _flowSession.value ?: return
        if (session.currentStage == 0) return
        _isReSpin.value = true
        viewModelScope.launch {
            _isSpinning.value = true
            try {
                val flow = repository.getFlow(session.flowId)
                if (flow == null) {
                    _isReSpin.value = false
                    _isSpinning.value = false
                    return@launch
                }
                val prevIdx = session.currentStage - 1
                if (prevIdx !in flow.stages.indices) {
                    _isReSpin.value = false
                    _isSpinning.value = false
                    return@launch
                }
                val prevStage = flow.stages[prevIdx]
                val result = repository.spin(prevStage.turntableId)
                _spinResult.value = result
                val history = _flowHistory.value.toMutableList()
                if (history.isNotEmpty()) {
                    history[history.size - 1] = prevStage.stageName to result
                    _flowHistory.value = history
                }
            } catch (e: Exception) {
                toast("重新抽取失败: ${e.message}", "error")
                _isReSpin.value = false
                _isSpinning.value = false
            }
        }
    }

    fun resetFlowSession() {
        _flowSession.value = null
        _flowHistory.value = emptyList()
        _spinResult.value = null
        _isSpinning.value = false
        _isReSpin.value = false
        _currentStageName.value = ""
    }

    fun toast(msg: String, type: String) {
        _toastMessage.value = msg to type
    }

    fun clearToast() { _toastMessage.value = null }
}
