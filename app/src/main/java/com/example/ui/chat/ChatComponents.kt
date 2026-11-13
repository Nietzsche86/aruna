package com.example.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.ApprovalRequest
import com.example.data.entity.AttachmentEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MessageEntity
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun TokenUsageBar(
    usedTokens: Int,
    contextLimit: Int,
    isCompacting: Boolean,
    onCompactClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeLimit = contextLimit.coerceAtLeast(4096)
    val ratio = (usedTokens.toFloat() / safeLimit.toFloat()).coerceIn(0f, 1f)
    val indicatorColor = when {
        ratio >= 0.70f -> StatusWarning
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isCompacting) "Merangkum konteks (/compact)…"
                    else "Konteks: $usedTokens / $safeLimit token (${(ratio * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ratio >= 0.70f) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onCompactClick,
                    enabled = !isCompacting && usedTokens > 0,
                    modifier = Modifier.testTag("compact_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Compress,
                        contentDescription = stringResource(R.string.action_compact),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "/compact",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = indicatorColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    attachments: List<AttachmentEntity>,
    onSaveAsSkill: (String) -> Unit,
) {
    val isUser = message.role == "user"
    val isTool = message.role == "tool"

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(animationSpec = spring()) { it / 6 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            if (isTool) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.widthIn(max = 600.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = "Hasil tool",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Tool: ${message.toolName ?: "aksi"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = message.content.take(320),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                val containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
                val contentColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Surface(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 20.dp,
                    ),
                    color = containerColor,
                    tonalElevation = if (isUser) 2.dp else 1.dp,
                    modifier = Modifier.widthIn(max = 620.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (attachments.isNotEmpty()) {
                            attachments.forEach { att ->
                                if (att.isImage && !att.base64Preview.isNullOrBlank()) {
                                    Base64ImagePreview(att.base64Preview, att.fileName)
                                    Spacer(Modifier.height(8.dp))
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Description,
                                                contentDescription = "Lampiran berkas",
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = att.fileName,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (message.content.isNotBlank()) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                            )
                        }

                        if (!isUser && message.content.length > 60) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (message.promptTokens > 0 || message.completionTokens > 0) {
                                    Text(
                                        text = "in ${message.promptTokens} · out ${message.completionTokens}",
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }
                                TextButton(
                                    onClick = { onSaveAsSkill(message.content) },
                                    modifier = Modifier.testTag("save_skill_inline_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = stringResource(R.string.action_save_skill),
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Simpan Skill",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Base64ImagePreview(dataUrl: String, fileName: String) {
    val bitmap = remember(dataUrl) {
        runCatching {
            val base64 = dataUrl.substringAfter("base64,", dataUrl)
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Lampiran foto $fileName",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.small),
        )
    }
}

@Composable
fun SessionListPane(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    searchQuery: String,
    searchResults: List<MessageEntity>,
    onSearchChange: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Sesi Obrolan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onNewChatClick,
                modifier = Modifier.testTag("new_chat_button"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Baru")
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cari riwayat (@Fts4)…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Cari riwayat") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Hapus pencarian")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("session_search_input"),
        )

        Spacer(Modifier.height(12.dp))
        if (searchQuery.isNotBlank()) {
            Text(
                text = "Hasil Pencarian (${searchResults.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResults, key = { it.id }) { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectConversation(msg.conversationId) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.role.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = msg.content.take(140),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == activeConversationId
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectConversation(conv.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${conv.model} · ${conv.totalTokens} tok",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Hapus obrolan ${conv.title}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewChatDialog(
    hasPreviousSession: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (carryLastSummary: Boolean) -> Unit,
) {
    var carrySummary by remember { mutableStateOf(hasPreviousSession) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mulai Obrolan Baru") },
        text = {
            Column {
                Text(
                    text = "Buat sesi percakapan bersih agar konteks dan token tetap ringan.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (hasPreviousSession) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { carrySummary = !carrySummary },
                    ) {
                        Checkbox(checked = carrySummary, onCheckedChange = { carrySummary = it })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bawa ringkasan chat terakhir sebagai konteks awal",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(carrySummary) }) { Text("Buat Sesi") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
fun SaveSkillDialog(
    initialSteps: String,
    onDismiss: () -> Unit,
    onSave: (name: String, whenToUse: String, steps: String, pitfalls: String, verification: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var whenToUse by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf(initialSteps.take(1200)) }
    var pitfalls by remember { mutableStateOf("") }
    var verification by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simpan sebagai Skill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    label = { Text("Kapan dipakai (when_to_use)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Langkah-langkah") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pitfalls,
                    onValueChange = { pitfalls = it },
                    label = { Text("Hal yang dihindari (opsional)") },
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
            ) {
                Text("Simpan Skill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
fun ApprovalDialog(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = "Persetujuan keamanan",
                tint = StatusWarning,
            )
        },
        title = { Text("Persetujuan Aksi: ${request.toolName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = request.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                HorizontalDivider()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = request.argumentsJson,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                modifier = Modifier.testTag("dialog_approve_button"),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_approve))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.testTag("dialog_reject_button"),
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = StatusDanger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_reject))
            }
        },
    )
}
