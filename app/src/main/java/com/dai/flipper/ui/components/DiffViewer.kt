package com.dai.flipper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dai.flipper.domain.model.FileDiff
import com.dai.flipper.domain.service.DiffLine
import com.dai.flipper.domain.service.DiffLineType
import com.dai.flipper.domain.service.DiffService
import com.dai.flipper.ui.theme.*

@Composable
fun DiffViewer(
    diff: FileDiff,
    modifier: Modifier = Modifier
) {
    val diffService = remember { DiffService() }
    val sideBySideLines = remember(diff) { diffService.generateSideBySide(diff) }

    Column(modifier = modifier) {
        // Stats header
        DiffStatsHeader(diff)

        Spacer(modifier = Modifier.height(8.dp))

        // Diff content
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                itemsIndexed(sideBySideLines) { index, line ->
                    DiffLineRow(line = line)
                    if (index < sideBySideLines.lastIndex) {
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun UnifiedDiffViewer(
    unifiedDiff: String,
    modifier: Modifier = Modifier
) {
    val lines = unifiedDiff.lines()
    val horizontalScrollState = rememberScrollState()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
                .heightIn(max = 400.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val (backgroundColor, textColor) = when {
                    line.startsWith("+++") || line.startsWith("---") -> {
                        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
                    }
                    line.startsWith("@@") -> {
                        RiskBlocked.copy(alpha = 0.2f) to RiskBlocked
                    }
                    line.startsWith("+") -> {
                        DiffAddedBackground to DiffAdded
                    }
                    line.startsWith("-") -> {
                        DiffRemovedBackground to DiffRemoved
                    }
                    else -> {
                        Color.Transparent to MaterialTheme.colorScheme.onSurface
                    }
                }

                Text(
                    text = line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun DiffStatsHeader(diff: FileDiff) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Changes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBadge(
                text = "+${diff.linesAdded}",
                color = DiffAdded
            )
            StatBadge(
                text = "-${diff.linesRemoved}",
                color = DiffRemoved
            )
        }
    }
}

@Composable
private fun StatBadge(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val backgroundColor = when (line.type) {
        DiffLineType.ADDED -> DiffAddedBackground
        DiffLineType.REMOVED -> DiffRemovedBackground
        DiffLineType.CHANGED -> DiffChanged.copy(alpha = 0.1f)
        DiffLineType.UNCHANGED -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Original side
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row {
                // Line number
                Text(
                    text = line.originalLineNumber?.toString() ?: "",
                    modifier = Modifier.width(32.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Content
                Text(
                    text = line.originalContent ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = when (line.type) {
                        DiffLineType.REMOVED, DiffLineType.CHANGED -> DiffRemoved
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        // New side
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row {
                // Line number
                Text(
                    text = line.newLineNumber?.toString() ?: "",
                    modifier = Modifier.width(32.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Content
                Text(
                    text = line.newContent ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = when (line.type) {
                        DiffLineType.ADDED, DiffLineType.CHANGED -> DiffAdded
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}
