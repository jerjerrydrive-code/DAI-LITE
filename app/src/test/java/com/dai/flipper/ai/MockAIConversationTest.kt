package com.dai.flipper.ai

import com.dai.flipper.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests simulating complete AI conversation flows.
 * Validates the back-and-forth between user prompts and AI tool calls.
 */
class MockAIConversationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Simulates a typical conversation where user asks to view and edit a SubGHz file.
     */
    @Test
    fun `conversation flow - view and edit subghz file`() {
        // Turn 1: User asks to see their garage remote
        val userMessage1 = "Show me my garage door remote"

        // AI Response 1: Read the file first (read before write principle)
        val aiCommand1 = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/subghz/Garage_Door.sub"),
            justification = "User wants to see garage door remote configuration",
            expectedEffect = "Return the SubGHz capture file contents"
        )

        assertEquals(CommandAction.READ_FILE, aiCommand1.action)

        // System returns file contents
        val result1 = CommandResult(
            success = true,
            action = CommandAction.READ_FILE,
            data = CommandResultData(
                content = """
                    Filetype: Flipper SubGhz Key File
                    Version: 1
                    Frequency: 390000000
                    Preset: FuriHalSubGhzPresetOok650Async
                    Protocol: Princeton
                    Bit: 24
                    Key: 00 00 00 00 00 12 34 56
                    TE: 400
                """.trimIndent()
            ),
            executionTimeMs = 45
        )

        assertTrue(result1.success)
        assertTrue(result1.data?.content?.contains("390000000") == true)

        // Turn 2: User asks to change frequency
        val userMessage2 = "Change it to 315MHz"

        // AI Response 2: Write with new frequency
        val newContent = """
            Filetype: Flipper SubGhz Key File
            Version: 1
            Frequency: 315000000
            Preset: FuriHalSubGhzPresetOok650Async
            Protocol: Princeton
            Bit: 24
            Key: 00 00 00 00 00 12 34 56
            TE: 400
        """.trimIndent()

        val aiCommand2 = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/subghz/Garage_Door.sub",
                content = newContent
            ),
            justification = "Changing frequency from 390MHz to 315MHz as requested",
            expectedEffect = "Update file with new frequency setting"
        )

        assertEquals(CommandAction.WRITE_FILE, aiCommand2.action)
        assertTrue(aiCommand2.args.content?.contains("315000000") == true)

        // System shows diff and requests approval (MEDIUM risk)
        val result2 = CommandResult(
            success = true,
            action = CommandAction.WRITE_FILE,
            data = CommandResultData(
                diff = FileDiff(
                    originalContent = result1.data?.content,
                    newContent = newContent,
                    linesAdded = 1,
                    linesRemoved = 1,
                    unifiedDiff = "@@ -2,1 +2,1 @@\n-Frequency: 390000000\n+Frequency: 315000000"
                )
            ),
            requiresConfirmation = true,
            pendingApprovalId = "approval-123"
        )

        assertTrue(result2.requiresConfirmation)
        assertNotNull(result2.pendingApprovalId)
    }

    /**
     * Simulates creating a new IR remote from scratch.
     */
    @Test
    fun `conversation flow - create new IR remote`() {
        // User: "Create a new TV remote for my LG TV"

        // Step 1: AI checks if file exists
        val checkCommand = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/infrared"),
            justification = "Checking existing IR remotes before creating new one",
            expectedEffect = "List existing IR files to avoid overwriting"
        )

        val listResult = CommandResult(
            success = true,
            action = CommandAction.LIST_DIRECTORY,
            data = CommandResultData(
                entries = listOf(
                    FileEntry("Samsung_TV.ir", "/ext/infrared/Samsung_TV.ir", false, 2048),
                    FileEntry("AC_Remote.ir", "/ext/infrared/AC_Remote.ir", false, 1536)
                )
            )
        )

        // No LG_TV.ir exists, safe to create

        // Step 2: AI creates the IR file
        val irContent = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 08 00 00 00
            #
            name: Vol_up
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 02 00 00 00
            #
            name: Vol_dn
            type: parsed
            protocol: NEC
            address: 04 00 00 00
            command: 03 00 00 00
        """.trimIndent()

        val createCommand = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/infrared/LG_TV.ir",
                content = irContent
            ),
            justification = "Creating new LG TV remote with Power and Volume controls using NEC protocol",
            expectedEffect = "Create IR remote file compatible with LG TVs"
        )

        assertEquals(CommandAction.WRITE_FILE, createCommand.action)
        assertTrue(createCommand.args.content?.contains("NEC") == true)
        assertTrue(createCommand.args.content?.contains("Power") == true)
    }

    /**
     * Simulates organizing files with backup creation.
     */
    @Test
    fun `conversation flow - organize files with backup`() {
        // User: "Organize my subghz folder and back up the old files"

        // Step 1: List current files
        val listCommand = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/subghz"),
            justification = "Listing files to organize",
            expectedEffect = "Get current file list"
        )

        // Step 2: Create backup directory
        val mkdirCommand = ExecuteCommand(
            action = CommandAction.CREATE_DIRECTORY,
            args = CommandArgs(path = "/ext/subghz/backup"),
            justification = "Creating backup folder for old files",
            expectedEffect = "Create backup directory"
        )

        assertEquals(CommandAction.CREATE_DIRECTORY, mkdirCommand.action)

        // Step 3: Copy old files to backup (HIGH risk - needs confirmation)
        val copyCommand = ExecuteCommand(
            action = CommandAction.COPY,
            args = CommandArgs(
                path = "/ext/subghz/old_remote.sub",
                destinationPath = "/ext/subghz/backup/old_remote.sub"
            ),
            justification = "Backing up old remote before potential deletion",
            expectedEffect = "Create backup copy"
        )

        assertEquals(CommandAction.COPY, copyCommand.action)
    }

    /**
     * Simulates a BadUSB script creation request.
     */
    @Test
    fun `conversation flow - create badusb script`() {
        // User: "Create a BadUSB script that opens notepad and types hello world"

        val badUsbContent = """
            REM Simple Hello World BadUSB Script
            DELAY 1000
            GUI r
            DELAY 500
            STRING notepad
            ENTER
            DELAY 1000
            STRING Hello World from Flipper Zero!
        """.trimIndent()

        val createCommand = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/badusb/hello_world.txt",
                content = badUsbContent
            ),
            justification = "Creating educational BadUSB script that opens notepad and types a message",
            expectedEffect = "Create BadUSB payload file"
        )

        assertEquals(CommandAction.WRITE_FILE, createCommand.action)
        assertTrue(createCommand.args.content?.contains("GUI r") == true)
        assertTrue(createCommand.args.content?.contains("notepad") == true)
    }

    /**
     * Simulates device status check flow.
     */
    @Test
    fun `conversation flow - check device status`() {
        // User: "How's my Flipper doing? Check everything."

        // AI makes multiple parallel queries
        val deviceInfoCommand = ExecuteCommand(
            action = CommandAction.GET_DEVICE_INFO,
            args = CommandArgs(),
            justification = "Checking device battery and hardware status",
            expectedEffect = "Return device information"
        )

        val storageCommand = ExecuteCommand(
            action = CommandAction.GET_STORAGE_INFO,
            args = CommandArgs(),
            justification = "Checking available storage space",
            expectedEffect = "Return storage usage"
        )

        // Results
        val deviceResult = CommandResult(
            success = true,
            action = CommandAction.GET_DEVICE_INFO,
            data = CommandResultData(
                deviceInfo = DeviceInfo(
                    name = "Flipper Zero",
                    firmwareVersion = "0.93.0",
                    hardwareVersion = "1.0",
                    batteryLevel = 78,
                    isCharging = false
                )
            )
        )

        val storageResult = CommandResult(
            success = true,
            action = CommandAction.GET_STORAGE_INFO,
            data = CommandResultData(
                storageInfo = StorageInfo(
                    internalTotal = 16 * 1024 * 1024,
                    internalFree = 4 * 1024 * 1024,
                    externalTotal = 32L * 1024 * 1024 * 1024,
                    externalFree = 28L * 1024 * 1024 * 1024,
                    hasSdCard = true
                )
            )
        )

        assertEquals(78, deviceResult.data?.deviceInfo?.batteryLevel)
        assertTrue(storageResult.data?.storageInfo?.hasSdCard == true)
    }

    /**
     * Simulates a delete operation with confirmation.
     */
    @Test
    fun `conversation flow - delete with confirmation`() {
        // User: "Delete my test folder"

        val deleteCommand = ExecuteCommand(
            action = CommandAction.DELETE,
            args = CommandArgs(
                path = "/ext/test",
                recursive = true
            ),
            justification = "User explicitly requested deletion of test folder",
            expectedEffect = "Remove test directory and all its contents"
        )

        assertEquals(CommandAction.DELETE, deleteCommand.action)
        assertTrue(deleteCommand.args.recursive)

        // System requires confirmation (HIGH risk)
        val deleteResult = CommandResult(
            success = true,
            action = CommandAction.DELETE,
            data = CommandResultData(
                message = "Awaiting user approval for recursive deletion"
            ),
            requiresConfirmation = true,
            pendingApprovalId = "approval-delete-456"
        )

        assertTrue(deleteResult.requiresConfirmation)

        // After user holds to confirm
        val confirmedResult = CommandResult(
            success = true,
            action = CommandAction.DELETE,
            data = CommandResultData(
                message = "Deleted: /ext/test (15 files removed)"
            ),
            executionTimeMs = 234
        )

        assertTrue(confirmedResult.success)
    }

    /**
     * Simulates error handling when file doesn't exist.
     */
    @Test
    fun `conversation flow - handle file not found`() {
        // User: "Read my super_secret.sub file"

        val readCommand = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/subghz/super_secret.sub"),
            justification = "User wants to read file contents",
            expectedEffect = "Return file contents"
        )

        val errorResult = CommandResult(
            success = false,
            action = CommandAction.READ_FILE,
            error = "File not found: /ext/subghz/super_secret.sub",
            executionTimeMs = 12
        )

        assertFalse(errorResult.success)
        assertTrue(errorResult.error?.contains("not found") == true)

        // AI would then respond: "I couldn't find that file. Let me list your SubGHz captures..."
    }

    /**
     * Simulates blocked operation on protected path.
     */
    @Test
    fun `conversation flow - blocked protected path`() {
        // User: "Write something to the internal storage"

        val blockedResult = CommandResult(
            success = false,
            action = CommandAction.WRITE_FILE,
            error = "Blocked: System path requires settings unlock"
        )

        assertFalse(blockedResult.success)
        assertTrue(blockedResult.error?.contains("Blocked") == true)
    }

    /**
     * Simulates multi-step NFC workflow.
     */
    @Test
    fun `conversation flow - NFC card operations`() {
        // User: "Show me my NFC cards and tell me about my office badge"

        // Step 1: List NFC files
        val listCommand = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/nfc"),
            justification = "Listing NFC cards as requested",
            expectedEffect = "Return list of saved NFC files"
        )

        val listResult = CommandResult(
            success = true,
            action = CommandAction.LIST_DIRECTORY,
            data = CommandResultData(
                entries = listOf(
                    FileEntry("Office_Badge.nfc", "/ext/nfc/Office_Badge.nfc", false, 512),
                    FileEntry("Metro_Card.nfc", "/ext/nfc/Metro_Card.nfc", false, 1024),
                    FileEntry("Gym_Access.nfc", "/ext/nfc/Gym_Access.nfc", false, 256)
                )
            )
        )

        assertEquals(3, listResult.data?.entries?.size)

        // Step 2: Read the office badge
        val readCommand = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/nfc/Office_Badge.nfc"),
            justification = "Reading office badge details as requested",
            expectedEffect = "Return NFC card type and UID"
        )

        val readResult = CommandResult(
            success = true,
            action = CommandAction.READ_FILE,
            data = CommandResultData(
                content = """
                    Filetype: Flipper NFC device
                    Version: 2
                    Device type: MIFARE Classic 1K
                    UID: 04 A3 2B 1C
                    ATQA: 00 04
                    SAK: 08
                """.trimIndent()
            )
        )

        assertTrue(readResult.data?.content?.contains("MIFARE Classic") == true)
    }
}
