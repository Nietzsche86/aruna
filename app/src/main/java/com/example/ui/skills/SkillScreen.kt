package com.example.ui.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skills.SkillItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillScreen(
    viewModel: SkillViewModel,
    isWideScreen: Boolean,
) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    var selectedSkill by remember(skills) { mutableStateOf(skills.firstOrNull()) }
    var editingSkill by remember { mutableStateOf<SkillItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Katalog Skill") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("add_skill_button"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Tambah skill baru")
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = if (isWideScreen) Modifier.width(360.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (skills.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Belum ada Skill", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Simpan prosedur berhasil dari chat atau tekan + untuk menambahkan skill baru. Header skill disuntikkan hemat token ke prompt sistem.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize(),
                    ) {
                        items(skills, key = { it.id }) { skill ->
                            val isSelected = isWideScreen && selectedSkill?.id == skill.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSkill = skill },
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(skill.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "Dipakai ${skill.useCount}x",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = skill.enabled,
                                                onCheckedChange = { viewModel.setEnabled(skill.id, it) },
                                            )
                                            IconButton(onClick = { editingSkill = skill }) {
                                                Icon(Icons.Outlined.Edit, contentDescription = "Edit skill")
                                            }
                                            IconButton(onClick = { viewModel.deleteSkill(skill.id) }) {
                                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus skill")
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = skill.whenToUse,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!isWideScreen) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = skill.steps,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(10.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isWideScreen) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp),
                ) {
                    val detail = selectedSkill
                    if (detail == null) {
                        Text(
                            text = "Pilih skill di sebelah kiri untuk melihat langkah lengkap, pitfalls, dan verifikasi.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 680.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(detail.name, style = MaterialTheme.typography.headlineSmall)
                            SkillDetailBlock("Kapan Dipakai", detail.whenToUse)
                            SkillDetailBlock("Langkah-langkah", detail.steps)
                            if (detail.pitfalls.isNotBlank()) SkillDetailBlock("Pitfalls (Hal Dihindari)", detail.pitfalls)
                            if (detail.verification.isNotBlank()) SkillDetailBlock("Verifikasi", detail.verification)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingSkill != null) {
        SkillEditorDialog(
            initial = editingSkill,
            onDismiss = {
                showCreateDialog = false
                editingSkill = null
            },
            onSave = { name, whenToUse, steps, pitfalls, verification ->
                viewModel.saveSkill(editingSkill?.id, name, whenToUse, steps, pitfalls, verification)
                showCreateDialog = false
                editingSkill = null
            },
        )
    }
}

@Composable
private fun SkillDetailBlock(label: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SkillEditorDialog(
    initial: SkillItem?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var whenToUse by remember { mutableStateOf(initial?.whenToUse.orEmpty()) }
    var steps by remember { mutableStateOf(initial?.steps.orEmpty()) }
    var pitfalls by remember { mutableStateOf(initial?.pitfalls.orEmpty()) }
    var verification by remember { mutableStateOf(initial?.verification.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Skill" else "Edit Skill") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Skill") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = whenToUse,
                    onValueChange = { whenToUse = it },
                    label = { Text("Kapan dipakai") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Langkah-langkah") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pitfalls,
                    onValueChange = { pitfalls = it },
                    label = { Text("Hal dihindari (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = verification,
                    onValueChange = { verification = it },
                    label = { Text("Verifikasi (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && steps.isNotBlank(),
                onClick = { onSave(name, whenToUse, steps, pitfalls, verification) },
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
