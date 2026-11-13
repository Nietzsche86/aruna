package com.example.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    isWideScreen: Boolean,
    onNavigateToSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var skillCandidateSteps by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.attachPhoto(uri)
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.attachDocument(uri)
    }

    LaunchedEffect(uiState.statusNotice, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusNotice
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotices()
        }
    }

    val chatContent: @Composable () -> Unit = {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        navigationIcon = {
                            if (!isWideScreen) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_sessions_drawer"),
                                ) {
                                    Icon(Icons.Outlined.Menu, contentDescription = "Buka daftar sesi")
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = uiState.currentConversation?.title ?: "Aruna",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = uiState.activeProvider?.let {
                                        "${it.name} · ${it.defaultModel}"
                                    } ?: "Belum ada provider aktif",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showNewChatDialog = true },
                                modifier = Modifier.testTag("top_new_chat_button"),
                            ) {
                                Icon(
                                    Icons.Outlined.AddComment,
                                    contentDescription = stringResource(R.string.action_new_chat),
                                )
                            }
                        },
                    )
                    uiState.currentConversation?.let { conv ->
                        TokenUsageBar(
                            usedTokens = conv.totalTokens,
                            contextLimit = uiState.activeProvider?.contextLimit ?: 32768,
                            isCompacting = uiState.isCompacting,
                            onCompactClick = { viewModel.triggerManualCompact() },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(uiState.messages.size, uiState.streamingText) {
                    val count = uiState.messages.size + if (uiState.streamingText.isNotEmpty()) 1 else 0
                    if (count > 0) {
                        listState.animateScrollToItem(count - 1)
                    }
                }

                // Messages Area (Centered max width for comfortable tablet reading)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                        WelcomeEmptyChatState(
                            hasProvider = uiState.activeProvider != null,
                            onOpenSettings = onNavigateToSettings,
                            onQuickPrompt = { prompt ->
                                inputText = prompt
                            },
                            modifier = Modifier
                                .widthIn(max = 620.dp)
                                .fillMaxSize()
                                .padding(24.dp),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxSize(),
                        ) {
                            uiState.currentConversation?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                                item(key = "summary_card") {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Ringkasan Konteks Sesi",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        }
                                    }
                                }
                            }

                            items(uiState.messages, key = { it.id }) { msg ->
                                MessageBubble(
                                    message = msg,
                                    attachments = uiState.attachmentsByMessage[msg.id].orEmpty(),
                                    onSaveAsSkill = { content -> skillCandidateSteps = content },
                                )
                            }

                            if (uiState.isGenerating || uiState.streamingText.isNotEmpty()) {
                                item(key = "streaming_bubble") {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.widthIn(max = 620.dp),
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            uiState.liveToolBanner?.let { banner ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(bottom = 8.dp),
                                                ) {
                                                    if (banner.isRunning) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(14.dp),
                                                            strokeWidth = 2.dp,
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (banner.success == true) StatusSuccess else StatusDanger
                                                                )
                                                        )
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "${banner.toolName}: ${banner.summary}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            Text(
                                                text = uiState.streamingText.ifBlank { "Sedang berpikir…" },
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pending Attachment Chips
                if (uiState.pendingAttachments.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.pendingAttachments, key = { it.id }) { att ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removePendingAttachment(att.id) },
                                label = { Text(att.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = "Hapus lampiran", modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }

                // Composer Bar
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            val visionEnabled = uiState.activeProvider?.supportsVision == true
                            IconButton(
                                enabled = visionEnabled && !uiState.isGenerating,
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("attach_photo_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = if (visionEnabled) {
                                        stringResource(R.string.action_attach_photo)
                                    } else {
                                        "Foto tidak didukung oleh model aktif"
                                    },
                                )
                            }

                            IconButton(
                                enabled = !uiState.isGenerating,
                                onClick = {
                                    docPickerLauncher.launch(
                                        arrayOf("text/*", "application/pdf", "application/json")
                                    )
                                },
                                modifier = Modifier.testTag("attach_file_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = stringResource(R.string.action_attach_file),
                                )
                            }

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                                maxLines = 5,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_input"),
                            )

                            Spacer(Modifier.width(8.dp))

                            IconButton(
                                enabled = !uiState.isGenerating && (inputText.isNotBlank() || uiState.pendingAttachments.isNotEmpty()),
                                onClick = {
                                    val toSend = inputText
                                    inputText = ""
                                    viewModel.sendMessage(toSend)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("send_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = stringResource(R.string.action_send),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            SessionListPane(
                conversations = uiState.conversations,
                activeConversationId = uiState.currentConversation?.id,
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                onSearchChange = viewModel::updateSearchQuery,
                onSelectConversation = viewModel::selectConversation,
                onDeleteConversation = viewModel::deleteConversation,
                onNewChatClick = { showNewChatDialog = true },
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(modifier = Modifier.weight(1f)) {
                chatContent()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                    SessionListPane(
                        conversations = uiState.conversations,
                        activeConversationId = uiState.currentConversation?.id,
                        searchQuery = uiState.searchQuery,
                        searchResults = uiState.searchResults,
                        onSearchChange = viewModel::updateSearchQuery,
                        onSelectConversation = { id ->
                            viewModel.selectConversation(id)
                            scope.launch { drawerState.close() }
                        },
                        onDeleteConversation = viewModel::deleteConversation,
                        onNewChatClick = {
                            scope.launch { drawerState.close() }
                            showNewChatDialog = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        ) {
            chatContent()
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            hasPreviousSession = uiState.conversations.isNotEmpty(),
            onDismiss = { showNewChatDialog = false },
            onConfirm = { carrySummary ->
                showNewChatDialog = false
                viewModel.createNewChat(carrySummary)
            },
        )
    }

    skillCandidateSteps?.let { steps ->
        SaveSkillDialog(
            initialSteps = steps,
            onDismiss = { skillCandidateSteps = null },
            onSave = { name, whenToUse, s, pitfalls, verification ->
                skillCandidateSteps = null
                viewModel.saveAsSkill(name, whenToUse, s, pitfalls, verification)
            },
        )
    }
}

@Composable
private fun WelcomeEmptyChatState(
    hasProvider: Boolean,
    onOpenSettings: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_aruna_emblem),
            contentDescription = "Ilustrasi Aruna",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.empty_chat_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_chat_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        if (!hasProvider) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("welcome_open_settings"),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Atur Provider & API Key")
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { onQuickPrompt("Buatkan catatan target minggu ini dan ingatkan saya tiap jam 07:00.") },
                    label = { Text("Buat catatan & jadwal rutin") },
                )
                AssistChip(
                    onClick = { onQuickPrompt("Apa saja memori tentang kebiasaan saya yang sudah kamu ingat?") },
                    label = { Text("Cek memori & profil saya") },
                )
            }
        }
    }
}
