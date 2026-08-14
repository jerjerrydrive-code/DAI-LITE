package com.dai.flipper.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dai.flipper.data.db.*
import com.dai.flipper.ui.viewmodel.DeviceTrackerViewModel
import com.dai.flipper.ui.viewmodel.DeviceSortOption
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceTrackerScreen(
    viewModel: DeviceTrackerViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val deviceCount by viewModel.deviceCount.collectAsState()
    val recentCount by viewModel.recentCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val typeCounts by viewModel.typeCounts.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A2E),
                        Color(0xFF0D0D0D)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with stats
            DeviceTrackerHeader(
                totalDevices = deviceCount,
                recentDevices = recentCount
            )

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search devices...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedBorderColor = Color(0xFF00BCD4)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Type filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = { viewModel.selectType(null) },
                        label = { Text("All ($deviceCount)") },
                        leadingIcon = if (selectedType == null) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00BCD4),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(DeviceType.entries) { type ->
                    val count = typeCounts[type] ?: 0
                    if (count > 0) {
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { viewModel.selectType(type) },
                            label = { Text("${type.icon} ${type.displayName} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00BCD4),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Sort row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${devices.size} devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(sortBy.displayName)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DeviceSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    viewModel.setSortOption(option)
                                    showSortMenu = false
                                },
                                leadingIcon = if (sortBy == option) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Device list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    TrackedDeviceCard(
                        device = device,
                        onClick = { viewModel.selectDevice(device) },
                        onFavorite = { viewModel.toggleFavorite(device) }
                    )
                }

                if (devices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.DevicesOther,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF444444)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No devices tracked yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Scan for BLE, Sub-GHz, or NFC devices",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Device detail sheet
        selectedDevice?.let { device ->
            DeviceDetailSheet(
                device = device,
                viewModel = viewModel,
                onDismiss = { viewModel.selectDevice(null) }
            )
        }
    }
}

@Composable
private fun DeviceTrackerHeader(
    totalDevices: Int,
    recentDevices: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "📡 Device Tracker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "$recentDevices active in last 24h",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF00BCD4)
            )
        }

        // Stats badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatBadge(
                value = totalDevices.toString(),
                label = "Total",
                color = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
private fun StatBadge(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun TrackedDeviceCard(
    device: TrackedDevice,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E2E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                getTypeColor(device.type),
                                getTypeColor(device.type).copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = device.type.icon,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Device info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.alias ?: device.name ?: "Unknown Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (device.isFavorite) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorite",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFFFD700)
                        )
                    }
                    if (device.threat != ThreatLevel.NONE) {
                        Spacer(modifier = Modifier.width(8.dp))
                        ThreatBadge(device.threat)
                    }
                }

                Text(
                    text = device.identifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category
                    Text(
                        text = "${device.category.icon} ${device.category.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00BCD4)
                    )

                    // Sighting count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${device.sightingCount}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    // Last seen
                    Text(
                        text = dateFormat.format(Date(device.lastSeen)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            // Favorite button
            IconButton(onClick = onFavorite) {
                Icon(
                    if (device.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle favorite",
                    tint = if (device.isFavorite) Color(0xFFFFD700) else Color.Gray
                )
            }
        }
    }
}

@Composable
private fun ThreatBadge(threat: ThreatLevel) {
    Box(
        modifier = Modifier
            .background(Color(threat.color).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = threat.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = Color(threat.color)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailSheet(
    device: TrackedDevice,
    viewModel: DeviceTrackerViewModel,
    onDismiss: () -> Unit
) {
    val sightings by viewModel.selectedDeviceSightings.collectAsState()
    val notes by viewModel.selectedDeviceNotes.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault()) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newNote by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        getTypeColor(device.type),
                                        getTypeColor(device.type).copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = device.type.icon,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.alias ?: device.name ?: "Unknown Device",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = device.identifier,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text(device.type.displayName) },
                                leadingIcon = { Text(device.type.icon) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(device.category.displayName) },
                                leadingIcon = { Text(device.category.icon) }
                            )
                        }
                    }
                }
            }

            // Stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn("First Seen", dateFormat.format(Date(device.firstSeen)))
                    StatColumn("Last Seen", dateFormat.format(Date(device.lastSeen)))
                    StatColumn("Sightings", device.sightingCount.toString())
                }
            }

            // Manufacturer / Model
            if (device.manufacturer != null || device.subType != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2A2A3E)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (device.manufacturer != null) {
                                InfoRow("Manufacturer", device.manufacturer)
                            }
                            if (device.subType != null) {
                                InfoRow("Type", device.subType)
                            }
                        }
                    }
                }
            }

            // Threat Level
            item {
                Text(
                    text = "Threat Level",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThreatLevel.entries.forEach { threat ->
                        FilterChip(
                            selected = device.threat == threat,
                            onClick = { viewModel.setThreatLevel(device, threat) },
                            label = { Text(threat.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(threat.color),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Recent Sightings
            if (sightings.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Sightings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                items(sightings.take(5)) { sighting ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dateFormat.format(Date(sighting.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        sighting.rssi?.let {
                            Text(
                                text = "${it} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00BCD4)
                            )
                        }
                    }
                }
            }

            // Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.toggleHidden(device) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (device.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (device.isHidden) "Unhide" else "Hide")
                    }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Device") },
            text = { Text("Are you sure you want to delete this device and all its sighting history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDevice(device)
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

private fun getTypeColor(type: DeviceType): Color {
    return when (type) {
        DeviceType.BLE -> Color(0xFF2196F3)
        DeviceType.SUBGHZ -> Color(0xFFFF9800)
        DeviceType.NFC -> Color(0xFF4CAF50)
        DeviceType.RFID -> Color(0xFF9C27B0)
        DeviceType.WIFI -> Color(0xFF00BCD4)
        DeviceType.INFRARED -> Color(0xFFF44336)
        DeviceType.IBUTTON -> Color(0xFF607D8B)
    }
}
