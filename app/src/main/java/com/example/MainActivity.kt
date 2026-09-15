package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AiRepository
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.SettingEntity
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val aiRepository = remember { AiRepository() }

    var selectedTab = remember { mutableStateOf(0) } // 0: Setup, 1: Customize, 2: Hotkeys, 3: MCP, 4: History, 5: Report

    var apiKeyInput = remember { mutableStateOf("") }
    var apiTypeInput = remember { mutableStateOf("openrouter") }
    var modelInput = remember { mutableStateOf("anthropic/claude-3.5-sonnet") }
    var startupEnabled = remember { mutableStateOf(true) }
    var showNumberRow = remember { mutableStateOf(true) }
    var hapticIntensity = remember { mutableStateOf(30f) }
    var fontSizeSp = remember { mutableStateOf(16f) }

    var simulatorInput = remember { mutableStateOf("") }
    var simulatorOutput = remember { mutableStateOf("") }
    var isSimulating = remember { mutableStateOf(false) }

    // Load saved settings
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val key = db.keyboardDao().getSetting("api_key") ?: ""
            val type = db.keyboardDao().getSetting("api_type") ?: "openrouter"
            val model = db.keyboardDao().getSetting("model_name") ?: "anthropic/claude-3.5-sonnet"
            val startup = db.keyboardDao().getSetting("start_on_startup") ?: "true"
            val numRow = db.keyboardDao().getSetting("show_number_row") ?: "true"
            val haptic = db.keyboardDao().getSetting("haptic_intensity") ?: "30"
            val font = db.keyboardDao().getSetting("font_size") ?: "16"

            withContext(Dispatchers.Main) {
                apiKeyInput.value = key
                apiTypeInput.value = type
                modelInput.value = model
                startupEnabled.value = startup.toBoolean()
                showNumberRow.value = numRow.toBoolean()
                hapticIntensity.value = haptic.toFloatOrNull() ?: 30f
                fontSizeSp.value = font.toFloatOrNull() ?: 16f
            }
        }
    }

    val historyList by db.keyboardDao().getAllHistory().collectAsState(initial = emptyList())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI Keyboard Extension (SNES 16-Bit)", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Keyboard, contentDescription = "Setup") },
                    label = { Text("Setup", fontSize = 9.sp) },
                    selected = selectedTab.value == 0,
                    onClick = { selectedTab.value = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Customize") },
                    label = { Text("Customize", fontSize = 9.sp) },
                    selected = selectedTab.value == 1,
                    onClick = { selectedTab.value = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Bolt, contentDescription = "Hotkeys") },
                    label = { Text("Hotkeys", fontSize = 9.sp) },
                    selected = selectedTab.value == 2,
                    onClick = { selectedTab.value = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Extension, contentDescription = "MCP") },
                    label = { Text("MCP/Apps", fontSize = 9.sp) },
                    selected = selectedTab.value == 3,
                    onClick = { selectedTab.value = 3 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontSize = 9.sp) },
                    selected = selectedTab.value == 4,
                    onClick = { selectedTab.value = 4 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Description, contentDescription = "Report") },
                    label = { Text("Report", fontSize = 9.sp) },
                    selected = selectedTab.value == 5,
                    onClick = { selectedTab.value = 5 }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab.value) {
                0 -> DashboardTab(
                    onOpenInputSettings = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    simulatorInput = simulatorInput,
                    simulatorOutput = simulatorOutput,
                    isSimulating = isSimulating,
                    onRunSmartFetch = { query ->
                        isSimulating.value = true
                        scope.launch {
                            val res = aiRepository.processSmartFetch(query, apiTypeInput.value, apiKeyInput.value, modelInput.value)
                            simulatorOutput.value = res
                            db.keyboardDao().insertHistory(HistoryEntity(prompt = query, queryType = "SmartFetch", result = res))
                            isSimulating.value = false
                        }
                    }
                )
                1 -> CustomizationTab(
                    apiKeyInput = apiKeyInput,
                    apiTypeInput = apiTypeInput,
                    modelInput = modelInput,
                    startupEnabled = startupEnabled,
                    showNumberRow = showNumberRow,
                    hapticIntensity = hapticIntensity,
                    fontSizeSp = fontSizeSp,
                    onSave = {
                        scope.launch(Dispatchers.IO) {
                            db.keyboardDao().setSetting(SettingEntity("api_key", apiKeyInput.value))
                            db.keyboardDao().setSetting(SettingEntity("api_type", apiTypeInput.value))
                            db.keyboardDao().setSetting(SettingEntity("model_name", modelInput.value))
                            db.keyboardDao().setSetting(SettingEntity("start_on_startup", startupEnabled.value.toString()))
                            db.keyboardDao().setSetting(SettingEntity("show_number_row", showNumberRow.value.toString()))
                            db.keyboardDao().setSetting(SettingEntity("haptic_intensity", hapticIntensity.value.toInt().toString()))
                            db.keyboardDao().setSetting(SettingEntity("font_size", fontSizeSp.value.toString()))
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Settings & Customization Saved Successfully!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                2 -> HotkeysTab()
                3 -> McpTab()
                4 -> HistoryTab(historyList = historyList, onClear = {
                    scope.launch(Dispatchers.IO) {
                        db.keyboardDao().clearHistory()
                    }
                })
                5 -> ConceptReportTab()
            }
        }
    }
}

@Composable
fun DashboardTab(
    onOpenInputSettings: () -> Unit,
    simulatorInput: MutableState<String>,
    simulatorOutput: MutableState<String>,
    isSimulating: MutableState<Boolean>,
    onRunSmartFetch: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎮 SNES Retro AI Keyboard Ready", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Enable 'AI Keyboard' in your Android Input Methods.\n2. Select it in any text box.\n3. Type 'sunset wallpaper' or 'weather in London', and hold Spacebar for instant AI / Image fetch!", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onOpenInputSettings) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Keyboard in Android Settings")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("✨ Live AI & Image Fetch Simulator", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Test real Unsplash images, Spotify tracks, weather snippets, or text refinement.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = simulatorInput.value,
                        onValueChange = { simulatorInput.value = it },
                        label = { Text("Query (e.g. cyber punk wallpaper, weather in Tokyo)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onRunSmartFetch(simulatorInput.value.ifBlank { "cyber punk wallpaper" }) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSimulating.value
                    ) {
                        if (isSimulating.value) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Simulate Hold Spacebar AI / Image Fetch")
                    }

                    if (simulatorOutput.value.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("AI Result Snippet / Image Markdown:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(simulatorOutput.value, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomizationTab(
    apiKeyInput: MutableState<String>,
    apiTypeInput: MutableState<String>,
    modelInput: MutableState<String>,
    startupEnabled: MutableState<Boolean>,
    showNumberRow: MutableState<Boolean>,
    hapticIntensity: MutableState<Float>,
    fontSizeSp: MutableState<Float>,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Comprehensive Customization", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Tailor your SNES keyboard layout, haptics, themes, and AI models.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI Provider & Model", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = apiTypeInput.value == "openrouter",
                            onClick = { apiTypeInput.value = "openrouter" },
                            label = { Text("OpenRouter") }
                        )
                        FilterChip(
                            selected = apiTypeInput.value == "gemini",
                            onClick = { apiTypeInput.value = "gemini" },
                            label = { Text("Gemini API") }
                        )
                    }

                    OutlinedTextField(
                        value = apiKeyInput.value,
                        onValueChange = { apiKeyInput.value = it },
                        label = { Text(if (apiTypeInput.value == "openrouter") "OpenRouter API Key" else "Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = modelInput.value,
                        onValueChange = { modelInput.value = it },
                        label = { Text("Model Name (e.g. anthropic/claude-3.5-sonnet, openai/gpt-4o)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Keyboard Layout & Appearance", fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Top Number Row")
                        Switch(checked = showNumberRow.value, onCheckedChange = { showNumberRow.value = it })
                    }

                    Column {
                        Text("Font Size: ${fontSizeSp.value.toInt()} sp", fontSize = 14.sp)
                        Slider(
                            value = fontSizeSp.value,
                            onValueChange = { fontSizeSp.value = it },
                            valueRange = 12f..22f,
                            steps = 10
                        )
                    }

                    Column {
                        Text("Haptic Feedback Intensity: ${hapticIntensity.value.toInt()} ms", fontSize = 14.sp)
                        Slider(
                            value = hapticIntensity.value,
                            onValueChange = { hapticIntensity.value = it },
                            valueRange = 0f..60f,
                            steps = 6
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Start on Device Startup")
                        Switch(checked = startupEnabled.value, onCheckedChange = { startupEnabled.value = it })
                    }

                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save All Settings & Customizations")
                    }
                }
            }
        }
    }
}

@Composable
fun HotkeysTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Action Hotkeys & Skills", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Select any text in any app, then trigger hotkeys or action chips for instant summarization, grammar correction, refinement, and image generation.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val hotkeys = listOf(
            Triple("Grammar & Spelling", "G", "Corrects grammar while preserving tone"),
            Triple("Fact Check", "F", "Verifies statements against live search data"),
            Triple("Refine Phrasing", "R", "Elevates clarity and professional tone"),
            Triple("Summarize", "S", "Condenses selected text into 1 sentence"),
            Triple("Format Clean", "C", "Organizes text into structured bullet points"),
            Triple("Image Fetch", "I", "Searches and attaches relevant image link")
        )
        items(hotkeys) { hk ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hk.first, fontWeight = FontWeight.Bold)
                        Text(hk.third, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Key '${hk.second}'",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun McpTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("MCP & Composio Integrations", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Connect your AI keyboard extension to external apps via Model Context Protocol (MCP) and Composio.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val connectors = listOf(
            Triple("Slack / Discord", "Send formatted snippets directly to channels", true),
            Triple("Spotify", "Auto-fetch track links and player info", true),
            Triple("Google Maps / Places", "Auto-fetch locations and navigation links", true),
            Triple("Unsplash / Images", "Auto-fetch high resolution image links", true),
            Triple("GitHub / Notion", "Quick search and paste notes or commits", false)
        )
        items(connectors) { conn ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(conn.first, fontWeight = FontWeight.Bold)
                        Text(conn.second, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = conn.third, onCheckedChange = {})
                }
            }
        }
    }
}

@Composable
fun HistoryTab(historyList: List<HistoryEntity>, onClear: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("AI Snippet History", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear History")
                    }
                }
            }
        }

        if (historyList.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No AI snippets generated yet. Start typing and hold spacebar!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(historyList) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.queryType, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            Text("Query: ${item.prompt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(item.result, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConceptReportTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("App Concept Report & Architecture", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Comprehensive system documentation for the SNES Retro AI Keyboard Extension.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Executive Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("The AI Keyboard Extension merges the nostalgic ergonomics of a 16-bit SNES console controller with cutting-edge generative AI and multi-modal image/media retrieval directly inside any Android text field.", fontSize = 13.sp)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Core Capabilities & Mechanics", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Smart Spacebar Hold: Long-pressing spacebar invokes instant contextual intelligence (weather, Spotify tracks, Google Maps locations).\n• Unsplash Image Integration: Typing queries like 'wallpaper' or 'sunset' and triggering AI image fetch returns direct markdown image links.\n• Text Refinement & Summarization: Selected text can be instantly summarized, fact-checked, or refined via action hotkeys.\n• SNES 16-Bit Aesthetic: Distinct dark grey chassis, purple/lavender accent buttons, and monospace typography.", fontSize = 13.sp)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Technical Stack & Security", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• InputMethodService API: Native Android IME architecture supporting secure text injection.\n• Room Database: Offline-first persistence for settings, custom models, and generation history.\n• OkHttp & REST: Direct integration with OpenRouter / Gemini APIs with secure API key storage.", fontSize = 13.sp)
                }
            }
        }
    }
}
