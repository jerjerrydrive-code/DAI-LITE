package com.dai.flipper.ai

import com.dai.flipper.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the OpenRouter client command parsing.
 * Validates that AI responses are correctly parsed into ExecuteCommand objects.
 */
class OpenRouterClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `parse list directory command`() {
        val arguments = """
        {
            "action": "list_directory",
            "args": {
                "path": "/ext/subghz"
            },
            "justification": "User wants to see their SubGHz captures",
            "expected_effect": "Return list of .sub files"
        }
        """.trimIndent()

        val command = json.decodeFromString<ExecuteCommand>(arguments)

        assertEquals(CommandAction.LIST_DIRECTORY, command.action)
        assertEquals("/ext/subghz", command.args.path)
        assertEquals("User wants to see their SubGHz captures", command.justification)
    }

    @Test
    fun `parse read file command`() {
        val arguments = """
        {
            "action": "read_file",
            "args": {
                "path": "/ext/subghz/garage_door.sub"
            },
            "justification": "User wants to inspect garage door remote configuration",
            "expected_effect": "Return contents of the SubGHz capture file"
        }
        """.trimIndent()

        val command = json.decodeFromString<ExecuteCommand>(arguments)

        assertEquals(CommandAction.READ_FILE, command.action)
        assertEquals("/ext/subghz/garage_door.sub", command.args.path)
    }

    @Test
    fun `parse write file command with content`() {
        val arguments = """
        {
            "action": "write_file",
            "args": {
                "path": "/ext/infrared/TV_Remote.ir",
                "content": "Filetype: IR signals file\nVersion: 1\n#\nname: Power\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\ncommand: 08 00 00 00\n"
            },
            "justification": "Creating new IR remote file for TV",
            "expected_effect": "Create TV remote with power button"
        }
        """.trimIndent()

        val command = json.decodeFromString<ExecuteCommand>(arguments)

        assertEquals(CommandAction.WRITE_FILE, command.action)
        assertEquals("/ext/infrared/TV_Remote.ir", command.args.path)
        assertNotNull(command.args.content)
        assertTrue(command.args.content!!.contains("Filetype: IR signals file"))
    }

    @Test
    fun `parse delete command with recursive flag`() {
        val arguments = """
        {
            "action": "delete",
            "args": {
                "path": "/ext/test_folder",
                "recursive": true
            },
            "justification": "User requested cleanup of test files",
            "expected_effect": "Remove test_folder and all contents"
        }
        """.trimIndent()

        val command = json.decodeFromString<ExecuteCommand>(arguments)

        assertEquals(CommandAction.DELETE, command.action)
        assertEquals("/ext/test_folder", command.args.path)
        assertTrue(command.args.recursive)
    }

    @Test
    fun `parse move command`() {
        val arguments = """
        {
            "action": "move",
            "args": {
                "path": "/ext/subghz/old_remote.sub",
                "destination_path": "/ext/subghz/backup/old_remote.sub"
            },
            "justification": "Organizing files into backup folder",
            "expected_effect": "Move file to backup directory"
        }
        """.trimIndent()

        val command = json.decodeFromString<ExecuteCommand>(arguments)

        assertEquals(CommandAction.MOVE, command.action)
        assertEquals("/ext/subghz/old_remote.sub", command.args.path)
        assertEquals("/ext/subghz/backup/old_remote.sub", command.args.destinationPath)
    }

    @Test
    fun `parse push artifact command`() {
        val arguments = """
        {
            "action": "push_artifact",
            "args": {
                "path": "/ext/apps/Games/snake.fap",
                "artifact_type": "fap",
                "artifact_data": "UEsDBBQAAAAIAA..."
            },
            "justification": "Installing Snake game app",
            "expected_effect": "Deploy Snake app to Flipper"
        }
        """.trimIndent()

        val command = json.decodeFromString<ExecuteCommand>(arguments)

        assertEquals(CommandAction.PUSH_ARTIFACT, command.action)
        assertEquals("fap", command.args.artifactType)
        assertNotNull(command.args.artifactData)
    }

    @Test
    fun `parse get device info command`() {
        val arguments = """
        {
            "action": "get_device_info",
            "args": {},
            "justification": "User wants to check device status",
            "expected_effect": "Return battery level and firmware version"
        }
        """.trimIndent()

        val command = json.decodeFromString<ExecuteCommand>(arguments)

        assertEquals(CommandAction.GET_DEVICE_INFO, command.action)
    }

    @Test
    fun `serialize command result with file entries`() {
        val result = CommandResult(
            success = true,
            action = CommandAction.LIST_DIRECTORY,
            data = CommandResultData(
                entries = listOf(
                    FileEntry("garage.sub", "/ext/subghz/garage.sub", false, 1024),
                    FileEntry("car.sub", "/ext/subghz/car.sub", false, 2048),
                    FileEntry("backup", "/ext/subghz/backup", true, 0)
                )
            ),
            executionTimeMs = 150
        )

        val jsonString = json.encodeToString(CommandResult.serializer(), result)

        assertTrue(jsonString.contains("garage.sub"))
        assertTrue(jsonString.contains("\"is_directory\":true"))
    }
}
