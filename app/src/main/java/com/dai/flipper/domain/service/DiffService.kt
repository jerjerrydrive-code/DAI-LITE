package com.dai.flipper.domain.service

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.dai.flipper.domain.model.FileDiff
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for computing file diffs.
 * Shows what will change before any write operation.
 */
@Singleton
class DiffService @Inject constructor() {

    /**
     * Compute a diff between original and new content.
     */
    fun computeDiff(originalContent: String?, newContent: String): FileDiff {
        val originalLines = originalContent?.lines() ?: emptyList()
        val newLines = newContent.lines()

        val patch = DiffUtils.diff(originalLines, newLines)

        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            "original",
            "modified",
            originalLines,
            patch,
            3 // context lines
        ).joinToString("\n")

        var linesAdded = 0
        var linesRemoved = 0

        patch.deltas.forEach { delta ->
            linesRemoved += delta.source.lines.size
            linesAdded += delta.target.lines.size
        }

        return FileDiff(
            originalContent = originalContent,
            newContent = newContent,
            linesAdded = linesAdded,
            linesRemoved = linesRemoved,
            unifiedDiff = unifiedDiff
        )
    }

    /**
     * Generate a side-by-side diff representation
     */
    fun generateSideBySide(diff: FileDiff): List<DiffLine> {
        val originalLines = diff.originalContent?.lines() ?: emptyList()
        val newLines = diff.newContent.lines()

        val patch = DiffUtils.diff(originalLines, newLines)
        val result = mutableListOf<DiffLine>()

        var originalIndex = 0
        var newIndex = 0

        for (delta in patch.deltas) {
            // Add unchanged lines before this delta
            while (originalIndex < delta.source.position) {
                result.add(
                    DiffLine(
                        type = DiffLineType.UNCHANGED,
                        originalLineNumber = originalIndex + 1,
                        newLineNumber = newIndex + 1,
                        originalContent = originalLines.getOrNull(originalIndex),
                        newContent = newLines.getOrNull(newIndex)
                    )
                )
                originalIndex++
                newIndex++
            }

            // Handle the delta
            val sourceLines = delta.source.lines
            val targetLines = delta.target.lines
            val maxLines = maxOf(sourceLines.size, targetLines.size)

            for (i in 0 until maxLines) {
                val origLine = sourceLines.getOrNull(i)
                val newLine = targetLines.getOrNull(i)

                val lineType = when {
                    origLine == null -> DiffLineType.ADDED
                    newLine == null -> DiffLineType.REMOVED
                    origLine != newLine -> DiffLineType.CHANGED
                    else -> DiffLineType.UNCHANGED
                }

                result.add(
                    DiffLine(
                        type = lineType,
                        originalLineNumber = if (origLine != null) originalIndex + i + 1 else null,
                        newLineNumber = if (newLine != null) newIndex + i + 1 else null,
                        originalContent = origLine,
                        newContent = newLine
                    )
                )
            }

            originalIndex += sourceLines.size
            newIndex += targetLines.size
        }

        // Add remaining unchanged lines
        while (originalIndex < originalLines.size || newIndex < newLines.size) {
            result.add(
                DiffLine(
                    type = DiffLineType.UNCHANGED,
                    originalLineNumber = if (originalIndex < originalLines.size) originalIndex + 1 else null,
                    newLineNumber = if (newIndex < newLines.size) newIndex + 1 else null,
                    originalContent = originalLines.getOrNull(originalIndex),
                    newContent = newLines.getOrNull(newIndex)
                )
            )
            originalIndex++
            newIndex++
        }

        return result
    }
}

data class DiffLine(
    val type: DiffLineType,
    val originalLineNumber: Int?,
    val newLineNumber: Int?,
    val originalContent: String?,
    val newContent: String?
)

enum class DiffLineType {
    UNCHANGED,
    ADDED,
    REMOVED,
    CHANGED
}
