package com.example.ui.memory

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.memory.MemoryItem
import com.example.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    isWideScreen: Boolean,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val profileCard: @Composable (Modifier) -> Unit = { mod ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = mod,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.PersonOutline,
                            contentDescription = "Profil Pengguna",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Profil Pengguna Ringkas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    IconButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.testTag("edit_profile_button"),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit profil")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiState.profile?.summaryText?.ifBlank { null }
                        ?: "Profil belum terbentuk. Profil akan diperbarui otomatis setelah Aruna mengekstrak fakta penting dari obrolan Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memori & Profil") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("add_memory_button"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Tambah memori manual")
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isWideScreen) {
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    profileCard(Modifier.fillMaxWidth())
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                ) {
                    if (!isWideScreen) {
                        item(key = "profile_card") {
                            profileCard(Modifier.fillMaxWidth())
                        }
                    }

                    item(key = "memory_filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearch,
                                placeholder = { Text("Cari memori atau tag…") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.categories) { cat ->
                                    FilterChip(
                                        selected = uiState.selectedCategory == cat,
                                        onClick = { viewModel.selectCategory(cat) },
                                        label = { Text(cat.replaceFirstChar { it.uppercase() }) },
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.memories.isEmpty()) {
                        item(key = "empty_memories") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        Icons.Outlined.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Belum ada memori tersimpan",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "Aruna mengekstrak fakta secara otomatis tiap 10 giliran atau sebelum /compact. Anda juga bisa menambahkannya manual.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.memories, key = { it.id }) { mem ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (mem.pendingConfirmation) {
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AssistChip(
                                            onClick = { viewModel.selectCategory(mem.category) },
                                            label = { Text(mem.category) },
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = mem.enabled,
                                                onCheckedChange = { viewModel.toggleMemory(mem.id, it) },
                                            )
                                            IconButton(onClick = { editingMemory = mem }) {
                                                Icon(Icons.Outlined.Edit, contentDescription = "Edit memori")
                                            }
                                            IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus memori")
                                            }
                                        }
                                    }
                                    Text(
                                        text = mem.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Normal,
                                    )
                                    if (mem.tags.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Tag: ${mem.tags}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (mem.pendingConfirmation) {
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Outlined.WarningAmber,
                                                    contentDescription = "Kategori sensitif",
                                                    tint = StatusWarning,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "Info sensitif — butuh konfirmasi",
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                            }
                                            Button(onClick = { viewModel.confirmSensitiveMemory(mem.id) }) {
                                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Aktifkan")
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
    }

    if (showCreateDialog || editingMemory != null) {
        MemoryEditorDialog(
            initial = editingMemory,
            onDismiss = {
                showCreateDialog = false
                editingMemory = null
            },
            onSave = { text, cat, tags ->
                viewModel.addOrUpdateMemory(editingMemory?.id, text, cat, tags)
                showCreateDialog = false
                editingMemory = null
            },
        )
    }

    if (showEditProfileDialog) {
        var profileText by remember(uiState.profile) {
            mutableStateOf(uiState.profile?.summaryText.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profil Ringkas") },
            text = {
                OutlinedTextField(
                    value = profileText,
                    onValueChange = { profileText = it },
                    label = { Text("Ringkasan profil (maks ~300 token)") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveProfile(profileText)
                    showEditProfileDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    initial: MemoryItem?,
    onDismiss: () -> Unit,
    onSave: (text: String, category: String, tags: String) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: "preferensi") }
    var tags by remember { mutableStateOf(initial?.tags.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tambah Memori" else "Edit Memori") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Fakta / Preferensi") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori (mis. preferensi, pekerjaan, rutinitas)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (pisah koma)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = { onSave(text, category, tags) }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
