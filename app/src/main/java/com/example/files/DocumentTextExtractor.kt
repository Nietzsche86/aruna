package com.example.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractedDocumentResult(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String,
)

class DocumentTextExtractor(private val context: Context) {

    @Volatile
    private var pdfBoxInitialized = false

    suspend fun extractFromUri(uri: Uri): ExtractedDocumentResult = withContext(Dispatchers.IO) {
        val (fileName, sizeBytes) = resolveMeta(uri)
        val mimeType = context.contentResolver.getType(uri) ?: inferMime(fileName)

        val isPdf = mimeType.contains("pdf", ignoreCase = true) ||
            fileName.endsWith(".pdf", ignoreCase = true)

        val extracted = if (isPdf) {
            extractPdfText(uri)
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }.orEmpty()
        }

        ExtractedDocumentResult(
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            extractedText = extracted.trim().ifBlank {
                "(Berkas tidak berisi teks yang dapat diekstrak; PDF hasil scan gambar tidak didukung di v1.)"
            },
        )
    }

    private fun extractPdfText(uri: Uri): String {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxInitialized = true
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                PDFTextStripper().getText(doc)
            }
        }.orEmpty()
    }

    private fun resolveMeta(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "dokumen"
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nIdx >= 0) name = cursor.getString(nIdx) ?: name
                if (sIdx >= 0) size = cursor.getLong(sIdx)
            }
        }
        return name to size
    }

    private fun inferMime(fileName: String): String = when {
        fileName.endsWith(".pdf", true) -> "application/pdf"
        fileName.endsWith(".md", true) -> "text/markdown"
        fileName.endsWith(".csv", true) -> "text/csv"
        fileName.endsWith(".json", true) -> "application/json"
        else -> "text/plain"
    }
}
