# ARCHITECTURE.md — Struktur Direktori & Batasan Modul

Dokumen ini mendefinisikan tata letak berkas, pohon direktori (*folder tree*), serta tanggung jawab setiap komponen pada aplikasi **Asisten AI Pribadi Adaptif**.

---

## 1. Gambaran Umum Arsitektur

Aplikasi menggunakan arsitektur **Single-Module Clean MVVM** dengan pemisahan tegas antara **Core Domain (Pure Kotlin)** dan **Android Framework Layer**.

```
[ UI Layer (Jetpack Compose + ViewModels) ]
                    │
                    ▼
[ Agent & Domain Layer (Pure Kotlin: AgentLoop, PromptBuilder, Memory, Skills, LLM) ]
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
[ Android Tools & Files ]  [ Data & Security Layer (Room @Fts4, DataStore, Keystore) ]
```

---

## 2. Pohon Direktori Lengkap (Folder Tree)

Package dasar: `id.pribadi.asisten` (dapat disesuaikan dengan `applicationId` di `build.gradle.kts`).

```text
root/
├── .github/
│   └── workflows/
│       └── build.yml                          # Pipeline CI: ktlint, unit test, lint, signed APK
├── gradle/
│   └── libs.versions.toml                     # Version catalog (Kotlin, KSP, Compose, Room, Hilt, OkHttp, dll.)
├── app/
│   ├── schemas/                               # File JSON skema Room (wajib di-commit, exportSchema = true)
│   ├── build.gradle.kts                       # Konfigurasi build app, KSP, signingConfigs dari env vars
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml            # Izin: INTERNET, POST_NOTIFICATIONS, CALENDAR, EXACT_ALARM
│       │   └── java/id/pribadi/asisten/
│       │       ├── AsistenApp.kt              # @HiltAndroidApp + inisialisasi WorkManager
│       │       ├── MainActivity.kt            # Single Activity host untuk Compose Navigation
│       │       │
│       │       ├── di/                        # Hilt Dependency Injection Modules
│       │       │   ├── AppModule.kt
│       │       │   ├── DatabaseModule.kt
│       │       │   ├── NetworkModule.kt
│       │       │   ├── AgentModule.kt
│       │       │   └── ToolModule.kt
│       │       │
│       │       ├── ui/                        # Jetpack Compose Screens & ViewModels
│       │       │   ├── navigation/
│       │       │   │   └── AppNavGraph.kt
│       │       │   ├── theme/
│       │       │   │   ├── Color.kt
│       │       │   │   ├── Theme.kt
│       │       │   │   └── Type.kt
│       │       │   ├── chat/
│       │       │   │   ├── ChatScreen.kt              # Layar utama obrolan, indikator token, tombol /compact
│       │       │   │   ├── ChatViewModel.kt           # State obrolan, streaming, pemicu chat baru & lampiran
│       │       │   │   ├── SessionDrawer.kt           # Daftar sesi, pencarian @Fts4, dialog Chat Baru
│       │       │   │   ├── AttachmentPickerBar.kt     # Pemilih foto (Photo Picker) & dokumen
│       │       │   │   └── ApprovalDialog.kt          # Dialog persetujuan tool berisiko (foreground)
│       │       │   ├── memory/
│       │       │   │   ├── MemoryScreen.kt            # Daftar memori, filter kategori, edit/hapus, profil
│       │       │   │   └── MemoryViewModel.kt
│       │       │   ├── skills/
│       │       │   │   ├── SkillScreen.kt             # Daftar skill, tambah/edit manual, toggle aktif
│       │       │   │   └── SkillViewModel.kt
│       │       │   ├── tasks/
│       │       │   │   ├── ScheduledTaskScreen.kt     # Kelola cron task & antrean pending_approvals
│       │       │   │   └── ScheduledTaskViewModel.kt
│       │       │   └── settings/
│       │       │       ├── SettingsScreen.kt          # Kelola Provider, API Key, Model Utama & Utilitas, Backup
│       │       │       └── SettingsViewModel.kt
│       │       │
│       │       ├── agent/                     # [PURE KOTLIN] Otak Agent & Orkestrasi Konteks
│       │       │   ├── AgentLoop.kt                   # Loop eksekusi (maks 8 langkah FG / 4 langkah BG)
│       │       │   ├── PromptBuilder.kt               # Penyusun konteks ramah prompt-caching
│       │       │   ├── Compactor.kt                   # Logika /compact (manual & ambang 70% token)
│       │       │   ├── TokenTracker.kt                # Kalkulasi usage API & fallback estimasi karakter
│       │       │   ├── ToolRegistry.kt                # Pendaftaran & pencarian definisi tool
│       │       │   ├── ApprovalGate.kt                # Evaluasi izin: BACA_SAJA, TANYA_DULU, OTOMATIS
│       │       │   └── model/
│       │       │       ├── AgentContext.kt
│       │       │       ├── ExecutionMode.kt           # FOREGROUND vs BACKGROUND
│       │       │       └── ToolDefinition.kt
│       │       │
│       │       ├── llm/                       # [PURE KOTLIN] Klien Multi-Provider & Streaming
│       │       │   ├── LlmProvider.kt                 # Interface utama: streamChat(), chat(), listModels()
│       │       │   ├── LlmProviderFactory.kt          # Pemilih instance provider berdasarkan konfigurasi
│       │       │   ├── OpenAiCompatProvider.kt        # OpenAI, Groq, OpenRouter, DeepSeek, Ollama
│       │       │   ├── AnthropicProvider.kt           # Claude native API (Fase 5)
│       │       │   ├── GeminiProvider.kt              # Google Gemini native API (Fase 5)
│       │       │   ├── SseParser.kt                   # Parser Server-Sent Events untuk streaming
│       │       │   └── dto/
│       │       │       ├── ChatCompletionRequest.kt
│       │       │       ├── ChatCompletionChunk.kt
│       │       │       └── UsageDto.kt
│       │       │
│       │       ├── memory/                    # [PURE KOTLIN] Ekstraksi & Resolusi Konflik Memori
│       │       │   ├── MemoryRepository.kt            # Interface akses memori & profil
│       │       │   ├── MemoryExtractor.kt             # Ekstraktor JSON: ADD, UPDATE(id), DELETE(id)
│       │       │   ├── ProfileUpdater.kt              # Pembaru ringkasan profil (maks 300 token)
│       │       │   └── ConversationSummarizer.kt      # Perangkum sesi untuk /compact & bawa konteks
│       │       │
│       │       ├── skills/                    # [PURE KOTLIN] Manajemen Prosedur & Skill
│       │       │   ├── SkillRepository.kt             # Interface akses skill
│       │       │   ├── SkillCatalogBuilder.kt         # Penyusun daftar header (id, name, when_to_use)
│       │       │   └── SkillProposer.kt               # Pengusul skill otomatis (Fase 5)
│       │       │
│       │       ├── tools/                     # Implementasi Tool Android & Lokal
│       │       │   ├── AgentTool.kt                   # Interface dasar eksekusi tool
│       │       │   ├── NoteTool.kt                    # buat_catatan, cari_catatan
│       │       │   ├── AlarmTool.kt                   # buat_alarm (AlarmClock Intent / AlarmManager)
│       │       │   ├── CalendarTool.kt                # buat_event_kalender (CalendarContract)
│       │       │   ├── ScheduleTool.kt                # kelola_tugas_terjadwal (CRUD cron task)
│       │       │   ├── ChatSearchTool.kt              # cari_riwayat_chat (via @Fts4)
│       │       │   ├── SkillReadTool.kt               # baca_skill(id) (muat steps, pitfalls, verification)
│       │       │   └── FileReadTool.kt                # baca_berkas(attachment_id, query/chunk_index)
│       │       │
│       │       ├── files/                     # Pemrosesan Media, Dokumen & Backup
│       │       │   ├── ImageCompressor.kt             # Resize (maks 1024-1536px) & kompresi JPEG/WebP 80%
│       │       │   ├── DocumentTextExtractor.kt       # Ekstraktor TXT, MD, CSV, JSON, dan PDF (pdfbox)
│       │       │   ├── TextChunker.kt                 # Pemotong teks panjang untuk baca_berkas
│       │       │   └── BackupManager.kt               # Ekspor/Impor JSON/ZIP & catatan Markdown (tanpa API key)
│       │       │
│       │       ├── scheduler/                 # Eksekusi Latar Belakang & Notifikasi
│       │       │   ├── CronParser.kt                  # [PURE KOTLIN] Parser ekspresi cron & hitung next_run_at
│       │       │   ├── TaskScheduler.kt               # Orkestrator WorkManager & Exact AlarmManager
│       │       │   ├── TaskWorker.kt                  # CoroutineWorker (timeout 3 menit, maks 4 langkah)
│       │       │   ├── NotificationHelper.kt          # Notifikasi hasil tugas & tombol interaktif Setuju/Tolak
│       │       │   └── ApprovalReceiver.kt            # BroadcastReceiver untuk aksi tombol notifikasi
│       │       │
│       │       ├── data/                      # Persistensi Lokal (Room Database)
│       │       │   ├── AppDatabase.kt                 # @Database(version = ..., exportSchema = true)
│       │       │   ├── entity/
│       │       │   │   ├── ConversationEntity.kt
│       │       │   │   ├── MessageEntity.kt
│       │       │   │   ├── MessageFtsEntity.kt        # @Fts4(contentEntity = MessageEntity::class)
│       │       │   │   ├── AttachmentEntity.kt
│       │       │   │   ├── MemoryEntity.kt
│       │       │   │   ├── MemoryFtsEntity.kt         # @Fts4(contentEntity = MemoryEntity::class)
│       │       │   │   ├── ProfileEntity.kt
│       │       │   │   ├── SkillEntity.kt
│       │       │   │   ├── NoteEntity.kt
│       │       │   │   ├── NoteFtsEntity.kt           # @Fts4(contentEntity = NoteEntity::class)
│       │       │   │   ├── ScheduledTaskEntity.kt
│       │       │   │   ├── PendingApprovalEntity.kt
│       │       │   │   ├── ProviderEntity.kt
│       │       │   │   └── ActionLogEntity.kt
│       │       │   ├── dao/
│       │       │   │   ├── ConversationDao.kt
│       │       │   │   ├── MessageDao.kt
│       │       │   │   ├── AttachmentDao.kt
│       │       │   │   ├── MemoryDao.kt
│       │       │   │   ├── ProfileDao.kt
│       │       │   │   ├── SkillDao.kt
│       │       │   │   ├── NoteDao.kt
│       │       │   │   ├── ScheduledTaskDao.kt
│       │       │   │   ├── PendingApprovalDao.kt
│       │       │   │   ├── ProviderDao.kt
│       │       │   │   └── ActionLogDao.kt
│       │       │   └── repository/
│       │       │       ├── ChatRepositoryImpl.kt
│       │       │       ├── MemoryRepositoryImpl.kt
│       │       │       └── SkillRepositoryImpl.kt
│       │       │
│       │       └── security/                  # Keamanan & Penyimpanan Rahasia
│       │           ├── CryptoManager.kt               # Android Keystore AES/GCM/NoPadding encrypt/decrypt
│       │           └── SecretDataStore.kt             # Jetpack DataStore untuk menyimpan ciphertext API key
│       │
│       └── test/                              # Unit Tests (Berjalan di JVM CI tanpa Emulator)
│           └── java/id/pribadi/asisten/
│               ├── agent/
│               │   ├── AgentLoopTest.kt
│               │   ├── PromptBuilderTest.kt
│               │   ├── CompactorTest.kt
│               │   └── ApprovalGateTest.kt
│               ├── llm/
│               │   ├── OpenAiCompatProviderTest.kt
│               │   └── SseParserTest.kt
│               ├── memory/
│               │   └── MemoryExtractorTest.kt
│               ├── skills/
│               │   └── SkillCatalogBuilderTest.kt
│               ├── files/
│               │   └── TextChunkerTest.kt
│               └── scheduler/
│                   └── CronParserTest.kt
├── .gitignore
├── AGENTS.md
├── ARCHITECTURE.md
├── TASK.md
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 3. Aliran Data Utama

### 3.1 Aliran Chat, Streaming, & Auto-Compact
1. Pengguna mengirim pesan (beserta opsional lampiran foto/dokumen) dari `ChatScreen`.
2. `ChatViewModel` memproses lampiran lewat `ImageCompressor` atau `DocumentTextExtractor`, menyimpan pesan ke `MessageDao`, lalu memanggil `AgentLoop.run(conversationId, mode = FOREGROUND)`.
3. `PromptBuilder` menyusun prompt dengan urutan:
   - **Prefix Statis:** Instruksi Sistem + Definisi Tool + `ProfileEntity` + Katalog Header `SkillEntity`.
   - **Suffix Dinamis:** Hasil pencarian `@Fts4` `MemoryDao` + `ConversationEntity.summary` + daftar `MessageEntity` (`is_compacted = false`).
4. `LlmProvider.streamChat()` mengalirkan token ke UI dan mengembalikan objek `UsageDto` di akhir stream.
5. `TokenTracker` memperbarui `prompt_tokens`, `completion_tokens`, dan `conversations.total_tokens`.
6. Jika `total_tokens` melampaui $70\%$ batas konteks model:
   - `MemoryExtractor` dijalankan menggunakan **Model Utilitas** pada pesan-pesan lama.
   - `Compactor` merangkum pesan lama ke `conversations.summary`, menandai pesan lama sebagai `is_compacted = true`, dan menyisakan $K=6$ pesan terakhir tetap utuh.

### 3.2 Aliran Eksekusi Tool & Persetujuan (Foreground vs Background)
1. Saat respons LLM berisi `tool_calls`, `AgentLoop` memeriksa `ApprovalGate`.
2. Jika izin tool adalah `OTOMATIS` atau `BACA_SAJA`, tool langsung dijalankan dan dicatat ke `action_log`.
3. Jika izin tool adalah `TANYA_DULU`:
   - **Mode `FOREGROUND`:** `AgentLoop` menangguhkan coroutine dan menampilkan `ApprovalDialog` di UI. Setelah disetujui, eksekusi dilanjutkan.
   - **Mode `BACKGROUND` (`TaskWorker`):** `AgentLoop` menyimpan rencana aksi ke tabel `pending_approvals`, memicu `NotificationHelper` dengan tombol **Setuju** / **Tolak**, dan menghentikan loop dengan aman. Saat pengguna menekan **Setuju**, `ApprovalReceiver` mengeksekusi tool terkait.