package com.turntable.app.data.preferences

import android.content.Context
import android.content.SharedPreferences

data class AiConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat"
)

class AiPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("turntable_ai_prefs", Context.MODE_PRIVATE)

    fun load(): AiConfig = AiConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        baseUrl = prefs.getString(KEY_BASE_URL, "https://api.deepseek.com") ?: "https://api.deepseek.com",
        model = prefs.getString(KEY_MODEL, "deepseek-chat") ?: "deepseek-chat"
    )

    fun save(config: AiConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_MODEL, config.model)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "ai_enabled"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_MODEL = "ai_model"
    }
}
