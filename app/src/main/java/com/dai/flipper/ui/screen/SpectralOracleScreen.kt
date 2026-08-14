@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dai.flipper.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dai.flipper.domain.model.*
import com.dai.flipper.ui.theme.*
import com.dai.flipper.ui.viewmodel.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectralOracleScreen(
    viewModel: SpectralOracleViewModel = hiltViewModel()
) {
    val availableSignals by viewModel.availableSignals.collectAsState()
    val selectedSignal by viewModel.selectedSignal.collectAsState()
    val analysisType by viewModel.analysisType.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val error by viewModel.error.collectAsState()

    var showSignalPicker by remember { mutableStateOf(false) }
    var showRawResponse by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D1A),
                        Color(0xFF1A0D2E),
                        Color(0xFF0D0D1A)
                    )
                )
            )
    ) {
        // Animated background effect
        OracleBackground(isAnalyzing = isAnalyzing)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            OracleHeader()

            Spacer(modifier = Modifier.height(16.dp))

            // Signal Selection
            SignalSelector(
                selectedSignal = selectedSignal,
                onClick = { showSignalPicker = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Analysis Type Selection
            AnalysisTypeSelector(
                selectedType = analysisType,
                onTypeSelected = { viewModel.setAnalysisType(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Analyze Button
            OracleAnalyzeButton(
                enabled = selectedSignal != null && !isAnalyzing,
                isAnalyzing = isAnalyzing,
                progress = analysisProgress,
                onClick = { viewModel.analyzeSignal() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Results Area
            Box(modifier = Modifier.weight(1f)) {
                when {
                    analysisResult != null -> {
                        AnalysisResultView(
                            result = analysisResult!!,
                            onShowRaw = { showRawResponse = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    isAnalyzing -> {
                        OracleLoadingAnimation(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    else -> {
                        OracleIdleState(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Error Banner
            error?.let { errorMsg ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(errorMsg, color = Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }
                }
            }
        }

        // Signal Picker Dialog
        if (showSignalPicker) {
            SignalPickerDialog(
                signals = availableSignals,
                selectedSignal = selectedSignal,
                onSelect = {
                    viewModel.selectSignal(it)
                    showSignalPicker = false
                },
                onDismiss = { showSignalPicker = false },
                onRefresh = { viewModel.loadAvailableSignals() }
            )
        }

        // Raw Response Dialog
        if (showRawResponse && analysisResult != null) {
            RawResponseDialog(
                response = analysisResult!!.rawResponse,
                onDismiss = { showRawResponse = false }
            )
        }
    }
}

@Composable
private fun OracleBackground(isAnalyzing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isAnalyzing) pulse else 0.3f)
            .blur(50.dp)
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension

        // Draw mystical circles
        listOf(0.3f, 0.5f, 0.7f).forEachIndexed { index, radiusFactor ->
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF6B00FF).copy(alpha = 0.3f),
                        Color(0xFFFF6B00).copy(alpha = 0.2f),
                        Color(0xFF00FFD1).copy(alpha = 0.3f),
                        Color(0xFF6B00FF).copy(alpha = 0.3f)
                    ),
                    center = center
                ),
                radius = maxRadius * radiusFactor,
                center = center,
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
private fun OracleHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "SPECTRAL ORACLE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp
            )
            Text(
                "AI Signal Intelligence",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF00FFD1)
            )
        }
        Text("🔮", fontSize = 40.sp)
    }
}

@Composable
private fun SignalSelector(
    selectedSignal: SignalCapture?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.8f)
        ),
        border = BorderStroke(1.dp, Color(0xFF6B00FF).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    tint = Color(0xFFFF6B00),
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        selectedSignal?.name ?: "Select a signal to analyze",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (selectedSignal is SignalCapture.SubGhz) {
                        Text(
                            "${selectedSignal.protocol} @ ${formatFrequency(selectedSignal.frequency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00FFD1)
                        )
                    }
                }
            }
            Icon(Icons.Default.ExpandMore, null, tint = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisTypeSelector(
    selectedType: AnalysisType,
    onTypeSelected: (AnalysisType) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnalysisType.entries.forEach { type ->
            val isSelected = type == selectedType

            Surface(
                onClick = { onTypeSelected(type) },
                color = if (isSelected) Color(0xFF6B00FF) else Color(0xFF1A1A2E).copy(alpha = 0.8f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF6B00FF) else Color(0xFF6B00FF).copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(type.icon, fontSize = 14.sp)
                    Text(
                        type.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun OracleAnalyzeButton(
    enabled: Boolean,
    isAnalyzing: Boolean,
    progress: String,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "btn")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6B00FF),
            disabledContainerColor = Color(0xFF1A1A2E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                progress,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                "🔮",
                fontSize = 24.sp,
                modifier = Modifier.alpha(if (enabled) glowAlpha else 0.5f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "CONSULT THE ORACLE",
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun OracleLoadingAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "rotate"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Outer ring
            Canvas(
                modifier = Modifier
                    .size(150.dp)
                    .rotate(rotation)
            ) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF6B00FF),
                            Color(0xFFFF6B00),
                            Color(0xFF00FFD1),
                            Color(0xFF6B00FF)
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Center orb
            Text(
                "🔮",
                fontSize = (60 * scale).sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "The Oracle is analyzing...",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF00FFD1),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Decoding spectral patterns",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun OracleIdleState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("👁️", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Select a signal to begin",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            "The Oracle awaits your query",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun AnalysisResultView(
    result: OracleAnalysisResult,
    onShowRaw: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Threat Level Header
        item {
            ThreatLevelCard(result.threatLevel, result.protocolInfo)
        }

        // Summary
        item {
            ResultSection(title = "📋 Summary") {
                Text(
                    result.summary,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Vulnerabilities
        if (result.vulnerabilities.isNotEmpty()) {
            item {
                ResultSection(title = "🛡️ Vulnerabilities (${result.vulnerabilities.size})") {
                    result.vulnerabilities.forEach { vuln ->
                        VulnerabilityItem(vuln)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Exploits
        if (result.exploits.isNotEmpty()) {
            item {
                ResultSection(title = "⚔️ Exploit Vectors (${result.exploits.size})") {
                    result.exploits.forEach { exploit ->
                        ExploitItem(exploit)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Threat Analysis
        item {
            ResultSection(title = "⚠️ Threat Analysis") {
                Text(
                    result.threatAnalysis,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Recommendations
        if (result.recommendations.isNotEmpty()) {
            item {
                ResultSection(title = "💡 Recommendations") {
                    result.recommendations.forEachIndexed { index, rec ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${index + 1}.",
                                color = Color(0xFF00FFD1),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                rec,
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Raw Response Button
        item {
            OutlinedButton(
                onClick = onShowRaw,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF6B00FF)
                ),
                border = BorderStroke(1.dp, Color(0xFF6B00FF).copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Code, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Raw AI Response")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ThreatLevelCard(
    threatLevel: OracleRiskLevel,
    protocolInfo: ProtocolInfo
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(threatLevel.color).copy(alpha = 0.2f)
        ),
        border = BorderStroke(2.dp, Color(threatLevel.color))
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
                Column {
                    Text(
                        "THREAT LEVEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        threatLevel.displayName.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(threatLevel.color)
                    )
                }
                Text(
                    when (threatLevel) {
                        OracleRiskLevel.CRITICAL -> "🔴"
                        OracleRiskLevel.HIGH -> "🟠"
                        OracleRiskLevel.ELEVATED -> "🟡"
                        OracleRiskLevel.MODERATE -> "🟢"
                        OracleRiskLevel.LOW -> "🟢"
                        OracleRiskLevel.MINIMAL -> "🔵"
                    },
                    fontSize = 40.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProtocolInfoChip("Protocol", protocolInfo.name)
                ProtocolInfoChip("Encoding", protocolInfo.encoding)
                ProtocolInfoChip(
                    "Security",
                    when {
                        protocolInfo.isEncrypted && protocolInfo.isRollingCode -> "🔒 Strong"
                        protocolInfo.isEncrypted || protocolInfo.isRollingCode -> "🔓 Partial"
                        else -> "⚠️ Weak"
                    }
                )
            }
        }
    }
}

@Composable
private fun ProtocolInfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun ResultSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.8f)
        ),
        border = BorderStroke(1.dp, Color(0xFF6B00FF).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00FFD1)
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun VulnerabilityItem(vuln: VulnItem) {
    Surface(
        color = Color(vuln.severity.color).copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(vuln.severity.color).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = Color(vuln.severity.color),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    vuln.severity.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vuln.name,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    vuln.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExploitItem(exploit: ExploitItem) {
    Surface(
        color = Color(0xFFFF6B00).copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exploit.name,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B00)
                )
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        exploit.difficulty,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                exploit.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SignalPickerDialog(
    signals: List<SignalCapture>,
    selectedSignal: SignalCapture?,
    onSelect: (SignalCapture) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Signal", color = Color.White)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null, tint = Color(0xFF00FFD1))
                }
            }
        },
        text = {
            if (signals.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📡", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No signals found",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        "Capture signals with your Flipper first",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(signals) { signal ->
                        val isSelected = signal.path == selectedSignal?.path

                        Card(
                            onClick = { onSelect(signal) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    Color(0xFF6B00FF).copy(alpha = 0.3f)
                                else
                                    Color.White.copy(alpha = 0.05f)
                            ),
                            border = if (isSelected)
                                BorderStroke(1.dp, Color(0xFF6B00FF))
                            else null
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
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    if (signal is SignalCapture.SubGhz) {
                                        Text(
                                            "${signal.protocol} @ ${formatFrequency(signal.frequency)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF00FFD1)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = Color(0xFF6B00FF)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF6B00FF))
            }
        }
    )
}

@Composable
private fun RawResponseDialog(
    response: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = { Text("Raw AI Response", color = Color.White) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    response,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00FFD1)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF6B00FF))
            }
        }
    )
}

private fun formatFrequency(hz: Long): String = when {
    hz >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.3f GHz", hz / 1_000_000_000.0)
    hz >= 1_000_000 -> String.format(java.util.Locale.US, "%.3f MHz", hz / 1_000_000.0)
    hz >= 1_000 -> String.format(java.util.Locale.US, "%.3f kHz", hz / 1_000.0)
    else -> "$hz Hz"
}
