package com.dai.flipper.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dai.flipper.domain.model.*
import com.dai.flipper.ui.theme.*
import com.dai.flipper.ui.viewmodel.AuditFilterType
import com.dai.flipper.ui.viewmodel.AuditViewModel
import com.dai.flipper.ui.viewmodel.CommandTimelineItem
import com.dai.flipper.ui.viewmodel.CommandTraceStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    viewModel: AuditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Audit Log", fontWeight = FontWeight.Bold)
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear all logs") },
                            onClick = {
                                viewModel.clearAuditLog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteForever, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear logs older than 7 days") },
                            onClick = {
                                viewModel.clearOldEntries(7)
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.CleaningServices, contentDescription = null)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ReplayStatusBanner(
                message = state.replayMessage,
                onDismiss = { viewModel.clearReplayMessage() }
            )

            state.pendingReplayApproval?.let { approval ->
                ReplayApprovalCard(
                    approval = approval,
                    onApprove = { viewModel.approveReplay() },
                    onReject = { viewModel.rejectReplay() }
                )
            }

            CommandTimelineSection(
                timeline = state.timeline,
                replayingTraceId = state.replayingTraceId,
                onReplay = { traceId -> viewModel.replayTrace(traceId) }
            )

            // Filter chips
            FilterChipRow(
                selectedFilter = state.filterType,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            Text(
                text = "Raw Audit Entries",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold
            )

            if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No audit entries",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries) { entry ->
                        AuditEntryItem(
                            entry = entry,
                            onClick = { viewModel.selectEntry(entry) }
                        )
                    }
                }
            }
        }

        // Detail sheet
        state.selectedEntry?.let { entry ->
            AuditDetailSheet(
                entry = entry,
                onDismiss = { viewModel.clearSelection() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    selectedFilter: AuditFilterType,
    onFilterSelected: (AuditFilterType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == AuditFilterType.ALL,
            onClick = { onFilterSelected(AuditFilterType.ALL) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selectedFilter == AuditFilterType.COMMANDS,
            onClick = { onFilterSelected(AuditFilterType.COMMANDS) },
            label = { Text("Commands") }
        )
        FilterChip(
            selected = selectedFilter == AuditFilterType.APPROVALS,
            onClick = { onFilterSelected(AuditFilterType.APPROVALS) },
            label = { Text("Approvals") }
        )
        FilterChip(
            selected = selectedFilter == AuditFilterType.ERRORS,
            onClick = { onFilterSelected(AuditFilterType.ERRORS) },
            label = { Text("Errors") }
        )
    }
}

@Composable
private fun ReplayStatusBanner(
    message: String?,
    onDismiss: () -> Unit
) {
    if (message.isNullOrBlank()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun ReplayApprovalCard(
    approval: PendingApproval,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = RiskMedium.copy(alpha = 0.16f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Replay Approval Required",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = approval.command.action.name.replace('_', ' '),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onApprove) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve")
                }
                OutlinedButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun CommandTimelineSection(
    timeline: List<CommandTimelineItem>,
    replayingTraceId: String?,
    onReplay: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Command Timeline",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (timeline.isEmpty()) {
            Text(
                text = "No command traces yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            timeline.take(20).forEach { item ->
                CommandTimelineItemCard(
                    item = item,
                    isReplaying = replayingTraceId == item.traceId,
                    onReplay = { onReplay(item.traceId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CommandTimelineItemCard(
    item: CommandTimelineItem,
    isReplaying: Boolean,
    onReplay: () -> Unit
) {
    val (icon, tint) = when (item.status) {
        CommandTraceStatus.RECEIVED -> Icons.Default.CallReceived to MaterialTheme.colorScheme.primary
        CommandTraceStatus.WAITING_APPROVAL -> Icons.Default.PendingActions to RiskMedium
        CommandTraceStatus.EXECUTED -> Icons.Default.CheckCircle to DaiAccent
        CommandTraceStatus.FAILED -> Icons.Default.Error to RiskHigh
        CommandTraceStatus.BLOCKED -> Icons.Default.Block to RiskBlocked
        CommandTraceStatus.DENIED -> Icons.Default.ThumbDown to RiskMedium
        CommandTraceStatus.TIMED_OUT -> Icons.Default.TimerOff to RiskHigh
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.command.action.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${item.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            item.error?.takeIf { it.isNotBlank() }?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = err.take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = RiskHigh
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReplay,
                enabled = !isReplaying
            ) {
                if (isReplaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Replaying...")
                } else {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Replay")
                }
            }
        }
    }
}

@Composable
private fun AuditEntryItem(
    entry: AuditEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(getAuditColor(entry.actionType)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getAuditIcon(entry.actionType),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatActionType(entry.actionType),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(entry.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    entry.riskLevel?.let { risk ->
                        Spacer(modifier = Modifier.width(8.dp))
                        RiskBadge(risk)
                    }
                }
            }

            // Approval indicator
            entry.userApproved?.let { approved ->
                Icon(
                    if (approved) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (approved) DaiAccent else RiskHigh,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RiskBadge(riskLevel: RiskLevel) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = when (riskLevel) {
            RiskLevel.LOW -> RiskLow
            RiskLevel.MEDIUM -> RiskMedium
            RiskLevel.HIGH -> RiskHigh
            RiskLevel.BLOCKED -> RiskBlocked
        }.copy(alpha = 0.2f)
    ) {
        Text(
            text = riskLevel.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = when (riskLevel) {
                RiskLevel.LOW -> RiskLow
                RiskLevel.MEDIUM -> RiskMedium
                RiskLevel.HIGH -> RiskHigh
                RiskLevel.BLOCKED -> RiskBlocked
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditDetailSheet(
    entry: AuditEntry,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatActionType(entry.actionType),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // Command details
            entry.command?.let { command ->
                Text(
                    "Command",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DetailRow("Action", command.action.name)
                        command.args.path?.let {
                            DetailRow("Path", it)
                        }
                        DetailRow("Justification", command.justification)
                        DetailRow("Expected Effect", command.expectedEffect)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Result details
            entry.result?.let { result ->
                Text(
                    "Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DetailRow("Success", if (result.success) "Yes" else "No")
                        result.error?.let {
                            DetailRow("Error", it)
                        }
                        DetailRow("Execution Time", "${result.executionTimeMs}ms")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Metadata
            if (entry.metadata.isNotEmpty()) {
                Text(
                    "Metadata",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        entry.metadata.forEach { (key, value) ->
                            DetailRow(key, value)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun getAuditIcon(actionType: AuditActionType) = when (actionType) {
    AuditActionType.COMMAND_RECEIVED -> Icons.Default.CallReceived
    AuditActionType.COMMAND_EXECUTED -> Icons.Default.CheckCircle
    AuditActionType.COMMAND_FAILED -> Icons.Default.Error
    AuditActionType.COMMAND_BLOCKED -> Icons.Default.Block
    AuditActionType.APPROVAL_REQUESTED -> Icons.Default.HelpOutline
    AuditActionType.APPROVAL_GRANTED -> Icons.Default.ThumbUp
    AuditActionType.APPROVAL_DENIED -> Icons.Default.ThumbDown
    AuditActionType.APPROVAL_TIMEOUT -> Icons.Default.Timer
    AuditActionType.PERMISSION_GRANTED -> Icons.Default.Key
    AuditActionType.PERMISSION_REVOKED -> Icons.Default.NoEncryption
    AuditActionType.PERMISSION_EXPIRED -> Icons.Default.Schedule
    AuditActionType.DEVICE_CONNECTED -> Icons.Default.BluetoothConnected
    AuditActionType.DEVICE_DISCONNECTED -> Icons.Default.BluetoothDisabled
    AuditActionType.SESSION_STARTED -> Icons.Default.PlayArrow
    AuditActionType.SESSION_ENDED -> Icons.Default.Stop
    AuditActionType.AI_REQUEST -> Icons.Default.Upload
    AuditActionType.AI_RESPONSE -> Icons.Default.Download
    AuditActionType.ERROR -> Icons.Default.Warning
}

private fun getAuditColor(actionType: AuditActionType) = when (actionType) {
    AuditActionType.COMMAND_EXECUTED,
    AuditActionType.APPROVAL_GRANTED,
    AuditActionType.PERMISSION_GRANTED,
    AuditActionType.DEVICE_CONNECTED -> DaiAccent

    AuditActionType.COMMAND_FAILED,
    AuditActionType.APPROVAL_DENIED,
    AuditActionType.ERROR -> RiskHigh

    AuditActionType.COMMAND_BLOCKED -> RiskBlocked

    AuditActionType.APPROVAL_REQUESTED,
    AuditActionType.APPROVAL_TIMEOUT -> RiskMedium

    else -> DaiSecondary
}

private fun formatActionType(actionType: AuditActionType): String {
    return actionType.name
        .replace("_", " ")
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
