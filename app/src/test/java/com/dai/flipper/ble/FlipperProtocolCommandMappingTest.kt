package com.dai.flipper.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipperProtocolCommandMappingTest {

    @Test
    fun `ble spam space alias resolves rpc mapping and preferred route`() {
        val protocol = FlipperProtocol()

        assertTrue(protocol.hasRpcAppCommandMapping("ble spam apple_airpods"))
        assertTrue(protocol.hasRpcAppCommandMapping("ble spam stop"))

        val profile = FirmwareCompatibilityProfile(
            family = FirmwareFamily.UNKNOWN,
            label = "test",
            transportMode = FirmwareTransportMode.CLI_AND_RPC,
            supportsCli = true,
            supportsRpc = true,
            supportsRpcAppBridge = true,
            confidence = 1f
        )
        val compatibility = FirmwareCompatibilityLayer.assessCliCommand(
            profile = profile,
            command = "ble spam apple_airpods",
            hasRpcMapping = true
        )
        assertTrue(compatibility.supported)
        assertEquals(FirmwareCommandRoute.RPC_APP_BRIDGE, compatibility.route)
    }

    @Test
    fun `ble scan space alias resolves rpc mapping`() {
        val protocol = FlipperProtocol()

        assertTrue(protocol.hasRpcAppCommandMapping("ble scan"))
        assertTrue(protocol.hasRpcAppCommandMapping("ble scan quick"))
    }
}
