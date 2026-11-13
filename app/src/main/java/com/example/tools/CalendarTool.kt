package com.example.tools

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.agent.AgentTool
import com.example.agent.model.ExecutionMode
import com.example.agent.model.PermissionLevel
import com.example.agent.model.ToolExecutionContext
import com.example.agent.model.ToolExecutionResult
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CalendarTool(
    private val context: Context,
) : AgentTool {
    override val name = "buat_event_kalender"
    override val description = "Menambahkan agenda/acara ke kalender Android pengguna."
    override val permissionLevel = PermissionLevel.TANYA_DULU
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string", "description" to "Judul acara"),
            "start_iso" to mapOf("type" to "string", "description" to "Waktu mulai format ISO-8601 lokal, contoh: 2026-11-14T09:00"),
            "duration_minutes" to mapOf("type" to "integer", "description" to "Durasi acara dalam menit (default 60)"),
            "description" to mapOf("type" to "string", "description" to "Deskripsi atau catatan acara"),
            "location" to mapOf("type" to "string", "description" to "Lokasi acara"),
        ),
        "required" to listOf("title", "start_iso"),
    )

    override fun summarizeCall(arguments: Map<String, Any?>): String {
        val title = arguments["title"]?.toString() ?: "Acara"
        val start = arguments["start_iso"]?.toString() ?: "-"
        return "Buat acara kalender '$title' pada $start"
    }

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val title = arguments["title"]?.toString()?.trim().orEmpty()
        val startIso = arguments["start_iso"]?.toString()?.trim().orEmpty()
        val durationMin = (arguments["duration_minutes"] as? Number)?.toLong()?.coerceAtLeast(5L) ?: 60L
        val desc = arguments["description"]?.toString().orEmpty()
        val location = arguments["location"]?.toString().orEmpty()

        if (title.isBlank() || startIso.isBlank()) {
            return ToolExecutionResult(false, "Judul acara dan waktu mulai (start_iso) wajib diisi.")
        }

        val startMillis = parseIsoMillis(startIso)
            ?: return ToolExecutionResult(false, "Format start_iso tidak dikenali: '$startIso'. Gunakan YYYY-MM-DDTHH:MM.")
        val endMillis = startMillis + durationMin * 60_000L

        val hasWritePerm = ContextCompat.checkSelfPermission(
            this.context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val hasReadPerm = ContextCompat.checkSelfPermission(
            this.context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasWritePerm && hasReadPerm) {
            val calendarId = findPrimaryCalendarId()
            if (calendarId != null) {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, desc)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                val uri = runCatching {
                    this.context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                }.getOrNull()
                if (uri != null) {
                    return ToolExecutionResult(true, "Acara '$title' berhasil ditambahkan ke kalender ($startIso).")
                }
            }
        }

        if (context.executionMode == ExecutionMode.FOREGROUND) {
            val insertIntent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, desc)
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val opened = runCatching { this.context.startActivity(insertIntent) }.isSuccess
            if (opened) {
                return ToolExecutionResult(true, "Formulir Kalender dibuka untuk menyimpan acara '$title' ($startIso).")
            }
        }

        return ToolExecutionResult(
            false,
            "Izin Kalender belum diberikan. Berikan izin Kalender di Setelan perangkat agar Aruna bisa menyimpan acara secara langsung.",
        )
    }

    private fun findPrimaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        return runCatching {
            this.context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    private fun parseIsoMillis(raw: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(raw.take(19))?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
