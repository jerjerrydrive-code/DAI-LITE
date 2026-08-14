@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dai.flipper.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dai.flipper.domain.model.*
import com.dai.flipper.ui.theme.*
import com.dai.flipper.ui.viewmodel.SignalArsenalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalArsenalScreen(
    viewModel: SignalArsenalViewModel = hiltViewModel()
) {
    val selectedType by viewModel.selectedType.collectAsState()
    val signals by viewModel.filteredSignals.collectAsState()
    val selectedSignal by viewModel.selectedSignal.collectAsState()
    val waveformData by viewModel.waveformData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isReplaying by viewModel.isReplaying.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val signalCounts by viewModel.signalCounts.collectAsState()
    val replaySuccess by viewModel.replaySuccess.collectAsState()

    // Show success snackbar
    LaunchedEffect(replaySuccess) {
        replaySuccess?.let {
            kotlinx.coroutines.delay(2000)
            viewModel.clearReplaySuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text("Signal Arsenal", fontWeight = FontWeight.Bold)
                    Text(
                        "${signals.size} captures",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Signal Type Tabs
        SignalTypeTabs(
            selectedType = selectedType,
            signalCounts = signalCounts,
            onTypeSelected = { viewModel.selectType(it) }
        )

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search signals...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DaiOrange,
                cursorColor = DaiOrange
            )
        )

        // Main Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Signal List
            LazyColumn(
                modifier = Modifier
                    .weight(if (selectedSignal != null) 0.4f else 1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = DaiOrange)
                        }
                    }
                } else if (signals.isEmpty()) {
                    item {
                        EmptySignalsPlaceholder(selectedType)
                    }
                } else {
                    items(signals, key = { it.path }) { signal ->
                        SignalCard(
                            signal = signal,
                            isSelected = selectedSignal?.path == signal.path,
                            onClick = { viewModel.selectSignal(signal) },
                            onReplay = { viewModel.replaySignal(signal) },
                            onDelete = { viewModel.deleteSignal(signal) },
                            isReplaying = isReplaying && selectedSignal?.path == signal.path
                        )
                    }
                }
            }

            // Detail Panel (when signal selected)
            AnimatedVisibility(
                visible = selectedSignal != null,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                selectedSignal?.let { signal ->
                    SignalDetailPanel(
                        signal = signal,
                        waveformData = waveformData,
                        isReplaying = isReplaying,
                        onReplay = { viewModel.replaySignal(signal) },
                        onClose = { viewModel.clearSelection() },
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // Replay Success Banner
        AnimatedVisibility(
            visible = replaySuccess != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DaiAccent.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        replaySuccess ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error Banner
        error?.let { errorMsg ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(errorMsg, color = Color.White)
                    IconButton(onClick = { viewModel.clearError() }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalTypeTabs(
    selectedType: SignalType,
    signalCounts: Map<SignalType, Int>,
    onTypeSelected: (SignalType) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = SignalType.entries.indexOf(selectedType),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = DaiOrange,
        edgePadding = 16.dp
    ) {
        SignalType.entries.forEach { type ->
            val count = signalCounts[type] ?: 0
            Tab(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(SignalArsenalViewModel.getSignalIcon(type))
                        Text(type.displayName)
                        if (count > 0) {
                            Badge(containerColor = DaiOrange) {
                                Text("$count", color = Color.White)
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignalCard(
    signal: SignalCapture,
    isSelected: Boolean,
    onClick: () -> Unit,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
    isReplaying: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                DaiOrange.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, DaiOrange)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    signal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    SignalArsenalViewModel.getSignalSubtitle(signal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Replay Button
                FilledIconButton(
                    onClick = onReplay,
                    enabled = !isReplaying,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DaiOrange
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isReplaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Replay",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // More Options
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalDetailPanel(
    signal: SignalCapture,
    waveformData: WaveformData?,
    isReplaying: Boolean,
    onReplay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    signal.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Signal Type Badge
            Surface(
                color = DaiOrange.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "${SignalArsenalViewModel.getSignalIcon(signal.type)} ${signal.type.displayName}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = DaiOrange,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Waveform Visualization
            waveformData?.let { data ->
                Text(
                    "WAVEFORM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                WaveformView(
                    waveformData = data,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                Text(
                    "Duration: ${String.format(java.util.Locale.US, "%.2f", data.duration)}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Signal Details
            Text(
                "DETAILS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            SignalDetails(signal)

            Spacer(modifier = Modifier.height(24.dp))

            // Replay Button
            Button(
                onClick = onReplay,
                enabled = !isReplaying,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DaiOrange
                )
            ) {
                if (isReplaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transmitting...")
                } else {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REPLAY SIGNAL")
                }
            }
        }
    }
}

@Composable
private fun SignalDetails(signal: SignalCapture) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (signal) {
                is SignalCapture.SubGhz -> {
                    DetailRow("Protocol", signal.protocol)
                    DetailRow("Frequency", SignalArsenalViewModel.formatFrequency(signal.frequency))
                    signal.bitLength?.let { DetailRow("Bit Length", it.toString()) }
                    signal.keyData?.let { DetailRow("Key", it, mono = true) }
                    DetailRow("Preset", signal.preset)
                }
                is SignalCapture.Infrared -> {
                    DetailRow("Protocol", signal.protocol)
                    DetailRow("Address", signal.address, mono = true)
                    DetailRow("Command", signal.command, mono = true)
                }
                is SignalCapture.Nfc -> {
                    DetailRow("Type", signal.deviceType)
                    DetailRow("UID", signal.uid, mono = true)
                    signal.atqa?.let { DetailRow("ATQA", it, mono = true) }
                    signal.sak?.let { DetailRow("SAK", it, mono = true) }
                    signal.dataBlocks?.take(4)?.forEach { block ->
                        DetailRow("Block ${block.blockNumber}", block.data, mono = true)
                    }
                    if ((signal.dataBlocks?.size ?: 0) > 4) {
                        Text(
                            "... and ${signal.dataBlocks!!.size - 4} more blocks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SignalCapture.Rfid -> {
                    DetailRow("Protocol", signal.protocol)
                    DetailRow("Data", signal.keyData, mono = true)
                }
                is SignalCapture.IButton -> {
                    DetailRow("Protocol", signal.protocol)
                    DetailRow("Key", signal.keyData, mono = true)
                }
            }

            DetailRow("Path", signal.path.substringAfterLast("/"), mono = true)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
private fun WaveformView(
    waveformData: WaveformData,
    modifier: Modifier = Modifier
) {
    val waveColor = DaiOrange
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val width = size.width
        val height = size.height
        val padding = 16f

        // Draw grid
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = padding + (height - 2 * padding) * i / gridLines
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }

        if (waveformData.samples.isEmpty()) return@Canvas

        // Draw waveform
        val path = Path()
        val xScale = (width - 2 * padding) / waveformData.duration
        val yScale = height - 2 * padding

        waveformData.samples.forEachIndexed { index, sample ->
            val x = padding + sample.time * xScale
            val y = padding + (1f - sample.value) * yScale

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw filled area
        val filledPath = Path().apply {
            addPath(path)
            lineTo(padding + waveformData.duration * xScale, height - padding)
            lineTo(padding, height - padding)
            close()
        }
        drawPath(
            path = filledPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    waveColor.copy(alpha = 0.3f),
                    waveColor.copy(alpha = 0.05f)
                )
            )
        )
    }
}

@Composable
private fun EmptySignalsPlaceholder(type: SignalType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            SignalArsenalViewModel.getSignalIcon(type),
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No ${type.displayName} captures",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Captures will appear here when you record signals with your Flipper",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
