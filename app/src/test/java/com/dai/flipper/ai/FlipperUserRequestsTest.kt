package com.dai.flipper.ai

import com.dai.flipper.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests simulating real-world Flipper Zero user requests.
 * These represent common tasks users perform with their devices.
 */
class FlipperUserRequestsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ============================================
    // SubGHz Operations - Garage doors, car remotes
    // ============================================

    @Test
    fun `user request - list my subghz captures`() {
        // Simulates: "Show me my SubGHz files" or "What remotes do I have saved?"
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/subghz"),
            justification = "User wants to view their saved SubGHz captures and remotes",
            expectedEffect = "Display list of .sub files containing captured signals"
        )

        assertEquals(CommandAction.LIST_DIRECTORY, command.action)
        assertEquals("/ext/subghz", command.args.path)
    }

    @Test
    fun `user request - read garage door frequency`() {
        // Simulates: "What frequency is my garage door?" or "Show me the garage remote details"
        val command = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/subghz/Garage_Door.sub"),
            justification = "User wants to check the frequency and protocol of their garage door remote",
            expectedEffect = "Return file contents showing frequency (likely 315MHz or 390MHz) and protocol"
        )

        assertEquals(CommandAction.READ_FILE, command.action)
        assertTrue(command.args.path!!.endsWith(".sub"))
    }

    @Test
    fun `user request - change subghz frequency`() {
        // Simulates: "Change the frequency to 315MHz" or "Update this to use 433.92"
        val subghzContent = """
Filetype: Flipper SubGhz Key File
Version: 1
Frequency: 315000000
Preset: FuriHalSubGhzPresetOok650Async
Protocol: Princeton
Bit: 24
Key: 00 00 00 00 00 12 34 56
TE: 400
        """.trimIndent()

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/subghz/Garage_Door.sub",
                content = subghzContent
            ),
            justification = "Changing frequency from 390MHz to 315MHz as requested by user",
            expectedEffect = "Update the SubGHz file with new frequency setting"
        )

        assertEquals(CommandAction.WRITE_FILE, command.action)
        assertTrue(command.args.content!!.contains("315000000"))
    }

    @Test
    fun `user request - create new subghz remote`() {
        // Simulates: "Create a new remote for my gate at 433.92MHz"
        val newRemote = """
Filetype: Flipper SubGhz Key File
Version: 1
Frequency: 433920000
Preset: FuriHalSubGhzPresetOok650Async
Protocol: Princeton
Bit: 24
Key: 00 00 00 00 00 AB CD EF
TE: 350
        """.trimIndent()

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/subghz/Gate_433.sub",
                content = newRemote
            ),
            justification = "Creating new SubGHz remote file for gate at 433.92MHz",
            expectedEffect = "Create Gate_433.sub with Princeton protocol settings"
        )

        assertNotNull(command.args.content)
        assertTrue(command.args.content!!.contains("433920000"))
    }

    // ============================================
    // Infrared Operations - TV, AC, appliances
    // ============================================

    @Test
    fun `user request - list IR remotes`() {
        // Simulates: "What IR remotes do I have?" or "Show my infrared files"
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/infrared"),
            justification = "User wants to see all saved infrared remote files",
            expectedEffect = "List all .ir files in the infrared directory"
        )

        assertEquals("/ext/infrared", command.args.path)
    }

    @Test
    fun `user request - create TV remote with common buttons`() {
        // Simulates: "Create a Samsung TV remote" or "Make an IR file for my TV"
        val irContent = """
Filetype: IR signals file
Version: 1
#
name: Power
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 02 00 00 00
#
name: Vol_up
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 07 00 00 00
#
name: Vol_dn
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 0B 00 00 00
#
name: Ch_next
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 12 00 00 00
#
name: Ch_prev
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 10 00 00 00
        """.trimIndent()

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/infrared/Samsung_TV.ir",
                content = irContent
            ),
            justification = "Creating Samsung TV remote with Power, Volume, and Channel buttons",
            expectedEffect = "Create functional IR remote file for Samsung TV"
        )

        assertTrue(command.args.content!!.contains("Samsung32"))
        assertTrue(command.args.content!!.contains("Power"))
        assertTrue(command.args.content!!.contains("Vol_up"))
    }

    @Test
    fun `user request - add button to existing remote`() {
        // Simulates: "Add a mute button to my TV remote"
        val updatedContent = """
Filetype: IR signals file
Version: 1
#
name: Power
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 02 00 00 00
#
name: Mute
type: parsed
protocol: Samsung32
address: 07 00 00 00
command: 0F 00 00 00
        """.trimIndent()

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/infrared/Samsung_TV.ir",
                content = updatedContent
            ),
            justification = "Adding Mute button to existing Samsung TV remote",
            expectedEffect = "Update IR file with new Mute command"
        )

        assertTrue(command.args.content!!.contains("Mute"))
    }

    // ============================================
    // BadUSB Operations - Keyboard scripts
    // ============================================

    @Test
    fun `user request - create rickroll badusb script`() {
        // Simulates: "Create a rickroll BadUSB script" (classic prank)
        val badUsbContent = """
REM Rickroll BadUSB Script
REM Opens YouTube rickroll in default browser
DELAY 1000
GUI r
DELAY 500
STRING https://www.youtube.com/watch?v=dQw4w9WgXcQ
ENTER
        """.trimIndent()

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/badusb/rickroll.txt",
                content = badUsbContent
            ),
            justification = "Creating classic rickroll prank BadUSB script",
            expectedEffect = "Create BadUSB payload that opens rickroll video"
        )

        assertTrue(command.args.content!!.contains("GUI r"))
        assertTrue(command.args.content!!.contains("dQw4w9WgXcQ"))
    }

    @Test
    fun `user request - create wifi password stealer educational`() {
        // Simulates: "Show me how to view saved wifi passwords" (educational/pentesting)
        val badUsbContent = """
REM Educational: Display saved WiFi passwords (Windows)
REM For authorized penetration testing only
DELAY 1000
GUI r
DELAY 500
STRING cmd
DELAY 200
CTRL SHIFT ENTER
DELAY 1000
ALT y
DELAY 500
STRING netsh wlan show profiles
ENTER
DELAY 2000
STRING netsh wlan show profile name="WiFiName" key=clear
ENTER
        """.trimIndent()

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/badusb/wifi_audit.txt",
                content = badUsbContent
            ),
            justification = "Creating WiFi audit script for authorized security testing",
            expectedEffect = "Create BadUSB script that displays saved WiFi credentials"
        )

        assertTrue(command.args.content!!.contains("netsh wlan"))
    }

    @Test
    fun `user request - create system info gatherer`() {
        // Simulates: "Create a script to show system info"
        val badUsbContent = """
REM System Information Gatherer
DELAY 1000
GUI r
DELAY 300
STRING powershell
ENTER
DELAY 1000
STRING Get-ComputerInfo | Select-Object WindowsProductName, OsVersion, CsProcessors, CsTotalPhysicalMemory
ENTER
        """.trimIndent()

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/badusb/sysinfo.txt",
                content = badUsbContent
            ),
            justification = "Creating system information display script",
            expectedEffect = "Create BadUSB that shows computer specs"
        )

        assertTrue(command.args.content!!.contains("Get-ComputerInfo"))
    }

    // ============================================
    // NFC Operations - Cards, tags
    // ============================================

    @Test
    fun `user request - list NFC saves`() {
        // Simulates: "Show my NFC cards" or "What tags do I have saved?"
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/nfc"),
            justification = "User wants to view saved NFC cards and tags",
            expectedEffect = "List all .nfc files"
        )

        assertEquals("/ext/nfc", command.args.path)
    }

    @Test
    fun `user request - read NFC card details`() {
        // Simulates: "What type is my office card?" or "Show NFC card info"
        val command = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/nfc/Office_Badge.nfc"),
            justification = "User wants to inspect NFC card type and UID",
            expectedEffect = "Return NFC file showing card type (MIFARE, etc) and UID"
        )

        assertTrue(command.args.path!!.endsWith(".nfc"))
    }

    // ============================================
    // iButton Operations
    // ============================================

    @Test
    fun `user request - list iButton keys`() {
        // Simulates: "Show my iButton keys"
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/ibutton"),
            justification = "User wants to see saved iButton keys",
            expectedEffect = "List all .ibtn files"
        )

        assertEquals("/ext/ibutton", command.args.path)
    }

    // ============================================
    // File Management Operations
    // ============================================

    @Test
    fun `user request - organize files into folders`() {
        // Simulates: "Move my old remotes to a backup folder"
        val command = ExecuteCommand(
            action = CommandAction.CREATE_DIRECTORY,
            args = CommandArgs(path = "/ext/subghz/backup"),
            justification = "Creating backup folder for old SubGHz files",
            expectedEffect = "Create backup directory inside subghz folder"
        )

        assertEquals(CommandAction.CREATE_DIRECTORY, command.action)
    }

    @Test
    fun `user request - backup file before editing`() {
        // Simulates: AI should backup before modifying
        val command = ExecuteCommand(
            action = CommandAction.COPY,
            args = CommandArgs(
                path = "/ext/subghz/garage.sub",
                destinationPath = "/ext/subghz/backup/garage.sub.bak"
            ),
            justification = "Creating backup before modifying garage remote",
            expectedEffect = "Copy original file to backup location"
        )

        assertEquals(CommandAction.COPY, command.action)
        assertTrue(command.args.destinationPath!!.contains("backup"))
    }

    @Test
    fun `user request - delete old test files`() {
        // Simulates: "Clean up my test files" or "Delete the test folder"
        val command = ExecuteCommand(
            action = CommandAction.DELETE,
            args = CommandArgs(
                path = "/ext/test",
                recursive = true
            ),
            justification = "User requested cleanup of test directory",
            expectedEffect = "Remove test folder and all contents"
        )

        assertEquals(CommandAction.DELETE, command.action)
        assertTrue(command.args.recursive)
    }

    @Test
    fun `user request - rename file`() {
        // Simulates: "Rename my garage remote to front_gate"
        val command = ExecuteCommand(
            action = CommandAction.RENAME,
            args = CommandArgs(
                path = "/ext/subghz/garage.sub",
                newName = "front_gate.sub"
            ),
            justification = "Renaming garage remote to more descriptive name",
            expectedEffect = "Rename file from garage.sub to front_gate.sub"
        )

        assertEquals(CommandAction.RENAME, command.action)
        assertEquals("front_gate.sub", command.args.newName)
    }

    // ============================================
    // Device Operations
    // ============================================

    @Test
    fun `user request - check battery and storage`() {
        // Simulates: "How's my Flipper doing?" or "Check device status"
        val command = ExecuteCommand(
            action = CommandAction.GET_DEVICE_INFO,
            args = CommandArgs(),
            justification = "User wants to check device battery and status",
            expectedEffect = "Return battery percentage and charging status"
        )

        assertEquals(CommandAction.GET_DEVICE_INFO, command.action)
    }

    @Test
    fun `user request - check storage space`() {
        // Simulates: "How much space do I have left?"
        val command = ExecuteCommand(
            action = CommandAction.GET_STORAGE_INFO,
            args = CommandArgs(),
            justification = "User wants to check available storage",
            expectedEffect = "Return internal and SD card storage usage"
        )

        assertEquals(CommandAction.GET_STORAGE_INFO, command.action)
    }

    // ============================================
    // App/FAP Management
    // ============================================

    @Test
    fun `user request - list installed apps`() {
        // Simulates: "What apps do I have installed?"
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/apps"),
            justification = "User wants to see installed Flipper applications",
            expectedEffect = "List all .fap files and app directories"
        )

        assertEquals("/ext/apps", command.args.path)
    }

    @Test
    fun `user request - delete app`() {
        // Simulates: "Remove the snake game"
        val command = ExecuteCommand(
            action = CommandAction.DELETE,
            args = CommandArgs(
                path = "/ext/apps/Games/snake.fap",
                recursive = false
            ),
            justification = "User wants to remove Snake game to free space",
            expectedEffect = "Delete snake.fap application"
        )

        assertEquals(CommandAction.DELETE, command.action)
        assertFalse(command.args.recursive)
    }

    // ============================================
    // Music Player Operations
    // ============================================

    @Test
    fun `user request - list music files`() {
        // Simulates: "What songs are on my Flipper?"
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/music_player"),
            justification = "User wants to see music files for Flipper music player",
            expectedEffect = "List all .fmf files"
        )

        assertEquals("/ext/music_player", command.args.path)
    }

    // ============================================
    // Command Result Parsing
    // ============================================

    @Test
    fun `parse successful list directory result`() {
        val result = CommandResult(
            success = true,
            action = CommandAction.LIST_DIRECTORY,
            data = CommandResultData(
                entries = listOf(
                    FileEntry("garage.sub", "/ext/subghz/garage.sub", false, 1245),
                    FileEntry("car_key.sub", "/ext/subghz/car_key.sub", false, 892),
                    FileEntry("gate.sub", "/ext/subghz/gate.sub", false, 1567),
                    FileEntry("backup", "/ext/subghz/backup", true, 0)
                )
            ),
            executionTimeMs = 45
        )

        assertTrue(result.success)
        assertEquals(4, result.data?.entries?.size)
        assertEquals(1, result.data?.entries?.count { it.isDirectory })
    }

    @Test
    fun `parse file read result`() {
        val result = CommandResult(
            success = true,
            action = CommandAction.READ_FILE,
            data = CommandResultData(
                content = """
                    Filetype: Flipper SubGhz Key File
                    Version: 1
                    Frequency: 315000000
                """.trimIndent()
            ),
            executionTimeMs = 23
        )

        assertTrue(result.success)
        assertNotNull(result.data?.content)
        assertTrue(result.data!!.content!!.contains("315000000"))
    }

    @Test
    fun `parse write result with bytes written`() {
        val result = CommandResult(
            success = true,
            action = CommandAction.WRITE_FILE,
            data = CommandResultData(
                bytesWritten = 1456L
            ),
            executionTimeMs = 89
        )

        assertTrue(result.success)
        assertEquals(1456L, result.data?.bytesWritten)
    }

    @Test
    fun `parse failed command result`() {
        val result = CommandResult(
            success = false,
            action = CommandAction.READ_FILE,
            error = "File not found: /ext/subghz/nonexistent.sub",
            executionTimeMs = 12
        )

        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("not found"))
    }

    @Test
    fun `parse approval required result`() {
        val result = CommandResult(
            success = true,
            action = CommandAction.DELETE,
            data = CommandResultData(
                message = "Awaiting user approval for recursive deletion"
            ),
            requiresConfirmation = true,
            pendingApprovalId = "approval-12345"
        )

        assertTrue(result.success)
        assertTrue(result.requiresConfirmation)
        assertNotNull(result.pendingApprovalId)
    }
}
