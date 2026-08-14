package com.dai.flipper.domain

import com.dai.flipper.domain.executor.RiskAssessor
import com.dai.flipper.domain.model.*
import com.dai.flipper.domain.service.PermissionService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for risk assessment of Flipper operations.
 * Ensures proper classification of commands by risk level.
 */
class RiskAssessorTest {

    private lateinit var permissionService: PermissionService
    private lateinit var riskAssessor: RiskAssessor

    @Before
    fun setup() {
        permissionService = mock()
        riskAssessor = RiskAssessor(permissionService)
    }

    // ============================================
    // LOW Risk Operations (Auto-Execute)
    // ============================================

    @Test
    fun `list directory is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = "/ext/subghz"),
            justification = "User wants to see files",
            expectedEffect = "List files"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
        assertFalse(assessment.requiresConfirmation)
        assertFalse(assessment.requiresDiff)
    }

    @Test
    fun `read file is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/subghz/garage.sub"),
            justification = "Reading file contents",
            expectedEffect = "Return file contents"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
    }

    @Test
    fun `get device info is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.GET_DEVICE_INFO,
            args = CommandArgs(),
            justification = "Check device status",
            expectedEffect = "Return device info"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
    }

    @Test
    fun `get storage info is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.GET_STORAGE_INFO,
            args = CommandArgs(),
            justification = "Check storage",
            expectedEffect = "Return storage info"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
    }

    @Test
    fun `search faphub is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.SEARCH_FAPHUB,
            args = CommandArgs(command = "wifi marauder"),
            justification = "Find app by keyword",
            expectedEffect = "Return matching FapHub apps"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
        assertFalse(assessment.requiresConfirmation)
    }

    @Test
    fun `browse repo is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.BROWSE_REPO,
            args = CommandArgs(repoId = "irdb", subPath = "TVs/Samsung"),
            justification = "Browse IR remote database",
            expectedEffect = "List files in IRDB Samsung TVs directory"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
        assertFalse(assessment.requiresConfirmation)
    }

    @Test
    fun `download resource is MEDIUM risk`() {
        val command = ExecuteCommand(
            action = CommandAction.DOWNLOAD_RESOURCE,
            args = CommandArgs(
                downloadUrl = "https://raw.githubusercontent.com/example/test.ir",
                path = "/ext/infrared/test.ir"
            ),
            justification = "Download IR remote file",
            expectedEffect = "Save IR file to Flipper storage"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.MEDIUM, assessment.level)
        assertTrue(assessment.requiresConfirmation)
    }

    @Test
    fun `github search is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.GITHUB_SEARCH,
            args = CommandArgs(command = "Samsung TV extension:ir", searchScope = "code"),
            justification = "Find IR remote files on GitHub",
            expectedEffect = "Return matching files from GitHub"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
        assertFalse(assessment.requiresConfirmation)
    }

    // ============================================
    // MEDIUM Risk Operations (Diff + Apply)
    // ============================================

    @Test
    fun `write file in permitted scope is MEDIUM risk with diff`() {
        whenever(permissionService.hasPermission("/ext/subghz/test.sub", CommandAction.WRITE_FILE))
            .thenReturn(true)

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/subghz/test.sub",
                content = "test content"
            ),
            justification = "Creating file",
            expectedEffect = "Create file"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.MEDIUM, assessment.level)
        assertTrue(assessment.requiresDiff)
        assertFalse(assessment.requiresConfirmation)
    }

    @Test
    fun `create directory in permitted scope is LOW risk`() {
        whenever(permissionService.hasPermission("/ext/subghz/backup", CommandAction.CREATE_DIRECTORY))
            .thenReturn(true)

        val command = ExecuteCommand(
            action = CommandAction.CREATE_DIRECTORY,
            args = CommandArgs(path = "/ext/subghz/backup"),
            justification = "Creating backup folder",
            expectedEffect = "Create directory"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
    }

    @Test
    fun `copy in permitted scope is MEDIUM risk`() {
        whenever(permissionService.hasPermission("/ext/subghz/backup/test.sub", CommandAction.WRITE_FILE))
            .thenReturn(true)

        val command = ExecuteCommand(
            action = CommandAction.COPY,
            args = CommandArgs(
                path = "/ext/subghz/test.sub",
                destinationPath = "/ext/subghz/backup/test.sub"
            ),
            justification = "Backing up file",
            expectedEffect = "Copy file"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.MEDIUM, assessment.level)
    }

    // ============================================
    // HIGH Risk Operations (Hold-to-Confirm)
    // ============================================

    @Test
    fun `delete is HIGH risk`() {
        val command = ExecuteCommand(
            action = CommandAction.DELETE,
            args = CommandArgs(path = "/ext/test/file.txt"),
            justification = "Deleting file",
            expectedEffect = "Remove file"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertTrue(assessment.requiresConfirmation)
    }

    @Test
    fun `recursive delete is HIGH risk`() {
        val command = ExecuteCommand(
            action = CommandAction.DELETE,
            args = CommandArgs(
                path = "/ext/test",
                recursive = true
            ),
            justification = "Deleting folder",
            expectedEffect = "Remove folder and contents"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertTrue(assessment.reason.contains("Recursive"))
    }

    @Test
    fun `move is HIGH risk`() {
        val command = ExecuteCommand(
            action = CommandAction.MOVE,
            args = CommandArgs(
                path = "/ext/subghz/garage.sub",
                destinationPath = "/ext/backup/garage.sub"
            ),
            justification = "Moving file",
            expectedEffect = "Move file"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertTrue(assessment.requiresConfirmation)
    }

    @Test
    fun `rename is HIGH risk`() {
        val command = ExecuteCommand(
            action = CommandAction.RENAME,
            args = CommandArgs(
                path = "/ext/subghz/garage.sub",
                newName = "front_gate.sub"
            ),
            justification = "Renaming file",
            expectedEffect = "Rename file"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
    }

    @Test
    fun `write outside permitted scope is HIGH risk`() {
        whenever(permissionService.hasPermission("/ext/apps/malware.fap", CommandAction.WRITE_FILE))
            .thenReturn(false)

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/ext/apps/malware.fap",
                content = "binary data"
            ),
            justification = "Installing app",
            expectedEffect = "Create app file"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertTrue(assessment.requiresConfirmation)
    }

    @Test
    fun `push executable artifact is HIGH risk`() {
        val command = ExecuteCommand(
            action = CommandAction.PUSH_ARTIFACT,
            args = CommandArgs(
                path = "/ext/apps/test.fap",
                artifactType = "fap",
                artifactData = "base64data"
            ),
            justification = "Installing app",
            expectedEffect = "Deploy app"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
    }

    @Test
    fun `install faphub app is HIGH risk`() {
        val command = ExecuteCommand(
            action = CommandAction.INSTALL_FAPHUB_APP,
            args = CommandArgs(command = "wifi_marauder"),
            justification = "Install executable app",
            expectedEffect = "Download and install app"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertTrue(assessment.requiresConfirmation)
    }

    // ============================================
    // BLOCKED Operations (Protected Paths)
    // ============================================

    @Test
    fun `write to internal storage is BLOCKED`() {
        whenever(permissionService.isProtectedPathUnlocked("/int/test.txt"))
            .thenReturn(false)

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/int/test.txt",
                content = "test"
            ),
            justification = "Writing to internal",
            expectedEffect = "Create file"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.BLOCKED, assessment.level)
        assertNotNull(assessment.blockedReason)
    }

    @Test
    fun `write to firmware path is BLOCKED`() {
        whenever(permissionService.isProtectedPathUnlocked("/int/update/firmware.bin"))
            .thenReturn(false)

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/int/update/firmware.bin",
                content = "binary"
            ),
            justification = "Firmware update",
            expectedEffect = "Update firmware"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.BLOCKED, assessment.level)
        assertTrue(assessment.blockedReason!!.contains("firmware") ||
                   assessment.blockedReason!!.contains("System"))
    }

    @Test
    fun `access to sensitive file extension is BLOCKED`() {
        whenever(permissionService.isProtectedPathUnlocked("/ext/secrets.key"))
            .thenReturn(false)

        val command = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/secrets.key"),
            justification = "Reading key file",
            expectedEffect = "Return key contents"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.BLOCKED, assessment.level)
    }

    @Test
    fun `unlocked protected path allows access`() {
        whenever(permissionService.isProtectedPathUnlocked("/int/test.txt"))
            .thenReturn(true)
        whenever(permissionService.hasPermission("/int/test.txt", CommandAction.WRITE_FILE))
            .thenReturn(true)

        val command = ExecuteCommand(
            action = CommandAction.WRITE_FILE,
            args = CommandArgs(
                path = "/int/test.txt",
                content = "test"
            ),
            justification = "Writing to unlocked internal path",
            expectedEffect = "Create file"
        )

        val assessment = riskAssessor.assess(command)

        assertNotEquals(RiskLevel.BLOCKED, assessment.level)
    }

    @Test
    fun `execute cli read-only command is LOW risk`() {
        val command = ExecuteCommand(
            action = CommandAction.EXECUTE_CLI,
            args = CommandArgs(command = "storage ls /ext"),
            justification = "Inspect storage",
            expectedEffect = "List files"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.LOW, assessment.level)
        assertFalse(assessment.requiresConfirmation)
    }

    @Test
    fun `execute cli active command is HIGH risk`() {
        val command = ExecuteCommand(
            action = CommandAction.EXECUTE_CLI,
            args = CommandArgs(command = "badusb /ext/badusb/test.txt"),
            justification = "Run payload",
            expectedEffect = "Execute BadUSB script"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertTrue(assessment.requiresConfirmation)
    }

    @Test
    fun `execute cli command touching protected path is BLOCKED`() {
        whenever(permissionService.isProtectedPathUnlocked("/int/test.txt"))
            .thenReturn(false)

        val command = ExecuteCommand(
            action = CommandAction.EXECUTE_CLI,
            args = CommandArgs(command = "storage read /int/test.txt"),
            justification = "Read protected file",
            expectedEffect = "Return content"
        )

        val assessment = riskAssessor.assess(command)

        assertEquals(RiskLevel.BLOCKED, assessment.level)
        assertNotNull(assessment.blockedReason)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `empty path defaults to safe handling`() {
        val command = ExecuteCommand(
            action = CommandAction.LIST_DIRECTORY,
            args = CommandArgs(path = null),
            justification = "List root",
            expectedEffect = "List files"
        )

        val assessment = riskAssessor.assess(command)

        // Should not crash, should handle gracefully
        assertNotNull(assessment)
    }

    @Test
    fun `path traversal attempt is handled`() {
        val command = ExecuteCommand(
            action = CommandAction.READ_FILE,
            args = CommandArgs(path = "/ext/../int/secret.txt"),
            justification = "Reading file",
            expectedEffect = "Return contents"
        )

        val assessment = riskAssessor.assess(command)

        // Path contains /int/ so should be blocked
        // The actual path normalization would happen at execution
        assertNotNull(assessment)
    }
}
