package com.example.ui.tasks

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scheduler.CronParser
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskScreen(
    viewModel: ScheduledTaskViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val pendingCount = uiState.approvals.count { it.status == "PENDING" }
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Jadwal, Antrean & Catatan") })
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("add_task_button"),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Buat tugas terjadwal")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Jadwal") })
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(if (pendingCount > 0) "Izin ($pendingCount)" else "Izin") },
                )
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Catatan") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Log") })
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .widthIn(max = 780.dp)
                        .fillMaxSize(),
                ) {
                    when (selectedTab) {
                        0 -> {
                            if (uiState.tasks.isEmpty()) {
                                item {
                                    EmptySectionCard(
                                        title = "Belum ada tugas terjadwal",
                                        subtitle = "Tambahkan jadwal cron (misalnya rangkuman pagi jam 07:00) dengan menekan + atau minta Aruna di chat.",
                                    )
                                }
                            } else {
                                items(uiState.tasks, key = { it.id }) { task ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        text = CronParser.describe(task.cron),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { viewModel.runTaskNow(task.id) }) {
                                                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Jalankan sekarang")
                                                    }
                                                    Switch(
                                                        checked = task.enabled,
                                                        onCheckedChange = { viewModel.toggleTask(task, it) },
                                                    )
                                                    IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus tugas")
                                                    }
                                                }
                                            }
                                            Text(task.prompt, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(6.dp))
                                            val nextText = task.nextRunAt?.let { dateFmt.format(Date(it)) } ?: "-"
                                            Text(
                                                text = "Berikutnya: $nextText · Status: ${task.lastStatus ?: "Belum pernah jalan"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (uiState.approvals.isEmpty()) {
                                item {
                                    EmptySectionCard(
                                        title = "Antrean persetujuan kosong",
                                        subtitle = "Aksi sensitif dari tugas latar belakang akan muncul di sini dan di notifikasi agar Anda dapat Setuju atau Tolak.",
                                    )
                                }
                            } else {
                                items(uiState.approvals, key = { it.id }) { item ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = "${item.toolName} · ${item.status}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = when (item.status) {
                                                    "APPROVED" -> StatusSuccess
                                                    "REJECTED" -> StatusDanger
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                                            if (item.status == "PENDING") {
                                                Spacer(Modifier.height(10.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(onClick = { viewModel.decidePendingApproval(item, true) }) {
                                                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Setuju")
                                                    }
                                                    OutlinedButton(onClick = { viewModel.decidePendingApproval(item, false) }) {
                                                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Tolak")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (uiState.notes.isEmpty()) {
                                item {
                                    EmptySectionCard(
                                        title = "Belum ada catatan",
                                        subtitle = "Minta Aruna membuat catatan dari obrolan; catatan dapat diekspor ke Markdown dari Setelan.",
                                    )
                                }
                            } else {
                                items(uiState.notes, key = { it.id }) { note ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(note.title, style = MaterialTheme.typography.titleMedium)
                                                IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus catatan")
                                                }
                                            }
                                            Text(note.body, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            if (uiState.actionLogs.isEmpty()) {
                                item {
                                    EmptySectionCard(
                                        title = "Belum ada log aksi",
                                        subtitle = "Setiap pemanggilan tool Android dicatat di sini untuk audit transparansi.",
                                    )
                                }
                            } else {
                                items(uiState.actionLogs, key = { it.id }) { log ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "${log.tool} · ${log.executionContext} · ${if (log.approved) "DISETUJUI" else "DITOLAK"}",
                                                fontFamily = MonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (log.approved) StatusSuccess else StatusDanger,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(log.result.take(220), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { title, prompt, cron, exact ->
                val err = viewModel.saveTask(null, title, prompt, cron, exact)
                if (err == null) showCreateDialog = false
                err
            },
        )
    }
}

@Composable
private fun EmptySectionCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, prompt: String, cron: String, exact: Boolean) -> String?,
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var cron by remember { mutableStateOf("0 7 * * *") }
    var exact by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jadwalkan Tugas Baru") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Tugas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Instruksi untuk Aruna") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cron,
                    onValueChange = {
                        cron = it
                        errorText = null
                    },
                    label = { Text("Ekspresi Cron (5 bagian)") },
                    supportingText = { Text(CronParser.describe(cron)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        AssistChip(onClick = { cron = "0 7 * * *" }, label = { Text("Pagi 07:00") })
                    }
                    item {
                        AssistChip(onClick = { cron = "30 8 * * 1-5" }, label = { Text("Kerja 08:30") })
                    }
                    item {
                        AssistChip(onClick = { cron = "0 */2 * * *" }, label = { Text("Tiap 2 jam") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exact, onCheckedChange = { exact = it })
                    Text("Gunakan Alarm Presisi (Exact Alarm)", style = MaterialTheme.typography.bodySmall)
                }
                errorText?.let {
                    Text(it, color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                errorText = onSave(title, prompt, cron, exact)
            }) { Text("Simpan Jadwal") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
