package com.dai.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dai.flipper.data.db.*
import com.dai.flipper.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceTrackerViewModel @Inject constructor(
    private val repository: DeviceRepository
) : ViewModel() {

    // Filter state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedType = MutableStateFlow<DeviceType?>(null)
    val selectedType: StateFlow<DeviceType?> = _selectedType.asStateFlow()

    private val _selectedCategory = MutableStateFlow<DeviceCategory?>(null)
    val selectedCategory: StateFlow<DeviceCategory?> = _selectedCategory.asStateFlow()

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    private val _sortBy = MutableStateFlow(DeviceSortOption.LAST_SEEN)
    val sortBy: StateFlow<DeviceSortOption> = _sortBy.asStateFlow()

    // Device detail state
    private val _selectedDevice = MutableStateFlow<TrackedDevice?>(null)
    val selectedDevice: StateFlow<TrackedDevice?> = _selectedDevice.asStateFlow()

    // Statistics
    val deviceCount: StateFlow<Int> = repository.getDeviceCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val last24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
    val recentCount: StateFlow<Int> = repository.getRecentDeviceCount(last24Hours)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val baseDevices: Flow<List<TrackedDevice>> = combine(
        _searchQuery,
        _showHidden
    ) { query, showHidden ->
        query to showHidden
    }.flatMapLatest { (query, showHidden) ->
        if (query.isNotEmpty()) {
            repository.searchDevices(query)
        } else if (showHidden) {
            repository.getAllDevices()
        } else {
            repository.getVisibleDevices()
        }
    }

    // Filtered devices list
    val devices: StateFlow<List<TrackedDevice>> = combine(
        _selectedType,
        _selectedCategory,
        _sortBy,
        baseDevices
    ) { type, category, sort, devices ->
        var filtered = devices

        // Filter by type
        if (type != null) {
            filtered = filtered.filter { it.type == type }
        }

        // Filter by category
        if (category != null) {
            filtered = filtered.filter { it.category == category }
        }

        // Sort
        when (sort) {
            DeviceSortOption.LAST_SEEN -> filtered.sortedByDescending { it.lastSeen }
            DeviceSortOption.FIRST_SEEN -> filtered.sortedByDescending { it.firstSeen }
            DeviceSortOption.NAME -> filtered.sortedBy { it.alias ?: it.name ?: it.identifier }
            DeviceSortOption.SIGHTING_COUNT -> filtered.sortedByDescending { it.sightingCount }
            DeviceSortOption.THREAT_LEVEL -> filtered.sortedByDescending { it.threat.ordinal }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Type counts for filter
    val typeCounts: StateFlow<Map<DeviceType, Int>> = repository.getAllDevices()
        .map { devices ->
            DeviceType.entries.associateWith { type ->
                devices.count { it.type == type }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Sightings for selected device
    val selectedDeviceSightings: StateFlow<List<DeviceSighting>> =
        _selectedDevice.flatMapLatest { device ->
            if (device != null) {
                repository.getRecentSightings(device.id, 100)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes for selected device
    val selectedDeviceNotes: StateFlow<List<DeviceNote>> =
        _selectedDevice.flatMapLatest { device ->
            if (device != null) {
                repository.getNotesForDevice(device.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Actions
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectType(type: DeviceType?) {
        _selectedType.value = type
    }

    fun selectCategory(category: DeviceCategory?) {
        _selectedCategory.value = category
    }

    fun toggleShowHidden() {
        _showHidden.value = !_showHidden.value
    }

    fun setSortOption(option: DeviceSortOption) {
        _sortBy.value = option
    }

    fun selectDevice(device: TrackedDevice?) {
        _selectedDevice.value = device
    }

    fun setDeviceAlias(id: String, alias: String) {
        viewModelScope.launch {
            repository.setDeviceAlias(id, alias.ifBlank { null })
        }
    }

    fun toggleFavorite(device: TrackedDevice) {
        viewModelScope.launch {
            repository.setDeviceFavorite(device.id, !device.isFavorite)
        }
    }

    fun toggleHidden(device: TrackedDevice) {
        viewModelScope.launch {
            repository.setDeviceHidden(device.id, !device.isHidden)
        }
    }

    fun setThreatLevel(device: TrackedDevice, threat: ThreatLevel) {
        viewModelScope.launch {
            repository.setThreatLevel(device.id, threat)
        }
    }

    fun deleteDevice(device: TrackedDevice) {
        viewModelScope.launch {
            repository.deleteDevice(device)
            _selectedDevice.value = null
        }
    }

    fun addNote(deviceId: String, content: String) {
        viewModelScope.launch {
            repository.addNote(deviceId, content)
        }
    }

    fun deleteNote(note: DeviceNote) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun cleanupOldSightings(days: Int = 30) {
        viewModelScope.launch {
            repository.cleanupOldSightings(days)
        }
    }
}

enum class DeviceSortOption(val displayName: String) {
    LAST_SEEN("Last Seen"),
    FIRST_SEEN("First Seen"),
    NAME("Name"),
    SIGHTING_COUNT("Most Seen"),
    THREAT_LEVEL("Threat Level")
}
