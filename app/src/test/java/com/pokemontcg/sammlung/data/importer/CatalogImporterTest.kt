package com.pokemontcg.sammlung.data.importer

import android.content.Context
import com.pokemontcg.sammlung.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class CatalogImporterTest {

    @Test
    fun cachedCatalogIsUsableOnlyWhenSchemaAndRowsMatch() {
        val importer = CatalogImporter(
            Mockito.mock(Context::class.java),
            Mockito.mock(AppDatabase::class.java)
        )

        assertTrue(importer.isCachedCatalogUsable(17, 17, 1, 1))
        assertEquals(false, importer.isCachedCatalogUsable(-1, 17, 1, 1))
        assertEquals(false, importer.isCachedCatalogUsable(16, 17, 1, 1))
        assertEquals(false, importer.isCachedCatalogUsable(17, 17, 0, 1))
        assertEquals(false, importer.isCachedCatalogUsable(17, 17, 1, 0))
    }

    @Test
    fun testParseManifestJson() {
        val json = """
            {
                "version": "2.0.0",
                "schemaVersion": 17,
                "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "size": 1024,
                "builtAt": 1700000000000,
                "url": "https://example.com/catalog.sqlite"
            }
        """.trimIndent()

        val mockContext = Mockito.mock(Context::class.java)
        val mockDb = Mockito.mock(AppDatabase::class.java)
        val dummyImporter = CatalogImporter(mockContext, mockDb)

        val manifest = dummyImporter.parseManifestJson(json)
        assertEquals("2.0.0", manifest.version)
        assertEquals(17, manifest.schemaVersion)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", manifest.sha256)
        assertEquals(1024L, manifest.size)
        assertEquals("https://example.com/catalog.sqlite", manifest.url)
    }

    @Test
    fun testParseManifestRejectsMissingIntegrityFields() {
        val mockContext = Mockito.mock(Context::class.java)
        val mockDb = Mockito.mock(AppDatabase::class.java)
        val importer = CatalogImporter(mockContext, mockDb)
        val malformed = listOf(
            "{\"version\":\"2.0.0\",\"schemaVersion\":17,\"size\":10,\"builtAt\":1700000000000,\"url\":\"https://example.com/catalog.sqlite\"}",
            "{\"version\":\"2.0.0\",\"schemaVersion\":17,\"sha256\":\"bad\",\"size\":10,\"builtAt\":1700000000000,\"url\":\"https://example.com/catalog.sqlite\"}",
            "{\"version\":\"2.0.0\",\"schemaVersion\":17,\"sha256\":\"${"a".repeat(64)}\",\"size\":0,\"builtAt\":1700000000000,\"url\":\"https://example.com/catalog.sqlite\"}",
            "{\"version\":\"2.0.0\",\"schemaVersion\":17,\"sha256\":\"${"a".repeat(64)}\",\"size\":10,\"builtAt\":1700000000000,\"url\":\"http://example.com/catalog.sqlite\"}",
            "{\"version\":\"2.0.0\",\"schemaVersion\":17,\"sha256\":\"${"a".repeat(64)}\",\"size\":10,\"builtAt\":1700000000000,\"url\":\"https://user:password@example.com/catalog.sqlite\"}",
            "{\"version\":\"2.0.0\",\"schemaVersion\":17,\"sha256\":\"${"a".repeat(64)}\",\"size\":10,\"builtAt\":1700000000000,\"url\":\"https://example.com/catalog.sqlite#fragment\"}",
            "{\"version\":\"2.0.0\",\"schemaVersion\":17.9,\"sha256\":\"${"a".repeat(64)}\",\"size\":10,\"builtAt\":1700000000000,\"url\":\"https://example.com/catalog.sqlite\"}",
            "{\"version\":\"2.0.0\",\"schemaVersion\":17,\"sha256\":\"${"a".repeat(64)}\",\"size\":10.5,\"builtAt\":1700000000000,\"url\":\"https://example.com/catalog.sqlite\"}",
            "{\"version\":2.0,\"schemaVersion\":17,\"sha256\":\"${"a".repeat(64)}\",\"size\":10,\"builtAt\":1700000000000,\"url\":\"https://example.com/catalog.sqlite\"}"
        )
        malformed.forEach { json ->
            try {
                importer.parseManifestJson(json)
                fail("Malformed manifest must be rejected: $json")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message?.isNotBlank() == true)
            }
        }
    }


    @Test
    fun testReadTextLimitedRejectsOversizedManifest() {
        val importer = CatalogImporter(
            Mockito.mock(Context::class.java),
            Mockito.mock(AppDatabase::class.java)
        )
        try {
            importer.readTextLimited(ByteArrayInputStream("12345".toByteArray()), 4L)
            fail("Oversized manifest must be rejected")
        } catch (expected: IOException) {
            assertTrue(expected.message?.contains("exceeds") == true)
        }
    }

    @Test
    fun testCopyStreamLimitedRejectsOversizedCatalogPayload() {
        val importer = CatalogImporter(
            Mockito.mock(Context::class.java),
            Mockito.mock(AppDatabase::class.java)
        )
        val output = ByteArrayOutputStream()
        try {
            importer.copyStreamLimited(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)), output, 4L)
            fail("Oversized catalog must be rejected")
        } catch (expected: IOException) {
            assertTrue(expected.message?.contains("exceeds") == true)
            assertTrue("Only the permitted bytes may be written", output.size() <= 4)
        }
    }

    @Test
    fun testCopyStreamLimitedAcceptsPayloadExactlyAtLimit() {
        val importer = CatalogImporter(
            Mockito.mock(Context::class.java),
            Mockito.mock(AppDatabase::class.java)
        )
        val input = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()

        importer.copyStreamLimited(ByteArrayInputStream(input), output, input.size.toLong())

        assertEquals(input.toList(), output.toByteArray().toList())
    }

    @Test
    fun testCopyStreamLimitedRejectsNegativeLimit() {
        val importer = CatalogImporter(
            Mockito.mock(Context::class.java),
            Mockito.mock(AppDatabase::class.java)
        )
        try {
            importer.copyStreamLimited(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), -1L)
            fail("Negative size limit must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("maxBytes") == true)
        }
    }

    @Test
    fun testManifestRejectsCatalogSizeAboveLimit() {
        val importer = CatalogImporter(
            Mockito.mock(Context::class.java),
            Mockito.mock(AppDatabase::class.java)
        )
        val json = """{"version":"2.0.0","schemaVersion":17,"sha256":"${"a".repeat(64)}","size":536870913,"builtAt":1700000000000,"url":"https://example.com/catalog.sqlite"}"""
        try {
            importer.parseManifestJson(json)
            fail("Catalog above the configured size limit must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.isNotBlank() == true)
        }
    }

    @Test
    fun testSha256Computation() {
        val tempFile = File.createTempFile("test_sha256", ".txt")
        tempFile.writeText("hello world")

        val mockContext = Mockito.mock(Context::class.java)
        val mockDb = Mockito.mock(AppDatabase::class.java)
        val dummyImporter = CatalogImporter(mockContext, mockDb)

        val hash = dummyImporter.computeSha256(tempFile)
        tempFile.delete()

        // "hello world" SHA-256 = b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash)
    }
}
