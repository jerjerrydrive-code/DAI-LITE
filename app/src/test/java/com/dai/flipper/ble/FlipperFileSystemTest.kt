package com.dai.flipper.ble

import com.dai.flipper.security.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlipperFileSystemTest {

    private lateinit var protocol: FlipperProtocol
    private lateinit var securityUtils: SecurityUtils
    private lateinit var fileSystem: FlipperFileSystem

    @Before
    fun setup() {
        protocol = mock()
        securityUtils = mock()
        whenever(protocol.firmwareCompatibility).thenReturn(
            MutableStateFlow(
                FirmwareCompatibilityProfile(
                    family = FirmwareFamily.UNKNOWN,
                    label = "test",
                    transportMode = FirmwareTransportMode.CLI_AND_RPC,
                    supportsCli = true,
                    supportsRpc = true,
                    supportsRpcAppBridge = true,
                    confidence = 1f
                )
            )
        )
        fileSystem = FlipperFileSystem(protocol, securityUtils)
    }

    @Test
    fun `copy prefers cli copy when supported`() {
        runBlocking {
            val src = "/ext/source.bin"
            val dest = "/ext/dest.bin"
            whenever(securityUtils.validatePath(src)).thenReturn(src)
            whenever(securityUtils.validatePath(dest)).thenReturn(dest)
            whenever(protocol.sendCliCommand("storage copy $src $dest"))
                .thenReturn(ProtocolResponse.Success("copied"))

            val result = fileSystem.copy(src, dest)

            assertTrue(result.isSuccess)
            verify(protocol).sendCliCommand("storage copy $src $dest")
            verify(protocol, never()).readFileBinary(any<String>())
        }
    }

    @Test
    fun `copy falls back to read-write when cli copy fails`() {
        runBlocking {
            val src = "/ext/source.txt"
            val dest = "/ext/dest.txt"
            whenever(securityUtils.validatePath(src)).thenReturn(src)
            whenever(securityUtils.validatePath(dest)).thenReturn(dest)
            whenever(protocol.sendCliCommand("storage copy $src $dest"))
                .thenReturn(ProtocolResponse.Error("unknown command"))
            whenever(protocol.readFileBinary(src))
                .thenReturn(ProtocolResponse.BinaryContent("hello".toByteArray(Charsets.UTF_8)))
            whenever(protocol.writeFile(dest, "hello".toByteArray(Charsets.UTF_8)))
                .thenReturn(ProtocolResponse.Success("ok"))

            val result = fileSystem.copy(src, dest)

            assertTrue(result.isSuccess)
            verify(protocol).readFileBinary(src)
            val payloadCaptor = argumentCaptor<ByteArray>()
            verify(protocol).writeFile(eq(dest), payloadCaptor.capture())
            assertTrue(payloadCaptor.firstValue.contentEquals("hello".toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun `executeCli attempts rpc to cli recovery before failing`() {
        runBlocking {
            val initialStatus = CliCapabilityStatus(
                level = CliCapabilityLevel.READY,
                checkedAtMs = System.currentTimeMillis(),
                supportsCli = false,
                supportsRpc = true,
                details = "RPC-only transport"
            )
            val recoveredStatus = CliCapabilityStatus(
                level = CliCapabilityLevel.READY,
                checkedAtMs = System.currentTimeMillis(),
                supportsCli = true,
                supportsRpc = false,
                details = "CLI restored"
            )
            whenever(protocol.cliStatus).thenReturn(MutableStateFlow(initialStatus))
            whenever(protocol.recoverCliFromRpcSession()).thenReturn(recoveredStatus)
            whenever(protocol.sendCliCommand("version"))
                .thenReturn(ProtocolResponse.FileContent("firmware 1.0.0"))

            val result = fileSystem.executeCli("version")

            assertTrue(result.isSuccess)
            verify(protocol).recoverCliFromRpcSession()
            verify(protocol).sendCliCommand("version")
        }
    }

    @Test
    fun `executeCli fails with recovery details when cli remains unavailable`() {
        runBlocking {
            val initialStatus = CliCapabilityStatus(
                level = CliCapabilityLevel.READY,
                checkedAtMs = System.currentTimeMillis(),
                supportsCli = false,
                supportsRpc = true,
                details = "RPC-only transport"
            )
            val recoveredStatus = CliCapabilityStatus(
                level = CliCapabilityLevel.READY,
                checkedAtMs = System.currentTimeMillis(),
                supportsCli = false,
                supportsRpc = true,
                details = "stop_rpc_session failed"
            )
            whenever(protocol.cliStatus).thenReturn(MutableStateFlow(initialStatus))
            whenever(protocol.recoverCliFromRpcSession()).thenReturn(recoveredStatus)

            val result = fileSystem.executeCli("version")

            assertFalse(result.isSuccess)
            val errorMessage = result.exceptionOrNull()?.message
            assertNotNull(errorMessage)
            assertTrue(errorMessage!!.contains("stop_rpc_session failed"))
            verify(protocol).recoverCliFromRpcSession()
            verify(protocol, never()).sendCliCommand(any())
        }
    }

    @Test
    fun `executeCli uses rpc app bridge for actionable commands on rpc-only transport without cli recovery`() {
        runBlocking {
            val initialStatus = CliCapabilityStatus(
                level = CliCapabilityLevel.READY,
                checkedAtMs = System.currentTimeMillis(),
                supportsCli = false,
                supportsRpc = true,
                details = "RPC-only transport"
            )
            whenever(protocol.cliStatus).thenReturn(MutableStateFlow(initialStatus))
            whenever(protocol.hasRpcAppCommandMapping("badusb /ext/badusb/test.txt"))
                .thenReturn(true)
            whenever(protocol.executeRpcAppCommand("badusb /ext/badusb/test.txt"))
                .thenReturn(ProtocolResponse.Success("RPC app command accepted"))

            val result = fileSystem.executeCli("badusb /ext/badusb/test.txt")

            assertTrue(result.isSuccess)
            verify(protocol, never()).recoverCliFromRpcSession()
            verify(protocol).hasRpcAppCommandMapping("badusb /ext/badusb/test.txt")
            verify(protocol).executeRpcAppCommand("badusb /ext/badusb/test.txt")
            verify(protocol, never()).sendCliCommand(any())
        }
    }
}
