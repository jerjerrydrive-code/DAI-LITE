package com.dai.flipper.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dai.flipper.ble.FlipperFileSystem
import com.dai.flipper.domain.model.FileEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FileBrowserState(
    val currentPath: String = "/ext",
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pathHistory: List<String> = listOf("/ext"),
    val selectedFile: FileEntry? = null,
    val fileContent: String? = null
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val fileSystem: FlipperFileSystem
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    init {
        loadDirectory("/ext")
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, selectedFile = null, fileContent = null) }

            fileSystem.listDirectory(path)
                .onSuccess { entries ->
                    val sortedEntries = entries.sortedWith(
                        compareByDescending<FileEntry> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    _state.update { state ->
                        val newHistory = if (path != state.currentPath) {
                            state.pathHistory + path
                        } else {
                            state.pathHistory
                        }
                        state.copy(
                            currentPath = path,
                            entries = sortedEntries,
                            isLoading = false,
                            pathHistory = newHistory
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun navigateUp(): Boolean {
        val currentPath = _state.value.currentPath
        if (currentPath == "/ext" || currentPath == "/int") {
            return false
        }
        val parentPath = currentPath.substringBeforeLast("/").ifEmpty { "/ext" }
        loadDirectory(parentPath)
        return true
    }

    fun navigateBack(): Boolean {
        val history = _state.value.pathHistory
        if (history.size <= 1) return false

        val newHistory = history.dropLast(1)
        val previousPath = newHistory.lastOrNull() ?: "/ext"

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            fileSystem.listDirectory(previousPath)
                .onSuccess { entries ->
                    val sortedEntries = entries.sortedWith(
                        compareByDescending<FileEntry> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    _state.update {
                        it.copy(
                            currentPath = previousPath,
                            entries = sortedEntries,
                            isLoading = false,
                            pathHistory = newHistory
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
        return true
    }

    fun onEntryClick(entry: FileEntry) {
        if (entry.isDirectory) {
            loadDirectory(entry.path)
        } else {
            selectFile(entry)
        }
    }

    private fun selectFile(entry: FileEntry) {
        viewModelScope.launch {
            _state.update { it.copy(selectedFile = entry, fileContent = null, isLoading = true) }

            fileSystem.readFile(entry.path)
                .onSuccess { content ->
                    _state.update { it.copy(fileContent = content, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFile = null, fileContent = null) }
    }

    fun refresh() {
        loadDirectory(_state.value.currentPath)
    }

    fun navigateToPath(path: String) {
        loadDirectory(path)
    }
}
