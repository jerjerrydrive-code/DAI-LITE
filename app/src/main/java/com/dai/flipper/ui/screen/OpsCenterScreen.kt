package com.dai.flipper.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dai.flipper.ble.CliCapabilityStatus
import com.dai.flipper.ble.CommandPipelineAutotuneStatus
import com.dai.flipper.ble.ConnectionCheckLevel
import com.dai.flipper.ble.ConnectionDiagnosticsReport
import com.dai.flipper.ble.ConnectionState
import com.dai.flipper.ble.FirmwareCompatibilityProfile
import com.dai.flipper.domain.model.FlipperRemoteButton
import com.dai.flipper.ui.theme.DaiBackdropBrush
import com.dai.flipper.ui.viewmodel.DeviceViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class OpsRunbook {
    LINK_HEALTH_SWEEP,
    INPUT_SMOKE_TEST,
    RECOVER_AND_SCAN
}

private data class MacroStep(
    val button: FlipperRemoteButton,
    val delayMs: Long,
    val longPress: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsCenterScreen(
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val cliStatus by viewModel.cliCapabilityStatus.collectAsState()
    val diagnostics by viewModel.connectionDiagnostics.collectAsState()
    val autotuneStatus by viewModel.autotuneStatus.collectAsState()
    val firmwareCompatibility by viewModel.firmwareCompatibility.collectAsState()
    val isRunningDiagnostics by viewModel.isRunningDiagnostics.collectAsState()
    val isSendingRemoteInput by viewModel.isSendingRemoteInput.collectAsState()
    val remoteInputStatus by viewModel.remoteInputStatus.collectAsState()

    val scope = rememberCoroutineScope()

    var runbookStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var runbookInFlight by remember { mutableStateOf(false) }

    val macroSteps = remember { mutableStateListOf<MacroStep>() }
    var macroName by rememberSaveable { mutableStateOf("ops_macro") }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var longPressMode by rememberSaveable { mutableStateOf(false) }
    var lastRecordedAtMs by remember { mutableLongStateOf(0L) }
    var replayStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var replayJob by remember { mutableStateOf<Job?>(null) }
    var isReplaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            replayJob?.cancel()
        }
    }

    fun sendAndMaybeRecord(button: FlipperRemoteButton) {
        if (isRecording) {
            val now = System.currentTimeMillis()
            val delta = if (lastRecordedAtMs == 0L) 0L else (now - lastRecordedAtMs).coerceAtMost(5_000L)
            macroSteps.add(
                MacroStep(
                    button = button,
                    delayMs = delta,
                    longPress = longPressMode
                )
            )
            lastRecordedAtMs = now
        }
        scope.launch {
            viewModel.sendRemoteButtonAwait(button, longPressMode)
        }
    }

    fun executeRunbook(runbook: OpsRunbook) {
        if (runbookInFlight) return
        scope.launch {
            runbookInFlight = true
            try {
                when (runbook) {
                    OpsRunbook.LINK_HEALTH_SWEEP -> {
                        runbookStatus = "Running link health sweep..."
                        viewModel.refreshDeviceInfo()
                        delay(250)
                        viewModel.runConnectionDiagnostics()
                        runbookStatus = "Health sweep queued. Check diagnostics results below."
                    }

                    OpsRunbook.INPUT_SMOKE_TEST -> {
                        if (connectionState !is ConnectionState.Connected) {
                            runbookStatus = "Connect a Flipper first for input smoke test."
                            return@launch
                        }
                        runbookStatus = "Sending input smoke sequence..."
                        listOf(
                            FlipperRemoteButton.BACK,
                            FlipperRemoteButton.UP,
                            FlipperRemoteButton.DOWN,
                            FlipperRemoteButton.OK
                        ).forEach { button ->
                            val sent = viewModel.sendRemoteButtonAwait(button, false)
                            if (sent.isFailure) {
                                val message = sent.exceptionOrNull()?.message ?: "unknown error"
                                runbookStatus = "Input smoke failed on ${button.label}: $message"
                                return@launch
                            }
                            delay(220)
                        }
                        runbookStatus = "Input smoke test sent: Back -> Up -> Down -> OK."
                    }

                    OpsRunbook.RECOVER_AND_SCAN -> {
                        runbookStatus = "Resetting transport and starting scan..."
                        viewModel.disconnect()
                        delay(350)
                        viewModel.startScan()
                        runbookStatus = "Recovery runbook sent. Scanning for Flippers now."
                    }
                }
            } finally {
                runbookInFlight = false
            }
        }
    }

    fun startReplay() {
        if (macroSteps.isEmpty()) {
            replayStatus = "No macro steps recorded yet."
            return
        }
        if (connectionState !is ConnectionState.Connected) {
            replayStatus = "Connect a Flipper first."
            return
        }

        replayJob?.cancel()
        replayJob = scope.launch {
            isReplaying = true
            replayStatus = "Replaying ${macroName.ifBlank { "macro" }}..."
            try {
                macroSteps.forEachIndexed { index, step ->
                    if (!isActive) return@launch
                    if (step.delayMs > 0L) delay(step.delayMs)
                    replayStatus = "Step ${index + 1}/${macroSteps.size}: ${step.button.label}"
                    val sent = viewModel.sendRemoteButtonAwait(step.button, step.longPress)
                    if (sent.isFailure) {
                        val message = sent.exceptionOrNull()?.message ?: "unknown error"
                        replayStatus = "Replay failed at step ${index + 1}: $message"
                        return@launch
                    }
                    delay(160)
                }
                replayStatus = "Replay complete (${macroSteps.size} steps)."
            } finally {
                isReplaying = false
            }
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
        isReplaying = false
        replayStatus = "Replay cancelled."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ops Center", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = { viewModel.runConnectionDiagnostics() },
                        enabled = !isRunningDiagnostics
                    ) {
                        if (isRunningDiagnostics) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Run diagnostics")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DaiBackdropBrush)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    PipelineHealthCard(
                        connectionState = connectionState,
                        connectedName = connectedDevice?.name,
                        cliStatus = cliStatus,
                        diagnostics = diagnostics,
                        autotuneStatus = autotuneStatus,
                        firmwareCompatibility = firmwareCompatibility,
                        isRunningDiagnostics = isRunningDiagnostics,
                        onRunDiagnostics = { viewModel.runConnectionDiagnostics() },
                        onRefreshInfo = { viewModel.refreshDeviceInfo() }
                    )
                }

                item {
                    RunbooksCard(
                        status = runbookStatus,
                        inFlight = runbookInFlight,
                        onRunLinkHealth = { executeRunbook(OpsRunbook.LINK_HEALTH_SWEEP) },
                        onRunInputSmoke = { executeRunbook(OpsRunbook.INPUT_SMOKE_TEST) },
                        onRunRecover = { executeRunbook(OpsRunbook.RECOVER_AND_SCAN) }
                    )
                }

                item {
                    MacroRecorderCard(
                        macroName = macroName,
                        onMacroNameChange = { macroName = it },
                        isRecording = isRecording,
                        longPressMode = longPressMode,
                        onLongPressModeChange = { longPressMode = it },
                        onStartRecording = {
                            macroSteps.clear()
                            isRecording = true
                            lastRecordedAtMs = 0L
                            replayStatus = "Recording started."
                        },
                        onStopRecording = {
                            isRecording = false
                            replayStatus = "Recorded ${macroSteps.size} steps."
                        },
                        onClear = {
                            macroSteps.clear()
                            lastRecordedAtMs = 0L
                            replayStatus = "Macro cleared."
                        },
                        onStartReplay = { startReplay() },
                        onStopReplay = { stopReplay() },
                        isReplaying = isReplaying,
                        steps = macroSteps,
                        replayStatus = replayStatus,
                        remoteInputStatus = remoteInputStatus,
                        isSendingRemoteInput = isSendingRemoteInput,
                        onPressButton = { sendAndMaybeRecord(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineHealthCard(
    connectionState: ConnectionState,
    connectedName: String?,
    cliStatus: CliCapabilityStatus,
    diagnostics: ConnectionDiagnosticsReport,
    autotuneStatus: CommandPipelineAutotuneStatus,
    firmwareCompatibility: FirmwareCompatibilityProfile,
    isRunningDiagnostics: Boolean,
    onRunDiagnostics: () -> Unit,
    onRefreshInfo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Pipeline Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(connectionLabel(connectionState)) }
                )
                connectedName?.let {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
            }

            Text("CLI: ${cliStatus.level.name} | CLI=${cliStatus.supportsCli} RPC=${cliStatus.supportsRpc}")
            Text("Firmware: ${firmwareCompatibility.label} | ${firmwareCompatibility.transportMode.name}")
            Text(
                "Pipeline: ${autotuneStatus.profileLabel} | success ${(autotuneStatus.successRate * 100).toInt()}% | " +
                        "avg ${autotuneStatus.averageLatencyMs}ms"
            )
            Text("Diagnostics: ${diagnostics.summary}")

            if (diagnostics.checks.isNotEmpty()) {
                Divider()
                diagnostics.checks.take(4).forEach { check ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(check.name, modifier = Modifier.weight(1f))
                        Text(
                            check.level.name,
                            color = when (check.level) {
                                ConnectionCheckLevel.PASS -> Color(0xFF2E7D32)
                                ConnectionCheckLevel.WARN -> Color(0xFFF57C00)
                                ConnectionCheckLevel.FAIL -> Color(0xFFC62828)
                                ConnectionCheckLevel.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRunDiagnostics,
                    enabled = !isRunningDiagnostics
                ) {
                    if (isRunningDiagnostics) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Run Diagnostics")
                    }
                }
                OutlinedButton(onClick = onRefreshInfo) {
                    Text("Refresh Telemetry")
                }
            }
        }
    }
}

@Composable
private fun RunbooksCard(
    status: String?,
    inFlight: Boolean,
    onRunLinkHealth: () -> Unit,
    onRunInputSmoke: () -> Unit,
    onRunRecover: () -> Unit
) {
    val runbooks = listOf(
        Triple("Link Health Sweep", "Refresh device telemetry then run full link diagnostics.", onRunLinkHealth),
        Triple("Input Smoke Test", "Send Back, Up, Down, OK to confirm remote input path.", onRunInputSmoke),
        Triple("Recover And Scan", "Disconnect transport and relaunch Flipper-focused scan.", onRunRecover)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Runbooks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "One-tap operational sequences for common troubleshooting and readiness checks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            runbooks.forEach { (title, description, action) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = action,
                            enabled = !inFlight
                        ) {
                            Text(if (inFlight) "Running..." else "Run")
                        }
                    }
                }
            }

            status?.let {
                Divider()
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MacroRecorderCard(
    macroName: String,
    onMacroNameChange: (String) -> Unit,
    isRecording: Boolean,
    longPressMode: Boolean,
    onLongPressModeChange: (Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClear: () -> Unit,
    onStartReplay: () -> Unit,
    onStopReplay: () -> Unit,
    isReplaying: Boolean,
    steps: List<MacroStep>,
    replayStatus: String?,
    remoteInputStatus: String?,
    isSendingRemoteInput: Boolean,
    onPressButton: (FlipperRemoteButton) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Macro Recorder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = macroName,
                onValueChange = onMacroNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Macro Name") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Long Press Mode")
                Switch(checked = longPressMode, onCheckedChange = onLongPressModeChange)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { if (isRecording) onStopRecording() else onStartRecording() }
                ) {
                    Text(if (isRecording) "Stop Record" else "Start Record")
                }
                OutlinedButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear")
                }
                Button(
                    onClick = { if (isReplaying) onStopReplay() else onStartReplay() }
                ) {
                    Icon(
                        if (isReplaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isReplaying) "Stop" else "Replay")
                }
            }

            Text(
                "Recorded Steps: ${steps.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            replayStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            remoteInputStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Divider()
            Text("Remote Pad", fontWeight = FontWeight.SemiBold)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    RemoteButton("UP", enabled = !isSendingRemoteInput) {
                        onPressButton(FlipperRemoteButton.UP)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RemoteButton("LEFT", enabled = !isSendingRemoteInput) {
                        onPressButton(FlipperRemoteButton.LEFT)
                    }
                    RemoteButton("OK", enabled = !isSendingRemoteInput) {
                        onPressButton(FlipperRemoteButton.OK)
                    }
                    RemoteButton("RIGHT", enabled = !isSendingRemoteInput) {
                        onPressButton(FlipperRemoteButton.RIGHT)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RemoteButton("BACK", enabled = !isSendingRemoteInput) {
                        onPressButton(FlipperRemoteButton.BACK)
                    }
                    RemoteButton("DOWN", enabled = !isSendingRemoteInput) {
                        onPressButton(FlipperRemoteButton.DOWN)
                    }
                }
            }

            if (steps.isNotEmpty()) {
                Divider()
                Text("Sequence Preview", fontWeight = FontWeight.SemiBold)
                steps.take(12).forEachIndexed { index, step ->
                    Text(
                        "${index + 1}. +${step.delayMs}ms -> ${step.button.label}${if (step.longPress) " (hold)" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (steps.size > 12) {
                    Text(
                        "... +${steps.size - 12} more steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Text(label)
    }
}

private fun connectionLabel(state: ConnectionState): String {
    return when (state) {
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Scanning -> "Scanning"
        is ConnectionState.Error -> "Error"
        ConnectionState.Disconnected -> "Disconnected"
    }
}
