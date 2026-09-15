package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AiRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun processSmartFetch(
        query: String,
        apiType: String, // "gemini" or "openrouter"
        apiKey: String,
        modelName: String
    ): String = withContext(Dispatchers.IO) {
        val lower = query.lowercase()
        val prompt = when {
            lower.contains("image") || lower.contains("photo") || lower.contains("picture") || lower.contains("wallpaper") -> {
                val searchQuery = URLEncoder.encode(query.replace("image", "").replace("photo", "").trim(), "UTF-8")
                return@withContext "![Image] (https://source.unsplash.com/featured/?$searchQuery)"
            }
            lower.contains("weather") || lower.contains("forecast") ->
                "Provide a concise, retro typewriter formatted weather snippet or dynamic summary for: $query. Include temperature, condition, and quick highlights."
            lower.contains("spotify") || lower.contains("song") || lower.contains("track") || lower.contains("music") ->
                "Fetch or generate the direct Spotify web link and track info for the song/artist: $query. Format as a clean markdown link snippet."
            lower.contains("times square") || lower.contains("location") || lower.contains("place") || lower.contains("restaurant") || lower.contains("hotel") ->
                "Provide the official Google Maps link and location overview snippet for: $query."
            else ->
                "Provide a concise, useful smart snippet or summary for: $query."
        }

        callAi(prompt, apiType, apiKey, modelName)
    }

    suspend fun processAction(
        actionType: String, // "grammar", "factcheck", "refine", "format", "summarize", "image"
        text: String,
        apiType: String,
        apiKey: String,
        modelName: String
    ): String = withContext(Dispatchers.IO) {
        if (actionType == "image") {
            val searchQuery = URLEncoder.encode(text.ifBlank { "retro typewriter" }, "UTF-8")
            return@withContext "![Image] (https://source.unsplash.com/featured/?$searchQuery)"
        }

        val prompt = when (actionType) {
            "grammar" -> "Correct any grammar and spelling errors in this text, keeping the original tone. Return ONLY the corrected text: \n\n$text"
            "factcheck" -> "Fact-check this statement and provide a brief verification snippet or correction: \n\n$text"
            "refine" -> "Refine and elevate the phrasing of this text to make it professional and articulate: \n\n$text"
            "format" -> "Format this text cleanly into bullet points or a professional structure: \n\n$text"
            "summarize" -> "Provide a concise 1-sentence summary of: \n\n$text"
            else -> "Improve this text: \n\n$text"
        }
        callAi(prompt, apiType, apiKey, modelName)
    }

    suspend fun callAi(
        prompt: String,
        apiType: String,
        apiKey: String,
        modelName: String
    ): String = withContext(Dispatchers.IO) {
        val resolvedKey = if (apiKey.isBlank()) "AIzaSyPlaceholderKey" else apiKey

        if (resolvedKey.isBlank()) {
            return@withContext "Error: API Key is missing. Please enter your OpenRouter or Gemini API key in settings."
        }

        try {
            if (apiType == "openrouter") {
                callOpenRouter(prompt, resolvedKey, if (modelName.isBlank()) "anthropic/claude-3.5-sonnet" else modelName)
            } else {
                val model = if (modelName.isBlank()) "gemini-3.5-flash" else modelName
                callGeminiRest(prompt, resolvedKey, model)
            }
        } catch (e: Exception) {
            Log.e("AiRepository", "AI Call failed", e)
            "AI Error: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    private fun callGeminiRest(prompt: String, apiKey: String, model: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return "Gemini API Error (${response.code}): $body"
            }
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)
            return firstPart?.optString("text")?.trim() ?: "No response generated."
        }
    }

    private fun callOpenRouter(prompt: String, apiKey: String, model: String): String {
        val url = "https://openrouter.ai/api/v1/chat/completions"
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://ai.studio/build")
            .addHeader("X-Title", "AI Keyboard")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return "OpenRouter Error (${response.code}): $body"
            }
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            return message?.optString("content")?.trim() ?: "No response generated."
        }
    }
}

