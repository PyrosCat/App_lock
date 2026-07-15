package com.applock.vault

import java.util.Locale

/**
 * Mime-type helpers for the vault (FR-109..111). Pure Kotlin so the
 * classification rules are JVM-testable; deliberately independent of
 * android.webkit.MimeTypeMap (unavailable off-device).
 */
object VaultFileTypes {

    enum class Category { IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER }

    fun categoryOf(mimeType: String): Category {
        val mime = mimeType.lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") -> Category.IMAGE
            mime.startsWith("video/") -> Category.VIDEO
            mime.startsWith("audio/") -> Category.AUDIO
            mime in DOCUMENT_MIMES || mime.startsWith("text/") -> Category.DOCUMENT
            mime in ARCHIVE_MIMES -> Category.ARCHIVE
            else -> Category.OTHER
        }
    }

    /** Fallback when the provider reports no mime type (FR-111 formats first). */
    fun mimeForName(displayName: String): String {
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return EXTENSION_MIMES[ext] ?: "application/octet-stream"
    }

    private val DOCUMENT_MIMES = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )

    private val ARCHIVE_MIMES = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-7z-compressed",
        "application/x-rar-compressed",
        "application/gzip",
    )

    private val EXTENSION_MIMES = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip",
    )
}
