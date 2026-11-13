# PRD: Asisten AI Pribadi Adaptif (Android, Kotlin)

Versi 0.3 (Revisi Final) · 9 November 2026 · Pemilik produk: pengguna tunggal (pribadi)

## 1. Ringkasan

Aplikasi Android native (Kotlin) untuk pemakaian pribadi. Lapisan bawahnya adalah klien chat multi-provider (bawa API key sendiri, seperti AnyLLM) dengan dukungan manajemen sesi, perangkuman konteks (*compact*), serta lampiran foto dan berkas. Lapisan atasnya adalah otak agent adaptif ala Hermes Agent: memori jangka panjang dengan resolusi konflik, skill yang dipelajari dari pemakaian, tool calling, dan tugas terjadwal. Semua data tersimpan lokal, tanpa akun, dan tanpa backend wajib. Kode dikembangkan menggunakan AI agent CLI di Termux dan dikompilasi sepenuhnya lewat GitHub Actions pada repositori publik.

## 2. Latar belakang dan masalah

- Menjalankan agent Python lewat Termux merepotkan: harus dibuka manual dan prosesnya rawan dimatikan Android.
- Aplikasi chat biasa tidak belajar dari kebiasaan pengguna, mudah lupa konteks lintas sesi, atau memotong riwayat percakapan panjang secara kasar.
- Pengguna ingin satu APK mandiri yang langsung berjalan saat dibuka, dapat menerima foto/dokumen, dan tetap bisa menjalankan pengingat serta tugas terjadwal di latar belakang secara andal.

## 3. Tujuan dan non-tujuan

**Tujuan**

- **G1:** Chat multi-sesi dengan beberapa provider LLM dari satu aplikasi, mendukung jawaban streaming, lampiran foto/berkas, dan perangkuman percakapan (*compact*).
- **G2:** Agent mengingat fakta penting tentang pengguna, memperbarui fakta yang berubah (tanpa duplikasi), dan memakainya di sesi berikutnya.
- **G3:** Agent dapat menjalankan tool Android (catatan, alarm, kalender, tugas terjadwal, baca berkas) dengan persetujuan pengguna untuk aksi berisiko.
- **G4:** Agent dapat mempelajari prosedur berulang sebagai skill.
- **G5:** Tugas terjadwal (pengingat, rangkuman pagi) berjalan andal walau aplikasi ditutup.
- **G6:** Seluruh pembuatan kode dilakukan lewat AI agent CLI di Termux dan seluruh proses kompilasi serta penandatanganan APK konsisten dilakukan di GitHub Actions (repositori publik).

**Non-tujuan (versi 1)**

- Dukungan iOS dan multi-pengguna.
- Rilis Play Store.
- Backend sinkronisasi antar perangkat.
- OCR untuk PDF hasil pindaian (scan), model lokal (llama.cpp), dan aksi lintas aplikasi (AccessibilityService). Semuanya masuk fase lanjut.

## 4. Pengguna dan perangkat

Satu pengguna: pemilik aplikasi. Perangkat target: Android 8.0 ke atas (`minSdk = 26`, `compileSdk = 35`), diuji pada satu HP fisik.

## 5. Lingkup dan prioritas

| ID | Kebutuhan | Prioritas | Fase |
| --- | --- | --- | --- |
| F-01 | Chat dengan streaming jawaban | P0 | 1 |
| F-02 | Banyak provider, pemilihan model utama & model ringan (utility) | P0 | 1 |
| F-03 | Penyimpanan API key aman (Keystore + DataStore) | P0 | 1 |
| F-04 | Riwayat chat lokal, kelola sesi, dan pencarian (`@Fts4`) | P0 | 1 |
| F-15 | Ekspor dan impor data (backup JSON & catatan Markdown) | P0 | 1 |
| F-19 | Chat baru (dengan opsi bawa ringkasan sesi sebelumnya) | P0 | 1 |
| F-20 | Compact percakapan (manual `/compact` & otomatis) + indikator token | P1 | 2 |
| F-05 | Ekstraksi memori otomatis dengan deduplikasi (`ADD`/`UPDATE`/`DELETE`) | P0 | 2 |
| F-06 | Layar memori (lihat, edit, hapus) | P0 | 2 |
| F-07 | Profil pengguna ringkas | P1 | 2 |
| F-08 | Tool calling: catatan, alarm, kalender, jadwal, baca skill, baca berkas | P0 | 3 |
| F-09 | Persetujuan aksi berisiko (UI dialog & notifikasi latar belakang) | P0 | 3 |
| F-10 | Skill manual (simpan sebagai skill) | P1 | 3 |
| F-21 | Upload foto (vision) dan berkas teks/PDF berteks | P1 | 3 |
| F-12 | Tugas terjadwal (dengan penanganan batas waktu WorkManager) | P1 | 4 |
| F-13 | Notifikasi proaktif & interaktif (`POST_NOTIFICATIONS`) | P1 | 4 |
| F-11 | Skill otomatis | P2 | 5 |
| F-14 | Input suara dan TTS | P2 | 5 |
| F-16 | Klien MCP | P3 | Lanjut |
| F-17 | Model lokal (llama.cpp) | P3 | Lanjut |
| F-18 | Baca notifikasi & OCR PDF scan | P3 | Lanjut |

*Catatan perubahan fase:* **F-15 (Backup)** ditarik maju ke **Fase 1** agar data percakapan selama pengujian harian aman jika sewaktu-waktu perlu instal ulang aplikasi.

## 6. Kebutuhan fungsional

### 6.1 Chat, sesi, dan provider (F-01 sampai F-04, F-19, F-20)

- Pengguna dapat menambah provider: **OpenAI-compatible** (implementasi utama Fase 1; mencakup OpenAI, Groq, OpenRouter, DeepSeek, Ollama/LM Studio lokal), disusul Anthropic dan Google native.
- Setiap provider punya base URL, API key, dan daftar model yang bisa diambil otomatis lewat `GET /v1/models` atau diketik manual.
- **Model Utilitas (Murah/Cepat):** Pengguna dapat menetapkan model khusus yang lebih hemat untuk tugas latar belakang (ekstraksi memori dan perangkuman `/compact`).
- **Chat Baru (F-19):** Pengguna dapat membuat sesi percakapan baru kapan saja, dengan opsi mencentang "Bawa ringkasan chat terakhir" sebagai konteks awal.
- **Compact Percakapan (F-20):**
  - Dapat dipicu manual lewat tombol/perintah `/compact` atau otomatis saat pemakaian token percakapan mencapai 70% batas konteks.
  - Sebelum pesan lama dipangkas, sistem menjalankan ekstraksi memori terlebih dahulu agar fakta penting tidak hilang.
  - Pesan-pesan lama dirangkum ke dalam kolom `conversations.summary`, sementara `K` pesan terakhir (default 6 pesan) dibiarkan utuh agar alur obrolan tetap nyambung.
- **Pelacakan Token:** Layar chat menampilkan indikator jumlah token sesi berdasarkan field `usage` (`prompt_tokens`, `completion_tokens`) dari respons API, dengan estimasi berbasis karakter hanya sebagai cadangan sebelum respons diterima.
- **Kriteria penerimaan:** pesan terkirim dan jawaban mengalir kurang dari 3 detik sampai token pertama (jaringan normal); `/compact` berhasil memangkas token riwayat tanpa menghilangkan konteks utama percakapan.

### 6.2 Lampiran foto dan berkas (F-21)

- **Foto (Vision):**
  - Dipilih menggunakan Android Photo Picker resmi (tanpa perlu izin baca seluruh penyimpanan).
  - Gambar otomatis diperkecil (maksimal dimensi sisi terpanjang 1024px–1536px) dan dikompres (JPEG/WebP 80%) sebelum dienkode untuk menghemat kuota token dan bandwidth.
  - Tombol lampirkan foto hanya aktif jika model yang dipilih ditandai mendukung input gambar (*vision*).
- **Berkas Dokumen:**
  - Mendukung TXT, MD, CSV, JSON, dan PDF berteks (diekstrak di perangkat). PDF hasil scan (gambar) di luar lingkup versi 1.
  - Berkas berukuran kecil langsung disisipkan ke konteks pesan. Berkas berukuran besar disimpan lokal dan dipotong menjadi bagian-bagian (*chunks*) yang dapat dicari serta dibaca oleh agent melalui tool `baca_berkas`.
- **Kriteria penerimaan:** pengguna dapat mengirim foto struk/dokumen ke model vision dan mendapat penjelasan akurat; berkas teks panjang dapat ditanyakan isinya tanpa membuat prompt meluap.

### 6.3 Memori (F-05 sampai F-07)

- Setelah setiap `N` giliran (default 10) atau tepat sebelum proses `/compact`, aplikasi menjalankan proses latar memakai model utilitas untuk mengekstrak fakta baru tentang pengguna.
- **Resolusi Konflik & Deduplikasi:** Saat mengekstrak memori, sistem menyertakan memori yang sudah ada di kategori terkait ke dalam prompt ekstraktor. Model mengembalikan instruksi JSON: `ADD` (fakta baru), `UPDATE (id)` (memperbarui fakta lama yang berubah), atau `DELETE (id)` (menghapus fakta usang).
- Pengguna dapat melihat, mengedit, menghapus, dan menonaktifkan memori di layar khusus.
- Hal sensitif (kesehatan, keuangan, kredensial) meminta konfirmasi sebelum disimpan.
- **Kriteria penerimaan:** fakta yang disebut di sesi A muncul dalam jawaban di sesi B tanpa diulang; fakta yang dikoreksi di sesi B otomatis memperbarui entri lama tanpa duplikasi.

### 6.4 Tools dan persetujuan (F-08, F-09)

- Tool awal:
  - Catatan: `buat_catatan`, `cari_catatan`
  - Waktu & pengingat: `buat_alarm`, `buat_event_kalender`, `kelola_tugas_terjadwal` (buat/lihat/nonaktifkan jadwal)
  - Konteks lokal: `cari_riwayat_chat`, `baca_skill`, `baca_berkas`
- Tiga tingkat izin per tool: `baca_saja`, `tanya_dulu`, `otomatis`. Hapus data, ubah jadwal/kalender penting, dan aksi eksternal berisiko selalu `tanya_dulu`.
- **Persetujuan Latar Belakang (Pending Approval Queue):** Jika tool tingkat `tanya_dulu` dipicu oleh tugas terjadwal saat aplikasi tertutup, eksekusi disimpan ke tabel `pending_approvals` dan pengguna dikirimi notifikasi interaktif berisi tombol **Setuju** dan **Tolak**.
- **Kriteria penerimaan:** aksi tingkat `tanya_dulu` tidak berjalan sebelum pengguna menekan setuju (baik lewat dialog UI maupun tombol notifikasi).

### 6.5 Skill (F-10, F-11)

- Setelah tugas berhasil, muncul tombol **Simpan sebagai skill**. Skill berisi: nama, deskripsi singkat (`when_to_use`), langkah-langkah (`steps`), hal yang harus dihindari (`pitfalls`), dan langkah verifikasi (`verification`).
- **Katalog Skill Dua Tahap:** Daftar ringkas *header* skill yang aktif (hanya `id`, `name`, dan `when_to_use`) disuntikkan ke prompt sistem. Jika relevan dengan permintaan pengguna, model memanggil tool `baca_skill(id)` untuk memuat langkah lengkapnya.
- Pengguna dapat mengedit, menonaktifkan, dan menghapus skill.

### 6.6 Tugas terjadwal dan notifikasi (F-12, F-13)

- Pengguna (atau agent lewat tool `kelola_tugas_terjadwal`) dapat membuat tugas berulang dalam format cron.
- Aplikasi meminta izin runtime `POST_NOTIFICATIONS` (Android 13+) agar hasil tugas dan permintaan persetujuan dapat tampil.
- Penghitung waktu mendaftarkan `WorkManager` (`NetworkType.CONNECTED`, mengizinkan Wi-Fi maupun data seluler) untuk tugas berkala, atau `AlarmManager` dengan izin `SCHEDULE_EXACT_ALARM` untuk pengingat yang wajib tepat waktu.
- **Penanganan Batas Eksekusi:** `TaskWorker` diberi batas waktu *timeout* internal (misal 3 menit) dan batas langkah agent loop lebih pendek (maksimal 4 langkah) agar selesai dengan aman sebelum dihentikan paksa oleh OS Android.
- **Kriteria penerimaan:** tugas berjalan walau aplikasi ditutup (dengan optimasi baterai dimatikan); hasil rangkuman muncul sebagai notifikasi tepat waktu.

### 6.7 Backup dan Ekspor (F-15)

- Tersedia sejak **Fase 1** untuk melindungi data pengguna selama siklus pengembangan.
- Ekspor riwayat percakapan, memori, skill, catatan, dan pengaturan non-rahasia ke satu bundel JSON/ZIP. Catatan juga dapat diekspor ke format Markdown (`.md`).
- API key **tidak pernah** ikut diekspor.

## 7. Kebutuhan non-fungsional

- **Privasi:** data lokal; satu-satunya data yang keluar adalah isi prompt ke provider yang dipilih pengguna.
- **Keamanan:** Enkripsi API key menggunakan **Android Keystore (`AES/GCM/NoPadding`) + Jetpack DataStore**; tanpa analitik pihak ketiga.
- **Performa & Prompt Caching:** Urutan penyusunan prompt menaruh bagian statis (instruksi sistem, katalog skill, profil) di paling awal agar otomatis memanfaatkan fitur *Prompt Caching* dari provider (seperti OpenAI, Anthropic, DeepSeek) guna memangkas biaya dan latensi.
- **Ketahanan:** gagal jaringan ditangani dengan pesan jelas dan opsi coba lagi; tidak ada crash pada respons provider yang rusak.
- **Keterujian:** logika agent berada di paket Kotlin murni (tanpa dependensi `android.*`) agar bisa diuji unit secara cepat di JVM CI.

## 8. Arsitektur

Aplikasi satu modul Gradle (`app`) dengan pemisahan paket yang rapi.

```
app/src/main/java/.../
  ui/          Compose screens: chat, memory, skills, tasks, settings
  agent/       AgentLoop, PromptBuilder, ToolRegistry, ApprovalGate, Compactor (Pure Kotlin)
  llm/         LlmProvider (interface), OpenAiCompatProvider, AnthropicProvider, GeminiProvider, SseParser (Pure Kotlin)
  memory/      MemoryRepository, MemoryExtractor, Summarizer (Pure Kotlin interfaces)
  skills/      SkillRepository, SkillMatcher (Pure Kotlin)
  tools/       NoteTool, AlarmTool, CalendarTool, ScheduleTool, ChatSearchTool, SkillReadTool, FileReadTool
  files/       ImageCompressor, DocumentTextExtractor, TextChunker
  scheduler/   CronParser (Pure Kotlin), TaskScheduler, TaskWorker, ApprovalReceiver
  data/        Room: entities (@Fts4), DAOs, database (exportSchema = true), migrations
  security/    CryptoManager (Android Keystore AES-GCM) + SecretDataStore
```

## 9. Model data (Room)

Menggunakan `@Fts4` bawaan Room secara bertahap (dimulai dari `messages` di Fase 1, lalu `memories` dan `notes` di fase berikutnya).

| Tabel | Kolom utama |
| --- | --- |
| `conversations` | `id`, `title`, `provider_id`, `model`, `summary`, `total_tokens`, `created_at`, `updated_at` |
| `messages` | `id`, `conversation_id`, `role`, `content`, `tool_calls_json`, `prompt_tokens`, `completion_tokens`, `is_compacted`, `created_at` (+ indeks `@Fts4`) |
| `attachments` | `id`, `message_id`, `uri`, `file_name`, `mime_type`, `size_bytes`, `extracted_text` |
| `memories` | `id`, `text`, `category`, `tags`, `source_message_id`, `enabled`, `created_at`, `updated_at` (+ indeks `@Fts4`) |
| `profile` | `id`, `summary_text`, `updated_at` |
| `skills` | `id`, `name`, `when_to_use`, `steps`, `pitfalls`, `verification`, `enabled`, `use_count` |
| `notes` | `id`, `title`, `body`, `created_at` (+ indeks `@Fts4`) |
| `scheduled_tasks` | `id`, `prompt`, `cron`, `enabled`, `last_run_at`, `next_run_at` |
| `pending_approvals` | `id`, `task_id`, `conversation_id`, `tool_name`, `arguments_json`, `status`, `created_at` |
| `providers` | `id`, `name`, `type`, `base_url`, `default_model`, `utility_model`, `supports_vision` (API key di SecretDataStore) |
| `action_log` | `id`, `tool`, `arguments_json`, `result`, `approved`, `execution_context` (`FOREGROUND`/`BACKGROUND`), `created_at` |

## 10. Agent loop dan penyusunan prompt

Urutan konteks di `PromptBuilder` dirancang dari yang paling statis ke paling dinamis untuk memaksimalkan *Prompt Caching* API:

1. **Prefix Statis (Cacheable):**
   - Instruksi sistem tetap & daftar definisi tool.
   - Profil pengguna ringkas (maks 300 token).
   - Katalog *header* skill aktif (`id`, `name`, `when_to_use`).
2. **Konteks Dinamis Sesi:**
   - Memori relevan dari hasil pencarian `@Fts4` (maks 800 token).
   - Ringkasan percakapan saat ini (`conversations.summary` hasil `/compact`, jika ada).
   - Pesan-pesan terbaru yang belum di-*compact* (`is_compacted = false`) beserta lampiran gambar/teksnya.

Alur loop:
1. Kirim prompt ke model.
2. Catat pemakaian token asli dari objek `usage` pada respons API ke tabel `messages` dan `conversations`.
3. Jika model memanggil tool, periksa tingkat izin dan konteks (`FOREGROUND` vs `BACKGROUND`).
4. Jalankan tool yang diizinkan dan kembalikan hasilnya ke model (maksimal 8 langkah di foreground, 4 langkah di background).
5. Jika total token riwayat aktif melewati ambang 70% anggaran konteks, picu ekstraksi memori lalu jalankan `/compact` otomatis di latar belakang.

## 11. Keamanan, privasi, dan penandatanganan APK

- **Penandatanganan APK Konsisten (Wajib Sejak Fase 0):**
  - Agar APK hasil build GitHub Actions selalu bisa dipasang langsung di atas aplikasi lama (*update in-place*) tanpa harus *uninstall* dan kehilangan data lokal, seluruh build di CI menandatangani APK menggunakan **satu Keystore tetap** yang disimpan di GitHub Actions Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
- **Kebijakan Repositori Publik:**
  - Aktifkan **GitHub Secret Scanning** dan **Push Protection**.
  - `.gitignore` memblokir `*.jks`, `*.keystore`, `local.properties`, `.env`, dan file backup.
- **Izin Runtime:** `POST_NOTIFICATIONS` (notifikasi tugas), `READ_CALENDAR`/`WRITE_CALENDAR` (kalender), dan `SCHEDULE_EXACT_ALARM` (pengingat presisi). Photo Picker tidak memerlukan izin penyimpanan.

## 12. Environment pengembangan

### 12.1 Prinsip

- Kode ditulis dan di-commit dari **Termux** menggunakan **AI agent CLI**.
- **Kompilasi penuh hanya di GitHub Actions** (memanfaatkan menit CI gratis dari repositori publik).
- **Pemeriksaan di Termux:** Jika `openjdk-17` terpasang di Termux, agent CLI dapat menjalankan `ktlint` untuk menangkap kesalahan format/parsing dasar sebelum push. Namun, kebenaran tipe data dan referensi tetap diverifikasi oleh kompilator Kotlin di CI.

### 12.2 Aturan untuk AI agent CLI (isi `AGENTS.md`)

- Jangan menjalankan `./gradlew` di Termux; semua kompilasi dan pengujian unit dilakukan lewat GitHub Actions.
- Kerjakan perubahan dalam potongan kecil (satu fitur/perbaikan per commit) dan sertakan unit test untuk logika murni.
- Paket `agent/`, `llm/`, `memory/`, `skills/`, dan `scheduler/CronParser` dilarang mengimpor `android.*` agar bisa diuji cepat dengan JUnit di JVM.
- Gunakan KSP (`com.google.devtools.ksp`) untuk Room dan Hilt.
- Aktifkan `exportSchema = true` pada database Room dan commit file JSON skema ke `app/schemas/`.
- Jangan pernah menulis API key, rahasia, atau keystore ke dalam kode sumber.

### 12.3 Contoh workflow CI (`.github/workflows/build.yml`)

```yaml
name: Android CI

on:
  push:
    branches: [main]
  pull_request:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Setup Gradle with cache
        uses: gradle/actions/setup-gradle@v4

      - name: Decode Keystore from Secrets
        if: env.KEYSTORE_BASE64 != ''
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "$KEYSTORE_BASE64" | base64 -d > app/signing.keystore

      - name: Check Kotlin style (ktlint)
        run: ./gradlew ktlintCheck

      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest

      - name: Run Android Lint
        run: ./gradlew lintDebug

      - name: Assemble Signed Debug APK
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleDebug

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-signed
          path: app/build/outputs/apk/debug/*.apk
```

## 13. Stack teknologi

| Area | Pilihan |
| --- | --- |
| Bahasa | Kotlin (2.0+) |
| Pemrosesan Anotasi | KSP (Kotlin Symbol Processing) |
| UI | Jetpack Compose + Material 3 |
| Arsitektur & DI | MVVM, StateFlow, Hilt |
| HTTP dan streaming | OkHttp + `okhttp-sse` |
| JSON | `kotlinx.serialization` |
| Ekstraksi Dokumen | `pdfbox-android` (untuk PDF berteks) + parser teks bawaan |
| Database & Pencarian | Room + `@Fts4` (`exportSchema = true`) |
| Pengaturan & Rahasia | Jetpack DataStore + Android Keystore (`AES/GCM/NoPadding`) |
| Jadwal | WorkManager (`NetworkType.CONNECTED`), AlarmManager (`SCHEDULE_EXACT_ALARM`) |
| Tes & Lint | JUnit, MockK, Turbine, MockWebServer, `ktlint-gradle`, Android Lint |
| CI/CD | GitHub Actions (Repositori Publik + Persistent Signing Key) |

## 14. Milestone

| Fase | Isi | Hasil yang dapat diuji |
| --- | --- | --- |
| **0** | Repositori publik, Gradle wrapper, Keystore tetap di GitHub Secrets, CI hijau | APK kosong bertanda tangan tetap; update APK tidak menghapus data |
| **1** | Chat, `OpenAiCompatProvider`, streaming SSE, **Chat Baru**, riwayat `@Fts4`, Keystore API key, **Backup/Restore JSON** | Mengobrol stabil, ganti sesi chat, dan backup/restore riwayat berhasil |
| **2** | **Compact (`/compact` & otomatis)**, indikator token `usage`, ekstraksi memori (`ADD`/`UPDATE`/`DELETE`), layar memori & profil | Obrolan panjang dirangkum otomatis dan fakta diingat lintas sesi |
| **3** | **Upload foto (vision) & berkas teks/PDF**, tools (catatan, alarm, kalender, berkas), persetujuan UI, skill manual | Kirim foto/PDF ke chat, agent membuat alarm/catatan & memakai skill |
| **4** | Tugas terjadwal, tool `kelola_tugas_terjadwal`, `pending_approvals` via notifikasi interaktif | Agent menjadwalkan rangkuman pagi dan mengirim notifikasi tepat waktu |
| **5** | Provider Anthropic/Gemini native, usulan skill otomatis, input suara & TTS | Skill diusulkan otomatis oleh agent & interaksi suara berjalan |
| **Lanjut** | Klien MCP, model lokal (llama.cpp), baca notifikasi, OCR PDF scan | Sesuai kebutuhan lanjutan |

## 15. Metrik keberhasilan

- Aplikasi dipakai harian oleh pemilik selama 2 minggu berturut-turut tanpa membuka Termux untuk menjalankan bot.
- Setiap rilis APK baru dari GitHub Actions dapat langsung diinstal menimpa aplikasi lama tanpa kehilangan database lokal.
- Minimal 85 persen tugas terjadwal berjalan tepat waktu dengan optimasi baterai dimatikan.
- Fitur `/compact` dan *prompt caching* menjaga pemakaian token per pesan tetap terkendali walau percakapan berlangsung puluhan giliran.
- Target waktu build CI rata-rata di kisaran 3–5 menit setelah cache Gradle aktif.

## 16. Risiko dan mitigasi

| Risiko | Mitigasi |
| --- | --- |
| APK baru gagal diinstal di atas APK lama (data hilang) | Gunakan satu keystore tetap di GitHub Secrets sejak Fase 0 dan sediakan fitur Backup JSON sejak Fase 1 |
| Konteks percakapan panjang membengkak & mahal | Fitur `/compact` otomatis pada 70% batas token, penyusunan prompt ramah *caching*, dan pemakaian model utilitas murah |
| Lampiran foto/PDF menguras kuota token | Kompresi & *resize* gambar otomatis sebelum dikirim; berkas teks besar dibaca per bagian lewat tool `baca_berkas` |
| Repositori publik berisiko membocorkan rahasia | GitHub Push Protection aktif, aturan ketat di `AGENTS.md`, rahasia hanya di GitHub Secrets |
| Tugas latar belakang dihentikan paksa oleh OS | Batas *timeout* 3 menit & maksimal 4 langkah loop pada `TaskWorker`; aksi berisiko masuk ke `pending_approvals` |
| Memori membengkak atau saling bertentangan | Operasi deduplikasi (`ADD`/`UPDATE`/`DELETE`) saat ekstraksi memori |