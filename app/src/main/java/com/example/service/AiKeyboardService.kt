package com.example.service

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AiRepository
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiKeyboardService : InputMethodService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val aiRepository = AiRepository()

    private var currentInputText = mutableStateOf("")
    private var isAiLoading = mutableStateOf(false)
    private var showNumberRow = mutableStateOf(true)
    private var hapticIntensity = mutableStateOf(30)
    private var fontSizeSp = mutableStateOf(16f)

    override fun onCreateInputView(): View {
        loadSettings()
        return androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFF6B46C1), // SNES Deep Purple
                        secondary = Color(0xFF9F7AEA), // SNES Lavender
                        surface = Color(0xFF2D3748), // SNES Grey Key Surface
                        background = Color(0xFF1A202C), // Retro Chassis Dark Grey
                        onSurface = Color(0xFFEDF2F7),
                        onSurfaceVariant = Color(0xFFCBD5E0)
                    )
                ) {
                    KeyboardUI(
                        onKeyPressed = { char -> handleCharInput(char) },
                        onDelete = { handleDelete() },
                        onSpaceHold = { handleSpaceHold() },
                        onActionClicked = { action -> handleAiAction(action) },
                        isAiLoading = isAiLoading.value,
                        showNumberRow = showNumberRow.value,
                        fontSizeSp = fontSizeSp.value
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val numRow = db.keyboardDao().getSetting("show_number_row") ?: "true"
                val haptic = db.keyboardDao().getSetting("haptic_intensity") ?: "30"
                val font = db.keyboardDao().getSetting("font_size") ?: "16"
                withContext(Dispatchers.Main) {
                    showNumberRow.value = numRow.toBoolean()
                    hapticIntensity.value = haptic.toIntOrNull() ?: 30
                    fontSizeSp.value = font.toFloatOrNull() ?: 16f
                }
            } catch (e: Exception) {
                // fallback
            }
        }
    }

    private fun triggerHaptic() {
        try {
            if (hapticIntensity.value <= 0) return
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(hapticIntensity.value.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(hapticIntensity.value.toLong())
            }
        } catch (e: Exception) {
            // Ignore if vibration not permitted
        }
    }

    private fun handleCharInput(char: String) {
        triggerHaptic()
        if (char == "SHIFT") {
            return
        }
        currentInputText.value += char
        currentInputConnection?.commitText(char, 1)
    }

    private fun handleDelete() {
        triggerHaptic()
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
        if (currentInputText.value.isNotEmpty()) {
            currentInputText.value = currentInputText.value.dropLast(1)
        }
    }

    private fun handleSpaceHold() {
        triggerHaptic()
        val query = currentInputText.value.ifBlank { "weather in New York" }
        runAiFetch(query, "smart_fetch")
    }

    private fun handleAiAction(action: String) {
        triggerHaptic()
        val ic = currentInputConnection
        val selectedText = ic?.getSelectedText(0)?.toString() ?: ""
        val query = selectedText.ifBlank { currentInputText.value.ifBlank { "Provide a summary and helpful insights" } }
        runAiFetch(query, action)
    }

    private fun runAiFetch(query: String, actionType: String) {
        isAiLoading.value = true
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val apiKey = db.keyboardDao().getSetting("api_key") ?: ""
                val apiType = db.keyboardDao().getSetting("api_type") ?: "openrouter"
                val modelName = db.keyboardDao().getSetting("model_name") ?: "anthropic/claude-3.5-sonnet"

                val result = withContext(Dispatchers.IO) {
                    if (actionType == "smart_fetch") {
                        aiRepository.processSmartFetch(query, apiType, apiKey, modelName)
                    } else {
                        aiRepository.processAction(actionType, query, apiType, apiKey, modelName)
                    }
                }

                currentInputConnection?.commitText(result, 1)
                isAiLoading.value = false
                currentInputText.value = ""
            } catch (e: Exception) {
                isAiLoading.value = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}

@Composable
fun KeyboardUI(
    onKeyPressed: (String) -> Unit,
    onDelete: () -> Unit,
    onSpaceHold: () -> Unit,
    onActionClicked: (String) -> Unit,
    isAiLoading: Boolean,
    showNumberRow: Boolean,
    fontSizeSp: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(2.dp, Color(0xFF4A5568), RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        // SNES Retro 16-Bit Header / Skill Bar
        Surface(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isAiLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🎮 SNES AI FETCHING...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(listOf("Grammar", "FactCheck", "Refine", "Summarize", "Weather", "Spotify", "Image")) { action ->
                            Surface(
                                onClick = { onActionClicked(action.lowercase()) },
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF4A5568)
                            ) {
                                Text(
                                    text = action,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (showNumberRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { num ->
                    KeyButton(text = num, fontSize = fontSizeSp, modifier = Modifier.weight(1f), onClick = { onKeyPressed(num) })
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
        }

        // Row 1: Q W E R T Y U I O P
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").forEach { char ->
                KeyButton(text = char, fontSize = fontSizeSp, modifier = Modifier.weight(1f), onClick = { onKeyPressed(char) })
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Row 2: A S D F G H J K L
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").forEach { char ->
                KeyButton(text = char, fontSize = fontSizeSp, modifier = Modifier.weight(1f), onClick = { onKeyPressed(char) })
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Row 3: Shift, Z X C V B N M, Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            KeyButton(text = "⇧", fontSize = fontSizeSp, modifier = Modifier.weight(1.5f), onClick = { onKeyPressed("SHIFT") })
            listOf("z", "x", "c", "v", "b", "n", "m").forEach { char ->
                KeyButton(text = char, fontSize = fontSizeSp, modifier = Modifier.weight(1f), onClick = { onKeyPressed(char) })
            }
            KeyButton(text = "⌫", fontSize = fontSizeSp, modifier = Modifier.weight(1.5f), onClick = { onDelete() }, isAction = true)
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Bottom Row: ?123, Emoji, Spacebar (Hold for AI), Enter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            KeyButton(text = "?123", fontSize = fontSizeSp - 2, modifier = Modifier.weight(1.2f), onClick = { onKeyPressed("SYM") })
            KeyButton(text = "😊", fontSize = fontSizeSp - 2, modifier = Modifier.weight(1f), onClick = { onKeyPressed("EMOJI") })
            
            Surface(
                modifier = Modifier
                    .weight(4f)
                    .height(44.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onKeyPressed(" ") },
                            onLongPress = { onSpaceHold() }
                        )
                    },
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF553C9A), // SNES Deep Purple Accent for Spacebar
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "HOLD SPACE FOR AI ✨",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            KeyButton(text = "↵", fontSize = fontSizeSp, modifier = Modifier.weight(1.2f), onClick = { onKeyPressed("\n") }, isAction = true)
        }
    }
}

@Composable
fun KeyButton(
    text: String,
    fontSize: Float,
    modifier: Modifier = Modifier,
    isAction: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        shape = RoundedCornerShape(4.dp),
        color = if (isAction) Color(0xFF4A5568) else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
