# AGENTS.md — Panduan Pengembangan Aruna

- **Kompilasi & CI:** Proyek ini dibangun dengan JDK 21 dan Gradle 9.3.1 melalui GitHub Actions (`.github/workflows/build.yml`). Jangan menjalankan `./gradlew` jika wrapper belum di-generate; gunakan `gradle` atau biarkan CI mengompilasi & menguji setiap commit.
- **Pemisahan Pure Kotlin:** Paket `agent/`, `llm/`, `memory/`, `skills/`, `files/TextChunker`, dan `scheduler/CronParser` dilarang mengimpor kelas `android.*` agar unit test JUnit berjalan cepat di JVM CI.
- **Database Room:** `exportSchema = true` aktif; selalu commit berkas JSON skema terbaru di `app/schemas/` setiap kali mengubah versi/entitas Room.
- **Keamanan Repositori Publik:** Jangan pernah menaruh API key, `.env`, atau berkas `*.jks` / `*.keystore` di dalam kode sumber. Untuk penandatanganan rilis konsisten di GitHub Actions, simpan `KEYSTORE_BASE64`, `STORE_PASSWORD`, dan `KEY_PASSWORD` (alias `upload`) di GitHub Repository Secrets.
