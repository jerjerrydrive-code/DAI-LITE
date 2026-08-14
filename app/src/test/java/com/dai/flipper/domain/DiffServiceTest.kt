package com.dai.flipper.domain

import com.dai.flipper.domain.service.DiffLineType
import com.dai.flipper.domain.service.DiffService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the diff computation service.
 * Validates correct diff generation for Flipper file modifications.
 */
class DiffServiceTest {

    private lateinit var diffService: DiffService

    @Before
    fun setup() {
        diffService = DiffService()
    }

    @Test
    fun `compute diff for frequency change in SubGHz file`() {
        val original = """
            Filetype: Flipper SubGhz Key File
            Version: 1
            Frequency: 390000000
            Preset: FuriHalSubGhzPresetOok650Async
            Protocol: Princeton
        """.trimIndent()

        val modified = """
            Filetype: Flipper SubGhz Key File
            Version: 1
            Frequency: 315000000
            Preset: FuriHalSubGhzPresetOok650Async
            Protocol: Princeton
        """.trimIndent()

        val diff = diffService.computeDiff(original, modified)

        assertEquals(1, diff.linesAdded)
        assertEquals(1, diff.linesRemoved)
        assertTrue(diff.unifiedDiff.contains("-Frequency: 390000000"))
        assertTrue(diff.unifiedDiff.contains("+Frequency: 315000000"))
    }

    @Test
    fun `compute diff for adding IR button`() {
        val original = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 08 00 00 00
        """.trimIndent()

        val modified = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 08 00 00 00
            #
            name: Mute
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 09 00 00 00
        """.trimIndent()

        val diff = diffService.computeDiff(original, modified)

        assertTrue(diff.linesAdded > 0)
        assertEquals(0, diff.linesRemoved)
        assertTrue(diff.unifiedDiff.contains("+name: Mute"))
    }

    @Test
    fun `compute diff for new file creation`() {
        val newContent = """
            Filetype: Flipper SubGhz Key File
            Version: 1
            Frequency: 433920000
        """.trimIndent()

        val diff = diffService.computeDiff(null, newContent)

        assertEquals(3, diff.linesAdded)
        assertEquals(0, diff.linesRemoved)
        assertNull(diff.originalContent)
    }

    @Test
    fun `compute diff for BadUSB script modification`() {
        val original = """
            REM Original script
            DELAY 1000
            GUI r
            STRING notepad
            ENTER
        """.trimIndent()

        val modified = """
            REM Modified script with longer delay
            DELAY 2000
            GUI r
            STRING notepad
            ENTER
            DELAY 500
            STRING Hello World!
        """.trimIndent()

        val diff = diffService.computeDiff(original, modified)

        assertTrue(diff.linesAdded > diff.linesRemoved)
        assertTrue(diff.unifiedDiff.contains("Hello World"))
    }

    @Test
    fun `generate side by side diff`() {
        val original = """
            Line 1
            Line 2 original
            Line 3
        """.trimIndent()

        val modified = """
            Line 1
            Line 2 modified
            Line 3
        """.trimIndent()

        val diff = diffService.computeDiff(original, modified)
        val sideBySide = diffService.generateSideBySide(diff)

        assertTrue(sideBySide.isNotEmpty())

        val changedLines = sideBySide.filter { it.type == DiffLineType.CHANGED }
        assertTrue(changedLines.isNotEmpty() ||
                   sideBySide.any { it.type == DiffLineType.ADDED } &&
                   sideBySide.any { it.type == DiffLineType.REMOVED })
    }

    @Test
    fun `diff preserves line numbers`() {
        val original = """
            Line 1
            Line 2
            Line 3
            Line 4
            Line 5
        """.trimIndent()

        val modified = """
            Line 1
            Line 2
            INSERTED LINE
            Line 3
            Line 4
            Line 5
        """.trimIndent()

        val diff = diffService.computeDiff(original, modified)
        val sideBySide = diffService.generateSideBySide(diff)

        // Should have line numbers
        val withLineNumbers = sideBySide.filter {
            it.originalLineNumber != null || it.newLineNumber != null
        }
        assertTrue(withLineNumbers.isNotEmpty())
    }

    @Test
    fun `empty diff when files are identical`() {
        val content = """
            Filetype: Flipper SubGhz Key File
            Version: 1
            Frequency: 315000000
        """.trimIndent()

        val diff = diffService.computeDiff(content, content)

        assertEquals(0, diff.linesAdded)
        assertEquals(0, diff.linesRemoved)
    }

    @Test
    fun `diff handles binary-like content gracefully`() {
        val original = "Key: 00 00 00 00 00 12 34 56"
        val modified = "Key: 00 00 00 00 00 AB CD EF"

        val diff = diffService.computeDiff(original, modified)

        assertEquals(1, diff.linesAdded)
        assertEquals(1, diff.linesRemoved)
    }

    @Test
    fun `diff handles large file changes`() {
        val original = (1..100).joinToString("\n") { "Line $it" }
        val modified = (1..100).joinToString("\n") {
            if (it % 10 == 0) "Modified Line $it" else "Line $it"
        }

        val diff = diffService.computeDiff(original, modified)

        // Should have 10 changes (every 10th line)
        assertEquals(10, diff.linesAdded)
        assertEquals(10, diff.linesRemoved)
    }

    @Test
    fun `diff handles complete file replacement`() {
        val original = """
            Old content line 1
            Old content line 2
            Old content line 3
        """.trimIndent()

        val modified = """
            Completely new content
            Different structure
        """.trimIndent()

        val diff = diffService.computeDiff(original, modified)

        assertEquals(3, diff.linesRemoved)
        assertEquals(2, diff.linesAdded)
    }
}
