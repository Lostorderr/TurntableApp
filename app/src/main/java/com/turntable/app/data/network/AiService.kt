package com.turntable.app.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiService(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateWheel(userPrompt: String): Result<TurntableData> = runCatching {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("temperature", 0.7)
            put("response_format", JSONObject().apply {
                put("type", "json_object")
            })
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw IllegalStateException("API returned empty body (${response.code})")

        if (!response.isSuccessful) {
            val errMsg = try {
                JSONObject(body).optJSONObject("error")?.optString("message")
            } catch (_: Exception) {
                null
            } ?: body
            throw IllegalStateException("API error (${response.code}): $errMsg")
        }

        val content = JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: throw IllegalStateException("AI returned no content")

        val json = JSONObject(content)
        val name = json.optString("name", "").trim()
        val description = json.optString("description", "").trim()

        val segmentsJson = json.optJSONArray("segments")
            ?: throw IllegalStateException("AI response missing segments array")
        val segments = mutableListOf<SegmentData>()
        for (i in 0 until minOf(segmentsJson.length(), 20)) {
            val seg = segmentsJson.getJSONObject(i)
            segments.add(
                SegmentData(
                    name = seg.optString("name", "").trim(),
                    description = seg.optString("description", "").trim(),
                    weight = seg.optInt("weight", 1).coerceIn(1, 10)
                )
            )
        }

        require(name.isNotBlank()) { "AI did not return a valid wheel name" }
        require(segments.size >= 2) { "AI returned too few options (${segments.size}, need >= 2)" }

        TurntableData(name = name, description = description, segments = segments)
    }

    suspend fun generateFlow(userPrompt: String): Result<FlowData> = runCatching {
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", FLOW_SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("temperature", 0.7)
            put("response_format", JSONObject().apply {
                put("type", "json_object")
            })
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
            ?: throw IllegalStateException("API returned empty body (${response.code})")

        if (!response.isSuccessful) {
            val errMsg = try {
                JSONObject(body).optJSONObject("error")?.optString("message")
            } catch (_: Exception) { null } ?: body
            throw IllegalStateException("API error (${response.code}): $errMsg")
        }

        val content = JSONObject(body)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            ?: throw IllegalStateException("AI returned no content")

        val json = JSONObject(content)
        val name = json.optString("name", "").trim()
        val description = json.optString("description", "").trim()
        val stagesJson = json.optJSONArray("stages")
            ?: throw IllegalStateException("AI response missing stages array")

        val stages = mutableListOf<FlowStageData>()
        for (i in 0 until minOf(stagesJson.length(), 5)) {
            val stageJson = stagesJson.getJSONObject(i)
            val ttJson = stageJson.getJSONObject("turntable")
            val segsJson = ttJson.getJSONArray("segments")
            val segments = mutableListOf<SegmentData>()
            for (j in 0 until minOf(segsJson.length(), 10)) {
                val sj = segsJson.getJSONObject(j)
                segments.add(SegmentData(
                    name = sj.optString("name", "").trim(),
                    description = sj.optString("description", "").trim(),
                    weight = sj.optInt("weight", 1).coerceIn(1, 10)
                ))
            }
            stages.add(FlowStageData(
                stageName = stageJson.optString("stageName", "阶段${i + 1}").trim(),
                turntable = TurntableData(
                    name = ttJson.optString("name", "").trim(),
                    description = ttJson.optString("description", "").trim(),
                    segments = segments
                )
            ))
        }

        require(name.isNotBlank()) { "AI did not return a valid flow name" }
        require(stages.size >= 2) { "AI returned too few stages (${stages.size}, need >= 2)" }

        FlowData(name = name, description = description, stages = stages)
    }

    companion object {
        private val SYSTEM_PROMPT = """
你是一个转盘生成助手。根据用户的描述，生成一个转盘（wheel of fortune），包含转盘名称和若干选项。
每个选项包含：名称(name)、描述(description)、权重(weight，1-10之间的整数)。
请严格返回以下JSON格式，不要包含任何其他内容：
{
  "name": "转盘名称",
  "description": "转盘描述",
  "segments": [
    {"name": "选项名", "description": "简短描述", "weight": 5},
    ...
  ]
}
要求：
- 至少2个选项，最多20个选项
- 权重根据现实概率合理分配
- 名称和描述使用中文
        """.trimIndent()

        private val FLOW_SYSTEM_PROMPT = """
你是一个流程生成助手。根据用户的描述，生成一个多阶段流程（flow），包含流程名称、描述和若干阶段。
每个阶段包含阶段名称(stageName)和一个转盘(turntable)。
请严格返回以下JSON格式，不要包含任何其他内容：
{
  "name": "流程名称",
  "description": "流程描述",
  "stages": [
    {
      "stageName": "阶段名称",
      "turntable": {
        "name": "转盘名称",
        "description": "转盘描述",
        "segments": [
          {"name": "选项名", "description": "简短描述", "weight": 5},
          ...
        ]
      }
    },
    ...
  ]
}
要求：
- 至少2个阶段，最多5个阶段
- 每个转盘至少2个选项，最多10个选项
- 权重根据现实概率合理分配
- 名称和描述使用中文
        """.trimIndent()
    }
}
