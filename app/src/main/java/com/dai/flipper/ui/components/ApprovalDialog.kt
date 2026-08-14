package com.dai.flipper.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dai.flipper.domain.model.PendingApproval
import com.dai.flipper.domain.model.RiskLevel
import com.dai.flipper.ui.theme.*
import kotlinx.coroutines.delay

private const val DOUBLE_TAP_ARM_TIMEOUT_MS = 1800L

@Composable
fun ApprovalDialog(
    approval: PendingApproval,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var awaitingSecondTap by remember(approval.id) { mutableStateOf(false) }
    var decisionInFlight by remember(approval.id) { mutableStateOf(false) }

    val isHighRisk = approval.riskAssessment.level == RiskLevel.HIGH

    // High-risk actions require a second tap within a short timeout.
    LaunchedEffect(awaitingSecondTap) {
        if (awaitingSecondTap && isHighRisk && !decisionInFlight) {
            delay(DOUBLE_TAP_ARM_TIMEOUT_MS)
            awaitingSecondTap = false
        }
    }

    Dialog(
        onDismissRequest = {
            if (!decisionInFlight) {
                onReject()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !decisionInFlight,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Risk indicator
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            when (approval.riskAssessment.level) {
                                RiskLevel.LOW -> RiskLow
                                RiskLevel.MEDIUM -> RiskMedium
                                RiskLevel.HIGH -> RiskHigh
                                RiskLevel.BLOCKED -> RiskBlocked
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (approval.riskAssessment.level) {
                            RiskLevel.LOW -> Icons.Default.CheckCircle
                            RiskLevel.MEDIUM -> Icons.Default.Edit
                            RiskLevel.HIGH -> Icons.Default.Warning
                            RiskLevel.BLOCKED -> Icons.Default.Block
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = when (approval.riskAssessment.level) {
                        RiskLevel.HIGH -> "Confirm Destructive Action"
                        RiskLevel.MEDIUM -> "Review Changes"
                        else -> "Confirm Action"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action description
                Text(
                    text = approval.command.action.name.replace("_", " "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Path
                approval.command.args.path?.let { path ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = path,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Risk reason
                Text(
                    text = approval.riskAssessment.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Justification
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "AI Justification:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = approval.command.justification,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Diff viewer for medium risk writes
                approval.diff?.let { diff ->
                    Spacer(modifier = Modifier.height(16.dp))
                    UnifiedDiffViewer(
                        unifiedDiff = diff.unifiedDiff,
                        modifier = Modifier.heightIn(max = 200.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                if (isHighRisk) {
                    // Double tap to confirm for high risk
                    DoubleTapConfirmButton(
                        isArmed = awaitingSecondTap,
                        enabled = !decisionInFlight,
                        onTap = {
                            if (decisionInFlight) return@DoubleTapConfirmButton
                            if (awaitingSecondTap) {
                                awaitingSecondTap = false
                                decisionInFlight = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onApprove()
                            } else {
                                awaitingSecondTap = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                } else {
                    // Regular confirm for medium risk
                    Button(
                        onClick = {
                            if (!decisionInFlight) {
                                decisionInFlight = true
                                onApprove()
                            }
                        },
                        enabled = !decisionInFlight,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DaiAccent
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Approve")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reject button
                OutlinedButton(
                    onClick = {
                        if (!decisionInFlight) {
                            onReject()
                        }
                    },
                    enabled = !decisionInFlight,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RiskHigh
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun DoubleTapConfirmButton(
    isArmed: Boolean,
    enabled: Boolean,
    onTap: () -> Unit
) {
    val latestOnTap by rememberUpdatedState(onTap)
    val scale by animateFloatAsState(
        targetValue = if (isArmed && enabled) 0.98f else 1f,
        label = "button_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(
                when {
                    !enabled -> RiskHigh.copy(alpha = 0.6f)
                    isArmed -> RiskHigh
                    else -> RiskHigh.copy(alpha = 0.2f)
                }
            )
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = {
                        if (enabled) latestOnTap()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Text
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = if (isArmed || !enabled) Color.White else RiskHigh
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (!enabled) {
                    "Submitting..."
                } else if (isArmed) {
                    "Tap Again to Confirm"
                } else {
                    "Double Tap to Confirm"
                },
                fontWeight = FontWeight.Bold,
                color = if (isArmed || !enabled) Color.White else RiskHigh
            )
        }
    }
}
