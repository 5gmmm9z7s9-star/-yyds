package com.example.data.importer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImportResult(
    val importedTracks: List<TrackEntity>,
    val skippedDuplicates: Int,
    val failedCount: Int,
)

class LocalAudioImporter(private val context: Context) {

    private val coversDir: File by lazy {
        File(context.filesDir, "covers").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun extractTracksFromUris(
        uris: List<Uri>,
        existingUris: Set<String>,
    ): ImportResult = withContext(Dispatchers.IO) {
        val imported = mutableListOf<TrackEntity>()
        var skipped = 0
        var failed = 0
        val seenInBatch = existingUris.toMutableSet()

        for (uri in uris) {
            val uriStr = uri.toString()
            if (!seenInBatch.add(uriStr)) {
                skipped++
                continue
            }
            takePersistablePermission(uri)
            val entity = extractMetadata(uri)
            if (entity != null) {
                imported.add(entity)
            } else {
                failed++
            }
        }
        ImportResult(imported, skipped, failed)
    }

    suspend fun extractTracksFromTreeUri(
        treeUri: Uri,
        existingUris: Set<String>,
    ): ImportResult = withContext(Dispatchers.IO) {
        takePersistablePermission(treeUri)
        val collectedUris = mutableListOf<Uri>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        collectAudioDocuments(treeUri, rootDocId, collectedUris, depth = 0)
        extractTracksFromUris(collectedUris, existingUris)
    }

    private fun takePersistablePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun collectAudioDocuments(
        treeUri: Uri,
        parentDocumentId: String,
        output: MutableList<Uri>,
        depth: Int,
    ) {
        if (depth > MAX_FOLDER_DEPTH) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        collectAudioDocuments(treeUri, docId, output, depth + 1)
                    } else if (isAudioFile(mime, name)) {
                        output.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                    }
                }
            }
        }
    }

    private fun isAudioFile(mimeType: String, fileName: String): Boolean {
        if (mimeType.startsWith("audio/", ignoreCase = true)) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private fun extractMetadata(uri: Uri): TrackEntity? {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri)
        val mimeType = resolver.getType(uri).orEmpty()

        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var coverPath: String? = null

        val retrieved = runCatching {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
                ?.takeIf { it.isNotEmpty() }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
                    ?.takeIf { it.isNotEmpty() }
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
                ?.takeIf { it.isNotEmpty() }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val embeddedArt = retriever.embeddedPicture
            if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                coverPath = saveCoverBytes(uri.toString(), embeddedArt)
            }
        }.isSuccess

        runCatching { retriever.release() }

        if (!retrieved && displayName.isNullOrBlank()) {
            return null
        }

        val cleanName = displayName
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "本地音频"

        val formatLabel = resolveFormatLabel(mimeType, displayName.orEmpty())

        return TrackEntity(
            uriString = uri.toString(),
            title = title ?: cleanName,
            artist = artist ?: "未知艺术家",
            album = album ?: "本地音频",
            durationMs = durationMs,
            formatLabel = formatLabel,
            coverPath = coverPath,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun saveCoverBytes(uriKey: String, bytes: ByteArray): String? {
        return runCatching {
            val fileName = "cover_${uriKey.hashCode().toUInt()}.jpg"
            val outFile = File(coversDir, fileName)
            if (!outFile.exists()) {
                FileOutputStream(outFile).use { it.write(bytes) }
            }
            outFile.absolutePath
        }.getOrNull()
    }

    private fun resolveFormatLabel(mimeType: String, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        if (ext in KNOWN_FORMAT_LABELS) return ext
        return when {
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "MP3"
            mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "M4A"
            mimeType.contains("wav", ignoreCase = true) -> "WAV"
            mimeType.contains("ogg", ignoreCase = true) -> "OGG"
            mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            else -> "AUDIO"
        }
    }

    companion object {
        private const val MAX_FOLDER_DEPTH = 4
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "aiff", "alac", "amr", "3gp"
        )
        private val KNOWN_FORMAT_LABELS = setOf(
            "MP3", "FLAC", "WAV", "M4A", "AAC", "OGG", "OPUS", "WMA", "AIFF", "ALAC"
        )
    }
}
