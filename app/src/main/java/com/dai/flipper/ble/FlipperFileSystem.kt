package com.dai.flipper.ble

import com.flipperdevices.protobuf.screen.Gui
import com.dai.flipper.domain.model.*
import com.dai.flipper.security.SecurityUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level file system operations for Flipper Zero.
 * Wraps the protocol layer with type-safe operations.
 * Includes security validation for all paths.
 */
@Singleton
class FlipperFileSystem @Inject constructor(
    private val protocol: FlipperProtocol,
    private val securityUtils: SecurityUtils
) {
    val firmwareCompatibility: StateFlow<FirmwareCompatibilityProfile>
        get() = protocol.firmwareCompatibility

    private val _autotuneStatus = MutableStateFlow(CommandPipelineAutotuneStatus())
    val autotuneStatus: StateFlow<CommandPipelineAutotuneStatus> = _autotuneStatus.asStateFlow()
    private val autotuneWindow = ArrayDeque<AutotuneSample>()
    private val autotuneLock = Any()

    suspend fun listDirectory(path: String): Result<List<FileEntry>> {
        return try {
            val validPath = securityUtils.validatePath(path)
            when (val response = protocol.listDirectory(validPath).withCliFallback {
                protocol.sendCliCommand("storage list $validPath")
            }) {
                is ProtocolResponse.DirectoryList -> {
                    Result.success(response.entries.map { entry ->
                        entry.copy(path = "$validPath/${entry.name}".normalizePath())
                    })
                }
                is ProtocolResponse.FileContent -> {
                    Result.success(parseCliDirectoryListing(response.content, validPath))
                }
                is ProtocolResponse.BinaryContent -> {
                    val content = response.data.toString(Charsets.UTF_8)
                    Result.success(parseCliDirectoryListing(content, validPath))
                }
                is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
                else -> Result.failure(FlipperException("Unexpected response type"))
            }
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun readFile(path: String): Result<String> {
        return try {
            val validPath = securityUtils.validatePath(path)
            when (val response = protocol.readFile(validPath).withCliFallback {
                protocol.sendCliCommand("storage read $validPath")
            }) {
                is ProtocolResponse.FileContent -> Result.success(response.content)
                is ProtocolResponse.BinaryContent -> {
                    Result.success(response.data.toString(Charsets.UTF_8))
                }
                is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
                else -> Result.failure(FlipperException("Unexpected response type"))
            }
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun readFileBytes(path: String): Result<ByteArray> {
        return try {
            val validPath = securityUtils.validatePath(path)
            when (val response = protocol.readFileBinary(validPath).withCliFallback {
                protocol.sendCliCommand("storage read $validPath")
            }) {
                is ProtocolResponse.BinaryContent -> Result.success(response.data)
                is ProtocolResponse.FileContent -> Result.success(response.content.toByteArray(Charsets.UTF_8))
                is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
                else -> Result.failure(FlipperException("Unexpected response type"))
            }
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun writeFile(path: String, content: String): Result<Long> {
        return try {
            securityUtils.validateContentSize(content)
            val bytes = content.toByteArray(Charsets.UTF_8)
            writeFileBytes(path, bytes)
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun writeFileBytes(path: String, content: ByteArray): Result<Long> {
        return try {
            val validPath = securityUtils.validatePath(path)
            securityUtils.validateContentSize(content)
            when (val response = writeFileWithRetry(validPath, content)) {
                is ProtocolResponse.Success -> Result.success(content.size.toLong())
                is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
                else -> Result.failure(FlipperException("Unexpected response type"))
            }
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun createDirectory(path: String): Result<Unit> {
        return try {
            val validPath = securityUtils.validatePath(path)
            when (val response = protocol.createDirectory(validPath).withCliFallback {
                protocol.sendCliCommand("storage mkdir $validPath")
            }) {
                is ProtocolResponse.Success -> Result.success(Unit)
                is ProtocolResponse.FileContent -> Result.success(Unit)
                is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
                else -> Result.failure(FlipperException("Unexpected response type"))
            }
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun delete(path: String, recursive: Boolean = false): Result<Unit> {
        return try {
            val validPath = securityUtils.validatePath(path)
            val cliCommand = if (recursive) {
                "storage remove_recursive $validPath"
            } else {
                "storage remove $validPath"
            }
            when (val response = protocol.delete(validPath, recursive).withCliFallback {
                protocol.sendCliCommand(cliCommand)
            }) {
                is ProtocolResponse.Success -> Result.success(Unit)
                is ProtocolResponse.FileContent -> Result.success(Unit)
                is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
                else -> Result.failure(FlipperException("Unexpected response type"))
            }
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    /**
     * Convenience wrapper for non-recursive deletes (files).
     */
    suspend fun deleteFile(path: String): Result<Unit> {
        return delete(path, recursive = false)
    }

    suspend fun move(sourcePath: String, destPath: String): Result<Unit> {
        return try {
            val validSource = securityUtils.validatePath(sourcePath)
            val validDest = securityUtils.validatePath(destPath)
            when (val response = protocol.move(validSource, validDest).withCliFallback {
                protocol.sendCliCommand("storage move $validSource $validDest")
            }) {
                is ProtocolResponse.Success -> Result.success(Unit)
                is ProtocolResponse.FileContent -> Result.success(Unit)
                is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
                else -> Result.failure(FlipperException("Unexpected response type"))
            }
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun rename(path: String, newName: String): Result<Unit> {
        return try {
            val sanitizedName = securityUtils.sanitizeFileName(newName)
            val parentPath = path.substringBeforeLast("/")
            val newPath = "$parentPath/$sanitizedName"
            move(path, newPath)
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun copy(sourcePath: String, destPath: String): Result<Unit> {
        return try {
            val validSource = securityUtils.validatePath(sourcePath)
            val validDest = securityUtils.validatePath(destPath)

            // Prefer device-native copy to preserve binary integrity.
            val cliCopy = protocol.sendCliCommand("storage copy $validSource $validDest")
            when (cliCopy) {
                is ProtocolResponse.Success,
                is ProtocolResponse.FileContent,
                is ProtocolResponse.BinaryContent -> return Result.success(Unit)
                is ProtocolResponse.Error,
                is ProtocolResponse.DirectoryList,
                is ProtocolResponse.DeviceInformation -> Unit
            }

            // Read source file
            val contentResult = readFileBytes(validSource)
            if (contentResult.isFailure) {
                val exception = contentResult.exceptionOrNull()
                    ?: FlipperException("Failed to read source file")
                return Result.failure(exception)
            }

            // Write to destination
            val writeResult = writeFileBytes(validDest, contentResult.getOrThrow())
            if (writeResult.isFailure) {
                val exception = writeResult.exceptionOrNull()
                    ?: FlipperException("Failed to write destination file")
                return Result.failure(exception)
            }

            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(FlipperException("Security: ${e.message}"))
        }
    }

    suspend fun exists(path: String): Boolean {
        val parentPath = path.substringBeforeLast("/")
        val name = path.substringAfterLast("/")

        val listResult = listDirectory(parentPath)
        if (listResult.isFailure) return false

        return listResult.getOrThrow().any { it.name == name }
    }

    suspend fun getDeviceInfo(): Result<DeviceInfo> {
        return when (val response = protocol.getDeviceInfo().withCliFallback {
            protocol.sendCliCommand("device_info")
        }) {
            is ProtocolResponse.DeviceInformation -> Result.success(response.deviceInfo)
            is ProtocolResponse.FileContent -> Result.success(parseCliDeviceInfo(response.content))
            is ProtocolResponse.BinaryContent -> {
                Result.success(parseCliDeviceInfo(response.data.toString(Charsets.UTF_8)))
            }
            is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
            else -> Result.failure(FlipperException("Unexpected response type"))
        }
    }

    suspend fun getStorageInfo(): Result<StorageInfo> {
        return when (val response = protocol.getStorageInfo().withCliFallback {
            protocol.sendCliCommand("storage info")
        }) {
            is ProtocolResponse.DeviceInformation -> Result.success(response.storageInfo)
            is ProtocolResponse.FileContent -> Result.success(parseCliStorageInfo(response.content))
            is ProtocolResponse.BinaryContent -> {
                Result.success(parseCliStorageInfo(response.data.toString(Charsets.UTF_8)))
            }
            is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
            else -> Result.failure(FlipperException("Unexpected response type"))
        }
    }

    suspend fun executeCli(command: String): Result<String> {
        val startedAt = System.currentTimeMillis()
        fun finish(
            result: Result<String>,
            errorMessageOverride: String? = null
        ): Result<String> {
            val errorMessage = errorMessageOverride ?: result.exceptionOrNull()?.message
            recordOperationSample(
                latencyMs = System.currentTimeMillis() - startedAt,
                success = result.isSuccess,
                errorMessage = errorMessage
            )
            return result
        }

        return try {
            val validatedCommand = validateCliCommand(command)
            val hasRpcMapping = shouldUseRpcAppBridge(validatedCommand)
            val compatibility = FirmwareCompatibilityLayer.assessCliCommand(
                profile = protocol.firmwareCompatibility.value,
                command = validatedCommand,
                hasRpcMapping = hasRpcMapping
            )
            if (!compatibility.supported) {
                return finish(
                    Result.failure(
                        FlipperException("Firmware compatibility: ${compatibility.message}")
                    )
                )
            }

            val canUseRpcBridge = hasRpcMapping ||
                    compatibility.route == FirmwareCommandRoute.RPC_APP_BRIDGE
            var initialRpcBridgeFailure: ProtocolResponse.Error? = null

            // Prefer RPC app bridge first for mapped commands to avoid slow CLI probing loops.
            if (canUseRpcBridge) {
                when (val rpcBridgeResponse = executeRpcAppCommandWithRetry(validatedCommand)) {
                    is ProtocolResponse.Success,
                    is ProtocolResponse.FileContent,
                    is ProtocolResponse.BinaryContent -> {
                        return finish(protocolResponseToStringResult(rpcBridgeResponse))
                    }
                    is ProtocolResponse.Error -> {
                        initialRpcBridgeFailure = rpcBridgeResponse
                    }
                    else -> {
                        return finish(
                            Result.failure(
                                FlipperException("RPC bridge returned unexpected response type")
                            )
                        )
                    }
                }
            }

            val initialStatus = protocol.cliStatus.value
            var cliStatus = when {
                initialStatus.level == CliCapabilityLevel.UNKNOWN -> {
                    protocol.probeCliAvailability(force = false)
                }
                initialStatus.level == CliCapabilityLevel.UNAVAILABLE &&
                        System.currentTimeMillis() - initialStatus.checkedAtMs >= CLI_STATUS_RETRY_MS -> {
                    protocol.probeCliAvailability(force = true)
                }
                else -> initialStatus
            }

            if (cliStatus.level == CliCapabilityLevel.READY &&
                !cliStatus.supportsCli &&
                cliStatus.supportsRpc &&
                !canUseRpcBridge
            ) {
                // Some sessions start in RPC mode due competing BLE clients.
                // Attempt to recover CLI before failing command execution.
                cliStatus = protocol.recoverCliFromRpcSession()
            }

            if (cliStatus.level == CliCapabilityLevel.UNAVAILABLE) {
                if (canUseRpcBridge) {
                    initialRpcBridgeFailure?.let {
                        return finish(Result.failure(FlipperException(it.message, it.code)))
                    }
                    val refreshedStatus = protocol.probeCliAvailability(force = true)
                    if (refreshedStatus.level == CliCapabilityLevel.READY && refreshedStatus.supportsRpc) {
                        val rpcBridgeResponse = executeRpcAppCommandWithRetry(validatedCommand)
                        return finish(protocolResponseToStringResult(rpcBridgeResponse))
                    }
                }
                return finish(
                    Result.failure(
                        FlipperException(
                            "CLI transport unavailable: ${cliStatus.details}. " +
                                    "Reconnect Flipper and ensure no other app owns the BLE session."
                        )
                    )
                )
            }
            if (cliStatus.level == CliCapabilityLevel.READY && !cliStatus.supportsCli) {
                if (cliStatus.supportsRpc && canUseRpcBridge) {
                    initialRpcBridgeFailure?.let {
                        return finish(Result.failure(FlipperException(it.message, it.code)))
                    }
                    val rpcBridgeResponse = executeRpcAppCommandWithRetry(validatedCommand)
                    return finish(protocolResponseToStringResult(rpcBridgeResponse))
                }
                return finish(
                    Result.failure(
                        FlipperException(
                            "CLI commands are unavailable on this connection (RPC-only transport detected). " +
                                    "${cliStatus.details}"
                        )
                    )
                )
            }
            when (val response = sendCliCommandWithRetry(validatedCommand)) {
                is ProtocolResponse.Success -> finish(Result.success(response.message))
                is ProtocolResponse.FileContent -> finish(Result.success(response.content))
                is ProtocolResponse.BinaryContent -> finish(Result.success(response.data.toString(Charsets.UTF_8)))
                is ProtocolResponse.Error -> {
                    if (shouldRetryViaRpcBridge(response.message, validatedCommand)) {
                        val rpcBridgeResponse = executeRpcAppCommandWithRetry(validatedCommand)
                        return finish(protocolResponseToStringResult(rpcBridgeResponse))
                    }
                    finish(Result.failure(FlipperException(response.message, response.code)))
                }
                else -> finish(Result.failure(FlipperException("Unexpected response type")))
            }
        } catch (e: SecurityException) {
            finish(Result.failure(FlipperException("Security: ${e.message}")))
        }
    }

    private fun protocolResponseToStringResult(response: ProtocolResponse): Result<String> {
        return when (response) {
            is ProtocolResponse.Success -> Result.success(response.message)
            is ProtocolResponse.FileContent -> Result.success(response.content)
            is ProtocolResponse.BinaryContent -> Result.success(response.data.toString(Charsets.UTF_8))
            is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
            else -> Result.failure(FlipperException("Unexpected response type"))
        }
    }

    /**
     * Send a direct remote-control button event to Flipper GUI via RPC.
     */
    suspend fun sendRemoteButton(
        button: FlipperRemoteButton,
        longPress: Boolean = false
    ): Result<String> {
        return when (
            val response = protocol.sendGuiInputEventImmediate(
                key = button.toGuiInputKey(),
                inputType = if (longPress) Gui.InputType.LONG else Gui.InputType.SHORT
            )
        ) {
            is ProtocolResponse.Success -> Result.success(response.message)
            is ProtocolResponse.FileContent -> Result.success(response.content)
            is ProtocolResponse.BinaryContent -> Result.success(response.data.toString(Charsets.UTF_8))
            is ProtocolResponse.Error -> Result.failure(FlipperException(response.message, response.code))
            else -> Result.failure(FlipperException("Unexpected response type"))
        }
    }

    private fun String.normalizePath(): String {
        return this.replace("//", "/").trimEnd('/')
    }

    private fun validateCliCommand(command: String): String {
        val sanitized = command.trim()
        if (sanitized.isEmpty()) {
            throw SecurityException("Command cannot be empty")
        }
        if (sanitized.contains('\n') || sanitized.contains('\r') || sanitized.contains('\u0000')) {
            throw SecurityException("Command contains invalid control characters")
        }
        if (sanitized.length > MAX_CLI_COMMAND_LENGTH) {
            throw SecurityException("Command exceeds max length of $MAX_CLI_COMMAND_LENGTH characters")
        }
        return sanitized
    }

    private fun shouldUseRpcAppBridge(command: String): Boolean {
        return protocol.hasRpcAppCommandMapping(command)
    }

    private fun shouldRetryViaRpcBridge(message: String, command: String): Boolean {
        if (!shouldUseRpcAppBridge(command)) return false
        val normalized = message.lowercase()
        return normalized.contains("produced no cli output") ||
                normalized.contains("cannot be confirmed") ||
                normalized.contains("no cli response") ||
                normalized.contains("rpc is responsive") ||
                normalized.contains("cli is unavailable on this transport") ||
                normalized.contains("no compatible cli variant succeeded")
    }

    private fun isPipelineBusy(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("command pipeline busy") ||
                normalized.contains("timed out waiting for lock")
    }

    private suspend fun executeRpcAppCommandWithRetry(command: String): ProtocolResponse {
        val tune = autotuneStatus.value
        var lastResponse: ProtocolResponse = ProtocolResponse.Error("RPC app command failed")
        val startedAt = System.currentTimeMillis()
        repeat(tune.rpcRetryAttempts) { attempt ->
            val response = protocol.executeRpcAppCommand(command)
            lastResponse = response
            val isRetryable = response is ProtocolResponse.Error && isPipelineBusy(response.message)
            if (response is ProtocolResponse.Error) {
                recordRetrySignal(response.message)
            } else {
                recordOperationSample(
                    latencyMs = System.currentTimeMillis() - startedAt,
                    success = true,
                    errorMessage = null
                )
            }
            if (!isRetryable) {
                if (response is ProtocolResponse.Error) {
                    recordOperationSample(
                        latencyMs = System.currentTimeMillis() - startedAt,
                        success = false,
                        errorMessage = response.message
                    )
                }
                return response
            }
            if (attempt < tune.rpcRetryAttempts - 1) {
                delay(tune.rpcRetryDelayMs * (attempt + 1))
            }
        }
        val rpcError = lastResponse as? ProtocolResponse.Error
        if (rpcError != null) {
            recordOperationSample(
                latencyMs = System.currentTimeMillis() - startedAt,
                success = false,
                errorMessage = rpcError.message
            )
        }
        return lastResponse
    }

    private suspend fun sendCliCommandWithRetry(command: String): ProtocolResponse {
        val tune = autotuneStatus.value
        var lastResponse: ProtocolResponse = ProtocolResponse.Error("CLI command failed")
        val startedAt = System.currentTimeMillis()
        repeat(tune.cliRetryAttempts) { attempt ->
            val response = protocol.sendCliCommand(command)
            lastResponse = response
            val isRetryable = response is ProtocolResponse.Error && isPipelineBusy(response.message)
            if (response is ProtocolResponse.Error) {
                recordRetrySignal(response.message)
            } else {
                recordOperationSample(
                    latencyMs = System.currentTimeMillis() - startedAt,
                    success = true,
                    errorMessage = null
                )
            }
            if (!isRetryable) {
                if (response is ProtocolResponse.Error) {
                    recordOperationSample(
                        latencyMs = System.currentTimeMillis() - startedAt,
                        success = false,
                        errorMessage = response.message
                    )
                }
                return response
            }
            if (attempt < tune.cliRetryAttempts - 1) {
                delay(tune.cliRetryDelayMs * (attempt + 1))
            }
        }
        val cliError = lastResponse as? ProtocolResponse.Error
        if (cliError != null) {
            recordOperationSample(
                latencyMs = System.currentTimeMillis() - startedAt,
                success = false,
                errorMessage = cliError.message
            )
        }
        return lastResponse
    }

    private suspend fun writeFileWithRetry(path: String, content: ByteArray): ProtocolResponse {
        val tune = autotuneStatus.value
        var lastResponse: ProtocolResponse = ProtocolResponse.Error("Write file failed")
        val startedAt = System.currentTimeMillis()
        repeat(tune.fileWriteRetryAttempts) { attempt ->
            val response = protocol.writeFile(path, content)
            lastResponse = response
            val isRetryable = response is ProtocolResponse.Error &&
                    (isPipelineBusy(response.message) || isTransientWriteFailure(response.message))
            if (response is ProtocolResponse.Error) {
                recordRetrySignal(response.message)
            } else {
                recordOperationSample(
                    latencyMs = System.currentTimeMillis() - startedAt,
                    success = true,
                    errorMessage = null
                )
            }
            if (!isRetryable) {
                if (response is ProtocolResponse.Error) {
                    recordOperationSample(
                        latencyMs = System.currentTimeMillis() - startedAt,
                        success = false,
                        errorMessage = response.message
                    )
                }
                return response
            }
            if (attempt < tune.fileWriteRetryAttempts - 1) {
                delay(tune.fileWriteRetryDelayMs * (attempt + 1))
            }
        }
        val writeError = lastResponse as? ProtocolResponse.Error
        if (writeError != null) {
            recordOperationSample(
                latencyMs = System.currentTimeMillis() - startedAt,
                success = false,
                errorMessage = writeError.message
            )
        }
        return lastResponse
    }

    private fun isTransientWriteFailure(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("command timed out") ||
                normalized.contains("no rpc response for storage write") ||
                normalized.contains("write queue busy") ||
                normalized.contains("serial overflow budget timeout")
    }

    private fun recordRetrySignal(message: String) {
        val normalized = message.lowercase()
        val busy = isPipelineBusy(normalized)
        val disconnect = isDisconnectLikeFailure(normalized)
        if (!busy && !disconnect) return
        recordOperationSample(
            latencyMs = 0L,
            success = false,
            errorMessage = message
        )
    }

    private fun recordOperationSample(
        latencyMs: Long,
        success: Boolean,
        errorMessage: String?
    ) {
        val now = System.currentTimeMillis()
        val normalizedError = errorMessage?.lowercase().orEmpty()
        val sample = AutotuneSample(
            timestampMs = now,
            latencyMs = latencyMs.coerceAtLeast(0L),
            success = success,
            busySignal = !success && isPipelineBusy(normalizedError),
            disconnectSignal = !success && isDisconnectLikeFailure(normalizedError)
        )
        val newStatus = synchronized(autotuneLock) {
            autotuneWindow.addLast(sample)
            while (autotuneWindow.size > AUTOTUNE_WINDOW_SIZE) {
                autotuneWindow.removeFirst()
            }
            computeAutotuneStatusLocked(now)
        }
        _autotuneStatus.value = newStatus
    }

    private fun computeAutotuneStatusLocked(nowMs: Long): CommandPipelineAutotuneStatus {
        val samples = autotuneWindow.toList()
        if (samples.isEmpty()) {
            return defaultAutotuneStatus(nowMs)
        }

        val successRate = samples.count { it.success }.toFloat() / samples.size.toFloat()
        val averageLatencyMs = samples
            .map { it.latencyMs }
            .filter { it > 0L }
            .average()
            .let { if (it.isNaN()) 0L else it.toLong() }

        val busySignals = samples.count { it.busySignal }
        val disconnectSignals = samples.count { it.disconnectSignal }

        val profile = when {
            disconnectSignals >= 3 -> CommandPipelineProfile.RESILIENT
            busySignals >= 7 -> CommandPipelineProfile.RESILIENT
            successRate < 0.6f -> CommandPipelineProfile.RESILIENT
            successRate >= 0.9f && busySignals <= 1 && averageLatencyMs in 1..220 ->
                CommandPipelineProfile.FAST
            else -> CommandPipelineProfile.BALANCED
        }

        return when (profile) {
            CommandPipelineProfile.FAST -> CommandPipelineAutotuneStatus(
                profile = profile,
                cliRetryAttempts = 2,
                cliRetryDelayMs = 140L,
                rpcRetryAttempts = 2,
                rpcRetryDelayMs = 120L,
                fileWriteRetryAttempts = 2,
                fileWriteRetryDelayMs = 180L,
                successRate = successRate,
                averageLatencyMs = averageLatencyMs,
                busySignals = busySignals,
                disconnectSignals = disconnectSignals,
                sampleSize = samples.size,
                lastUpdatedMs = nowMs
            )
            CommandPipelineProfile.BALANCED -> CommandPipelineAutotuneStatus(
                profile = profile,
                cliRetryAttempts = 3,
                cliRetryDelayMs = 300L,
                rpcRetryAttempts = 3,
                rpcRetryDelayMs = 320L,
                fileWriteRetryAttempts = 3,
                fileWriteRetryDelayMs = 350L,
                successRate = successRate,
                averageLatencyMs = averageLatencyMs,
                busySignals = busySignals,
                disconnectSignals = disconnectSignals,
                sampleSize = samples.size,
                lastUpdatedMs = nowMs
            )
            CommandPipelineProfile.RESILIENT -> CommandPipelineAutotuneStatus(
                profile = profile,
                cliRetryAttempts = 5,
                cliRetryDelayMs = 620L,
                rpcRetryAttempts = 5,
                rpcRetryDelayMs = 680L,
                fileWriteRetryAttempts = 5,
                fileWriteRetryDelayMs = 720L,
                successRate = successRate,
                averageLatencyMs = averageLatencyMs,
                busySignals = busySignals,
                disconnectSignals = disconnectSignals,
                sampleSize = samples.size,
                lastUpdatedMs = nowMs
            )
        }
    }

    private fun defaultAutotuneStatus(nowMs: Long): CommandPipelineAutotuneStatus {
        return CommandPipelineAutotuneStatus(
            profile = CommandPipelineProfile.BALANCED,
            cliRetryAttempts = 3,
            cliRetryDelayMs = 300L,
            rpcRetryAttempts = 3,
            rpcRetryDelayMs = 320L,
            fileWriteRetryAttempts = 3,
            fileWriteRetryDelayMs = 350L,
            successRate = 1f,
            averageLatencyMs = 0L,
            busySignals = 0,
            disconnectSignals = 0,
            sampleSize = 0,
            lastUpdatedMs = nowMs
        )
    }

    private fun isDisconnectLikeFailure(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("not connected") ||
                normalized.contains("transport unavailable") ||
                normalized.contains("peer terminated") ||
                normalized.contains("gatt rejected write") ||
                normalized.contains("ble disconnected") ||
                normalized.contains("service unavailable")
    }

    private fun FlipperRemoteButton.toGuiInputKey(): Gui.InputKey {
        return when (this) {
            FlipperRemoteButton.UP -> Gui.InputKey.UP
            FlipperRemoteButton.DOWN -> Gui.InputKey.DOWN
            FlipperRemoteButton.LEFT -> Gui.InputKey.LEFT
            FlipperRemoteButton.RIGHT -> Gui.InputKey.RIGHT
            FlipperRemoteButton.OK -> Gui.InputKey.OK
            FlipperRemoteButton.BACK -> Gui.InputKey.BACK
        }
    }

    private suspend fun ProtocolResponse.withCliFallback(
        fallback: suspend () -> ProtocolResponse
    ): ProtocolResponse {
        return if (this is ProtocolResponse.Error && shouldUseCliFallback(this.message)) {
            fallback()
        } else {
            this
        }
    }

    private fun shouldUseCliFallback(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("non-protocol response") ||
                normalized.contains("invalid protocol frame") ||
                normalized.contains("timed out") ||
                normalized.contains("unknown response type") ||
                normalized.contains("no rpc response") ||
                normalized.contains("rpc transport is unavailable") ||
                normalized.contains("rpc transport unavailable") ||
                normalized.contains("rpc ping did not respond")
    }

    private fun parseCliDirectoryListing(content: String, basePath: String): List<FileEntry> {
        val lines = content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith(">") || it.startsWith("storage>") }
            .toList()

        return lines.map { line ->
            val isDirectory = line.startsWith("[D]") || line.endsWith("/")
            val cleaned = line
                .removePrefix("[D]")
                .removePrefix("[F]")
                .trim()
            val name = cleaned.substringBeforeLast(" ").trim().ifBlank { cleaned }
            FileEntry(
                name = name,
                path = "$basePath/$name".normalizePath(),
                isDirectory = isDirectory,
                size = 0
            )
        }
    }

    private fun parseCliDeviceInfo(content: String): DeviceInfo {
        val firmware = Regex(
            "(?im)^(?:firmware(?:_version)?|version|fw)\\s*[:=]\\s*([^\\r\\n]+)$"
        ).find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: Regex("(firmware|version)\\D+([\\w.\\-]+)", RegexOption.IGNORE_CASE)
                .find(content)
                ?.groupValues
                ?.getOrNull(2)
                ?.trim()
                ?: "unknown"
        val batteryLevel = parseCliBatteryPercent(content)
            ?: parseCliBatteryPercentFromVoltage(content)
            ?: 0
        val isCharging = Regex(
            "(?i)\\b(charging|charger_connected\\s*[:=]\\s*(?:1|true|yes)|charge_state\\s*[:=]\\s*charging)\\b"
        ).containsMatchIn(content)
        return DeviceInfo(
            name = "Flipper Zero",
            firmwareVersion = firmware,
            hardwareVersion = "unknown",
            batteryLevel = batteryLevel.coerceIn(0, 100),
            isCharging = isCharging
        )
    }

    private fun parseCliBatteryPercent(content: String): Int? {
        val keyValueRegex = Regex(
            "(?im)^(?:battery_level|charge_percent|charge_level|capacity_percent|battery|charge)\\s*[:=]\\s*(\\d{1,3})\\s*%?\\s*$"
        )
        keyValueRegex.find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 0..100 }
            ?.let { return it }

        val inlinePercentRegex = Regex(
            "(?i)(?:battery|charge|capacity)[^\\r\\n\\d]{0,16}(\\d{1,3})\\s*%"
        )
        inlinePercentRegex.find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it in 0..100 }
            ?.let { return it }

        return null
    }

    private fun parseCliBatteryPercentFromVoltage(content: String): Int? {
        val voltageRegex = Regex(
            "(?i)(?:battery_voltage|power_voltage|vbat|vbatt|voltage)\\s*[:=]?\\s*(-?\\d+(?:\\.\\d+)?)\\s*(mv|mV|volt|volts|v)?"
        )

        voltageRegex.findAll(content).forEach { match ->
            val value = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return@forEach
            if (value <= 0.0) return@forEach
            val unit = match.groupValues.getOrNull(2)?.lowercase(java.util.Locale.US).orEmpty()
            val mv = when {
                unit == "mv" -> value.toInt()
                unit == "v" || unit == "volt" || unit == "volts" -> (value * 1000.0).toInt()
                value in 2.5..5.5 -> (value * 1000.0).toInt()
                value in 1000.0..5000.0 -> value.toInt()
                else -> return@forEach
            }
            val clampedMv = mv.coerceIn(BATTERY_MIN_MV, BATTERY_MAX_MV)
            val percent = ((clampedMv - BATTERY_MIN_MV).toDouble() /
                    (BATTERY_MAX_MV - BATTERY_MIN_MV).toDouble() * 100.0).toInt()
            return percent.coerceIn(0, 100)
        }
        return null
    }

    private fun parseCliStorageInfo(content: String): StorageInfo {
        val totalRegex = Regex("(total|size)\\D+(\\d+)", RegexOption.IGNORE_CASE)
        val freeRegex = Regex("(free|available)\\D+(\\d+)", RegexOption.IGNORE_CASE)
        val total = totalRegex.find(content)?.groupValues?.getOrNull(2)?.toLongOrNull() ?: 0L
        val free = freeRegex.find(content)?.groupValues?.getOrNull(2)?.toLongOrNull() ?: 0L
        return StorageInfo(
            internalTotal = total,
            internalFree = free,
            hasSdCard = content.contains("sd", ignoreCase = true)
        )
    }

    companion object {
        private const val MAX_CLI_COMMAND_LENGTH = 512
        private const val CLI_STATUS_RETRY_MS = 5_000L
        private const val AUTOTUNE_WINDOW_SIZE = 36
        private const val BATTERY_MIN_MV = 3_300
        private const val BATTERY_MAX_MV = 4_200
    }

    private data class AutotuneSample(
        val timestampMs: Long,
        val latencyMs: Long,
        val success: Boolean,
        val busySignal: Boolean,
        val disconnectSignal: Boolean
    )
}

/**
 * Exception for Flipper file system operations
 */
class FlipperException(
    message: String,
    val code: Int = -1
) : Exception(message)
