package com.example.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ThemeMode

private data class ProviderPreset(
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val utilityModel: String,
    val vision: Boolean,
)

private val PRESETS = listOf(
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o", "gpt-4o-mini", true),
    ProviderPreset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "llama-3.1-8b-instant", false),
    ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openai/gpt-4o-mini", true),
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "deepseek-chat", false),
    ProviderPreset("Ollama Lokal", "http://127.0.0.1:11434/v1", "llama3.2", "llama3.2", false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("OpenAI") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("gpt-4o") }
    var utilityModel by remember { mutableStateOf("gpt-4o-mini") }
    var supportsVision by remember { mutableStateOf(true) }
    var contextLimitText by remember { mutableStateOf("32768") }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportBackupToUri(context, uri) }

    val exportNotesMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> if (uri != null) viewModel.exportNotesMarkdownToUri(context, uri) }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importBackupFromUri(context, uri) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotices()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Setelan, Provider & Backup") }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize(),
            ) {
                // 1. Saved Providers List
                if (uiState.providers.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Provider Tersimpan", style = MaterialTheme.typography.titleMedium)
                                uiState.providers.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = p.id == uiState.activeProviderId,
                                            onClick = { viewModel.selectActiveProvider(p.id) },
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "Utama: ${p.defaultModel} · Utilitas: ${p.utilityModel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteProvider(p.id) }) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus provider ${p.name}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Add / Edit OpenAI-Compatible Provider
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Konfigurasi Provider LLM (Keystore AES-GCM)", style = MaterialTheme.typography.titleMedium)
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(PRESETS) { preset ->
                                    AssistChip(
                                        onClick = {
                                            editingId = null
                                            name = preset.label
                                            baseUrl = preset.baseUrl
                                            defaultModel = preset.defaultModel
                                            utilityModel = preset.utilityModel
                                            supportsVision = preset.vision
                                        },
                                        label = { Text(preset.label) },
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Nama Provider") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                label = { Text("Base URL (OpenAI-Compatible)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("API Key (tersimpan terenkripsi lokal)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input"),
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    enabled = !uiState.isFetchingModels,
                                    onClick = { viewModel.fetchModels(baseUrl, apiKey, editingId) },
                                ) {
                                    if (uiState.isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ambil Model (/v1/models)")
                                }
                            }

                            if (uiState.discoveredModels.isNotEmpty()) {
                                Text("Ketuk untuk memilih Model Utama:", style = MaterialTheme.typography.labelMedium)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(uiState.discoveredModels.take(30)) { m ->
                                        AssistChip(onClick = { defaultModel = m }, label = { Text(m) })
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = defaultModel,
                                onValueChange = { defaultModel = it },
                                label = { Text("Model Utama (Chat & Tool)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = utilityModel,
                                onValueChange = { utilityModel = it },
                                label = { Text("Model Utilitas Murah (Memori & /compact)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = contextLimitText,
                                onValueChange = { contextLimitText = it.filter(Char::isDigit) },
                                label = { Text("Batas Konteks Token (Auto-compact di 70%)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = supportsVision, onCheckedChange = { supportsVision = it })
                                Text("Model mendukung input foto (Vision)", style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(
                                onClick = {
                                    viewModel.saveProvider(
                                        existingId = editingId,
                                        name = name,
                                        baseUrl = baseUrl,
                                        apiKey = apiKey,
                                        defaultModel = defaultModel,
                                        utilityModel = utilityModel,
                                        supportsVision = supportsVision,
                                        contextLimit = contextLimitText.toIntOrNull() ?: 32768,
                                    )
                                    apiKey = ""
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_provider_button"),
                            ) {
                                Text("Simpan & Aktifkan Provider")
                            }
                        }
                    }
                }

                // 3. Dynamic Material You & Theme Settings
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Tampilan & Material You Dinamis", style = MaterialTheme.typography.titleMedium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Warna Dinamis Wallpaper", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            "Otomatis menyesuaikan palet wallpaper sistem Android"
                                        } else {
                                            "Perangkat di bawah Android 12 memakai palet kurasi Indigo-Teal"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = uiState.dynamicColorEnabled,
                                    onCheckedChange = viewModel::setDynamicColor,
                                )
                            }
                            HorizontalDivider()
                            Text("Mode Tema", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = uiState.themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) },
                                        label = {
                                            Text(
                                                when (mode) {
                                                    ThemeMode.SYSTEM -> "Sistem"
                                                    ThemeMode.LIGHT -> "Terang"
                                                    ThemeMode.DARK -> "Gelap"
                                                }
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Backup & Export
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Backup & Ekspor Data Lokal", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "API key tidak pernah disertakan dalam berkas ekspor demi keamanan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { exportJsonLauncher.launch("aruna_backup_${System.currentTimeMillis()}.json") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_backup_button"),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ekspor Backup Lengkap (JSON)")
                            }
                            OutlinedButton(
                                onClick = { exportNotesMdLauncher.launch("aruna_catatan.md") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ekspor Catatan (Markdown .md)")
                            }
                            OutlinedButton(
                                onClick = { importJsonLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_backup_button"),
                            ) {
                                Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Impor / Pulihkan Backup JSON")
                            }
                        }
                    }
                }
            }
        }
    }
}
