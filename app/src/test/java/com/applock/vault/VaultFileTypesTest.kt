package com.applock.vault

import com.applock.vault.VaultFileTypes.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultFileTypesTest {

    @Test
    fun `media mime prefixes map to their categories`() {
        assertEquals(Category.IMAGE, VaultFileTypes.categoryOf("image/jpeg"))
        assertEquals(Category.IMAGE, VaultFileTypes.categoryOf("IMAGE/PNG"))
        assertEquals(Category.VIDEO, VaultFileTypes.categoryOf("video/mp4"))
        assertEquals(Category.AUDIO, VaultFileTypes.categoryOf("audio/mpeg"))
    }

    @Test
    fun `FR-111 document formats are documents`() {
        assertEquals(Category.DOCUMENT, VaultFileTypes.categoryOf("application/pdf"))
        assertEquals(
            Category.DOCUMENT,
            VaultFileTypes.categoryOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ),
        )
        assertEquals(
            Category.DOCUMENT,
            VaultFileTypes.categoryOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ),
        )
        assertEquals(Category.DOCUMENT, VaultFileTypes.categoryOf("text/plain"))
        assertEquals(Category.ARCHIVE, VaultFileTypes.categoryOf("application/zip"))
    }

    @Test
    fun `unknown mime is OTHER`() {
        assertEquals(Category.OTHER, VaultFileTypes.categoryOf("application/x-unknown"))
        assertEquals(Category.OTHER, VaultFileTypes.categoryOf(""))
    }

    @Test
    fun `extension fallback covers FR-111 formats`() {
        assertEquals("application/pdf", VaultFileTypes.mimeForName("statement.pdf"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            VaultFileTypes.mimeForName("letter.docx"),
        )
        assertEquals("text/plain", VaultFileTypes.mimeForName("notes.TXT"))
        assertEquals("application/zip", VaultFileTypes.mimeForName("backup.zip"))
        assertEquals("image/jpeg", VaultFileTypes.mimeForName("IMG_0001.JPG"))
    }

    @Test
    fun `no extension or unknown extension falls back to octet-stream`() {
        assertEquals("application/octet-stream", VaultFileTypes.mimeForName("README"))
        assertEquals("application/octet-stream", VaultFileTypes.mimeForName("data.xyz"))
        assertEquals("application/octet-stream", VaultFileTypes.mimeForName(""))
    }

    @Test
    fun `pathological display names do not crash classification`() {
        assertEquals("application/octet-stream", VaultFileTypes.mimeForName("name."))
        assertEquals("application/octet-stream", VaultFileTypes.mimeForName("..."))
        assertEquals("application/octet-stream", VaultFileTypes.mimeForName(".hidden"))
        assertEquals("image/png", VaultFileTypes.mimeForName("фото 测试 🎉.png"))
        assertEquals("image/jpeg", VaultFileTypes.mimeForName("a.b.c.many.dots.JPEG".lowercase()))
        assertEquals(
            "application/octet-stream",
            VaultFileTypes.mimeForName("x".repeat(10_000) + ".unknownext"),
        )
    }

    @Test
    fun `multi-part extensions use only the last segment`() {
        // .tar.gz → "gz" (not mapped) → octet-stream, but the gzip MIME itself is ARCHIVE.
        assertEquals("application/octet-stream", VaultFileTypes.mimeForName("backup.tar.gz"))
        assertEquals(Category.ARCHIVE, VaultFileTypes.categoryOf("application/gzip"))
    }
}
