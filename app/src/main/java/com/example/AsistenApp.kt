package com.example

import android.app.Application
import com.example.agent.ActionAuditLogger
import com.example.agent.AgentLoop
import com.example.agent.ApprovalGate
import com.example.agent.ApprovalRequest
import com.example.agent.Compactor
import com.example.agent.ForegroundApprovalHandler
import com.example.agent.PendingApprovalSink
import com.example.agent.PromptBuilder
import com.example.agent.ToolRegistry
import com.example.data.AppDatabase
import com.example.data.entity.ActionLogEntity
import com.example.data.entity.PendingApprovalEntity
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.MemoryRepositoryImpl
import com.example.data.repository.ProviderRepository
import com.example.data.repository.SkillRepositoryImpl
import com.example.data.repository.TaskAndApprovalRepository
import com.example.files.BackupManager
import com.example.files.DocumentTextExtractor
import com.example.files.ImageCompressor
import com.example.llm.OpenAiCompatProvider
import com.example.memory.ConversationSummarizer
import com.example.memory.MemoryExtractor
import com.example.memory.ProfileUpdater
import com.example.scheduler.NotificationHelper
import com.example.scheduler.TaskScheduler
import com.example.security.CryptoManager
import com.example.security.SecretDataStore
import com.example.tools.AlarmTool
import com.example.tools.CalendarTool
import com.example.tools.ChatSearchTool
import com.example.tools.CreateNoteTool
import com.example.tools.FileReadTool
import com.example.tools.ScheduleTool
import com.example.tools.SearchNoteTool
import com.example.tools.SkillReadTool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ForegroundApprovalPrompt(
    val request: ApprovalRequest,
    val decision: CompletableDeferred<Boolean>,
)

class AppContainer(private val application: Application) {
    val cryptoManager = CryptoManager()
    val secretDataStore = SecretDataStore(application, cryptoManager)
    val database = AppDatabase.build(application)

    val chatRepository = ChatRepositoryImpl(
        conversationDao = database.conversationDao(),
        messageDao = database.messageDao(),
        attachmentDao = database.attachmentDao(),
    )
    val memoryRepository = MemoryRepositoryImpl(
        memoryDao = database.memoryDao(),
        profileDao = database.profileDao(),
    )
    val skillRepository = SkillRepositoryImpl(database.skillDao())
    val providerRepository = ProviderRepository(database.providerDao(), secretDataStore)
    val taskAndApprovalRepository = TaskAndApprovalRepository(
        taskDao = database.scheduledTaskDao(),
        approvalDao = database.pendingApprovalDao(),
        actionLogDao = database.actionLogDao(),
        noteDao = database.noteDao(),
    )

    val notificationHelper = NotificationHelper(application)
    val taskScheduler = TaskScheduler(application, database.scheduledTaskDao())

    val imageCompressor = ImageCompressor(application)
    val documentTextExtractor = DocumentTextExtractor(application)
    val backupManager = BackupManager(database)

    val llmProvider = OpenAiCompatProvider()

    val memoryExtractor = MemoryExtractor(llmProvider, memoryRepository)
    val conversationSummarizer = ConversationSummarizer(llmProvider)
    val profileUpdater = ProfileUpdater(llmProvider, memoryRepository)
    val compactor = Compactor(
        memoryExtractor = memoryExtractor,
        summarizer = conversationSummarizer,
        profileUpdater = profileUpdater,
        persistence = chatRepository,
    )

    private val _foregroundApproval = MutableStateFlow<ForegroundApprovalPrompt?>(null)
    val foregroundApproval = _foregroundApproval.asStateFlow()

    fun resolveForegroundApproval(approved: Boolean) {
        val current = _foregroundApproval.value ?: return
        _foregroundApproval.value = null
        current.decision.complete(approved)
    }

    val toolRegistry = ToolRegistry(
        tools = listOf(
            CreateNoteTool(database.noteDao()),
            SearchNoteTool(database.noteDao()),
            AlarmTool(application, notificationHelper),
            CalendarTool(application),
            ScheduleTool(database.scheduledTaskDao(), taskScheduler),
            ChatSearchTool(chatRepository),
            SkillReadTool(skillRepository),
            FileReadTool(database.attachmentDao()),
        )
    )

    private val foregroundHandler = ForegroundApprovalHandler { request ->
        val deferred = CompletableDeferred<Boolean>()
        _foregroundApproval.value = ForegroundApprovalPrompt(request, deferred)
        deferred.await()
    }

    private val pendingSink = PendingApprovalSink { request ->
        val id = UUID.randomUUID().toString()
        database.pendingApprovalDao().upsert(
            PendingApprovalEntity(
                id = id,
                taskId = request.taskId,
                conversationId = request.conversationId,
                toolName = request.toolName,
                argumentsJson = request.argumentsJson,
                summary = request.summary,
                status = "PENDING",
                createdAt = System.currentTimeMillis(),
            )
        )
        notificationHelper.showApprovalRequest(id, request.toolName, request.summary)
        id
    }

    private val auditLogger = ActionAuditLogger { tool, argsJson, result, approved, mode ->
        database.actionLogDao().insert(
            ActionLogEntity(
                id = UUID.randomUUID().toString(),
                tool = tool,
                argumentsJson = argsJson,
                result = result,
                approved = approved,
                executionContext = mode.name,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    val approvalGate = ApprovalGate(foregroundHandler, pendingSink)
    val promptBuilder = PromptBuilder()

    val agentLoop = AgentLoop(
        llmProvider = llmProvider,
        promptBuilder = promptBuilder,
        toolRegistry = toolRegistry,
        approvalGate = approvalGate,
        auditLogger = auditLogger,
        memoryRepository = memoryRepository,
        skillRepository = skillRepository,
        memoryExtractor = memoryExtractor,
        profileUpdater = profileUpdater,
        compactor = compactor,
        conversationStore = chatRepository,
    )
}

class AsistenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannels()
    }
}
